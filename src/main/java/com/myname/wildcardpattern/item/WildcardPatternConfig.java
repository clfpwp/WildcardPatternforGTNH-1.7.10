package com.myname.wildcardpattern.item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.oredict.OreDictionary;

public final class WildcardPatternConfig {

    private static final String KEY_GLOBAL_EXCLUDE_MATERIALS = "WildcardGlobalExcludeMaterials";
    private static final String KEY_RULE_INCLUDE_MATERIALS = "WildcardRuleIncludeMaterials";
    private static final String KEY_RULE_EXCLUDE_MATERIALS = "WildcardRuleExcludeMaterials";
    private static final String KEY_OREDICT_PREFERENCES = "WildcardOreDictPreferences";
    private static final Map<String, List<TokenMatcher>> TOKEN_MATCHER_CACHE = new ConcurrentHashMap<>();

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

    public static boolean acceptsCandidate(
        ItemStack stack,
        int ruleIndex,
        String candidateName,
        ItemStack inputStack,
        ItemStack outputStack) {
        Set<String> candidateTerms = collectCandidateTerms(candidateName, inputStack, outputStack);
        if (candidateTerms.isEmpty()) {
            return false;
        }
        if (matchesList(getGlobalExcludeMaterials(stack), candidateTerms)) {
            return false;
        }
        String includeValue = getRuleIncludeMaterials(stack, ruleIndex);
        if (!includeValue.isEmpty() && !matchesList(includeValue, candidateTerms)) {
            return false;
        }
        return !matchesList(getRuleExcludeMaterials(stack, ruleIndex), candidateTerms);
    }

    public static boolean acceptsMaterial(ItemStack stack, int ruleIndex, Materials material) {
        return acceptsCandidate(stack, ruleIndex, material, null, null);
    }

    public static boolean acceptsCandidate(
        ItemStack stack,
        int ruleIndex,
        Materials material,
        ItemStack inputStack,
        ItemStack outputStack) {
        Set<String> candidateTerms = collectCandidateTerms(material, inputStack, outputStack);
        if (candidateTerms.isEmpty()) {
            return false;
        }

        if (matchesList(getGlobalExcludeMaterials(stack), candidateTerms)) {
            return false;
        }

        String includeValue = getRuleIncludeMaterials(stack, ruleIndex);
        if (!includeValue.isEmpty() && !matchesList(includeValue, candidateTerms)) {
            return false;
        }

        return !matchesList(getRuleExcludeMaterials(stack, ruleIndex), candidateTerms);
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

        String[] parts = value.split("[,;锛岋紱\\s]+");
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

    private static Set<String> collectCandidateTerms(String candidateName, ItemStack inputStack, ItemStack outputStack) {
        Set<String> result = new LinkedHashSet<>();
        String materialName = normalizeMaterialName(candidateName);
        if (!materialName.isEmpty()) {
            result.add(materialName);
        }
        addStackTerms(result, inputStack);
        addStackTerms(result, outputStack);
        return result;
    }

    private static Set<String> collectCandidateTerms(Materials material, ItemStack inputStack, ItemStack outputStack) {
        Set<String> result = new LinkedHashSet<>();
        String materialName = getMaterialName(material);
        if (!materialName.isEmpty()) {
            result.add(materialName);
        }
        addStackTerms(result, inputStack);
        addStackTerms(result, outputStack);
        return result;
    }

    private static void addStackTerms(Set<String> result, ItemStack stack) {
        if (stack == null) {
            return;
        }
        String displayName = normalizeMaterialName(stack.getDisplayName());
        if (!displayName.isEmpty()) {
            result.add(displayName);
        }
        ItemData association = GTOreDictUnificator.getAssociation(stack);
        if (association != null && association.hasValidPrefixMaterialData()) {
            OrePrefixes prefix = association.mPrefix;
            Materials material = association.mMaterial == null ? null : association.mMaterial.mMaterial;
            if (prefix != null && material != null && material.mName != null && !material.mName.isEmpty()) {
                String oreName = normalizeMaterialName(getPrefixName(prefix) + material.mName);
                if (!oreName.isEmpty()) {
                    result.add(oreName);
                }
            }
        } else {
            // Fallback for GT++ items not registered in the GT5 unificator
            int[] oreIds = OreDictionary.getOreIDs(stack);
            if (oreIds != null) {
                for (int oreId : oreIds) {
                    String oreName = OreDictionary.getOreName(oreId);
                    if (oreName != null && !oreName.isEmpty()) {
                        result.add(normalizeMaterialName(oreName));
                    }
                }
            }
        }
    }

    private static String getPrefixName(OrePrefixes prefix) {
        if (prefix == null) {
            return "";
        }
        try {
            return (String) prefix.getClass().getMethod("getName").invoke(prefix);
        } catch (Exception ignored) {}
        try {
            return (String) prefix.getClass().getMethod("name").invoke(prefix);
        } catch (Exception ignored) {}
        return prefix.toString();
    }

    private static boolean matchesList(String value, String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return false;
        }
        return matchesList(value, java.util.Collections.singleton(materialName));
    }

    private static boolean matchesList(String value, Set<String> candidateTerms) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        for (TokenMatcher matcher : TOKEN_MATCHER_CACHE.computeIfAbsent(value, WildcardPatternConfig::buildTokenMatchers)) {
            for (String candidateTerm : candidateTerms) {
                if (candidateTerm != null && !candidateTerm.isEmpty() && matcher.matches(candidateTerm)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<TokenMatcher> buildTokenMatchers(String value) {
        List<TokenMatcher> result = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return result;
        }
        for (String part : value.split("[,;锛岋紱\\s]+")) {
            String token = normalizeMaterialName(part);
            if (token.isEmpty()) {
                continue;
            }
            if (token.indexOf('*') >= 0 || token.indexOf('?') >= 0) {
                result.add(new TokenMatcher(Pattern.compile(wildcardToRegex(token)), null));
            } else {
                result.add(new TokenMatcher(null, token));
            }
        }
        return result;
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

    private static final class TokenMatcher {
        private final Pattern wildcardPattern;
        private final String exactToken;

        private TokenMatcher(Pattern wildcardPattern, String exactToken) {
            this.wildcardPattern = wildcardPattern;
            this.exactToken = exactToken;
        }

        private boolean matches(String value) {
            if (this.wildcardPattern != null) {
                return this.wildcardPattern.matcher(value).matches();
            }
            return this.exactToken != null && this.exactToken.equals(value);
        }
    }
}
