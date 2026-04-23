package com.myname.wildcardpattern.crafting;

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

    private boolean oreDictMode;
    private ItemStack stack;
    private ItemStack displayStack;
    private String matcher;
    private int amount;

    public static WildcardPatternEntry fromStack(ItemStack stack) {
        WildcardPatternEntry entry = new WildcardPatternEntry();
        entry.stack = stack == null ? null : stack.copy();
        entry.displayStack = stack == null ? null : stack.copy();
        entry.amount = stack == null ? 1 : Math.max(1, stack.stackSize);
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
        entry.amount = Math.max(1, tag.getInteger(KEY_AMOUNT));
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
        tag.setInteger(KEY_AMOUNT, getAmount());
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
        if (isEmpty()) {
            return null;
        }
        if (this.oreDictMode) {
            return createOreDictStack(material, configStack);
        }
        return createNameMatchedStack(material, configStack);
    }

    private ItemStack createOreDictStack(Materials material, ItemStack configStack) {
        String token = normalizeOreToken(this.matcher);
        OrePrefixes prefix = OrePrefixes.getPrefix(token);
        if (prefix == null || material == null) {
            return null;
        }
        ItemStack preferred = getPreferredOreStack(configStack, prefix, material);
        return preferred != null ? preferred : GTOreDictUnificator.get(prefix, material, getAmount());
    }

    private ItemStack createNameMatchedStack(Materials material, ItemStack configStack) {
        if (material == null) {
            return null;
        }

        ItemStack preferred = tryCreatePreferredStack(material, configStack);
        if (preferred != null && matchesName(preferred.getDisplayName(), this.matcher)) {
            preferred.stackSize = getAmount();
            return preferred;
        }

        for (OrePrefixes prefix : OrePrefixes.values()) {
            ItemStack candidate = getPreferredOreStack(configStack, prefix, material);
            if (candidate == null) {
                candidate = GTOreDictUnificator.get(prefix, material, getAmount());
            }
            if (candidate != null && matchesName(candidate.getDisplayName(), this.matcher)) {
                candidate.stackSize = getAmount();
                return candidate;
            }
        }

        if (this.stack != null && matchesName(this.stack.getDisplayName(), this.matcher)) {
            ItemStack copy = this.stack.copy();
            copy.stackSize = getAmount();
            return copy;
        }
        return null;
    }

    private ItemStack tryCreatePreferredStack(Materials material, ItemStack configStack) {
        ItemData association = getDisplayAssociation();
        if (association != null && association.hasValidPrefixMaterialData()) {
            ItemStack preferred = getPreferredOreStack(configStack, association.mPrefix, material);
            return preferred != null ? preferred : GTOreDictUnificator.get(association.mPrefix, material, getAmount());
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
        if ((this.matcher == null || this.matcher.trim().isEmpty()) && this.displayStack != null) {
            this.matcher = this.displayStack.getDisplayName();
        }
    }

    public ItemStack getDisplayStack() {
        if (this.displayStack != null) {
            ItemStack copy = this.displayStack.copy();
            copy.stackSize = getAmount();
            return copy;
        }
        if (this.stack != null) {
            ItemStack copy = this.stack.copy();
            copy.stackSize = getAmount();
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
        if (this.oreDictMode) {
            this.matcher = normalizeOreToken(this.matcher) + "*";
        }
    }

    public String getOrePrefix() {
        return getMatcher();
    }

    public int getAmount() {
        return Math.max(1, this.amount);
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
    }

    public void multiplyAmount(int factor) {
        if (factor <= 1) {
            return;
        }
        this.amount = Math.max(1, Math.min(Integer.MAX_VALUE, (int) Math.min(Integer.MAX_VALUE, (long) getAmount() * factor)));
    }

    public void divideAmount(int divisor) {
        if (divisor <= 1) {
            return;
        }
        this.amount = Math.max(1, getAmount() / divisor);
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

    private static boolean matchesName(String displayName, String wildcardPattern) {
        if (displayName == null) {
            return false;
        }
        String pattern = wildcardPattern == null ? "" : wildcardPattern.trim();
        if (pattern.isEmpty()) {
            return false;
        }
        Pattern compiled = compileNamePattern(pattern);
        return compiled != null && compiled.matcher(displayName).find();
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

    private static boolean containsWildcard(String pattern) {
        return pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0;
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
        copy.stackSize = getAmount();
        return copy;
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

    private static String getOreName(OrePrefixes prefix, Materials material) {
        String prefixName = getPrefixName(prefix);
        return prefixName.isEmpty() || material == null ? "" : prefixName + material.mName;
    }
}
