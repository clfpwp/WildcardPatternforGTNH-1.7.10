package com.myname.wildcardpattern.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
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
            NBTTagCompound encodedFixed = encoded.getCompoundTagAt(0);
            assertEquals(amount, encodedFixed.getInteger("Count"));
            assertTrue(encodedFixed.hasKey("Cnt", NBT.TAG_LONG));
            assertEquals((long) amount, encodedFixed.getLong("Cnt"));
            assertEquals("original", encodedFixed.getCompoundTag("tag").getString("marker"));
            assertSame(patternItem, ItemStack.loadItemStackFromNBT(encoded.getCompoundTagAt(1)).getItem());
        }
    }

    @Test
    void multipleFixedInputsAreEncodedFirstWithoutReorderingThePreview() throws Exception {
        ItemStack wildcard = new ItemStack(patternItem, 5, 2);
        ItemStack first = ingredient(257);
        ItemStack second = ingredient(128);
        second.setItemDamage(7);
        second.getTagCompound().setString("marker", "second");
        Constructor<CompositeWildcardPatternGenerator.GeneratedPattern> constructor =
            CompositeWildcardPatternGenerator.GeneratedPattern.class.getDeclaredConstructor(
                String.class, List.class, ItemStack.class);
        constructor.setAccessible(true);
        CompositeWildcardPatternGenerator.GeneratedPattern preview = constructor.newInstance(
            "Iron", Arrays.asList(wildcard, first, second), new ItemStack(patternItem));
        List<NBTTagCompound> before = new ArrayList<>();
        for (ItemStack input : preview.inputStacks) {
            before.add(input.writeToNBT(new NBTTagCompound()));
        }

        ItemStack generated = generatePattern(Collections.unmodifiableList(preview.inputStacks));
        NBTTagList encoded = generated.getTagCompound().getTagList("in", NBT.TAG_COMPOUND);
        assertEquals(3, encoded.tagCount());
        assertEquals(257, encoded.getCompoundTagAt(0).getInteger("Count"));
        assertEquals(257L, encoded.getCompoundTagAt(0).getLong("Cnt"));
        assertEquals("original", encoded.getCompoundTagAt(0).getCompoundTag("tag").getString("marker"));
        assertEquals(128, encoded.getCompoundTagAt(1).getInteger("Count"));
        assertEquals(128L, encoded.getCompoundTagAt(1).getLong("Cnt"));
        assertEquals(7, ItemStack.loadItemStackFromNBT(encoded.getCompoundTagAt(1)).getItemDamage());
        assertEquals("second", encoded.getCompoundTagAt(1).getCompoundTag("tag").getString("marker"));
        assertSame(patternItem, ItemStack.loadItemStackFromNBT(encoded.getCompoundTagAt(2)).getItem());
        assertEquals(5L, encoded.getCompoundTagAt(2).getLong("Cnt"));

        assertSame(patternItem, preview.inputStack.getItem());
        assertSame(patternItem, preview.inputStacks.get(0).getItem());
        for (int index = 0; index < preview.inputStacks.size(); index++) {
            assertEquals(before.get(index), preview.inputStacks.get(index).writeToNBT(new NBTTagCompound()));
        }
        assertEquals(257, preview.inputStacks.get(1).stackSize);
        assertEquals(128, preview.inputStacks.get(2).stackSize);
    }

    @Test
    void encodingWithoutFixedInputsKeepsTheOnlyInputAndStableIdentity() throws Exception {
        ItemStack wildcard = new ItemStack(patternItem, 257, 2);
        List<ItemStack> inputs = Collections.singletonList(wildcard);
        ItemStack generated = generatePattern(inputs);
        ItemStack repeated = generatePattern(inputs);
        NBTTagList encoded = generated.getTagCompound().getTagList("in", NBT.TAG_COMPOUND);
        assertEquals(1, encoded.tagCount());
        assertSame(patternItem, ItemStack.loadItemStackFromNBT(encoded.getCompoundTagAt(0)).getItem());
        assertEquals(2, ItemStack.loadItemStackFromNBT(encoded.getCompoundTagAt(0)).getItemDamage());
        assertEquals(257, encoded.getCompoundTagAt(0).getInteger("Count"));
        assertEquals(257L, encoded.getCompoundTagAt(0).getLong("Cnt"));
        assertEquals(257, wildcard.stackSize);
        assertEquals(generated.getTagCompound().getString("WildcardGeneratedPatternId"),
            repeated.getTagCompound().getString("WildcardGeneratedPatternId"));
        NBTTagList outputs = generated.getTagCompound().getTagList("out", NBT.TAG_COMPOUND);
        assertEquals(1, outputs.tagCount());
        assertEquals(1L, outputs.getCompoundTagAt(0).getLong("Cnt"));
    }

    private static ItemStack generatePattern(List<ItemStack> inputs) throws Exception {
        Method generate = CompositeWildcardPatternGenerator.class.getDeclaredMethod(
            "createPatternStack", ItemStack.class, String.class, List.class, ItemStack.class);
        generate.setAccessible(true);
        return (ItemStack) generate.invoke(null, pattern(), "Iron", inputs, new ItemStack(patternItem));
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
