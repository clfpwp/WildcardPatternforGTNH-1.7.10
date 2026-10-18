package com.myname.wildcardpattern.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import codechicken.nei.recipe.ShapelessRecipeHandler;
import com.myname.wildcardpattern.ModItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapelessRecipes;

class CompositeWildcardRecipeTest {

    private static Item previousComposite;
    private static Item material;
    private static Item composite;

    @BeforeAll
    static void registerItems() throws Exception {
        previousComposite = ModItems.compositeWildcardPattern;
        material = new Item().setHasSubtypes(true);
        composite = new Item();
        Method register = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        int id = 28_000;
        for (Item item : new Item[] { material, composite }) {
            while (Item.itemRegistry.getObjectById(id) != null) id++;
            register.invoke(Item.itemRegistry, id, "wildcard_recipe_test:item_" + id, item);
            id++;
        }
        ModItems.compositeWildcardPattern = composite;
    }

    @AfterAll
    static void restoreItems() {
        ModItems.compositeWildcardPattern = previousComposite;
    }

    @Test
    void neiParsesBothIngredientsAndTheCompositeOutput() throws Exception {
        IRecipe recipe = recipe();
        ShapelessRecipes supported = assertInstanceOf(ShapelessRecipes.class, recipe);
        // Skip the client GUI constructor. The real NEI parser and positioned stacks run below.
        Class<?> factoryClass = Class.forName("sun.reflect.ReflectionFactory");
        Object factory = factoryClass.getMethod("getReflectionFactory").invoke(null);
        Constructor<?> constructor = (Constructor<?>) factoryClass
            .getMethod("newConstructorForSerialization", Class.class, Constructor.class)
            .invoke(factory, ShapelessRecipeHandler.class, Object.class.getDeclaredConstructor());
        constructor.setAccessible(true);
        ShapelessRecipeHandler handler = (ShapelessRecipeHandler) constructor.newInstance();
        handler.stackorder = new int[][] { { 0, 0 }, { 1, 0 } };
        Method parse = ShapelessRecipeHandler.class.getDeclaredMethod("shapelessRecipe", ShapelessRecipes.class);
        parse.setAccessible(true);
        ShapelessRecipeHandler.CachedShapelessRecipe cached =
            (ShapelessRecipeHandler.CachedShapelessRecipe) parse.invoke(handler, supported);
        assertNotNull(cached);
        assertEquals(2, cached.ingredients.size());
        assertEquals(material, cached.ingredients.get(0).items[0].getItem());
        assertEquals(1, cached.ingredients.get(0).items[0].getItemDamage());
        assertEquals(material, cached.ingredients.get(1).items[0].getItem());
        assertEquals(2, cached.ingredients.get(1).items[0].getItemDamage());
        assertEquals(composite, cached.result.items[0].getItem());
        assertEquals(1, cached.result.items[0].stackSize);
    }

    @Test
    void ingredientsMatchInAnyTwoSlotsAndRejectOtherAeCards() {
        IRecipe recipe = recipe();
        for (int size : new int[] { 2, 3 }) {
            for (int first = 0; first < size * size; first++) {
                for (int second = 0; second < size * size; second++) {
                    if (first == second) continue;
                    InventoryCrafting grid = grid(size);
                    grid.setInventorySlotContents(first, new ItemStack(material, 1, 1));
                    grid.setInventorySlotContents(second, new ItemStack(material, 1, 2));
                    assertTrue(recipe.matches(grid, null));
                }
            }
        }
        InventoryCrafting grid = grid(3);
        grid.setInventorySlotContents(0, new ItemStack(material, 1, 1));
        assertFalse(recipe.matches(grid, null));
        grid.setInventorySlotContents(1, new ItemStack(material, 1, 3));
        assertFalse(recipe.matches(grid, null));
        grid.setInventorySlotContents(1, new ItemStack(material, 1, 2));
        grid.setInventorySlotContents(2, new ItemStack(material, 1, 1));
        assertFalse(recipe.matches(grid, null));
    }

    @Test
    void craftingInitializesTheOutputWithoutMutatingIngredientsOrThePreview() {
        ItemStack blank = new ItemStack(material, 64, 1);
        ItemStack card = new ItemStack(material, 12, 2);
        CompositeWildcardRecipe recipe = new CompositeWildcardRecipe(blank, card);
        assertEquals(2, recipe.getRecipeSize());
        assertEquals(1, recipe.recipeItems.get(0).stackSize);
        assertEquals(1, recipe.recipeItems.get(1).stackSize);
        assertNotSame(blank, recipe.recipeItems.get(0));
        assertNotSame(card, recipe.recipeItems.get(1));
        assertEquals(64, blank.stackSize);
        assertEquals(12, card.stackSize);
        ItemStack first = recipe.getCraftingResult(grid(2));
        ItemStack second = recipe.getCraftingResult(grid(2));
        assertEquals(composite, first.getItem());
        assertEquals(1, first.stackSize);
        assertTrue(first.getTagCompound().getBoolean("CompositeWildcardPattern"));
        assertNotSame(first, second);
        assertFalse(recipe.getRecipeOutput().hasTagCompound());
    }

    private static CompositeWildcardRecipe recipe() {
        return new CompositeWildcardRecipe(new ItemStack(material, 1, 1), new ItemStack(material, 1, 2));
    }

    private static InventoryCrafting grid(int size) {
        return new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        }, size, size);
    }
}
