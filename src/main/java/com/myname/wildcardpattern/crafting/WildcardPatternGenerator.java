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

    public static int countPreviewPatterns(ItemStack stack) {
        if (!isWildcardPattern(stack)) {
            return 0;
        }

        int count = 0;
        for (int ruleIndex = 0; ruleIndex < MAX_RULES; ruleIndex++) {
            count += generateRulePreviewPatterns(stack, ruleIndex).size();
        }
        return count;
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
        List<GeneratedPattern> patterns = generateRulePreviewPatterns(stack, ruleIndex);
        if (patterns.isEmpty()) {
            return Collections.emptyList();
        }

        List<ICraftingPatternDetails> result = new ArrayList<>();
        for (GeneratedPattern pattern : patterns) {
            ItemStack generated = createPatternStack(stack, pattern.materialName, pattern.inputStack, pattern.outputStack);
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

    public static List<GeneratedPattern> generateRulePreviewPatterns(ItemStack stack, int ruleIndex) {
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

        List<GeneratedPattern> result = new ArrayList<>();
        for (String candidate : collectRuleCandidatePool(input, output)) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            ItemStack inputStack = input == null || input.isEmpty() ? null : input.createStack(candidate, stack);
            ItemStack outputStack = output == null || output.isEmpty() ? null : output.createStack(candidate, stack);
            if ((input != null && !input.isEmpty() && inputStack == null) || (output != null && !output.isEmpty() && outputStack == null)) {
                continue;
            }
            if (!WildcardPatternConfig.acceptsCandidate(stack, ruleIndex, candidate, inputStack, outputStack)) {
                continue;
            }
            result.add(new GeneratedPattern(candidate, inputStack, outputStack));
        }
        return result;
    }

    private static Iterable<String> collectRuleCandidatePool(WildcardPatternEntry input, WildcardPatternEntry output) {
        Set<String> candidates = null;
        boolean narrowed = false;

        Set<String> inputCandidates = input == null || input.isEmpty() ? java.util.Collections.<String>emptySet() : input.getCandidateMaterials();
        if (inputCandidates != null && !inputCandidates.isEmpty()) {
            candidates = mergeCandidatePool(candidates, inputCandidates);
            narrowed = true;
        }

        Set<String> outputCandidates = output == null || output.isEmpty() ? java.util.Collections.<String>emptySet() : output.getCandidateMaterials();
        if (outputCandidates != null && !outputCandidates.isEmpty()) {
            candidates = mergeCandidatePool(candidates, outputCandidates);
            narrowed = true;
        }

        if (narrowed) {
            return candidates == null ? java.util.Collections.<String>emptySet() : candidates;
        }

        // Neither entry produced ore-dict candidates. If any non-empty name-mode entry is a
        // "direct item" (no recognised ore prefix — e.g. a vanilla block), avoid the
        // O(materials × prefixes) explosion that would come from getAllKnownMaterialNames().
        // Instead use a single synthetic key; createNameMatchedStack will return the display
        // stack directly for such entries regardless of what that key says.
        String directKey = getDirectItemKey(input, output);
        if (directKey != null) {
            Set<String> direct = new LinkedHashSet<>();
            direct.add(directKey);
            return direct;
        }

        return WildcardPatternEntry.getAllKnownMaterialNames();
    }

    private static String getDirectItemKey(WildcardPatternEntry input, WildcardPatternEntry output) {
        if (input != null && !input.isEmpty() && !input.isOreDict() && input.isDirectItem()) {
            String key = input.getMatcher();
            if (!key.isEmpty()) {
                return key;
            }
        }
        if (output != null && !output.isEmpty() && !output.isOreDict() && output.isDirectItem()) {
            String key = output.getMatcher();
            if (!key.isEmpty()) {
                return key;
            }
        }
        return null;
    }

    private static Set<String> mergeCandidatePool(Set<String> current, Set<String> narrowed) {
        if (current == null) {
            return new LinkedHashSet<>(narrowed);
        }
        current.retainAll(narrowed);
        return current;
    }

    public static List<String> getCandidateMaterials(ItemStack stack) {
        List<String> result = new ArrayList<>();
        for (int ruleIndex = 0; ruleIndex < MAX_RULES; ruleIndex++) {
            for (String materialName : getCandidateMaterials(stack, ruleIndex)) {
                if (!result.contains(materialName)) {
                    result.add(materialName);
                }
            }
        }
        return result;
    }

    public static List<String> getCandidateMaterials(ItemStack stack, int ruleIndex) {
        List<GeneratedPattern> patterns = generateRulePreviewPatterns(stack, ruleIndex);
        if (patterns.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (GeneratedPattern pattern : patterns) {
            if (pattern.materialName != null && !pattern.materialName.isEmpty() && !result.contains(pattern.materialName)) {
                result.add(pattern.materialName);
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

        private GeneratedPattern(String materialName, ItemStack inputStack, ItemStack outputStack) {
            this.materialName = materialName == null ? "" : materialName;
            this.inputStack = inputStack == null ? null : inputStack.copy();
            this.outputStack = outputStack == null ? null : outputStack.copy();
        }
    }
}
