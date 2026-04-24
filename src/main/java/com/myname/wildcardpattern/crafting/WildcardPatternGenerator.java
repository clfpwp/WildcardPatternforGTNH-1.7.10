package com.myname.wildcardpattern.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    public static ICraftingPatternDetails getDisplayDetails(ItemStack stack, World world) {
        if (!isWildcardPattern(stack)) {
            return null;
        }
        return new WildcardPreviewPatternDetails(stack, getRepresentativeInput(stack), getRepresentativeOutput(stack));
    }

    public static ICraftingPatternDetails getFirstDetail(ItemStack stack, World world) {
        List<ICraftingPatternDetails> details = generateAllDetails(stack, world);
        return details.isEmpty() ? null : details.get(0);
    }

    public static ItemStack getRepresentativeOutput(ItemStack stack) {
        return getRepresentativeEntryStack(WildcardPatternState.getOutputEntries(stack), stack);
    }

    public static ItemStack getRepresentativeInput(ItemStack stack) {
        return getRepresentativeEntryStack(WildcardPatternState.getInputEntries(stack), stack);
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
        List<GeneratedPattern> patterns = generateRulePatterns(stack, ruleIndex);
        if (patterns.isEmpty()) {
            return Collections.emptyList();
        }
        List<ICraftingPatternDetails> result = new ArrayList<>();
        for (GeneratedPattern pattern : patterns) {
            ICraftingPatternDetails detail = createDetailForCurrentStack(pattern.patternStack, world);
            if (detail != null) {
                result.add(detail);
            }
        }
        return result;
    }

    public static List<GeneratedPattern> generateRulePatterns(ItemStack stack, int ruleIndex) {
        return generatePatterns(stack, ruleIndex, true);
    }

    public static List<GeneratedPattern> generateRulePreviewPatterns(ItemStack stack, int ruleIndex) {
        return generatePatterns(stack, ruleIndex, false);
    }

    private static List<GeneratedPattern> generatePatterns(ItemStack stack, int ruleIndex, boolean buildPatternStack) {
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

        List<GeneratedPattern> result = new ArrayList<>();
        for (Materials candidate : getCandidateMaterials(stack, ruleIndex)) {
            List<ItemStack> inputStacks = getGeneratedStacks(input, candidate, stack);
            List<ItemStack> outputStacks = getGeneratedStacks(output, candidate, stack);
            for (ItemStack inputStack : inputStacks) {
                for (ItemStack outputStack : outputStacks) {
                    if (!WildcardPatternConfig.acceptsCandidate(stack, ruleIndex, candidate, inputStack, outputStack)) {
                        continue;
                    }
                    ItemStack generated = buildPatternStack
                        ? createPatternStack(stack, candidate.mName, inputStack, outputStack)
                        : null;
                    if (!buildPatternStack || generated != null) {
                        result.add(new GeneratedPattern(candidate.mName, inputStack, outputStack, generated));
                    }
                }
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

        Iterable<Materials> candidates = collectRuleCandidatePool(input, output);
        List<Materials> result = new ArrayList<>();
        for (Materials candidate : candidates) {
            if (!isRealMaterial(candidate)) {
                continue;
            }
            List<ItemStack> inputStacks = getGeneratedStacks(input, candidate, stack);
            List<ItemStack> outputStacks = getGeneratedStacks(output, candidate, stack);
            boolean accepted = false;
            for (ItemStack inputStack : inputStacks) {
                for (ItemStack outputStack : outputStacks) {
                    if (WildcardPatternConfig.acceptsCandidate(stack, ruleIndex, candidate, inputStack, outputStack)) {
                        accepted = true;
                        break;
                    }
                }
                if (accepted) {
                    break;
                }
            }
            if (accepted) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static Iterable<Materials> collectRuleCandidatePool(WildcardPatternEntry input, WildcardPatternEntry output) {
        Set<Materials> oreCandidates = null;
        if (input != null && input.isOreDict()) {
            oreCandidates = new LinkedHashSet<>(input.getOreDictCandidateMaterials());
        }
        if (output != null && output.isOreDict()) {
            Set<Materials> outputCandidates = new LinkedHashSet<>(output.getOreDictCandidateMaterials());
            if (oreCandidates == null) {
                oreCandidates = outputCandidates;
            } else {
                oreCandidates.retainAll(outputCandidates);
            }
        }
        return oreCandidates != null ? oreCandidates : Materials.getAll();
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
        String materialName,
        ItemStack inputStack,
        ItemStack outputStack) {
        if (inputStack == null && outputStack == null) {
            return null;
        }

        NBTTagList inputList = buildPatternList(inputStack);
        NBTTagList outputList = buildPatternList(outputStack);
        if (inputList == null || outputList == null) {
            return null;
        }

        ItemStack result = template.copy();
        NBTTagCompound resultTag = getOrCreateTag(result);
        resultTag.setTag("in", inputList);
        resultTag.setTag("out", outputList);
        resultTag.setString(KEY_SELECTED_MATERIAL, materialName == null ? "" : materialName);
        resultTag.setBoolean("crafting", false);
        resultTag.removeTag("InvalidPattern");
        return result;
    }

    private static NBTTagList buildPatternList(ItemStack stack) {
        NBTTagList rewritten = new NBTTagList();
        if (stack == null) {
            rewritten.appendTag(new NBTTagCompound());
            return rewritten;
        }
        NBTTagCompound rewrittenTag = new NBTTagCompound();
        stack.writeToNBT(rewrittenTag);
        int count = Math.max(1, stack.stackSize);
        rewrittenTag.setInteger("Count", count);
        rewrittenTag.setLong("Cnt", count);
        rewritten.appendTag(rewrittenTag);
        return rewritten;
    }

    private static List<ItemStack> getGeneratedStacks(WildcardPatternEntry entry, Materials candidate, ItemStack template) {
        if (entry == null || entry.isEmpty()) {
            return java.util.Collections.singletonList(null);
        }
        List<ItemStack> stacks = entry.createStacks(candidate, template);
        return stacks.isEmpty() ? java.util.Collections.<ItemStack>emptyList() : stacks;
    }

    private static boolean isRealMaterial(Materials material) {
        return material != null && material != Materials._NULL && material != Materials.Empty;
    }

    private static ItemStack getRepresentativeEntryStack(List<WildcardPatternEntry> entries, ItemStack fallbackStack) {
        if (entries != null) {
            for (WildcardPatternEntry entry : entries) {
                if (entry == null || entry.isEmpty()) {
                    continue;
                }
                ItemStack display = entry.getDisplayStack();
                if (display != null) {
                    return display;
                }
            }
        }
        if (fallbackStack == null) {
            return null;
        }
        ItemStack fallback = fallbackStack.copy();
        fallback.stackSize = 1;
        return fallback;
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    public static final class GeneratedPattern {
        public final String materialName;
        public final ItemStack inputStack;
        public final ItemStack outputStack;
        public final ItemStack patternStack;

        private GeneratedPattern(String materialName, ItemStack inputStack, ItemStack outputStack, ItemStack patternStack) {
            this.materialName = materialName == null ? "" : materialName;
            this.inputStack = inputStack == null ? null : inputStack.copy();
            this.outputStack = outputStack == null ? null : outputStack.copy();
            this.patternStack = patternStack == null ? null : patternStack.copy();
        }
    }
}
