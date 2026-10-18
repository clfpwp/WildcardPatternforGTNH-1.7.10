package com.myname.wildcardpattern.gui;

import com.myname.wildcardpattern.crafting.WildcardPatternEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

final class CompositeWildcardRuleEditing {

    private CompositeWildcardRuleEditing() {}

    static WildcardPatternEntry fromDroppedStack(ItemStack stack) {
        return toOreDict(WildcardPatternEntry.fromStack(stack));
    }

    static WildcardPatternEntry toOreDict(WildcardPatternEntry entry) {
        if (!entry.canOreDict()) {
            return emptyEntry(entry, true);
        }
        entry.convertToOreDict();
        return entry;
    }

    static WildcardPatternEntry updateMatcher(WildcardPatternEntry entry, String value) {
        if (value == null || value.trim().isEmpty()) {
            return emptyEntry(entry, entry.isOreDict());
        }
        if (!entry.isOreDict() && entry.isEmpty() && WildcardPatternEntry.looksLikeOreDictPattern(value)) {
            entry = emptyEntry(entry, true);
        }
        entry.setMatcher(value);
        return entry;
    }

    private static WildcardPatternEntry emptyEntry(WildcardPatternEntry previous, boolean oreDictMode) {
        // Omit Stack and Display so loading an empty rule cannot restore the dragged item's name.
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Mode", oreDictMode);
        tag.setLong("Amount", previous.getAmountLong());
        tag.setString("Matcher", "");
        return WildcardPatternEntry.fromNbt(tag);
    }
}
