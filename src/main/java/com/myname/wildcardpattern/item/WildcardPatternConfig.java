package com.myname.wildcardpattern.item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import gregtech.api.enums.Materials;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

public final class WildcardPatternConfig {

    private static final String KEY_GLOBAL_EXCLUDE_MATERIALS = "WildcardGlobalExcludeMaterials";
    private static final String KEY_RULE_INCLUDE_MATERIALS = "WildcardRuleIncludeMaterials";
    private static final String KEY_RULE_EXCLUDE_MATERIALS = "WildcardRuleExcludeMaterials";
    private static final String KEY_OREDICT_PREFERENCES = "WildcardOreDictPreferences";

    private WildcardPatternConfig() {}

    public static boolean shouldReplaceInputs(ItemStack stack) {
        return true;
    }

    public static boolean shouldReplaceOutputs(ItemStack stack) {
        return true;
    }

    public static String getGlobalExcludeMaterials(ItemStack stack) {
        return getString(stack, KEY_GLOBAL_EXCLUDE_MATERIALS);
    }

    public static String getIncludeMaterials(ItemStack stack) {
        return getRuleMaterialList(stack, KEY_RULE_INCLUDE_MATERIALS, 0);
    }

    public static String getExcludeMaterials(ItemStack stack) {
        return getGlobalExcludeMaterials(stack);
    }

    public static String getRuleIncludeMaterials(ItemStack stack, int ruleIndex) {
        return getRuleMaterialList(stack, KEY_RULE_INCLUDE_MATERIALS, ruleIndex);
    }

    public static String getRuleExcludeMaterials(ItemStack stack, int ruleIndex) {
        return getRuleMaterialList(stack, KEY_RULE_EXCLUDE_MATERIALS, ruleIndex);
    }

    public static void apply(
        ItemStack stack,
        boolean replaceInputs,
        boolean replaceOutputs,
        String include,
        String exclude) {
        List<String> includes = new ArrayList<>();
        includes.add(normalizeList(include));
        List<String> excludes = new ArrayList<>();
        excludes.add("");
        apply(stack, normalizeList(exclude), includes, excludes);
    }

    public static void apply(
        ItemStack stack,
        String globalExclude,
        List<String> ruleIncludes,
        List<String> ruleExcludes) {
        NBTTagCompound tag = getOrCreateTag(stack);
        tag.setString(KEY_GLOBAL_EXCLUDE_MATERIALS, normalizeList(globalExclude));
        tag.setTag(KEY_RULE_INCLUDE_MATERIALS, writeStringList(ruleIncludes));
        tag.setTag(KEY_RULE_EXCLUDE_MATERIALS, writeStringList(ruleExcludes));
    }

    public static ItemStack getPreferredOreStack(ItemStack stack, String oreName) {
        if (stack == null || oreName == null || oreName.trim().isEmpty()) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(KEY_OREDICT_PREFERENCES, NBT.TAG_COMPOUND)) {
            return null;
        }
        NBTTagCompound prefs = tag.getCompoundTag(KEY_OREDICT_PREFERENCES);
        String key = normalizePreferenceKey(oreName);
        return prefs.hasKey(key, NBT.TAG_COMPOUND) ? ItemStack.loadItemStackFromNBT(prefs.getCompoundTag(key)) : null;
    }

    public static void setPreferredOreStack(ItemStack stack, String oreName, ItemStack preferred) {
        if (stack == null || oreName == null || oreName.trim().isEmpty()) {
            return;
        }
        NBTTagCompound prefs = getOrCreatePreferences(stack);
        String key = normalizePreferenceKey(oreName);
        if (preferred == null) {
            prefs.removeTag(key);
            return;
        }
        NBTTagCompound itemTag = new NBTTagCompound();
        preferred.writeToNBT(itemTag);
        prefs.setTag(key, itemTag);
    }

    public static List<String> getPreferredOreNames(ItemStack stack) {
        List<String> result = new ArrayList<>();
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag == null || !tag.hasKey(KEY_OREDICT_PREFERENCES, NBT.TAG_COMPOUND)) {
            return result;
        }
        NBTTagCompound prefs = tag.getCompoundTag(KEY_OREDICT_PREFERENCES);
        for (Object key : prefs.func_150296_c()) {
            if (key != null) {
                result.add(String.valueOf(key));
            }
        }
        return result;
    }

    public static boolean acceptsMaterial(ItemStack stack, int ruleIndex, Materials material) {
        String materialName = getMaterialName(material);
        if (materialName.length() == 0) {
            return false;
        }

        if (matchesList(getGlobalExcludeMaterials(stack), materialName)) {
            return false;
        }

        Set<String> includes = parseList(getRuleIncludeMaterials(stack, ruleIndex));
        if (!includes.isEmpty() && !includes.contains(materialName)) {
            return false;
        }

        return !matchesList(getRuleExcludeMaterials(stack, ruleIndex), materialName);
    }

    public static List<String> getRuleIncludeList(ItemStack stack, int size) {
        return getRuleMaterialLists(stack, KEY_RULE_INCLUDE_MATERIALS, size);
    }

    public static List<String> getRuleExcludeList(ItemStack stack, int size) {
        return getRuleMaterialLists(stack, KEY_RULE_EXCLUDE_MATERIALS, size);
    }

    private static String getRuleMaterialList(ItemStack stack, String key, int ruleIndex) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag == null || !tag.hasKey(key, NBT.TAG_LIST)) {
            return "";
        }
        NBTTagList list = tag.getTagList(key, NBT.TAG_STRING);
        return ruleIndex >= 0 && ruleIndex < list.tagCount() ? normalizeList(list.getStringTagAt(ruleIndex)) : "";
    }

    private static List<String> getRuleMaterialLists(ItemStack stack, String key, int size) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            result.add(getRuleMaterialList(stack, key, index));
        }
        return result;
    }

    private static String getString(ItemStack stack, String key) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        return tag == null || !tag.hasKey(key) ? "" : tag.getString(key);
    }

    private static NBTTagList writeStringList(List<String> values) {
        NBTTagList list = new NBTTagList();
        if (values == null) {
            return list;
        }
        for (String value : values) {
            list.appendTag(new net.minecraft.nbt.NBTTagString(normalizeList(value)));
        }
        return list;
    }

    private static Set<String> parseList(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty()) {
            return result;
        }

        String[] parts = value.split("[,;，；\\s]+");
        for (String part : parts) {
            String normalized = normalizeMaterialName(part);
            if (normalized.length() > 0) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static String normalizeList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (String name : parseList(value)) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(name);
        }
        return builder.toString();
    }

    private static String getMaterialName(Materials material) {
        return material == null ? "" : normalizeMaterialName(material.mName);
    }

    private static String normalizeMaterialName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }


    private static boolean matchesList(String value, String materialName) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String normalizedMaterial = normalizeMaterialName(materialName);
        for (String part : value.split("[,;，；\\s]+")) {
            String token = normalizeMaterialName(part);
            if (token.isEmpty()) {
                continue;
            }
            if (matchesToken(token, normalizedMaterial)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesToken(String token, String materialName) {
        if (token.indexOf('*') >= 0 || token.indexOf('?') >= 0) {
            return Pattern.compile(wildcardToRegex(token)).matcher(materialName).matches();
        }
        return token.equals(materialName);
    }

    private static String wildcardToRegex(String pattern) {
        StringBuilder builder = new StringBuilder("^");
        for (int index = 0; index < pattern.length(); index++) {
            char value = pattern.charAt(index);
            if (value == '*') {
                builder.append(".*");
            } else if (value == '?') {
                builder.append('.');
            } else {
                builder.append(Pattern.quote(String.valueOf(value)));
            }
        }
        builder.append('$');
        return builder.toString();
    }
    private static String normalizePreferenceKey(String value) {
        return value == null ? "" : value.trim();
    }

    private static NBTTagCompound getOrCreatePreferences(ItemStack stack) {
        NBTTagCompound tag = getOrCreateTag(stack);
        if (!tag.hasKey(KEY_OREDICT_PREFERENCES, NBT.TAG_COMPOUND)) {
            tag.setTag(KEY_OREDICT_PREFERENCES, new NBTTagCompound());
        }
        return tag.getCompoundTag(KEY_OREDICT_PREFERENCES);
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }
}


