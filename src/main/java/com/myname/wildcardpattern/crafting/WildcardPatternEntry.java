package com.myname.wildcardpattern.crafting;

import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import appeng.util.Platform;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

public class WildcardPatternEntry {

    private static final String KEY_MODE = "Mode";
    private static final String KEY_STACK = "Stack";
    private static final String KEY_DISPLAY = "Display";
    private static final String KEY_MATCHER = "Matcher";
    private static final String KEY_AMOUNT = "Amount";
    public static final long MAX_AMOUNT = 2_100_000_000L;
    private static final Map<String, Pattern> NAME_PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> ORE_PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Set<Materials>> ORE_CANDIDATE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Materials> MATERIAL_NAME_CACHE = new ConcurrentHashMap<>();

    private boolean oreDictMode;
    private ItemStack stack;
    private ItemStack displayStack;
    private String matcher;
    private long amount;

    public static WildcardPatternEntry fromStack(ItemStack stack) {
        WildcardPatternEntry entry = new WildcardPatternEntry();
        entry.stack = stack == null ? null : stack.copy();
        entry.displayStack = stack == null ? null : stack.copy();
        entry.amount = stack == null ? 1L : Math.max(1L, stack.stackSize);
        entry.oreDictMode = false;
        entry.matcher = stack == null ? "" : stack.getDisplayName();
        return entry;
    }

    public static WildcardPatternEntry fromPatternSlot(NBTTagCompound tag) {
        ItemStack stack = tag == null ? null : Platform.loadItemStackFromNBT(tag);
        if (stack != null && stack.stackSize == 0 && tag.hasKey("Cnt")) {
            stack.stackSize = (int) tag.getLong("Cnt");
        }
        return fromStack(stack);
    }

    public static WildcardPatternEntry fromNbt(NBTTagCompound tag) {
        WildcardPatternEntry entry = new WildcardPatternEntry();
        entry.oreDictMode = tag.getBoolean(KEY_MODE);
        entry.matcher = tag.hasKey(KEY_MATCHER) ? tag.getString(KEY_MATCHER) : tag.getString("Prefix");
        entry.amount = Math.max(1L, tag.getLong(KEY_AMOUNT));
        if (tag.hasKey(KEY_STACK)) {
            entry.stack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag(KEY_STACK));
        }
        if (tag.hasKey(KEY_DISPLAY)) {
            entry.displayStack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag(KEY_DISPLAY));
        }
        if (entry.displayStack == null && entry.stack != null) {
            entry.displayStack = entry.stack.copy();
        }
        if ((entry.matcher == null || entry.matcher.isEmpty()) && entry.stack != null) {
            entry.matcher = entry.stack.getDisplayName();
        }
        return entry;
    }

    public NBTTagCompound toNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean(KEY_MODE, this.oreDictMode);
        tag.setString(KEY_MATCHER, getMatcher());
        tag.setLong(KEY_AMOUNT, getAmountLong());
        if (this.stack != null) {
            NBTTagCompound stackTag = new NBTTagCompound();
            this.stack.writeToNBT(stackTag);
            tag.setTag(KEY_STACK, stackTag);
        }
        if (this.displayStack != null) {
            NBTTagCompound displayTag = new NBTTagCompound();
            this.displayStack.writeToNBT(displayTag);
            tag.setTag(KEY_DISPLAY, displayTag);
        }
        return tag;
    }

    public ItemStack createStack(Materials material, ItemStack configStack) {
        List<ItemStack> stacks = createStacks(material, configStack);
        return stacks.isEmpty() ? null : stacks.get(0);
    }

    public List<ItemStack> createStacks(Materials material, ItemStack configStack) {
        if (isEmpty()) {
            return java.util.Collections.emptyList();
        }
        if (this.oreDictMode) {
            return createOreDictStacks(material, configStack);
        }
        ItemStack stack = createNameMatchedStack(material, configStack);
        return stack == null ? java.util.Collections.<ItemStack>emptyList() : java.util.Collections.singletonList(stack);
    }

    private List<ItemStack> createOreDictStacks(Materials material, ItemStack configStack) {
        String matcher = normalizeOreMatcher(this.matcher);
        if (matcher.isEmpty() || isMatchAllPattern(matcher) || material == null) {
            return java.util.Collections.emptyList();
        }
        List<OreMatch> matches = new ArrayList<>();
        for (OrePrefixes prefix : OrePrefixes.values()) {
            String oreName = getOreName(prefix, material);
            if (oreName.isEmpty() || !matchesOreName(oreName, matcher)) {
                continue;
            }
            matches.add(new OreMatch(prefix, oreName));
        }
        if (matches.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        matches.sort((left, right) -> {
            if (left.score != right.score) {
                return Integer.compare(left.score, right.score);
            }
            if (left.oreName.length() != right.oreName.length()) {
                return Integer.compare(left.oreName.length(), right.oreName.length());
            }
            return left.oreName.compareToIgnoreCase(right.oreName);
        });

        List<ItemStack> result = new ArrayList<>();
        for (OreMatch match : matches) {
            ItemStack preferred = getPreferredOreStack(configStack, match.prefix, material);
            if (preferred != null) {
                preferred.stackSize = getClampedAmount();
                addDistinctStack(result, preferred);
                continue;
            }
            ItemStack unified = GTOreDictUnificator.get(match.prefix, material, getClampedAmount());
            if (unified != null) {
                unified.stackSize = getClampedAmount();
                addDistinctStack(result, unified);
                continue;
            }
            ItemStack fallback = getDefaultPreferredOreStack(match.oreName);
            if (fallback != null) {
                fallback.stackSize = getClampedAmount();
                addDistinctStack(result, fallback);
            }
        }
        return result;
    }

    private ItemStack createNameMatchedStack(Materials material, ItemStack configStack) {
        if (material == null) {
            return null;
        }

        ItemStack preferred = tryCreatePreferredStack(material, configStack);
        if (preferred != null && matchesName(preferred.getDisplayName(), this.matcher)) {
            preferred.stackSize = getClampedAmount();
            return preferred;
        }

        for (OrePrefixes prefix : OrePrefixes.values()) {
            ItemStack candidate = getPreferredOreStack(configStack, prefix, material);
            if (candidate == null) {
                candidate = GTOreDictUnificator.get(prefix, material, getClampedAmount());
            }
            if (candidate != null && matchesName(candidate.getDisplayName(), this.matcher)) {
                candidate.stackSize = getClampedAmount();
                return candidate;
            }
        }

        if (this.stack != null && matchesName(this.stack.getDisplayName(), this.matcher)) {
            ItemStack copy = this.stack.copy();
            copy.stackSize = getClampedAmount();
            return copy;
        }

        for (OrePrefixes prefix : OrePrefixes.values()) {
            String oreName = getOreName(prefix, material);
            if (oreName.isEmpty()) {
                continue;
            }
            java.util.ArrayList<ItemStack> options = OreDictionary.getOres(oreName);
            if (options == null || options.isEmpty()) {
                continue;
            }
            for (ItemStack option : options) {
                if (option == null || option.getItem() == null || !matchesName(option.getDisplayName(), this.matcher)) {
                    continue;
                }
                ItemStack copy = option.copy();
                copy.stackSize = getClampedAmount();
                return copy;
            }
        }
        return null;
    }

    private ItemStack tryCreatePreferredStack(Materials material, ItemStack configStack) {
        ItemData association = getDisplayAssociation();
        if (association != null && association.hasValidPrefixMaterialData()) {
            ItemStack preferred = getPreferredOreStack(configStack, association.mPrefix, material);
            return preferred != null ? preferred : GTOreDictUnificator.get(association.mPrefix, material, getClampedAmount());
        }
        return null;
    }

    public Materials getTemplateMaterial() {
        ItemData association = getDisplayAssociation();
        return association != null && association.hasValidPrefixMaterialData() ? association.mMaterial.mMaterial : Materials._NULL;
    }

    public boolean canOreDict() {
        ItemData association = getDisplayAssociation();
        return association != null && association.hasValidPrefixMaterialData();
    }

    public void convertToOreDict() {
        this.oreDictMode = true;
        if (canOreDict()) {
            this.matcher = getPrefixName(getDisplayAssociation().mPrefix) + "*";
        } else if (this.matcher == null || this.matcher.trim().isEmpty()) {
            this.matcher = "*";
        }
    }

    public void convertToItem() {
        this.oreDictMode = false;
        if (this.displayStack != null) {
            this.matcher = this.displayStack.getDisplayName();
        }
    }

    public ItemStack getDisplayStack() {
        if (this.displayStack != null) {
            ItemStack copy = this.displayStack.copy();
            copy.stackSize = getClampedAmount();
            return copy;
        }
        if (this.stack != null) {
            ItemStack copy = this.stack.copy();
            copy.stackSize = getClampedAmount();
            return copy;
        }
        return null;
    }

    public String getLabel() {
        String value = getMatcher();
        if (this.oreDictMode) {
            return value;
        }
        return value;
    }

    public boolean isOreDict() {
        return this.oreDictMode;
    }

    public Set<Materials> getOreDictCandidateMaterials() {
        if (!this.oreDictMode) {
            return new LinkedHashSet<>();
        }

        String matcher = normalizeOreMatcher(this.matcher);
        if (matcher.isEmpty() || isMatchAllPattern(matcher)) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(ORE_CANDIDATE_CACHE.computeIfAbsent(matcher, WildcardPatternEntry::collectOreDictCandidateMaterials));
    }

    private static Set<Materials> collectOreDictCandidateMaterials(String matcher) {
        Set<Materials> result = new LinkedHashSet<>();
        for (String oreName : OreDictionary.getOreNames()) {
            if (oreName == null || oreName.isEmpty() || !matchesOreName(oreName, matcher)) {
                continue;
            }

            Materials parsed = extractMaterialFromOreName(oreName);
            if (parsed != null && parsed != Materials._NULL && parsed != Materials.Empty) {
                result.add(parsed);
            }

            for (ItemStack option : OreDictionary.getOres(oreName)) {
                Materials associated = getAssociatedMaterial(option);
                if (associated != null && associated != Materials._NULL && associated != Materials.Empty) {
                    result.add(associated);
                }
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return getMatcher().isEmpty() && this.stack == null && this.displayStack == null;
    }

    public String getMatcher() {
        return this.matcher == null ? "" : this.matcher.trim();
    }

    public void setMatcher(String matcher) {
        this.matcher = matcher == null ? "" : matcher.trim();
    }

    public void setOreNameOrPrefix(String oreNameOrPrefix) {
        setMatcher(oreNameOrPrefix);
    }

    public static boolean looksLikeOreDictPattern(String value) {
        String matcher = value == null ? "" : value.trim();
        if (matcher.isEmpty() || matcher.indexOf(' ') >= 0) {
            return false;
        }
        boolean hasLower = false;
        boolean hasUpper = false;
        for (int index = 0; index < matcher.length(); index++) {
            char current = matcher.charAt(index);
            if (current == '*' || current == '?') {
                continue;
            }
            if (Character.isLowerCase(current)) {
                hasLower = true;
                continue;
            }
            if (Character.isUpperCase(current)) {
                hasUpper = true;
                continue;
            }
            if (!Character.isDigit(current)) {
                return false;
            }
        }
        return hasLower || hasUpper;
    }

    public String getOrePrefix() {
        return getMatcher();
    }

    public int getAmount() {
        return getClampedAmount();
    }

    public long getAmountLong() {
        return Math.max(1L, Math.min(MAX_AMOUNT, this.amount));
    }

    public void setAmount(int amount) {
        setAmount((long) amount);
    }

    public void setAmount(long amount) {
        this.amount = Math.max(1L, Math.min(MAX_AMOUNT, amount));
    }

    public void multiplyAmount(int factor) {
        if (factor <= 1) {
            return;
        }
        long current = getAmountLong();
        if (current > MAX_AMOUNT / factor) {
            this.amount = MAX_AMOUNT;
            return;
        }
        setAmount(current * factor);
    }

    public void divideAmount(int divisor) {
        if (divisor <= 1) {
            return;
        }
        this.amount = Math.max(1L, getAmountLong() / divisor);
    }

    public void setStack(ItemStack stack) {
        this.stack = stack == null ? null : stack.copy();
        this.displayStack = this.stack == null ? null : this.stack.copy();
        if (this.stack != null && !this.oreDictMode) {
            this.matcher = this.stack.getDisplayName();
        }
    }

    public ItemStack getStack() {
        return this.stack == null ? null : this.stack.copy();
    }

    private ItemData getDisplayAssociation() {
        ItemStack target = getDisplayStack();
        return target == null ? null : GTOreDictUnificator.getAssociation(target);
    }

    private static String normalizeOreToken(String value) {
        String token = value == null ? "" : value.trim();
        if (token.endsWith("*")) {
            token = token.substring(0, token.length() - 1).trim();
        }
        return token;
    }

    private static String normalizeOreMatcher(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean matchesName(String displayName, String wildcardPattern) {
        if (displayName == null) {
            return false;
        }
        String pattern = wildcardPattern == null ? "" : wildcardPattern.trim();
        if (pattern.isEmpty() || isMatchAllPattern(pattern)) {
            return false;
        }
        if (!containsWildcard(pattern) && !containsRegexMeta(pattern)) {
            return displayName.equalsIgnoreCase(pattern);
        }
        Pattern compiled = NAME_PATTERN_CACHE.computeIfAbsent(pattern, WildcardPatternEntry::compileNamePattern);
        return compiled != null && compiled.matcher(displayName).find();
    }

    private static boolean matchesOreName(String oreName, String pattern) {
        if (oreName == null) {
            return false;
        }
        String normalizedOre = oreName.trim();
        String normalizedPattern = pattern == null ? "" : pattern.trim();
        if (normalizedPattern.isEmpty() || isMatchAllPattern(normalizedPattern)) {
            return false;
        }
        if (!containsWildcard(normalizedPattern)) {
            return normalizedOre.equalsIgnoreCase(normalizedPattern);
        }
        Pattern compiled = ORE_PATTERN_CACHE.computeIfAbsent(normalizedPattern, WildcardPatternEntry::compileOrePattern);
        return compiled != null && compiled.matcher(normalizedOre).matches();
    }

    private static Pattern compileNamePattern(String pattern) {
        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            if (containsWildcard(pattern)) {
                return Pattern.compile(wildcardToRegex(pattern), flags);
            }
            if (containsRegexMeta(pattern)) {
                return Pattern.compile(pattern, flags);
            }
            return Pattern.compile(Pattern.quote(pattern), flags);
        } catch (PatternSyntaxException ignored) {
            try {
                return Pattern.compile(wildcardToRegex(pattern), flags);
            } catch (PatternSyntaxException ignoredAgain) {
                return null;
            }
        }
    }

    private static Pattern compileOrePattern(String pattern) {
        try {
            return Pattern.compile(wildcardToRegex(pattern), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException ignored) {
            return null;
        }
    }

    private static boolean containsWildcard(String pattern) {
        return pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0;
    }

    private static boolean isMatchAllPattern(String pattern) {
        if (pattern == null) {
            return false;
        }
        String trimmed = pattern.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (current != '*' && current != '?') {
                return false;
            }
        }
        return true;
    }

    private static boolean containsRegexMeta(String pattern) {
        for (int index = 0; index < pattern.length(); index++) {
            if ("\\.^$|()[]{}+".indexOf(pattern.charAt(index)) >= 0) {
                return true;
            }
        }
        return false;
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

    private ItemStack getPreferredOreStack(ItemStack configStack, OrePrefixes prefix, Materials material) {
        String oreName = getOreName(prefix, material);
        if (oreName.isEmpty()) {
            return null;
        }
        ItemStack preferred = com.myname.wildcardpattern.item.WildcardPatternConfig.getPreferredOreStack(configStack, oreName);
        if (preferred == null) {
            preferred = getDefaultPreferredOreStack(oreName);
        }
        if (preferred == null) {
            return null;
        }
        java.util.ArrayList<ItemStack> options = OreDictionary.getOres(oreName);
        boolean matched = false;
        for (ItemStack option : options) {
            if (OreDictionary.itemMatches(option, preferred, false)) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            return null;
        }
        ItemStack copy = preferred.copy();
        copy.stackSize = getClampedAmount();
        return copy;
    }

    private int getClampedAmount() {
        return (int) Math.max(1L, Math.min((long) Integer.MAX_VALUE, getAmountLong()));
    }

    private static ItemStack getDefaultPreferredOreStack(String oreName) {
        java.util.ArrayList<ItemStack> options = OreDictionary.getOres(oreName);
        if (options == null || options.isEmpty()) {
            return null;
        }
        for (ItemStack option : options) {
            if (option == null || option.getItem() == null) {
                continue;
            }
            GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(option.getItem());
            if (id != null && "gregtech".equalsIgnoreCase(id.modId)) {
                return option.copy();
            }
        }
        return options.get(0) == null ? null : options.get(0).copy();
    }

    private static void addDistinctStack(List<ItemStack> stacks, ItemStack candidate) {
        if (candidate == null) {
            return;
        }
        for (ItemStack existing : stacks) {
            if (existing == null) {
                continue;
            }
            if (OreDictionary.itemMatches(existing, candidate, false)
                && ItemStack.areItemStackTagsEqual(existing, candidate)) {
                return;
            }
        }
        stacks.add(candidate.copy());
    }

    private static String getOreName(OrePrefixes prefix, Materials material) {
        String prefixName = getPrefixName(prefix);
        return prefixName.isEmpty() || material == null ? "" : prefixName + material.mName;
    }

    private static Materials getAssociatedMaterial(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemData association = GTOreDictUnificator.getAssociation(stack);
        return association != null && association.hasValidPrefixMaterialData() ? association.mMaterial.mMaterial : null;
    }

    private static Materials extractMaterialFromOreName(String oreName) {
        if (oreName == null || oreName.isEmpty()) {
            return null;
        }
        Materials bestMatch = null;
        int bestPrefixLength = -1;
        for (OrePrefixes prefix : OrePrefixes.values()) {
            String prefixName = getPrefixName(prefix);
            if (prefixName.isEmpty() || !oreName.regionMatches(true, 0, prefixName, 0, prefixName.length())) {
                continue;
            }
            Materials candidate = findMaterialByName(oreName.substring(prefixName.length()));
            if (candidate != null && prefixName.length() > bestPrefixLength) {
                bestMatch = candidate;
                bestPrefixLength = prefixName.length();
            }
        }
        return bestMatch;
    }

    private static Materials findMaterialByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        Materials cached = MATERIAL_NAME_CACHE.get(normalized);
        if (cached != null) {
            return cached;
        }
        for (Materials material : Materials.getAll()) {
            if (material == null || material == Materials._NULL || material == Materials.Empty || material.mName == null) {
                continue;
            }
            MATERIAL_NAME_CACHE.putIfAbsent(material.mName.toLowerCase(Locale.ROOT), material);
        }
        return MATERIAL_NAME_CACHE.get(normalized);
    }

    private static final class OreMatch {
        private final OrePrefixes prefix;
        private final String oreName;
        private final int score;

        private OreMatch(OrePrefixes prefix, String oreName) {
            this.prefix = prefix;
            this.oreName = oreName == null ? "" : oreName;
            this.score = computeScore(prefix, this.oreName);
        }

        private static int computeScore(OrePrefixes prefix, String oreName) {
            String prefixName = getPrefixName(prefix).toLowerCase(Locale.ROOT);
            if ("plate".equals(prefixName)) {
                return 0;
            }
            if (prefixName.startsWith("plate")) {
                return 100 + prefixName.length();
            }
            return 1000 + oreName.length();
        }
    }
}
