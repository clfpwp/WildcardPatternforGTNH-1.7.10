package com.myname.wildcardpattern.gui;

import java.util.function.Consumer;

import com.gtnewhorizons.modularui.api.widget.IDragAndDropHandler;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;

import net.minecraft.item.ItemStack;

public class WildcardEntryDropButton extends ButtonWidget implements IDragAndDropHandler {

    private final Consumer<ItemStack> dropHandler;
    private final boolean singleItem;

    public WildcardEntryDropButton(Consumer<ItemStack> dropHandler) {
        this(dropHandler, true);
    }

    public WildcardEntryDropButton(Consumer<ItemStack> dropHandler, boolean singleItem) {
        this.dropHandler = dropHandler;
        this.singleItem = singleItem;
    }

    @Override
    public boolean handleDragAndDrop(ItemStack draggedStack, int button) {
        if (draggedStack == null || this.dropHandler == null) {
            return false;
        }
        ItemStack copy = draggedStack.copy();
        if (this.singleItem) copy.stackSize = 1;
        this.dropHandler.accept(copy);
        markForUpdate();
        return true;
    }
}
