package com.myname.wildcardpattern.crafting;

import java.util.Arrays;

import com.myname.wildcardpattern.ModItems;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;

/** Exposes the standard ingredient list so NEI can display crafting and usage recipes. */
public class CompositeWildcardRecipe extends ShapelessRecipes {

    public CompositeWildcardRecipe(ItemStack blankPattern, ItemStack oreFilterCard) {
        super(new ItemStack(ModItems.compositeWildcardPattern),
            Arrays.asList(copyIngredient(blankPattern), copyIngredient(oreFilterCard)));
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inventory) {
        ItemStack result = super.getCraftingResult(inventory);
        CompositeWildcardPatternGenerator.markAsCompositeWildcard(result);
        return result;
    }

    private static ItemStack copyIngredient(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }
}
