package com.myname.wildcardpattern.crafting;

import com.myname.wildcardpattern.ModItems;

import appeng.api.AEApi;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

public class RecipeInitializedWildcardPattern implements IRecipe {

    @Override
    public boolean matches(InventoryCrafting inventory, World world) {
        boolean foundBlankPattern = false;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack == null) {
                continue;
            }

            ItemStack blankPattern = AEApi.instance().definitions().materials().blankPattern().maybeStack(1).orNull();
            if (blankPattern != null && stack.getItem() == blankPattern.getItem()
                && stack.getItemDamage() == blankPattern.getItemDamage()) {
                if (foundBlankPattern) {
                    return false;
                }
                foundBlankPattern = true;
                continue;
            }
            return false;
        }
        return foundBlankPattern;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inventory) {
        ItemStack result = new ItemStack(ModItems.wildcardPattern);
        WildcardPatternGenerator.markAsWildcard(result);
        return result;
    }

    @Override
    public int getRecipeSize() {
        return 1;
    }

    @Override
    public ItemStack getRecipeOutput() {
        ItemStack result = new ItemStack(ModItems.wildcardPattern);
        WildcardPatternGenerator.markAsWildcard(result);
        return result;
    }
}
