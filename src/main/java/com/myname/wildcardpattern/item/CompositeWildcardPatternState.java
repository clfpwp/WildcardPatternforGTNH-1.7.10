package com.myname.wildcardpattern.item;

import java.util.ArrayList;
import java.util.List;

import com.myname.wildcardpattern.crafting.WildcardPatternEntry;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

public final class CompositeWildcardPatternState {

    private static final String KEY_WILDCARD_INPUT = "CompositeWildcardInput";
    private static final String KEY_WILDCARD_OUTPUT = "CompositeWildcardOutput";
    private static final String KEY_FIXED_INPUTS = "CompositeWildcardFixedInputs";
    private static final String KEY_EXPANDED_PATTERN_COUNT = "CompositeWildcardExpandedPatternCount";
    private static final String KEY_SELECTED_MATERIAL = "WildcardSelectedMaterial";
    private static final String KEY_GENERATED_PATTERN_ID = "WildcardGeneratedPatternId";
    public static final int MAX_FIXED_INPUTS = 8;

    private CompositeWildcardPatternState() {}

    public static void ensureInitialized(ItemStack stack) {
        if (stack == null) {
            return;
        }

        NBTTagCompound tag = getOrCreateTag(stack);
        if (!tag.hasKey(KEY_WILDCARD_INPUT, NBT.TAG_COMPOUND)) {
            tag.setTag(KEY_WILDCARD_INPUT, importFirstPatternEntry(tag.getTagList("in", NBT.TAG_COMPOUND)));
        }
        if (!tag.hasKey(KEY_WILDCARD_OUTPUT, NBT.TAG_COMPOUND)) {
            tag.setTag(KEY_WILDCARD_OUTPUT, importFirstPatternEntry(tag.getTagList("out", NBT.TAG_COMPOUND)));
        }
        if (!tag.hasKey(KEY_FIXED_INPUTS, NBT.TAG_LIST)) {
            tag.setTag(KEY_FIXED_INPUTS, new NBTTagList());
        }
        cleanupLegacyPatternSlots(tag);
    }

    public static WildcardPatternEntry getWildcardInput(ItemStack stack) {
        ensureInitialized(stack);
        return WildcardPatternEntry.fromNbt(getOrCreateTag(stack).getCompoundTag(KEY_WILDCARD_INPUT));
    }

    public static void setWildcardInput(ItemStack stack, WildcardPatternEntry entry) {
        if (stack == null) {
            return;
        }
        getOrCreateTag(stack).setTag(KEY_WILDCARD_INPUT, toEntryTag(entry));
    }

    public static WildcardPatternEntry getWildcardOutput(ItemStack stack) {
        ensureInitialized(stack);
        return WildcardPatternEntry.fromNbt(getOrCreateTag(stack).getCompoundTag(KEY_WILDCARD_OUTPUT));
    }

    public static void setWildcardOutput(ItemStack stack, WildcardPatternEntry entry) {
        if (stack == null) {
            return;
        }
        getOrCreateTag(stack).setTag(KEY_WILDCARD_OUTPUT, toEntryTag(entry));
    }

    public static List<ItemStack> getFixedInputs(ItemStack stack) {
        ensureInitialized(stack);
        NBTTagList list = getOrCreateTag(stack).getTagList(KEY_FIXED_INPUTS, NBT.TAG_COMPOUND);
        List<ItemStack> result = new ArrayList<>();
        for (int index = 0; index < list.tagCount() && result.size() < MAX_FIXED_INPUTS; index++) {
            ItemStack fixed = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(index));
            result.add(fixed == null ? null : fixed.copy());
        }
        ensureFixedSize(result);
        return result;
    }

    public static void setFixedInputs(ItemStack stack, List<ItemStack> inputs) {
        if (stack == null) {
            return;
        }
        NBTTagList list = new NBTTagList();
        if (inputs != null) {
            for (int index = 0; index < inputs.size() && index < MAX_FIXED_INPUTS; index++) {
                ItemStack fixed = inputs.get(index);
                if (fixed == null || fixed.getItem() == null || fixed.stackSize <= 0) {
                    list.appendTag(new NBTTagCompound());
                    continue;
                }
                ItemStack copy = fixed.copy();
                copy.stackSize = Math.max(1, copy.stackSize);
                NBTTagCompound tag = new NBTTagCompound();
                copy.writeToNBT(tag);
                list.appendTag(tag);
            }
        }
        getOrCreateTag(stack).setTag(KEY_FIXED_INPUTS, list);
    }

    public static int getExpandedPatternCount(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        return tag == null || !tag.hasKey(KEY_EXPANDED_PATTERN_COUNT) ? 0 : Math.max(0, tag.getInteger(KEY_EXPANDED_PATTERN_COUNT));
    }

    public static void setExpandedPatternCount(ItemStack stack, int count) {
        if (stack == null) {
            return;
        }
        getOrCreateTag(stack).setInteger(KEY_EXPANDED_PATTERN_COUNT, Math.max(0, count));
    }

    public static void applyBitModification(ItemStack stack, int bitMultiplier) {
        if (stack == null || bitMultiplier == 0) {
            return;
        }
        int factor = 1 << Math.min(30, Math.abs(bitMultiplier));

        WildcardPatternEntry input = getWildcardInput(stack);
        WildcardPatternEntry output = getWildcardOutput(stack);
        applyFactor(input, factor, bitMultiplier < 0);
        applyFactor(output, factor, bitMultiplier < 0);
        setWildcardInput(stack, input);
        setWildcardOutput(stack, output);

        List<ItemStack> fixedInputs = getFixedInputs(stack);
        for (ItemStack fixed : fixedInputs) {
            applyFactor(fixed, factor, bitMultiplier < 0);
        }
        setFixedInputs(stack, fixedInputs);
    }

    public static int getMaxBitMultiplier(ItemStack stack) {
        return getMaxBitModification(stack, false);
    }

    public static int getMaxBitDivider(ItemStack stack) {
        return getMaxBitModification(stack, true);
    }

    public static NBTTagCompound exportConfig(ItemStack stack) {
        ensureInitialized(stack);
        NBTTagCompound exported = new NBTTagCompound();
        NBTTagCompound source = getOrCreateTag(stack);
        exported.setTag(KEY_WILDCARD_INPUT, source.getCompoundTag(KEY_WILDCARD_INPUT).copy());
        exported.setTag(KEY_WILDCARD_OUTPUT, source.getCompoundTag(KEY_WILDCARD_OUTPUT).copy());
        exported.setTag(KEY_FIXED_INPUTS, source.getTagList(KEY_FIXED_INPUTS, NBT.TAG_COMPOUND).copy());
        copyIfPresent(source, exported, "WildcardGlobalExcludeMaterials");
        copyIfPresent(source, exported, "WildcardRuleIncludeMaterials");
        copyIfPresent(source, exported, "WildcardRuleExcludeMaterials");
        copyIfPresent(source, exported, "WildcardOreDictPreferences");
        copyIfPresent(source, exported, KEY_EXPANDED_PATTERN_COUNT);
        copyIfPresent(source, exported, KEY_SELECTED_MATERIAL);
        return exported;
    }

    public static void applyConfig(ItemStack stack, NBTTagCompound config) {
        if (stack == null || config == null) {
            return;
        }
        ensureInitialized(stack);
        NBTTagCompound tag = getOrCreateTag(stack);
        copyIfPresent(config, tag, KEY_WILDCARD_INPUT);
        copyIfPresent(config, tag, KEY_WILDCARD_OUTPUT);
        copyIfPresent(config, tag, KEY_FIXED_INPUTS);
        copyIfPresent(config, tag, "WildcardGlobalExcludeMaterials");
        copyIfPresent(config, tag, "WildcardRuleIncludeMaterials");
        copyIfPresent(config, tag, "WildcardRuleExcludeMaterials");
        copyIfPresent(config, tag, "WildcardOreDictPreferences");
        copyIfPresent(config, tag, KEY_EXPANDED_PATTERN_COUNT);
        copyIfPresent(config, tag, KEY_SELECTED_MATERIAL);
    }

    private static void cleanupLegacyPatternSlots(NBTTagCompound tag) {
        if (tag.hasKey(KEY_GENERATED_PATTERN_ID)) {
            return;
        }
        tag.removeTag("in");
        tag.removeTag("out");
    }

    private static NBTTagCompound importFirstPatternEntry(NBTTagList source) {
        if (source == null || source.tagCount() == 0) {
            return WildcardPatternEntry.fromStack(null).toNbt();
        }
        return WildcardPatternEntry.fromPatternSlot(source.getCompoundTagAt(0)).toNbt();
    }

    private static NBTTagCompound toEntryTag(WildcardPatternEntry entry) {
        return entry == null ? WildcardPatternEntry.fromStack(null).toNbt() : entry.toNbt();
    }

    private static void ensureFixedSize(List<ItemStack> inputs) {
        while (inputs.size() < MAX_FIXED_INPUTS) {
            inputs.add(null);
        }
    }

    private static void applyFactor(WildcardPatternEntry entry, int factor, boolean dividing) {
        if (entry == null || entry.isEmpty()) {
            return;
        }
        if (dividing) {
            entry.divideAmount(factor);
        } else {
            entry.multiplyAmount(factor);
        }
    }

    private static void applyFactor(ItemStack stack, int factor, boolean dividing) {
        if (stack == null || stack.getItem() == null || factor <= 1) {
            return;
        }
        if (dividing) {
            stack.stackSize = Math.max(1, stack.stackSize / factor);
        } else if (stack.stackSize > Integer.MAX_VALUE / factor) {
            stack.stackSize = Integer.MAX_VALUE;
        } else {
            stack.stackSize = Math.max(1, stack.stackSize * factor);
        }
    }

    private static int getMaxBitModification(ItemStack stack, boolean dividing) {
        int result = 30;
        boolean found = false;
        WildcardPatternEntry input = getWildcardInput(stack);
        if (input != null && !input.isEmpty()) {
            result = Math.min(result, getMaxBits(input.getAmountLong(), dividing));
            found = true;
        }
        WildcardPatternEntry output = getWildcardOutput(stack);
        if (output != null && !output.isEmpty()) {
            result = Math.min(result, getMaxBits(output.getAmountLong(), dividing));
            found = true;
        }
        for (ItemStack fixed : getFixedInputs(stack)) {
            if (fixed != null && fixed.getItem() != null && fixed.stackSize > 0) {
                result = Math.min(result, getMaxBits(fixed.stackSize, dividing));
                found = true;
            }
        }
        return found ? result : 0;
    }

    private static int getMaxBits(long amount, boolean dividing) {
        long value = Math.max(1L, amount);
        int bits = 0;
        if (dividing) {
            while ((value & 1) == 0) {
                value >>= 1;
                bits++;
            }
        } else {
            while (value > 0 && value <= WildcardPatternEntry.MAX_AMOUNT / 2L) {
                value <<= 1;
                bits++;
            }
        }
        return bits;
    }

    private static void copyIfPresent(NBTTagCompound source, NBTTagCompound target, String key) {
        if (source.hasKey(key)) {
            target.setTag(key, source.getTag(key).copy());
        }
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }
}
