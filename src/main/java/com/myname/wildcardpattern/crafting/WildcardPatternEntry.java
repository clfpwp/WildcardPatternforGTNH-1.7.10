package com.myname.wildcardpattern.crafting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
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
    private static final Map<String, Set<String>> ORE_CANDIDATE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> NAME_CANDIDATE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Materials> MATERIAL_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> DEFAULT_ORE_STACK_CACHE = new ConcurrentHashMap<>();
    private static volatile Set<String> ALL_KNOWN_MATERIAL_NAMES;

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
        String displayName = safeGetDisplayName(stack);
        entry.matcher = displayName == null ? "" : displayName;
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
            String displayName = safeGetDisplayName(entry.stack);
            entry.matcher = displayName == null ? "" : displayName;
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

    public ItemStack createStack(String materialName, ItemStack configStack) {
        if (isEmpty()) {
            return null;
        }
        if (this.oreDictMode) {
            return createOreDictStack(materialName, configStack);
        }
        return createNameMatchedStack(materialName, configStack);
    }

    public ItemStack createStack(Materials material, ItemStack configStack) {
        return createStack(material == null ? null : material.mName, configStack);
    }

    public Set<String> getCandidateMaterials() {
        if (this.oreDictMode) {
            String oreMatcher = normalizeOreMatcher(this.matcher);
            if (oreMatcher.isEmpty() || isMatchAllPattern(oreMatcher)) {
                return new LinkedHashSet<>();
            }
            return new LinkedHashSet<>(ORE_CANDIDATE_CACHE.computeIfAbsent(oreMatcher, WildcardPatternEntry::collectOreDictCandidateMaterials));
        }

        String nameMatcher = getMatcher();
        if (nameMatcher.isEmpty() || isMatchAllPattern(nameMatcher)) {
            return new LinkedHashSet<>();
        }
        // Fast path: exact matcher — look up candidates from the item's own ore dict IDs
        // instead of iterating all ore dict entries (avoids lock contention with main thread).
        if (!containsWildcard(nameMatcher) && !containsRegexMeta(nameMatcher)) {
            return getDirectCandidateMaterials();
        }
        return new LinkedHashSet<>(NAME_CANDIDATE_CACHE.computeIfAbsent(nameMatcher, WildcardPatternEntry::collectNameCandidateMaterials));
    }

    // Returns candidate material names derived directly from the display item's ore dict
    // registrations. O(oreIDs) rather than O(allOreNames), so it doesn't hold the
    // OreDictionary lock long enough to stall the main thread.
    private Set<String> getDirectCandidateMaterials() {
        Set<String> result = new LinkedHashSet<>();
        ItemStack source = this.displayStack != null ? this.displayStack : this.stack;
        if (source == null) {
            return result;
        }
        ItemData association = GTOreDictUnificator.getAssociation(source);
        if (association != null && association.hasValidPrefixMaterialData()) {
            Materials mat = association.mMaterial.mMaterial;
            if (isRealMaterial(mat) && mat.mName != null && !mat.mName.isEmpty()) {
                result.add(mat.mName);
                return result;
            }
        }
        int[] oreIds = OreDictionary.getOreIDs(source);
        if (oreIds != null) {
            for (int oreId : oreIds) {
                String materialName = extractMaterialNameFromOreName(OreDictionary.getOreName(oreId));
                if (!materialName.isEmpty()) {
                    result.add(materialName);
                }
            }
        }
        return result;
    }

    // True when this entry is name-mode AND none of its display item's ore names carry a
    // recognised GT ore prefix.  Such entries always resolve to exactly one fixed item.
    boolean isDirectItem() {
        return !this.oreDictMode && getDisplayPrefix() == null;
    }

    public static Set<String> getAllKnownMaterialNames() {
        Set<String> cached = ALL_KNOWN_MATERIAL_NAMES;
        if (cached != null) {
            return new LinkedHashSet<>(cached);
        }

        LinkedHashSet<String> built = new LinkedHashSet<>();
        for (Materials material : Materials.getAll()) {
            if (isRealMaterial(material) && material.mName != null && !material.mName.isEmpty()) {
                built.add(material.mName);
            }
        }
        for (String oreName : OreDictionary.getOreNames()) {
            String materialName = extractMaterialNameFromOreName(oreName);
            if (!materialName.isEmpty()) {
                built.add(materialName);
            }
        }
        ALL_KNOWN_MATERIAL_NAMES = built;
        return new LinkedHashSet<>(built);
    }

    private ItemStack createOreDictStack(String materialName, ItemStack configStack) {
        String matcherValue = normalizeOreMatcher(this.matcher);
        if (matcherValue.isEmpty() || isMatchAllPattern(matcherValue) || materialName == null || materialName.trim().isEmpty()) {
            return null;
        }

        Materials material = findMaterialByName(materialName);
        String token = normalizeOreToken(matcherValue);
        OrePrefixes exactPrefix = findPrefix(token);
        if (exactPrefix != null) {
            return createPreferredOreVariant(configStack, exactPrefix, materialName, material);
        }

        OreMatch bestMatch = null;
        for (OrePrefixes prefix : OrePrefixes.values()) {
            String oreName = getOreName(prefix, materialName);
            if (oreName.isEmpty() || !matchesOreName(oreName, matcherValue)) {
                continue;
            }
            OreMatch current = new OreMatch(prefix, oreName);
            if (bestMatch == null || current.compareTo(bestMatch) < 0) {
                bestMatch = current;
            }
        }
        if (bestMatch == null) {
            return null;
        }

        return createPreferredOreVariant(configStack, bestMatch.prefix, materialName, material);
    }

    private ItemStack createNameMatchedStack(String materialName, ItemStack configStack) {
        if (materialName == null || materialName.trim().isEmpty()) {
            return null;
        }

        // Short-circuit for items with no recognised ore prefix (e.g. vanilla items).
        // There is no ore-prefix expansion possible, so return the display stack directly
        // instead of iterating all OrePrefixes looking for a match that can never exist.
        if (getDisplayPrefix() == null) {
            ItemStack source = this.displayStack != null ? this.displayStack : this.stack;
            if (source != null && matchesName(safeGetDisplayName(source), this.matcher)) {
                ItemStack copy = source.copy();
                copy.stackSize = getClampedAmount();
                return copy;
            }
            return null;
        }

        ItemStack preferred = tryCreatePreferredStack(materialName, configStack);
        if (preferred != null && matchesName(safeGetDisplayName(preferred), this.matcher)) {
            preferred.stackSize = getClampedAmount();
            return preferred;
        }

        ItemStack source = this.stack != null ? this.stack : this.displayStack;
        String sourceMaterial = getAssociatedMaterialName(source);
        if (source != null
            && !sourceMaterial.isEmpty()
            && sourceMaterial.equalsIgnoreCase(materialName)
            && matchesName(safeGetDisplayName(source), this.matcher)) {
            ItemStack copy = source.copy();
            copy.stackSize = getClampedAmount();
            return copy;
        }

        Materials material = findMaterialByName(materialName);
        for (OrePrefixes prefix : OrePrefixes.values()) {
            ItemStack candidate = getPreferredOreStack(configStack, prefix, materialName);
            if (candidate == null && isRealMaterial(material)) {
                candidate = GTOreDictUnificator.get(prefix, material, getClampedAmount());
            }
            if (candidate != null && matchesName(safeGetDisplayName(candidate), this.matcher)) {
                candidate.stackSize = getClampedAmount();
                return candidate;
            }
        }

        for (OrePrefixes prefix : OrePrefixes.values()) {
            String oreName = getOreName(prefix, materialName);
            if (oreName.isEmpty()) {
                continue;
            }
            ArrayList<ItemStack> options = OreDictionary.getOres(oreName);
            if (options == null || options.isEmpty()) {
                continue;
            }
            for (ItemStack option : options) {
                if (option == null || option.getItem() == null || !matchesName(safeGetDisplayName(option), this.matcher)) {
                    continue;
                }
                ItemStack copy = option.copy();
                copy.stackSize = getClampedAmount();
                return copy;
            }
        }
        return null;
    }

    private ItemStack tryCreatePreferredStack(String materialName, ItemStack configStack) {
        OrePrefixes prefix = getDisplayPrefix();
        return prefix == null ? null : createPreferredOreVariant(configStack, prefix, materialName, findMaterialByName(materialName));
    }

    public Materials getTemplateMaterial() {
        ItemData association = getDisplayAssociation();
        if (association != null && association.hasValidPrefixMaterialData()) {
            return association.mMaterial.mMaterial;
        }
        return findMaterialByName(extractMaterialNameFromOreName(getDisplayOreName()));
    }

    public boolean canOreDict() {
        return getDisplayPrefix() != null;
    }

    public void convertToOreDict() {
        this.oreDictMode = true;
        OrePrefixes prefix = getDisplayPrefix();
        if (prefix != null) {
            this.matcher = getPrefixName(prefix) + "*";
        } else if (this.matcher == null || this.matcher.trim().isEmpty()) {
            this.matcher = "*";
        }
    }

    public void convertToItem() {
        this.oreDictMode = false;
        if (this.displayStack != null) {
            String displayName = safeGetDisplayName(this.displayStack);
            this.matcher = displayName == null ? "" : displayName;
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
        return getMatcher();
    }

    public boolean isOreDict() {
        return this.oreDictMode;
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
        boolean hasLetter = false;
        for (int index = 0; index < matcher.length(); index++) {
            char current = matcher.charAt(index);
            if (current == '*' || current == '?') {
                continue;
            }
            if (Character.isLetterOrDigit(current)) {
                hasLetter = true;
                continue;
            }
            return false;
        }
        return hasLetter;
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
            String displayName = safeGetDisplayName(this.stack);
            this.matcher = displayName == null ? "" : displayName;
        }
    }

    public ItemStack getStack() {
        return this.stack == null ? null : this.stack.copy();
    }

    private ItemData getDisplayAssociation() {
        ItemStack target = getDisplayStack();
        return target == null ? null : GTOreDictUnificator.getAssociation(target);
    }

    private OrePrefixes getDisplayPrefix() {
        ItemData association = getDisplayAssociation();
        if (association != null && association.hasValidPrefixMaterialData()) {
            return association.mPrefix;
        }
        return extractPrefixFromOreName(getDisplayOreName());
    }

    private String getDisplayOreName() {
        ItemStack target = this.displayStack != null ? this.displayStack : this.stack;
        return getBestOreName(target);
    }

    private static Set<String> collectOreDictCandidateMaterials(String matcher) {
        String normalized = normalizeOreMatcher(matcher);
        if (normalized.isEmpty()) {
            return new LinkedHashSet<>();
        }

        if (!containsWildcard(normalized)) {
            return collectExactOreDictCandidateMaterials(normalized);
        }

        if (isSimplePrefixWildcard(normalized)) {
            return collectPrefixOreDictCandidateMaterials(normalizeOreToken(normalized));
        }

        Set<String> result = new LinkedHashSet<>();
        for (String oreName : OreDictionary.getOreNames()) {
            if (oreName == null || oreName.isEmpty() || !matchesOreName(oreName, normalized)) {
                continue;
            }
            collectMaterialNamesForOreName(result, oreName);
        }
        return result;
    }

    private static Set<String> collectExactOreDictCandidateMaterials(String oreName) {
        Set<String> result = new LinkedHashSet<>();
        if (oreName == null || oreName.isEmpty()) {
            return result;
        }
        for (String actualOreName : OreDictionary.getOreNames()) {
            if (actualOreName != null && actualOreName.equalsIgnoreCase(oreName)) {
                collectMaterialNamesForOreName(result, actualOreName);
            }
        }
        return result;
    }

    private static Set<String> collectPrefixOreDictCandidateMaterials(String prefixToken) {
        Set<String> result = new LinkedHashSet<>();
        OrePrefixes prefix = findPrefix(prefixToken);
        if (prefix == null) {
            return result;
        }

        String prefixName = getPrefixName(prefix);
        for (String oreName : OreDictionary.getOreNames()) {
            if (oreName == null || oreName.isEmpty()) {
                continue;
            }
            if (!oreName.regionMatches(true, 0, prefixName, 0, prefixName.length())) {
                continue;
            }
            String materialName = extractMaterialNameFromOreName(oreName);
            if (!materialName.isEmpty()) {
                result.add(materialName);
            }
        }
        return result;
    }

    private static void collectMaterialNamesForOreName(Set<String> result, String oreName) {
        if (result == null || oreName == null || oreName.isEmpty()) {
            return;
        }
        String parsed = extractMaterialNameFromOreName(oreName);
        if (!parsed.isEmpty()) {
            result.add(parsed);
        }

        ArrayList<ItemStack> options = OreDictionary.getOres(oreName);
        if (options == null) {
            return;
        }
        for (ItemStack option : options) {
            String associated = getAssociatedMaterialName(option);
            if (!associated.isEmpty()) {
                result.add(associated);
            }
        }
    }

    private static Set<String> collectNameCandidateMaterials(String matcher) {
        Set<String> result = new LinkedHashSet<>();
        for (String oreName : OreDictionary.getOreNames()) {
            ArrayList<ItemStack> options = OreDictionary.getOres(oreName);
            if (options == null) {
                continue;
            }
            String materialName = extractMaterialNameFromOreName(oreName);
            for (ItemStack option : options) {
                String displayName = safeGetDisplayName(option);
                if (displayName == null || !matchesName(displayName, matcher)) {
                    continue;
                }
                if (!materialName.isEmpty()) {
                    result.add(materialName);
                }
                String associated = getAssociatedMaterialName(option);
                if (!associated.isEmpty()) {
                    result.add(associated);
                }
                break;
            }
        }
        return result;
    }

    private static boolean isRealMaterial(Materials material) {
        return material != null && material != Materials._NULL && material != Materials.Empty;
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

    private static boolean isSimplePrefixWildcard(String pattern) {
        if (pattern == null || !pattern.endsWith("*") || pattern.indexOf('?') >= 0) {
            return false;
        }
        return pattern.indexOf('*') == pattern.length() - 1;
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

    private static OrePrefixes findPrefix(String token) {
        OrePrefixes exact = OrePrefixes.getPrefix(token);
        if (exact != null) {
            return exact;
        }
        if (token == null || token.isEmpty()) {
            return null;
        }
        for (OrePrefixes prefix : OrePrefixes.values()) {
            String prefixName = getPrefixName(prefix);
            if (!prefixName.isEmpty() && prefixName.equalsIgnoreCase(token)) {
                return prefix;
            }
        }
        return null;
    }

    private ItemStack createPreferredOreVariant(
        ItemStack configStack,
        OrePrefixes prefix,
        String materialName,
        Materials material) {
        ItemStack preferred = getPreferredOreStack(configStack, prefix, materialName);
        if (preferred != null) {
            preferred.stackSize = getClampedAmount();
            return preferred;
        }
        if (isRealMaterial(material)) {
            ItemStack unified = GTOreDictUnificator.get(prefix, material, getClampedAmount());
            if (unified != null) {
                unified.stackSize = getClampedAmount();
                return unified;
            }
        }
        String oreName = getOreName(prefix, materialName);
        ItemStack fallback = getDefaultPreferredOreStack(oreName);
        if (fallback != null) {
            fallback.stackSize = getClampedAmount();
        }
        return fallback;
    }

    private ItemStack getPreferredOreStack(ItemStack configStack, OrePrefixes prefix, String materialName) {
        String oreName = getOreName(prefix, materialName);
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
        ArrayList<ItemStack> options = OreDictionary.getOres(oreName);
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

    private static final ItemStack MISSING_ORE_SENTINEL = new ItemStack(net.minecraft.init.Items.apple, 0);

    private static ItemStack getDefaultPreferredOreStack(String oreName) {
        ItemStack cached = DEFAULT_ORE_STACK_CACHE.get(oreName);
        if (cached != null) {
            return cached == MISSING_ORE_SENTINEL ? null : cached.copy();
        }
        ItemStack result = computeDefaultPreferredOreStack(oreName);
        DEFAULT_ORE_STACK_CACHE.put(oreName, result != null ? result : MISSING_ORE_SENTINEL);
        return result;
    }

    private static ItemStack computeDefaultPreferredOreStack(String oreName) {
        ArrayList<ItemStack> options = OreDictionary.getOres(oreName);
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

    private static String getOreName(OrePrefixes prefix, String materialName) {
        String prefixName = getPrefixName(prefix);
        String name = materialName == null ? "" : materialName.trim();
        return prefixName.isEmpty() || name.isEmpty() ? "" : prefixName + name;
    }

    private static Materials getAssociatedMaterial(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemData association = GTOreDictUnificator.getAssociation(stack);
        return association != null && association.hasValidPrefixMaterialData() ? association.mMaterial.mMaterial : null;
    }

    private static String getAssociatedMaterialName(ItemStack stack) {
        Materials associated = getAssociatedMaterial(stack);
        if (isRealMaterial(associated) && associated.mName != null && !associated.mName.isEmpty()) {
            return associated.mName;
        }

        int[] oreIds = stack == null ? null : OreDictionary.getOreIDs(stack);
        if (oreIds == null || oreIds.length == 0) {
            return "";
        }
        String best = "";
        for (int oreId : oreIds) {
            String candidate = extractMaterialNameFromOreName(OreDictionary.getOreName(oreId));
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return best;
    }

    private static String safeGetDisplayName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        try {
            return stack.getDisplayName();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String getBestOreName(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        int[] oreIds = OreDictionary.getOreIDs(stack);
        if (oreIds == null || oreIds.length == 0) {
            return null;
        }

        String first = null;
        String best = null;
        int bestScore = Integer.MAX_VALUE;
        int bestLength = Integer.MAX_VALUE;
        for (int oreId : oreIds) {
            String oreName = OreDictionary.getOreName(oreId);
            if (oreName == null || oreName.isEmpty()) {
                continue;
            }
            if (first == null) {
                first = oreName;
            }
            OrePrefixes prefix = extractPrefixFromOreName(oreName);
            if (prefix == null) {
                continue;
            }
            int score = OreMatch.computeScore(prefix, oreName);
            if (best == null || score < bestScore || score == bestScore && oreName.length() < bestLength) {
                best = oreName;
                bestScore = score;
                bestLength = oreName.length();
            }
        }
        return best != null ? best : first;
    }

    private static OrePrefixes extractPrefixFromOreName(String oreName) {
        if (oreName == null || oreName.isEmpty()) {
            return null;
        }
        OrePrefixes bestMatch = null;
        int bestPrefixLength = -1;
        for (OrePrefixes prefix : OrePrefixes.values()) {
            String prefixName = getPrefixName(prefix);
            if (prefixName.isEmpty() || !oreName.regionMatches(true, 0, prefixName, 0, prefixName.length())) {
                continue;
            }
            if (prefixName.length() > bestPrefixLength) {
                bestMatch = prefix;
                bestPrefixLength = prefixName.length();
            }
        }
        return bestMatch;
    }

    private static String extractMaterialNameFromOreName(String oreName) {
        OrePrefixes prefix = extractPrefixFromOreName(oreName);
        if (prefix == null) {
            return "";
        }
        String prefixName = getPrefixName(prefix);
        if (prefixName.isEmpty() || oreName == null || oreName.length() <= prefixName.length()) {
            return "";
        }
        return oreName.substring(prefixName.length()).trim();
    }

    private static Materials findMaterialByName(String name) {
        if (name == null || name.isEmpty()) {
            return Materials._NULL;
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
        Materials material = MATERIAL_NAME_CACHE.get(normalized);
        return material == null ? Materials._NULL : material;
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

        private int compareTo(OreMatch other) {
            if (other == null) {
                return -1;
            }
            if (this.score != other.score) {
                return Integer.compare(this.score, other.score);
            }
            if (this.oreName.length() != other.oreName.length()) {
                return Integer.compare(this.oreName.length(), other.oreName.length());
            }
            return this.oreName.compareToIgnoreCase(other.oreName);
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
