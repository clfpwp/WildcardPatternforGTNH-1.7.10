package com.myname.wildcardpattern.gui;

import java.util.function.Consumer;

import com.gtnewhorizons.modularui.api.widget.IDragAndDropHandler;
import com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget;

import net.minecraft.item.ItemStack;

public class WildcardEntryDropTextField extends TextFieldWidget implements IDragAndDropHandler {

    private final Consumer<ItemStack> dropHandler;

    public WildcardEntryDropTextField(Consumer<ItemStack> dropHandler) {
        this.dropHandler = dropHandler;
    }

    @Override
    public boolean handleDragAndDrop(ItemStack draggedStack, int button) {
        if (draggedStack == null || this.dropHandler == null) {
            return false;
        }
        ItemStack copy = draggedStack.copy();
        copy.stackSize = Math.max(1, draggedStack.stackSize);
        this.dropHandler.accept(copy);
        markForUpdate();
        return true;
    }
}
