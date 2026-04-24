package com.myname.wildcardpattern.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.myname.wildcardpattern.crafting.WildcardPatternEntry;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

public final class WildcardPatternState {

    private static final String KEY_INPUT_COMPONENTS = "WildcardInputComponents";
    private static final String KEY_OUTPUT_COMPONENTS = "WildcardOutputComponents";
    private static final String KEY_PREVIEW_CACHE = "WildcardPreviewCache";

    private WildcardPatternState() {}

    public static void ensureInitialized(ItemStack stack) {
        if (stack == null) {
            return;
        }

        NBTTagCompound tag = getOrCreateTag(stack);
        if (!tag.hasKey(KEY_INPUT_COMPONENTS, NBT.TAG_LIST)) {
            tag.setTag(KEY_INPUT_COMPONENTS, importPatternList(tag.getTagList("in", NBT.TAG_COMPOUND)));
        }
        if (!tag.hasKey(KEY_OUTPUT_COMPONENTS, NBT.TAG_LIST)) {
            tag.setTag(KEY_OUTPUT_COMPONENTS, importPatternList(tag.getTagList("out", NBT.TAG_COMPOUND)));
        }
    }

    public static void initializeFromPattern(ItemStack stack) {
        ensureInitialized(stack);
    }

    public static List<WildcardPatternEntry> getInputEntries(ItemStack stack) {
        return getEntries(stack, KEY_INPUT_COMPONENTS);
    }

    public static List<WildcardPatternEntry> getOutputEntries(ItemStack stack) {
        return getEntries(stack, KEY_OUTPUT_COMPONENTS);
    }

    public static void setInputEntries(ItemStack stack, List<WildcardPatternEntry> entries) {
        getOrCreateTag(stack).setTag(KEY_INPUT_COMPONENTS, writeEntries(entries));
    }

    public static void setOutputEntries(ItemStack stack, List<WildcardPatternEntry> entries) {
        getOrCreateTag(stack).setTag(KEY_OUTPUT_COMPONENTS, writeEntries(entries));
    }

    public static void applyBitModification(ItemStack stack, int bitMultiplier) {
        if (stack == null || bitMultiplier == 0) {
            return;
        }

        int factor = 1 << Math.min(30, Math.abs(bitMultiplier));
        List<WildcardPatternEntry> inputs = getInputEntries(stack);
        List<WildcardPatternEntry> outputs = getOutputEntries(stack);
        for (WildcardPatternEntry entry : inputs) {
            applyFactor(entry, factor, bitMultiplier < 0);
        }
        for (WildcardPatternEntry entry : outputs) {
            applyFactor(entry, factor, bitMultiplier < 0);
        }
        setInputEntries(stack, inputs);
        setOutputEntries(stack, outputs);
    }

    public static int getMaxBitMultiplier(ItemStack stack) {
        return getMaxBitModification(stack, false);
    }

    public static int getMaxBitDivider(ItemStack stack) {
        return getMaxBitModification(stack, true);
    }

    public static NBTTagCompound exportConfig(ItemStack stack) {
        initializeFromPattern(stack);
        NBTTagCompound exported = new NBTTagCompound();
        NBTTagCompound source = getOrCreateTag(stack);
        exported.setTag(KEY_INPUT_COMPONENTS, source.getTagList(KEY_INPUT_COMPONENTS, NBT.TAG_COMPOUND).copy());
        exported.setTag(KEY_OUTPUT_COMPONENTS, source.getTagList(KEY_OUTPUT_COMPONENTS, NBT.TAG_COMPOUND).copy());
        copyIfPresent(source, exported, "WildcardGlobalExcludeMaterials");
        copyIfPresent(source, exported, "WildcardRuleIncludeMaterials");
        copyIfPresent(source, exported, "WildcardRuleExcludeMaterials");
        copyIfPresent(source, exported, "WildcardOreDictPreferences");
        copyIfPresent(source, exported, KEY_PREVIEW_CACHE);
        return exported;
    }

    public static void applyConfig(ItemStack stack, NBTTagCompound config) {
        if (stack == null || config == null) {
            return;
        }
        ensureInitialized(stack);
        NBTTagCompound tag = getOrCreateTag(stack);
        copyIfPresent(config, tag, KEY_INPUT_COMPONENTS);
        copyIfPresent(config, tag, KEY_OUTPUT_COMPONENTS);
        copyIfPresent(config, tag, "WildcardGlobalExcludeMaterials");
        copyIfPresent(config, tag, "WildcardRuleIncludeMaterials");
        copyIfPresent(config, tag, "WildcardRuleExcludeMaterials");
        copyIfPresent(config, tag, "WildcardOreDictPreferences");
        copyIfPresent(config, tag, KEY_PREVIEW_CACHE);
    }

    public static Map<Integer, List<PreviewCacheEntry>> getPreviewCache(ItemStack stack) {
        Map<Integer, List<PreviewCacheEntry>> result = new LinkedHashMap<>();
        if (stack == null || stack.getTagCompound() == null) {
            return result;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(KEY_PREVIEW_CACHE, NBT.TAG_LIST)) {
            return result;
        }
        NBTTagList rules = tag.getTagList(KEY_PREVIEW_CACHE, NBT.TAG_COMPOUND);
        for (int index = 0; index < rules.tagCount(); index++) {
            NBTTagCompound ruleTag = rules.getCompoundTagAt(index);
            int rule = ruleTag.getInteger("Rule");
            NBTTagList rows = ruleTag.getTagList("Rows", NBT.TAG_COMPOUND);
            List<PreviewCacheEntry> entries = new ArrayList<>();
            for (int rowIndex = 0; rowIndex < rows.tagCount(); rowIndex++) {
                NBTTagCompound rowTag = rows.getCompoundTagAt(rowIndex);
                entries.add(
                    new PreviewCacheEntry(
                        rowTag.getString("Material"),
                        rowTag.getString("Exclude"),
                        rowTag.getString("Line")));
            }
            result.put(Integer.valueOf(rule), entries);
        }
        return result;
    }

    public static void setPreviewCache(ItemStack stack, Map<Integer, List<PreviewCacheEntry>> cache) {
        if (stack == null) {
            return;
        }
        NBTTagList rules = new NBTTagList();
        if (cache != null) {
            for (Map.Entry<Integer, List<PreviewCacheEntry>> entry : cache.entrySet()) {
                if (entry == null || entry.getKey() == null) {
                    continue;
                }
                NBTTagCompound ruleTag = new NBTTagCompound();
                ruleTag.setInteger("Rule", entry.getKey().intValue());
                NBTTagList rows = new NBTTagList();
                if (entry.getValue() != null) {
                    for (PreviewCacheEntry row : entry.getValue()) {
                        if (row == null) {
                            continue;
                        }
                        NBTTagCompound rowTag = new NBTTagCompound();
                        rowTag.setString("Material", row.materialName == null ? "" : row.materialName);
                        rowTag.setString("Exclude", row.excludeToken == null ? "" : row.excludeToken);
                        rowTag.setString("Line", row.line == null ? "" : row.line);
                        rows.appendTag(rowTag);
                    }
                }
                ruleTag.setTag("Rows", rows);
                rules.appendTag(ruleTag);
            }
        }
        getOrCreateTag(stack).setTag(KEY_PREVIEW_CACHE, rules);
    }

    private static List<WildcardPatternEntry> getEntries(ItemStack stack, String key) {
        initializeFromPattern(stack);
        NBTTagList list = getOrCreateTag(stack).getTagList(key, NBT.TAG_COMPOUND);
        List<WildcardPatternEntry> result = new ArrayList<>();
        for (int index = 0; index < list.tagCount(); index++) {
            result.add(WildcardPatternEntry.fromNbt(list.getCompoundTagAt(index)));
        }
        return result;
    }

    private static NBTTagList importPatternList(NBTTagList source) {
        NBTTagList result = new NBTTagList();
        for (int index = 0; index < source.tagCount(); index++) {
            WildcardPatternEntry entry = WildcardPatternEntry.fromPatternSlot(source.getCompoundTagAt(index));
            result.appendTag(entry.toNbt());
        }
        return result;
    }

    private static NBTTagList writeEntries(List<WildcardPatternEntry> entries) {
        NBTTagList list = new NBTTagList();
        for (WildcardPatternEntry entry : entries) {
            list.appendTag(entry.toNbt());
        }
        return list;
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

    private static int getMaxBitModification(ItemStack stack, boolean dividing) {
        int result = 30;
        boolean found = false;
        for (WildcardPatternEntry entry : getInputEntries(stack)) {
            if (entry != null && !entry.isEmpty()) {
                result = Math.min(result, getMaxBits(entry.getAmountLong(), dividing));
                found = true;
            }
        }
        for (WildcardPatternEntry entry : getOutputEntries(stack)) {
            if (entry != null && !entry.isEmpty()) {
                result = Math.min(result, getMaxBits(entry.getAmountLong(), dividing));
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

    public static final class PreviewCacheEntry {
        public final String materialName;
        public final String excludeToken;
        public final String line;

        public PreviewCacheEntry(String materialName, String excludeToken, String line) {
            this.materialName = materialName == null ? "" : materialName;
            this.excludeToken = excludeToken == null ? "" : excludeToken;
            this.line = line == null ? "" : line;
        }
    }
}
