package com.myname.wildcardpattern.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import com.myname.wildcardpattern.ModItems;
import com.myname.wildcardpattern.item.WildcardPatternConfig;
import com.myname.wildcardpattern.item.WildcardPatternState;
import gregtech.api.enums.Materials;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

public final class WildcardPatternGenerator {

    private static final String KEY_WILDCARD = "WildcardPattern";
    private static final String KEY_SELECTED_MATERIAL = "WildcardSelectedMaterial";
    private static final int MAX_RULES = 9;

    private WildcardPatternGenerator() {}

    public static boolean isWildcardPattern(ItemStack stack) {
        return stack != null
            && (stack.getItem() == ModItems.wildcardPattern
                || stack.hasTagCompound() && stack.getTagCompound().getBoolean(KEY_WILDCARD));
    }

    public static void markAsWildcard(ItemStack stack) {
        if (stack == null) {
            return;
        }
        NBTTagCompound tag = getOrCreateTag(stack);
        tag.setBoolean(KEY_WILDCARD, true);
        WildcardPatternState.ensureInitialized(stack);
    }

    public static int countPatterns(ItemStack stack) {
        return generateAllDetails(stack, null).size();
    }

    public static ICraftingPatternDetails getFirstDetail(ItemStack stack, World world) {
        List<ICraftingPatternDetails> details = generateAllDetails(stack, world);
        return details.isEmpty() ? null : details.get(0);
    }

    public static List<ICraftingPatternDetails> generateAllDetails(ItemStack stack, World world) {
        if (!isWildcardPattern(stack)) {
            return Collections.emptyList();
        }

        List<ICraftingPatternDetails> result = new ArrayList<>();
        for (int ruleIndex = 0; ruleIndex < MAX_RULES; ruleIndex++) {
            result.addAll(generateRuleDetails(stack, world, ruleIndex));
        }
        return result;
    }

    public static List<ICraftingPatternDetails> generateRuleDetails(ItemStack stack, World world, int ruleIndex) {
        if (!isWildcardPattern(stack)) {
            return Collections.emptyList();
        }
        if (ruleIndex < 0 || ruleIndex >= MAX_RULES) {
            return Collections.emptyList();
        }

        List<WildcardPatternEntry> inputs = WildcardPatternState.getInputEntries(stack);
        List<WildcardPatternEntry> outputs = WildcardPatternState.getOutputEntries(stack);
        if (ruleIndex >= inputs.size() || ruleIndex >= outputs.size()) {
            return Collections.emptyList();
        }

        WildcardPatternEntry input = inputs.get(ruleIndex);
        WildcardPatternEntry output = outputs.get(ruleIndex);
        if ((input == null || input.isEmpty()) && (output == null || output.isEmpty())) {
            return Collections.emptyList();
        }

        List<ICraftingPatternDetails> result = new ArrayList<>();
        for (Materials candidate : getCandidateMaterials(stack, ruleIndex)) {
            ItemStack generated = createPatternStack(stack, candidate, input, output);
            if (generated == null) {
                continue;
            }
            ICraftingPatternDetails detail = createDetailForCurrentStack(generated, world);
            if (detail != null) {
                result.add(detail);
            }
        }
        return result;
    }

    public static List<Materials> getCandidateMaterials(ItemStack stack) {
        List<Materials> result = new ArrayList<>();
        for (int ruleIndex = 0; ruleIndex < MAX_RULES; ruleIndex++) {
            for (Materials material : getCandidateMaterials(stack, ruleIndex)) {
                if (!result.contains(material)) {
                    result.add(material);
                }
            }
        }
        return result;
    }

    public static List<Materials> getCandidateMaterials(ItemStack stack, int ruleIndex) {
        if (!isWildcardPattern(stack) || ruleIndex < 0 || ruleIndex >= MAX_RULES) {
            return Collections.emptyList();
        }

        List<WildcardPatternEntry> inputs = WildcardPatternState.getInputEntries(stack);
        List<WildcardPatternEntry> outputs = WildcardPatternState.getOutputEntries(stack);
        if (ruleIndex >= inputs.size() || ruleIndex >= outputs.size()) {
            return Collections.emptyList();
        }

        WildcardPatternEntry input = inputs.get(ruleIndex);
        WildcardPatternEntry output = outputs.get(ruleIndex);
        if ((input == null || input.isEmpty()) && (output == null || output.isEmpty())) {
            return Collections.emptyList();
        }

        List<Materials> result = new ArrayList<>();
        for (Materials candidate : Materials.getAll()) {
            if (!isRealMaterial(candidate)) {
                continue;
            }
            if (!WildcardPatternConfig.acceptsMaterial(stack, ruleIndex, candidate)) {
                continue;
            }
            if (canFillRule(stack, candidate, input, output)) {
                result.add(candidate);
            }
        }
        return result;
    }

    public static ICraftingPatternDetails createDetailForCurrentStack(ItemStack stack, World world) {
        try {
            return new WildcardPatternDetails(stack, world);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ItemStack createPatternStack(
        ItemStack template,
        Materials material,
        WildcardPatternEntry input,
        WildcardPatternEntry output) {
        if (!isRealMaterial(material)) {
            return null;
        }

        NBTTagList inputList = buildPatternList(template, input, material);
        NBTTagList outputList = buildPatternList(template, output, material);
        if (inputList == null || outputList == null) {
            return null;
        }

        ItemStack result = template.copy();
        NBTTagCompound resultTag = getOrCreateTag(result);
        resultTag.setTag("in", inputList);
        resultTag.setTag("out", outputList);
        resultTag.setString(KEY_SELECTED_MATERIAL, material.mName);
        resultTag.setBoolean("crafting", false);
        resultTag.removeTag("InvalidPattern");
        return result;
    }

    private static NBTTagList buildPatternList(ItemStack template, WildcardPatternEntry entry, Materials candidate) {
        NBTTagList rewritten = new NBTTagList();
        if (entry == null || entry.isEmpty()) {
            rewritten.appendTag(new NBTTagCompound());
            return rewritten;
        }
        ItemStack stack = entry.createStack(candidate, template);
        if (stack == null) {
            return null;
        }
        NBTTagCompound rewrittenTag = new NBTTagCompound();
        stack.writeToNBT(rewrittenTag);
        int count = Math.max(1, stack.stackSize);
        rewrittenTag.setInteger("Count", count);
        rewrittenTag.setLong("Cnt", count);
        rewritten.appendTag(rewrittenTag);
        return rewritten;
    }

    private static boolean canFillRule(
        ItemStack template,
        Materials candidate,
        WildcardPatternEntry input,
        WildcardPatternEntry output) {
        return (input == null || input.isEmpty() || input.createStack(candidate, template) != null)
            && (output == null || output.isEmpty() || output.createStack(candidate, template) != null);
    }

    private static boolean isRealMaterial(Materials material) {
        return material != null && material != Materials._NULL && material != Materials.Empty;
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }
}
