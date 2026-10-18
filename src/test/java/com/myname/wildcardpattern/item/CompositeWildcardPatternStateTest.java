package com.myname.wildcardpattern.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.myname.wildcardpattern.crafting.CompositeWildcardPatternGenerator;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

class CompositeWildcardPatternStateTest {

    private static final String FIXED_INPUTS = "CompositeWildcardFixedInputs";
    private static final int[] AMOUNTS = { 1, 127, 128, 255, 256, 257, 2_100_000_000, Integer.MAX_VALUE };
    private static Item patternItem;
    private static Item ingredientItem;

    @BeforeAll
    static void registerItems() throws Exception {
        // Populate only the item registry; these tests do not start an FML game or load mod registries.
        Method register = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        int id = 30_000;
        while (Item.itemRegistry.getObjectById(id) != null || Item.itemRegistry.getObjectById(id + 1) != null) {
            id += 2;
        }
        patternItem = new Item();
        ingredientItem = new Item();
        register.invoke(Item.itemRegistry, id, "wildcard_count_test:pattern", patternItem);
        register.invoke(Item.itemRegistry, id + 1, "wildcard_count_test:ingredient", ingredientItem);
    }

    @Test
    void fixedAmountsSurviveSaveAndReload() throws Exception {
        for (int amount : AMOUNTS) {
            ItemStack pattern = pattern();
            CompositeWildcardPatternState.setFixedInputs(pattern, Collections.singletonList(ingredient(amount)));
            ItemStack reloaded = ItemStack.loadItemStackFromNBT(roundTrip(pattern.writeToNBT(new NBTTagCompound())));
            assertIngredient(CompositeWildcardPatternState.getFixedInputs(reloaded).get(0), amount);
        }
    }

    @Test
    void storesTheFullCountAsALong() {
        for (int amount : AMOUNTS) {
            ItemStack pattern = pattern();
            CompositeWildcardPatternState.setFixedInputs(pattern, Collections.singletonList(ingredient(amount)));
            NBTTagCompound entry = fixedTags(pattern).getCompoundTagAt(0);
            assertTrue(entry.hasKey("Cnt", NBT.TAG_LONG));
            assertEquals((long) amount, entry.getLong("Cnt"));
        }
    }

    @Test
    void fullAmountsSurviveConfigNetworkEncoding() {
        for (int amount : AMOUNTS) {
            ItemStack source = pattern();
            CompositeWildcardPatternState.setFixedInputs(source, Collections.singletonList(ingredient(amount)));
            ByteBuf buffer = Unpooled.buffer();
            try {
                // The configuration messages use this exact codec for their NBT payload.
                ByteBufUtils.writeTag(buffer, CompositeWildcardPatternState.exportConfig(source));
                NBTTagCompound received = ByteBufUtils.readTag(buffer);
                ItemStack destination = pattern();
                CompositeWildcardPatternState.applyConfig(destination, received);
                assertIngredient(CompositeWildcardPatternState.getFixedInputs(destination).get(0), amount);
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void preservesEightSlotsAndCopiesItemData() {
        ItemStack source = ingredient(257);
        List<ItemStack> inputs = new ArrayList<>(Collections.nCopies(9, null));
        inputs.set(0, source);
        inputs.set(2, ingredient(128));
        inputs.set(7, ingredient(Integer.MAX_VALUE));
        inputs.set(8, ingredient(5));
        ItemStack pattern = pattern();
        CompositeWildcardPatternState.setFixedInputs(pattern, inputs);
        assertIngredient(source, 257);
        assertEquals(9, inputs.size());
        source.stackSize = 1;
        source.getTagCompound().setString("marker", "changed");

        List<ItemStack> restored = CompositeWildcardPatternState.getFixedInputs(pattern);
        assertEquals(8, restored.size());
        assertEquals(8, fixedTags(pattern).tagCount());
        assertIngredient(restored.get(0), 257);
        assertIngredient(restored.get(2), 128);
        assertIngredient(restored.get(7), Integer.MAX_VALUE);
        for (int slot : new int[] { 1, 3, 4, 5, 6 }) {
            assertNull(restored.get(slot));
        }
        restored.get(0).stackSize = 2;
        restored.get(0).getTagCompound().setString("marker", "changed again");
        assertIngredient(CompositeWildcardPatternState.getFixedInputs(pattern).get(0), 257);

        CompositeWildcardPatternState.setFixedInputs(pattern, null);
        restored = CompositeWildcardPatternState.getFixedInputs(pattern);
        assertEquals(8, restored.size());
        for (ItemStack stack : restored) {
            assertNull(stack);
        }
    }

    @Test
    void legacyCountOnlyEntriesDoNotGuessLostAmounts() {
        ItemStack pattern = pattern();
        NBTTagList tags = new NBTTagList();
        for (int amount : new int[] { 127, 128, 255, 256, 257 }) {
            tags.appendTag(ingredient(amount).writeToNBT(new NBTTagCompound()));
        }
        pattern.setTagCompound(new NBTTagCompound());
        pattern.getTagCompound().setTag(FIXED_INPUTS, tags);
        List<ItemStack> restored = CompositeWildcardPatternState.getFixedInputs(pattern);
        assertIngredient(restored.get(0), 127);
        assertNull(restored.get(1));
        assertNull(restored.get(2));
        assertNull(restored.get(3));
        assertIngredient(restored.get(4), 1);
        CompositeWildcardPatternState.setFixedInputs(pattern, restored);
        assertEquals(127L, fixedTags(pattern).getCompoundTagAt(0).getLong("Cnt"));
        assertEquals(1L, fixedTags(pattern).getCompoundTagAt(4).getLong("Cnt"));
        assertNull(CompositeWildcardPatternState.getFixedInputs(pattern).get(1));
    }

    @Test
    void invalidFullCountsDoNotFallBackToLegacyCount() {
        NBTTagList tags = new NBTTagList();
        for (long amount : new long[] { 0L, -1L, (long) Integer.MAX_VALUE + 1L, Long.MAX_VALUE }) {
            NBTTagCompound tag = ingredient(7).writeToNBT(new NBTTagCompound());
            tag.setLong("Cnt", amount);
            tags.appendTag(tag);
        }
        NBTTagCompound wrongType = ingredient(7).writeToNBT(new NBTTagCompound());
        wrongType.setInteger("Cnt", 128);
        tags.appendTag(wrongType);
        NBTTagCompound wrongString = ingredient(7).writeToNBT(new NBTTagCompound());
        wrongString.setString("Cnt", "128");
        tags.appendTag(wrongString);
        NBTTagCompound unknownItem = new NBTTagCompound();
        unknownItem.setShort("id", (short) -1);
        unknownItem.setLong("Cnt", 128L);
        tags.appendTag(unknownItem);
        tags.appendTag(new NBTTagCompound());
        ItemStack pattern = pattern();
        pattern.setTagCompound(new NBTTagCompound());
        pattern.getTagCompound().setTag(FIXED_INPUTS, tags);
        List<ItemStack> restored = CompositeWildcardPatternState.getFixedInputs(pattern);
        assertEquals(8, restored.size());
        for (ItemStack stack : restored) {
            assertNull(stack);
        }
    }

    @Test
    void doublingAcrossByteBoundaryPreservesTheFixedIngredient() {
        ItemStack pattern = pattern();
        CompositeWildcardPatternState.setFixedInputs(pattern, Collections.singletonList(ingredient(64)));
        for (int expected : new int[] { 128, 256 }) {
            CompositeWildcardPatternState.applyBitModification(pattern, 1);
            assertIngredient(CompositeWildcardPatternState.getFixedInputs(pattern).get(0), expected);
            assertEquals((long) expected, fixedTags(pattern).getCompoundTagAt(0).getLong("Cnt"));
        }
        CompositeWildcardPatternState.applyBitModification(pattern, -1);
        assertIngredient(CompositeWildcardPatternState.getFixedInputs(pattern).get(0), 128);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatedInputNbtKeepsTheRestoredFixedAmount() throws Exception {
        // Exercise the real generator's filtering and encoding without bootstrapping GT's material registry.
        Method normalize = CompositeWildcardPatternGenerator.class.getDeclaredMethod("normalizeFixedInputs", List.class);
        Method generate = CompositeWildcardPatternGenerator.class.getDeclaredMethod(
            "createPatternStack",
            ItemStack.class,
            String.class,
            List.class,
            ItemStack.class);
        normalize.setAccessible(true);
        generate.setAccessible(true);
        for (int amount : AMOUNTS) {
            ItemStack pattern = pattern();
            CompositeWildcardPatternState.setFixedInputs(pattern, Arrays.asList(null, ingredient(amount)));
            List<ItemStack> fixed = (List<ItemStack>) normalize.invoke(
                null,
                CompositeWildcardPatternState.getFixedInputs(pattern));
            assertEquals(1, fixed.size());
            List<ItemStack> inputs = new ArrayList<>();
            inputs.add(new ItemStack(patternItem, 1, 0));
            inputs.addAll(fixed);
            ItemStack generated = (ItemStack) generate.invoke(
                null,
                pattern,
                "Iron",
                inputs,
                new ItemStack(patternItem, 1, 0));
            assertNotNull(generated);
            NBTTagList encoded = generated.getTagCompound().getTagList("in", NBT.TAG_COMPOUND);
            assertEquals(2, encoded.tagCount());
            NBTTagCompound encodedFixed = encoded.getCompoundTagAt(1);
            assertEquals(amount, encodedFixed.getInteger("Count"));
            assertTrue(encodedFixed.hasKey("Cnt", NBT.TAG_LONG));
            assertEquals((long) amount, encodedFixed.getLong("Cnt"));
            assertEquals("original", encodedFixed.getCompoundTag("tag").getString("marker"));
        }
    }

    private static ItemStack pattern() {
        return new ItemStack(patternItem, 1, 0);
    }

    private static ItemStack ingredient(int amount) {
        ItemStack stack = new ItemStack(ingredientItem, amount, 3);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("marker", "original");
        stack.setTagCompound(tag);
        return stack;
    }

    private static NBTTagList fixedTags(ItemStack pattern) {
        return pattern.getTagCompound().getTagList(FIXED_INPUTS, NBT.TAG_COMPOUND);
    }

    private static NBTTagCompound roundTrip(NBTTagCompound tag) throws Exception {
        return CompressedStreamTools.func_152457_a(CompressedStreamTools.compress(tag), NBTSizeTracker.field_152451_a);
    }

    private static void assertIngredient(ItemStack stack, int amount) {
        assertNotNull(stack);
        assertSame(ingredientItem, stack.getItem());
        assertEquals(amount, stack.stackSize);
        assertEquals(3, stack.getItemDamage());
        assertEquals("original", stack.getTagCompound().getString("marker"));
    }
}
