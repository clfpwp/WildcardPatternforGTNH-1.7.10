package com.myname.wildcardpattern.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import com.myname.wildcardpattern.ModItems;
import com.myname.wildcardpattern.item.CompositeWildcardPatternState;
import com.myname.wildcardpattern.item.WildcardPatternConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

public final class CompositeWildcardPatternGenerator {

    private static final String KEY_COMPOSITE_WILDCARD = "CompositeWildcardPattern";
    private static final String KEY_SELECTED_MATERIAL = "WildcardSelectedMaterial";
    private static final int RULE_INDEX = 0;

    private CompositeWildcardPatternGenerator() {}

    public static boolean isCompositeWildcardPattern(ItemStack stack) {
        return stack != null
            && (stack.getItem() == ModItems.compositeWildcardPattern
                || stack.hasTagCompound() && stack.getTagCompound().getBoolean(KEY_COMPOSITE_WILDCARD));
    }

    public static void markAsCompositeWildcard(ItemStack stack) {
        if (stack == null) {
            return;
        }
        NBTTagCompound tag = getOrCreateTag(stack);
        tag.setBoolean(KEY_COMPOSITE_WILDCARD, true);
        CompositeWildcardPatternState.ensureInitialized(stack);
    }

    public static int countPatterns(ItemStack stack) {
        return generateAllDetails(stack, null).size();
    }

    public static int countPreviewPatterns(ItemStack stack) {
        return generatePreviewPatterns(stack).size();
    }

    public static ICraftingPatternDetails getDisplayDetails(ItemStack stack, World world) {
        if (!isCompositeWildcardPattern(stack)) {
            return null;
        }
        return new WildcardPreviewPatternDetails(stack, getRepresentativeInput(stack), getRepresentativeOutput(stack));
    }

    public static ICraftingPatternDetails getDetailsForItem(ItemStack stack, World world) {
        if (!isCompositeWildcardPattern(stack)) {
            return null;
        }
        markAsCompositeWildcard(stack);
        if (WildcardPatternGenerator.isGeneratedPattern(stack)) {
            return createDetailForCurrentStack(stack, world);
        }
        return getDisplayDetails(stack, world);
    }

    public static ItemStack getOutputForItem(ItemStack stack, World world) {
        if (!isCompositeWildcardPattern(stack)) {
            return null;
        }
        markAsCompositeWildcard(stack);
        if (WildcardPatternGenerator.isGeneratedPattern(stack)) {
            ItemStack generatedOutput = getFirstCondensedOutput(createDetailForCurrentStack(stack, world));
            if (generatedOutput != null) {
                return generatedOutput;
            }
        }
        return getRepresentativeOutput(stack);
    }

    public static ItemStack getRepresentativeOutput(ItemStack stack) {
        WildcardPatternEntry output = CompositeWildcardPatternState.getWildcardOutput(stack);
        if (output != null && !output.isEmpty()) {
            ItemStack display = output.getDisplayStack();
            if (display != null) {
                return display;
            }
        }
        return copyAsOne(stack);
    }

    public static ItemStack getRepresentativeInput(ItemStack stack) {
        WildcardPatternEntry input = CompositeWildcardPatternState.getWildcardInput(stack);
        if (input != null && !input.isEmpty()) {
            ItemStack display = input.getDisplayStack();
            if (display != null) {
                return display;
            }
        }
        for (ItemStack fixed : CompositeWildcardPatternState.getFixedInputs(stack)) {
            if (fixed != null && fixed.getItem() != null) {
                return fixed.copy();
            }
        }
        return copyAsOne(stack);
    }

    public static List<ICraftingPatternDetails> generateAllDetails(ItemStack stack, World world) {
        if (!isCompositeWildcardPattern(stack)) {
            return Collections.emptyList();
        }
        List<ICraftingPatternDetails> result = new ArrayList<>();
        for (GeneratedPattern pattern : generatePreviewPatterns(stack)) {
            ItemStack generated = createPatternStack(stack, pattern.materialName, pattern.inputStacks, pattern.outputStack);
            if (generated == null) {
                continue;
            }
            ICraftingPatternDetails detail = createDetailForCurrentStack(generated, world);
            if (detail != null) {
                result.add(detail);
            }
        }
        result.sort(Comparator.comparing(details -> WildcardPatternGenerator.getPatternIdentity(details == null ? null : details.getPattern())));
        return result;
    }

    public static List<GeneratedPattern> generatePreviewPatterns(ItemStack stack) {
        if (!isCompositeWildcardPattern(stack)) {
            return Collections.emptyList();
        }

        WildcardPatternEntry input = CompositeWildcardPatternState.getWildcardInput(stack);
        WildcardPatternEntry output = CompositeWildcardPatternState.getWildcardOutput(stack);
        if (input == null || input.isEmpty() || output == null || output.isEmpty()) {
            return Collections.emptyList();
        }

        List<ItemStack> fixedInputs = normalizeFixedInputs(CompositeWildcardPatternState.getFixedInputs(stack));
        List<String> candidates = new ArrayList<>();
        for (String candidate : collectCandidatePool(input, output)) {
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        candidates.sort(String.CASE_INSENSITIVE_ORDER);

        List<GeneratedPattern> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            ItemStack wildcardInput = input.createStack(candidate, stack);
            ItemStack outputStack = output.createStack(candidate, stack);
            if (wildcardInput == null || outputStack == null) {
                continue;
            }
            if (!WildcardPatternConfig.acceptsCandidate(stack, RULE_INDEX, candidate, wildcardInput, outputStack)) {
                continue;
            }

            List<ItemStack> allInputs = new ArrayList<>();
            allInputs.add(wildcardInput);
            for (ItemStack fixed : fixedInputs) {
                allInputs.add(fixed.copy());
            }
            result.add(new GeneratedPattern(candidate, allInputs, outputStack));
        }
        return result;
    }

    public static List<String> getCandidateMaterials(ItemStack stack) {
        List<GeneratedPattern> patterns = generatePreviewPatterns(stack);
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

    private static Iterable<String> collectCandidatePool(WildcardPatternEntry input, WildcardPatternEntry output) {
        Set<String> candidates = null;
        boolean narrowed = false;

        Set<String> inputCandidates = input.getCandidateMaterials();
        if (inputCandidates != null && !inputCandidates.isEmpty()) {
            candidates = mergeCandidatePool(candidates, inputCandidates);
            narrowed = true;
        }

        Set<String> outputCandidates = output.getCandidateMaterials();
        if (outputCandidates != null && !outputCandidates.isEmpty()) {
            candidates = mergeCandidatePool(candidates, outputCandidates);
            narrowed = true;
        }

        if (narrowed) {
            return candidates == null ? Collections.<String>emptySet() : candidates;
        }

        return WildcardPatternEntry.getAllKnownMaterialNames();
    }

    private static Set<String> mergeCandidatePool(Set<String> current, Set<String> narrowed) {
        if (current == null) {
            return new LinkedHashSet<>(narrowed);
        }
        current.retainAll(narrowed);
        return current;
    }

    private static List<ItemStack> normalizeFixedInputs(List<ItemStack> fixedInputs) {
        List<ItemStack> result = new ArrayList<>();
        if (fixedInputs == null) {
            return result;
        }
        for (ItemStack fixed : fixedInputs) {
            if (fixed == null || fixed.getItem() == null || fixed.stackSize <= 0) {
                continue;
            }
            ItemStack copy = fixed.copy();
            copy.stackSize = Math.max(1, copy.stackSize);
            result.add(copy);
            if (result.size() >= CompositeWildcardPatternState.MAX_FIXED_INPUTS) {
                break;
            }
        }
        return result;
    }

    private static ItemStack createPatternStack(
        ItemStack template,
        String materialName,
        List<ItemStack> inputStacks,
        ItemStack outputStack) {
        if (inputStacks == null || inputStacks.isEmpty() || outputStack == null) {
            return null;
        }

        // Encode the fixed inputs before the wildcard input. AE2 pushes pattern inputs in list order, and
        // fixed inputs are often configuration items (e.g. a Programmable Hatches circuit/shape selector)
        // that must reach the machine before the material, or it starts on its previous configuration.
        List<ItemStack> orderedInputs = new ArrayList<>(inputStacks);
        Collections.rotate(orderedInputs, -1);
        inputStacks = orderedInputs;

        NBTTagList inputList = buildPatternList(inputStacks);
        NBTTagList outputList = buildPatternList(Collections.singletonList(outputStack));
        if (inputList == null || outputList == null) {
            return null;
        }

        ItemStack result = template.copy();
        NBTTagCompound resultTag = getOrCreateTag(result);
        resultTag.setTag("in", inputList);
        resultTag.setTag("out", outputList);
        resultTag.setString(KEY_SELECTED_MATERIAL, materialName == null ? "" : materialName);
        resultTag.setString(
            WildcardPatternGenerator.KEY_GENERATED_PATTERN_ID,
            buildGeneratedPatternId(materialName, inputStacks, outputStack));
        resultTag.setBoolean("crafting", false);
        resultTag.removeTag("InvalidPattern");
        return result;
    }

    private static String buildGeneratedPatternId(String materialName, List<ItemStack> inputStacks, ItemStack outputStack) {
        StringBuilder builder = new StringBuilder("composite|");
        builder.append(sanitizeIdentityPart(materialName)).append('|');
        if (inputStacks != null) {
            for (ItemStack input : inputStacks) {
                if (builder.charAt(builder.length() - 1) != '|') {
                    builder.append('+');
                }
                builder.append(getStackFingerprint(input));
            }
        }
        builder.append("->").append(getStackFingerprint(outputStack));
        return builder.toString();
    }

    private static String sanitizeIdentityPart(String value) {
        return value == null ? "" : value.replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace("->", "-\\>");
    }

    private static String getStackFingerprint(ItemStack stack) {
        if (stack == null) {
            return "empty";
        }
        String itemName = String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
        if (itemName == null || itemName.isEmpty() || "null".equals(itemName)) {
            itemName = stack.getItem() == null ? "null" : stack.getItem().getClass().getName();
        }
        NBTTagCompound tag = stack.getTagCompound();
        return itemName + "@" + stack.getItemDamage() + "x" + Math.max(1, stack.stackSize)
            + "#" + (tag == null ? "" : Integer.toHexString(tag.hashCode()));
    }

    private static NBTTagList buildPatternList(List<ItemStack> stacks) {
        NBTTagList rewritten = new NBTTagList();
        if (stacks == null || stacks.isEmpty()) {
            rewritten.appendTag(new NBTTagCompound());
            return rewritten;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null) {
                rewritten.appendTag(new NBTTagCompound());
                continue;
            }
            NBTTagCompound rewrittenTag = new NBTTagCompound();
            stack.writeToNBT(rewrittenTag);
            int count = Math.max(1, stack.stackSize);
            rewrittenTag.setInteger("Count", count);
            rewrittenTag.setLong("Cnt", count);
            rewritten.appendTag(rewrittenTag);
        }
        return rewritten;
    }

    private static ItemStack getFirstCondensedOutput(ICraftingPatternDetails details) {
        appeng.api.storage.data.IAEItemStack[] outputs = details == null ? null : details.getCondensedOutputs();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) {
            return null;
        }
        ItemStack output = outputs[0].getItemStack();
        return output == null ? null : output.copy();
    }

    private static ItemStack copyAsOne(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    public static final class GeneratedPattern {
        public final String materialName;
        public final List<ItemStack> inputStacks;
        public final ItemStack inputStack;
        public final ItemStack outputStack;

        private GeneratedPattern(String materialName, List<ItemStack> inputStacks, ItemStack outputStack) {
            this.materialName = materialName == null ? "" : materialName;
            this.inputStacks = copyStacks(inputStacks);
            this.inputStack = this.inputStacks.isEmpty() ? null : this.inputStacks.get(0).copy();
            this.outputStack = outputStack == null ? null : outputStack.copy();
        }

        private static List<ItemStack> copyStacks(List<ItemStack> source) {
            List<ItemStack> result = new ArrayList<>();
            if (source == null) {
                return result;
            }
            for (ItemStack stack : source) {
                if (stack != null) {
                    result.add(stack.copy());
                }
            }
            return result;
        }
    }
}
