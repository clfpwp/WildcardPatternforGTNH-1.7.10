package com.myname.wildcardpattern.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import com.glodblock.github.common.item.ItemFluidDrop;
import com.glodblock.github.common.item.ItemFluidPacket;
import com.glodblock.github.loader.ItemAndBlockHolder;
import com.google.common.collect.HashBiMap;
import com.myname.wildcardpattern.compat.FixedFluidSupport;
import com.myname.wildcardpattern.crafting.CompositeFluidPatternDetails;
import com.myname.wildcardpattern.crafting.CompositeWildcardPatternGenerator;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import com.myname.wildcardpattern.item.CompositeWildcardPatternState;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

class CompositeFixedFluidTest {

    @Test
    void unitsConvertExactlyAndRejectInvalidAmounts() {
        for (int amount : new int[] { 1, 127, 128, 144, 255, 256, 257, 1000, 8000, Integer.MAX_VALUE }) {
            for (String unit : Arrays.asList("B", "mB")) {
                String text = FixedFluidSupport.formatAmount(amount, unit);
                assertEquals(amount, FixedFluidSupport.parseAmount(text, unit));
            }
        }
        assertEquals("0.144", FixedFluidSupport.formatAmount(144, "B"));
        assertEquals("1", FixedFluidSupport.formatAmount(1000, "B"));
        for (String text : Arrays.asList("", "0", "-1", "NaN", "1e3", "1k", "2147483648", "1.1")) {
            assertThrows(IllegalArgumentException.class, () -> FixedFluidSupport.parseAmount(text, "mB"), text);
        }
        for (String text : Arrays.asList("0", "0.0001", "1.0000", "2147483.648", "-0.001")) {
            assertThrows(IllegalArgumentException.class, () -> FixedFluidSupport.parseAmount(text, "B"), text);
        }
    }

    @Test void containersBecomeFluidsWithoutMutation() throws Exception { probe("containers"); }
    @Test void countsUnitsAndTagsSurviveConfigAndDoubling() throws Exception { probe("roundTrip"); }
    @Test void invalidFluidsPreventExpansionAndOverflowIsRejected() throws Exception { probe("invalidFluids"); }
    @Test void mixedAeInputsAndEncodedOrderSurviveRestoration() throws Exception { probe("details"); }
    @Test void identityUsesFluidTagsAndIgnoresUnits() throws Exception { probe("identity"); }
    @Test void previewAndExpansionHaveTheSameFixedFluidRequirements() throws Exception { probe("expansion"); }

    private static void probe(String name) throws Exception { CompositeOreQueryTest.runProbe(Probe.class, name); }

    public static class Probe extends CompositeOreQueryTest.Probe {
        private static Fluid water;
        private static Fluid acid;
        private static Item item;

        public static void initialize() throws Exception {
            CompositeOreQueryTest.Probe.initialize();
            Method vanilla = Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            vanilla.setAccessible(true);
            vanilla.invoke(Item.itemRegistry, 369, "minecraft:blaze_rod", new Item());
            for (String name : Arrays.asList("fluids", "fluidIDs", "fluidNames", "masterFluidReference", "defaultFluidName")) {
                Field field = FluidRegistry.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(null, HashBiMap.create());
            }
            Field delegates = FluidRegistry.class.getDeclaredField("delegates");
            delegates.setAccessible(true);
            delegates.set(null, new HashMap<>());
            water = new Fluid("wildcard_test_water");
            acid = new Fluid("wildcard_test_acid");
            seedFluid(water, 1);
            seedFluid(acid, 2);
            register(ItemAndBlockHolder.DROP, "drop", 31000);
            register(ItemAndBlockHolder.PACKET, "packet", 31005);
            item = new Item();
            register(item, "ingredient", 31001);
            Object comparisons = new appeng.core.features.registries.SpecialComparisonRegistry();
            Object registries = java.lang.reflect.Proxy.newProxyInstance(Probe.class.getClassLoader(),
                new Class<?>[] { appeng.api.features.IRegistryContainer.class }, (proxy, method, args) -> {
                    if (method.getName().equals("specialComparison")) return comparisons;
                    throw new AssertionError("Unexpected AE2 registry: " + method.getName());
                });
            Object api = java.lang.reflect.Proxy.newProxyInstance(Probe.class.getClassLoader(),
                new Class<?>[] { appeng.api.IAppEngApi.class }, (proxy, method, args) -> {
                    if (method.getName().equals("registries")) return registries;
                    if (method.getName().equals("storage")) return new appeng.core.api.ApiStorage();
                    throw new AssertionError("Unexpected AE2 API: " + method.getName());
                });
            Field heldApi = appeng.api.AEApi.class.getDeclaredField("HELD_API");
            heldApi.setAccessible(true);
            heldApi.set(null, api);
        }

        @SuppressWarnings("unchecked")
        private static void seedFluid(Fluid fluid, int id) throws Exception {
            for (String fieldName : Arrays.asList("fluids", "fluidIDs", "fluidNames")) {
                Field field = FluidRegistry.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Map<Object, Object> map = (Map<Object, Object>) field.get(null);
                if (fieldName.equals("fluids")) map.put(fluid.getName(), fluid);
                if (fieldName.equals("fluidIDs")) map.put(fluid, id);
                if (fieldName.equals("fluidNames")) map.put(id, fluid.getName());
            }
            Class<?> type = Class.forName("net.minecraftforge.fluids.FluidRegistry$FluidDelegate");
            java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(Fluid.class, String.class);
            constructor.setAccessible(true);
            Field delegates = FluidRegistry.class.getDeclaredField("delegates");
            delegates.setAccessible(true);
            ((Map<Object, Object>) delegates.get(null)).put(fluid, constructor.newInstance(fluid, fluid.getName()));
        }

        private static void register(Item value, String name, int id) throws Exception {
            Method method = Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            method.setAccessible(true);
            method.invoke(Item.itemRegistry, id, "fixed_fluid_test:" + name, value);
        }

        private static ItemStack pattern() {
            ItemStack stack = new ItemStack(item);
            stack.setTagCompound(new NBTTagCompound());
            stack.getTagCompound().setBoolean("CompositeWildcardPattern", true);
            return stack;
        }

        private static ItemStack drop(Fluid fluid, int amount, String tagValue) {
            FluidStack value = new FluidStack(fluid, amount);
            if (tagValue != null) {
                value.tag = new NBTTagCompound();
                value.tag.setString("variant", tagValue);
            }
            return FixedFluidSupport.newDrop(value);
        }

        public static void containers() throws Exception {
            Item filled = new Item();
            Item empty = new Item();
            register(filled, "bucket", 31002);
            register(empty, "empty", 31003);
            // Registry registration itself emits FML events; seed its backing table instead.
            Field table = FluidContainerRegistry.class.getDeclaredField("containerFluidMap");
            table.setAccessible(true);
            Map<Object, FluidContainerRegistry.FluidContainerData> map = new HashMap<>();
            table.set(null, map);
            FluidContainerRegistry.FluidContainerData data = new FluidContainerRegistry.FluidContainerData(
                new FluidStack(water, 1000), new ItemStack(filled), new ItemStack(empty));
            Class<?> keyType = Class.forName("net.minecraftforge.fluids.FluidContainerRegistry$ContainerKey");
            java.lang.reflect.Constructor<?> key = keyType.getDeclaredConstructor(ItemStack.class);
            key.setAccessible(true);
            map.put(key.newInstance(new ItemStack(filled)), data);
            ItemStack source = new ItemStack(filled, 16);
            assertEquals(1000, FixedFluidSupport.getFluid(FixedFluidSupport.fromDroppedStack(source)).amount);
            assertEquals(16, source.stackSize);
            Item container = new PartialContainer();
            register(container, "partial", 31004);
            ItemStack partial = new ItemStack(container, 2);
            NBTTagCompound before = partial.writeToNBT(new NBTTagCompound());
            ItemStack result = FixedFluidSupport.fromDroppedStack(partial);
            assertEquals(144, result.stackSize);
            assertEquals("Aa", FixedFluidSupport.getFluid(result).tag.getString("variant"));
            assertEquals(before, partial.writeToNBT(new NBTTagCompound()));
            assertEquals(empty, FixedFluidSupport.fromDroppedStack(new ItemStack(empty)).getItem());
            assertEquals(item, FixedFluidSupport.fromDroppedStack(new ItemStack(item)).getItem());
            ItemStack legacy = pattern();
            CompositeWildcardPatternState.setFixedInputs(legacy, Collections.singletonList(source));
            assertEquals(filled, CompositeWildcardPatternState.getFixedInputs(legacy).get(0).getItem());
        }

        public static void roundTrip() {
            for (int amount : new int[] { 1, 127, 128, 144, 255, 256, 257, 1000, 8000, Integer.MAX_VALUE }) {
                ItemStack pattern = pattern();
                List<ItemStack> inputs = new ArrayList<>(Collections.nCopies(8, null));
                inputs.set(0, new ItemStack(item, 257));
                inputs.set(7, drop(water, amount, "Aa"));
                CompositeWildcardPatternState.setFixedInputs(pattern, inputs);
                List<String> units = new ArrayList<>(Collections.nCopies(8, "mB"));
                units.set(7, "B");
                CompositeWildcardPatternState.setFixedInputUnits(pattern, units);
                NBTTagCompound saved = pattern.writeToNBT(new NBTTagCompound());
                pattern = ItemStack.loadItemStackFromNBT(saved);
                ByteBuf buffer = Unpooled.buffer();
                ItemStack received = pattern();
                try {
                    ByteBufUtils.writeTag(buffer, CompositeWildcardPatternState.exportConfig(pattern));
                    CompositeWildcardPatternState.applyConfig(received, ByteBufUtils.readTag(buffer));
                } finally { buffer.release(); }
                List<ItemStack> read = CompositeWildcardPatternState.getFixedInputs(received);
                assertEquals(257, read.get(0).stackSize);
                assertNull(read.get(1));
                assertEquals(amount, read.get(7).stackSize);
                assertEquals("Aa", FixedFluidSupport.getFluid(read.get(7)).tag.getString("variant"));
                assertEquals("B", CompositeWildcardPatternState.getFixedInputUnits(received).get(7));
                assertFalse(CompositeWildcardPatternState.hasInvalidFixedFluidInputs(received));
            }
            ItemStack stack = pattern();
            CompositeWildcardPatternState.setFixedInputs(stack, Collections.singletonList(drop(water, 64, null)));
            CompositeWildcardPatternState.setFixedInputUnits(stack, Collections.singletonList("B"));
            CompositeWildcardPatternState.applyBitModification(stack, 1);
            CompositeWildcardPatternState.applyBitModification(stack, 1);
            assertEquals(256, CompositeWildcardPatternState.getFixedInputs(stack).get(0).stackSize);
            assertEquals("B", CompositeWildcardPatternState.getFixedInputUnits(stack).get(0));
        }

        public static void invalidFluids() {
            ItemStack stack = pattern();
            CompositeWildcardPatternState.setFixedInputs(stack, Collections.singletonList(drop(water, 1000, null)));
            NBTTagCompound entry = stack.getTagCompound().getTagList("CompositeWildcardFixedInputs", 10).getCompoundTagAt(0);
            entry.getCompoundTag("tag").setString("Fluid", "missing_fluid");
            assertTrue(CompositeWildcardPatternState.hasInvalidFixedFluidInputs(stack));
            for (Object amount : Arrays.asList(0L, -1L, 2147483648L, "1000")) {
                ItemStack invalid = pattern();
                CompositeWildcardPatternState.setFixedInputs(invalid, Collections.singletonList(drop(water, 1000, null)));
                NBTTagCompound value = invalid.getTagCompound().getTagList("CompositeWildcardFixedInputs", 10).getCompoundTagAt(0);
                if (amount instanceof Long number) value.setLong("Cnt", number); else value.setString("Cnt", (String) amount);
                assertTrue(CompositeWildcardPatternState.hasInvalidFixedFluidInputs(invalid));
                assertTrue(CompositeWildcardPatternGenerator.generatePreviewPatterns(invalid).isEmpty());
            }
            assertTrue(CompositeWildcardPatternGenerator.generatePreviewPatterns(stack).isEmpty());
            entry.setString("id", "missing:item");
            assertTrue(CompositeWildcardPatternState.hasInvalidFixedFluidInputs(stack));
            assertFalse(FixedFluidSupport.areValidInputs(Arrays.asList(drop(water, Integer.MAX_VALUE, null), drop(water, 1, null))));
            assertTrue(FixedFluidSupport.areValidInputs(Arrays.asList(drop(water, Integer.MAX_VALUE, null), drop(acid, 1, null))));
            assertFalse(FixedFluidSupport.areValidInputs(Arrays.asList(drop(water, Integer.MAX_VALUE, "Aa"), drop(water, 1, "BB"))));
            assertThrows(IllegalArgumentException.class, () -> new CompositeFluidPatternDetails(encode(drop(acid, 144, "Aa"), "mB"), null));
        }

        public static void details() throws Exception {
            ItemStack template = pattern();
            List<ItemStack> inputs = Arrays.asList(new ItemStack(item, 3), new ItemStack(item, 1),
                drop(water, 144, null), drop(water, 1000, null), drop(acid, 8000, null));
            CompositeWildcardPatternState.setFixedInputs(template, inputs.subList(1, inputs.size()));
            Method create = CompositeWildcardPatternGenerator.class.getDeclaredMethod("createPatternStack",
                ItemStack.class, String.class, List.class, ItemStack.class);
            create.setAccessible(true);
            ItemStack encoded = (ItemStack) create.invoke(null, template, "Iron", inputs, new ItemStack(item, 2, 1));
            NBTTagList encodedInputs = encoded.getTagCompound().getTagList("in", 10);
            assertEquals(1, encodedInputs.getCompoundTagAt(0).getLong("Cnt"));
            assertEquals(144, encodedInputs.getCompoundTagAt(1).getLong("Cnt"));
            assertEquals(3, encodedInputs.getCompoundTagAt(4).getLong("Cnt"));
            new CompositeFluidPatternDetails(encoded, null);
            ICraftingPatternDetails details = WildcardPatternGenerator.createDetailForCurrentStack(encoded, null);
            assertNotNull(details);
            assertTrue(details instanceof CompositeFluidPatternDetails);
            assertTrue(details.getAEInputs()[1] instanceof IAEFluidStack);
            assertEquals(144, details.getAEInputs()[1].getStackSize());
            assertTrue(details.getAEInputs()[0] instanceof IAEItemStack);
            long waterTotal = 0;
            for (IAEStack<?> input : details.getCondensedAEInputs()) {
                if (input instanceof IAEFluidStack fluid && fluid.getFluidStack().getFluid() == water) waterTotal += input.getStackSize();
            }
            assertEquals(1144, waterTotal);
            assertEquals(144, ItemFluidDrop.getFluidStack(details.getInputs()[1].getItemStack()).amount);
            assertNull(((IAEFluidStack) details.getAEInputs()[3]).getFluidStack().tag);
            appeng.util.inv.MEInventoryCrafting table = new appeng.util.inv.MEInventoryCrafting(
                new appeng.container.ContainerNull(), details.getAEInputs().length, 1);
            for (int i = 0; i < details.getAEInputs().length; i++) table.setInventorySlotContents(i, details.getAEInputs()[i].copy());
            ItemStack packet = table.getStackInSlot(3);
            assertTrue(packet.getItem() instanceof ItemFluidPacket);
            assertEquals(8000, ItemFluidPacket.getFluidStack(packet).amount);
            assertNull(ItemFluidPacket.getFluidStack(packet).tag);
            ICraftingPatternDetails restored = WildcardPatternGenerator.createDetailForCurrentStack(
                ItemStack.loadItemStackFromNBT(encoded.writeToNBT(new NBTTagCompound())), null);
            assertNotNull(restored);
            assertTrue(restored.getAEInputs()[1] instanceof IAEFluidStack);
            for (int amount : new int[] { 127, 128, 255, 256, 257, Integer.MAX_VALUE }) {
                ItemStack boundary = encode(drop(water, amount, null), "mB");
                ICraftingPatternDetails result = new CompositeFluidPatternDetails(boundary, null);
                assertEquals(amount, result.getAEInputs()[0].getStackSize());
                assertEquals(amount, ItemFluidDrop.getFluidStack(result.getInputs()[0].getItemStack()).amount);
            }
        }

        public static void identity() throws Exception {
            ItemStack a = drop(water, 144, "Aa");
            ItemStack b = drop(water, 144, "BB");
            assertEquals(a.getTagCompound().hashCode(), b.getTagCompound().hashCode());
            assertNotEquals(FixedFluidSupport.identity(a), FixedFluidSupport.identity(b));
            ItemStack stack = pattern();
            CompositeWildcardPatternState.setFixedInputs(stack, Collections.singletonList(a));
            String before = FixedFluidSupport.identity(CompositeWildcardPatternState.getFixedInputs(stack).get(0));
            CompositeWildcardPatternState.setFixedInputUnits(stack, Collections.singletonList("B"));
            assertEquals(before, FixedFluidSupport.identity(CompositeWildcardPatternState.getFixedInputs(stack).get(0)));
            ItemStack encodedA = encode(a, "mB");
            ItemStack encodedB = encode(b, "mB");
            ItemStack encodedBuckets = encode(a, "B");
            assertNotEquals(WildcardPatternGenerator.getGeneratedPatternId(encodedA), WildcardPatternGenerator.getGeneratedPatternId(encodedB));
            assertEquals(WildcardPatternGenerator.getGeneratedPatternId(encodedA), WildcardPatternGenerator.getGeneratedPatternId(encodedBuckets));
        }

        public static void expansion() throws Exception {
            seed("ingotIron", new ItemStack(item));
            seed("plateIron", new ItemStack(item, 1, 1));
            ItemStack template = pattern();
            NBTTagCompound input = new NBTTagCompound();
            input.setBoolean("Mode", true);
            input.setString("Matcher", "ingot*");
            input.setLong("Amount", 1);
            NBTTagCompound output = (NBTTagCompound) input.copy();
            output.setString("Matcher", "plate*");
            template.getTagCompound().setTag("CompositeWildcardInput", input);
            template.getTagCompound().setTag("CompositeWildcardOutput", output);
            CompositeWildcardPatternState.setFixedInputs(template, Collections.singletonList(drop(water, 1000, null)));
            CompositeWildcardPatternState.setFixedInputUnits(template, Collections.singletonList("B"));
            java.util.Set<String> before = names();
            List<CompositeWildcardPatternGenerator.GeneratedPattern> preview = CompositeWildcardPatternGenerator.generatePreviewPatterns(template);
            assertEquals(1, preview.size());
            assertEquals(1000, FixedFluidSupport.getFluid(preview.get(0).inputStacks.get(1)).amount);
            List<ICraftingPatternDetails> expanded = CompositeWildcardPatternGenerator.generateAllDetails(template, null);
            assertEquals(1, expanded.size());
            assertTrue(expanded.get(0).getAEInputs()[0] instanceof IAEFluidStack);
            assertEquals(1000, expanded.get(0).getAEInputs()[0].getStackSize());
            assertEquals(before, names());
            CompositeWildcardPatternState.setFixedInputs(template, Collections.singletonList(drop(water, 1000, "Aa")));
            assertTrue(CompositeWildcardPatternGenerator.generatePreviewPatterns(template).isEmpty());
            assertTrue(CompositeWildcardPatternGenerator.generateAllDetails(template, null).isEmpty());
            assertEquals(before, names());
        }

        private static ItemStack encode(ItemStack fluid, String unit) throws Exception {
            ItemStack template = pattern();
            CompositeWildcardPatternState.setFixedInputs(template, Collections.singletonList(fluid));
            CompositeWildcardPatternState.setFixedInputUnits(template, Collections.singletonList(unit));
            Method create = CompositeWildcardPatternGenerator.class.getDeclaredMethod("createPatternStack",
                ItemStack.class, String.class, List.class, ItemStack.class);
            create.setAccessible(true);
            return (ItemStack) create.invoke(null, template, "Iron", Arrays.asList(new ItemStack(item), fluid), new ItemStack(item, 2, 1));
        }

        public static class PartialContainer extends Item implements IFluidContainerItem {
            public FluidStack getFluid(ItemStack stack) {
                FluidStack fluid = new FluidStack(water, 144);
                fluid.tag = new NBTTagCompound();
                fluid.tag.setString("variant", "Aa");
                return fluid;
            }
            public int getCapacity(ItemStack stack) { return 1000; }
            public int fill(ItemStack stack, FluidStack fluid, boolean perform) { throw new AssertionError("No filling"); }
            public FluidStack drain(ItemStack stack, int amount, boolean perform) { throw new AssertionError("No draining"); }
        }
    }
}
