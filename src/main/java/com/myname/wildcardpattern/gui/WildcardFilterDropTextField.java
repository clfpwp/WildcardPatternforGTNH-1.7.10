package com.myname.wildcardpattern.gui;

import java.util.Locale;
import java.util.function.Consumer;

import com.gtnewhorizons.modularui.api.widget.IDragAndDropHandler;
import com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget;

import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.enums.OrePrefixes;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import org.lwjgl.input.Keyboard;

public class WildcardFilterDropTextField extends TextFieldWidget implements IDragAndDropHandler {

    private final Consumer<String> changeListener;
    private Runnable enterHandler;

    public WildcardFilterDropTextField(Consumer<String> changeListener) {
        this.changeListener = changeListener;
    }

    @Override
    public boolean handleDragAndDrop(ItemStack draggedStack, int button) {
        if (draggedStack == null) {
            return false;
        }

        ItemData association = GTOreDictUnificator.getAssociation(draggedStack);
        String token;
        if (association != null && association.hasValidPrefixMaterialData()) {
            token = buildOreToken(association);
        } else {
            token = buildOreTokenFromOreDict(draggedStack);
        }
        if (token.isEmpty()) {
            return false;
        }
        String next = appendToken(getText(), token);
        setText(next);
        if (this.changeListener != null) {
            this.changeListener.accept(next);
        }
        markForUpdate();
        return true;
    }

    @Override
    public boolean onKeyPressed(char character, int keyCode) {
        boolean handled = super.onKeyPressed(character, keyCode);
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (this.enterHandler != null) {
                this.enterHandler.run();
            }
            markForUpdate();
            return true;
        }
        return handled;
    }

    public WildcardFilterDropTextField setOnEnter(Runnable enterHandler) {
        this.enterHandler = enterHandler;
        return this;
    }

    private static String buildOreTokenFromOreDict(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        int[] oreIds = OreDictionary.getOreIDs(stack);
        if (oreIds == null || oreIds.length == 0) {
            return "";
        }
        String best = null;
        int bestPrefixLen = 0;
        for (int oreId : oreIds) {
            String oreName = OreDictionary.getOreName(oreId);
            if (oreName == null || oreName.isEmpty()) {
                continue;
            }
            for (OrePrefixes prefix : OrePrefixes.values()) {
                String prefixName = getPrefixName(prefix);
                if (!prefixName.isEmpty()
                    && oreName.regionMatches(true, 0, prefixName, 0, prefixName.length())
                    && prefixName.length() > bestPrefixLen) {
                    best = oreName;
                    bestPrefixLen = prefixName.length();
                }
            }
        }
        return best != null ? best : "";
    }

    private static String buildOreToken(ItemData association) {
        if (association == null || !association.hasValidPrefixMaterialData()
            || association.mPrefix == null
            || association.mMaterial == null
            || association.mMaterial.mMaterial == null
            || association.mMaterial.mMaterial.mName == null) {
            return "";
        }
        String prefixName = getPrefixName(association.mPrefix);
        if (prefixName.isEmpty()) {
            return association.mMaterial.mMaterial.mName.toLowerCase(Locale.ROOT);
        }
        return prefixName + association.mMaterial.mMaterial.mName;
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

    private static String appendToken(String current, String token) {
        if (current == null || current.trim().isEmpty()) {
            return token;
        }
        for (String part : current.split("[,;，；\\s]+")) {
            if (part.equalsIgnoreCase(token)) {
                return current;
            }
        }
        return current.trim() + "," + token;
    }
}
