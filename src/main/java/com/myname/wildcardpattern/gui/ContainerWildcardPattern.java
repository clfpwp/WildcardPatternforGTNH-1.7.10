package com.myname.wildcardpattern.gui;

import com.myname.wildcardpattern.ModItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

public class ContainerWildcardPattern extends Container {

    private final int slot;

    public ContainerWildcardPattern(int slot) {
        this.slot = slot;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        ItemStack stack = player.inventory.getStackInSlot(this.slot);
        return stack != null && stack.getItem() == ModItems.wildcardPattern;
    }
}
