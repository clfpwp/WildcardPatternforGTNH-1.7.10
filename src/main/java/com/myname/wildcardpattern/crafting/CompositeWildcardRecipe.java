package com.myname.wildcardpattern.crafting;

import appeng.api.AEApi;
import com.myname.wildcardpattern.ModItems;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

public class CompositeWildcardRecipe implements IRecipe {

    @Override
    public boolean matches(InventoryCrafting inventory, World world) {
        return getCraftingResult(inventory) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inventory) {
        boolean foundBlankPattern = false;
        boolean foundOreFilterCard = false;

        for (int index = 0; index < inventory.getSizeInventory(); index++) {
            ItemStack stack = inventory.getStackInSlot(index);
            if (stack == null) {
                continue;
            }
            if (isBlankPattern(stack) && !foundBlankPattern) {
                foundBlankPattern = true;
                continue;
            }
            if (isOreFilterCard(stack) && !foundOreFilterCard) {
                foundOreFilterCard = true;
                continue;
            }
            return null;
        }

        if (!foundBlankPattern || !foundOreFilterCard) {
            return null;
        }
        ItemStack result = new ItemStack(ModItems.compositeWildcardPattern);
        CompositeWildcardPatternGenerator.markAsCompositeWildcard(result);
        return result;
    }

    @Override
    public int getRecipeSize() {
        return 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(ModItems.compositeWildcardPattern);
    }

    private static boolean isBlankPattern(ItemStack stack) {
        ItemStack blankPattern = AEApi.instance().definitions().materials().blankPattern().maybeStack(1).orNull();
        return blankPattern != null && stack.getItem() == blankPattern.getItem() && stack.getItemDamage() == blankPattern.getItemDamage();
    }

    private static boolean isOreFilterCard(ItemStack stack) {
        ItemStack card = AEApi.instance().definitions().materials().cardOreFilter().maybeStack(1).orNull();
        return card != null && stack.getItem() == card.getItem() && stack.getItemDamage() == card.getItemDamage();
    }
}
