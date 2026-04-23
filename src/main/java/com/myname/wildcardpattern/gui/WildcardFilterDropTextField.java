package com.myname.wildcardpattern.gui;

import java.util.Locale;
import java.util.function.Consumer;

import com.gtnewhorizons.modularui.api.widget.IDragAndDropHandler;
import com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget;

import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraft.item.ItemStack;

public class WildcardFilterDropTextField extends TextFieldWidget implements IDragAndDropHandler {

    private final Consumer<String> changeListener;

    public WildcardFilterDropTextField(Consumer<String> changeListener) {
        this.changeListener = changeListener;
    }

    @Override
    public boolean handleDragAndDrop(ItemStack draggedStack, int button) {
        if (draggedStack == null) {
            return false;
        }

        ItemData association = GTOreDictUnificator.getAssociation(draggedStack);
        if (association == null || !association.hasValidPrefixMaterialData()) {
            return false;
        }

        String materialName = association.mMaterial.mMaterial.mName.toLowerCase(Locale.ROOT);
        String next = appendToken(getText(), materialName);
        setText(next);
        if (this.changeListener != null) {
            this.changeListener.accept(next);
        }
        markForUpdate();
        return true;
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
