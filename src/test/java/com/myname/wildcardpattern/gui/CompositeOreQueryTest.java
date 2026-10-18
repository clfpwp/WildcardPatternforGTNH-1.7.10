package com.myname.wildcardpattern.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.myname.wildcardpattern.compat.GTCompat;
import com.myname.wildcardpattern.crafting.CompositeWildcardPatternGenerator;
import com.myname.wildcardpattern.crafting.WildcardPatternEntry;
import com.myname.wildcardpattern.item.CompositeWildcardPatternState;
import com.myname.wildcardpattern.item.WildcardPatternConfig;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

class CompositeOreQueryTest {
    @Test
    void compositeDedupeDoesNotRegisterMissingNames() throws Exception {
        runProbe("compositeDedupeDoesNotRegisterMissingNames");
    }

    @Test
    void compositeQueriesLeaveOrdinaryQueriesUnchanged() throws Exception {
        runProbe("compositeQueriesLeaveOrdinaryQueriesUnchanged");
    }

    @Test
    void defaultSelectionPreservesRegisteredItemsAndCopies() throws Exception {
        runProbe("defaultSelectionPreservesRegisteredItemsAndCopies");
    }

    @Test
    void compositeGenerationAndGtFallbackDoNotRegisterMissingNames() throws Exception {
        runProbe("compositeGenerationAndGtFallbackDoNotRegisterMissingNames");
    }

    @Test
    void gtOnlyEntriesAndDisabledComponentsKeepTheirBehavior() throws Exception {
        runProbe("gtOnlyEntriesAndDisabledComponentsKeepTheirBehavior");
    }

    @Test
    void nameFallbackAndConfiguredPreferencesPreserveResults() throws Exception {
        runProbe("nameFallbackAndConfiguredPreferencesPreserveResults");
    }

    @Test
    void previewAndActualExpansionUseReadOnlyQueries() throws Exception {
        runProbe("previewAndActualExpansionUseReadOnlyQueries");
    }

    private static void runProbe(String name) throws Exception {
        runProbe(Probe.class, name);
    }

    static void runProbe(Class<?> probeClass, String name) throws Exception {
        ClassLoader source = CompositeOreQueryTest.class.getClassLoader();
        ClassLoader loader = new ClassLoader(ClassLoader.getSystemClassLoader().getParent()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.apache.logging.log4j.")) return source.loadClass(name);
                return super.loadClass(name, resolve);
            }

            @Override
            public URL getResource(String name) {
                return source.getResource(name);
            }

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                // Keep the real Forge registry and query implementations. Only skip vanilla
                // recipe registration, which requires the FML launcher absent from unit tests.
                try (InputStream stream = getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (stream == null) throw new ClassNotFoundException(name);
                    if (name.equals("net.minecraft.nbt.NBTTagList") && probeClass != Probe.class) {
                        // Apply AE2's actual access-transformer rule (tagList is public at runtime).
                        ClassNode node = new ClassNode();
                        new ClassReader(stream).accept(node, 0);
                        for (org.objectweb.asm.tree.FieldNode field : node.fields) {
                            if (field.name.equals("tagList")) {
                                field.access = (field.access & ~(org.objectweb.asm.Opcodes.ACC_PRIVATE
                                    | org.objectweb.asm.Opcodes.ACC_PROTECTED)) | org.objectweb.asm.Opcodes.ACC_PUBLIC;
                            }
                        }
                        ClassWriter writer = new ClassWriter(0);
                        node.accept(writer);
                        byte[] bytes = writer.toByteArray();
                        return defineClass(name, bytes, 0, bytes.length);
                    }
                    if ((name.equals("net.minecraftforge.fluids.FluidRegistry")
                        || name.equals("net.minecraftforge.fluids.FluidContainerRegistry")
                        || name.equals("com.glodblock.github.loader.ItemAndBlockHolder")
                        || name.equals("appeng.util.item.AEStack")
                        || name.equals("appeng.api.AEApi")
                        || name.equals("appeng.util.Platform")) && probeClass != Probe.class) {
                        // The fluid tests seed registry maps directly. Skip vanilla blocks and FML
                        // registration events; keep real fluid lookup, stack and serialization code.
                        ClassNode node = new ClassNode();
                        new ClassReader(stream).accept(node, 0);
                        if (name.equals("appeng.api.AEApi")) {
                            for (org.objectweb.asm.tree.FieldNode field : node.fields) {
                                if (field.name.equals("HELD_API")) field.access &= ~org.objectweb.asm.Opcodes.ACC_FINAL;
                            }
                        }
                        for (MethodNode method : node.methods) {
                            if (!method.name.equals("<clinit>")) continue;
                            method.instructions.clear();
                            method.tryCatchBlocks.clear();
                            if (method.localVariables != null) method.localVariables.clear();
                            if (name.equals("com.glodblock.github.loader.ItemAndBlockHolder")) {
                                // Create the two real fluid representations without registering
                                // all AE2FC blocks/items through an absent FML launcher.
                                for (String fieldName : new String[] { "DROP", "PACKET" }) {
                                    String itemType = "com/glodblock/github/common/item/ItemFluid"
                                        + (fieldName.equals("DROP") ? "Drop" : "Packet");
                                    method.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(org.objectweb.asm.Opcodes.NEW, itemType));
                                    method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.DUP));
                                    method.instructions.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESPECIAL, itemType, "<init>", "()V", false));
                                    method.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(org.objectweb.asm.Opcodes.PUTSTATIC,
                                        "com/glodblock/github/loader/ItemAndBlockHolder", fieldName, "L" + itemType + ";"));
                                }
                            }
                            if (name.equals("appeng.util.Platform")) {
                                // Model AE2FC being installed, without running FML integration discovery.
                                method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ICONST_1));
                                method.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(org.objectweb.asm.Opcodes.PUTSTATIC,
                                    "appeng/util/Platform", "isAE2FCLoaded", "Z"));
                            }
                            method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
                            method.maxStack = 2;
                            method.maxLocals = 0;
                        }
                        ClassWriter writer = new ClassWriter(0);
                        node.accept(writer);
                        byte[] bytes = writer.toByteArray();
                        return defineClass(name, bytes, 0, bytes.length);
                    }
                    if (!name.equals("net.minecraftforge.oredict.OreDictionary")) {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
                        byte[] bytes = output.toByteArray();
                        return defineClass(name, bytes, 0, bytes.length);
                    }
                    ClassNode node = new ClassNode();
                    new ClassReader(stream).accept(node, 0);
                    for (MethodNode method : node.methods) {
                        if (!method.name.equals("<clinit>")) continue;
                        for (AbstractInsnNode instruction : method.instructions.toArray()) {
                            if (instruction instanceof MethodInsnNode
                                && ((MethodInsnNode) instruction).name.equals("initVanillaEntries")) {
                                method.instructions.remove(instruction);
                            }
                        }
                    }
                    ClassWriter writer = new ClassWriter(0);
                    node.accept(writer);
                    byte[] bytes = writer.toByteArray();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (Exception e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
        };
        try {
            Class<?> probe = loader.loadClass(probeClass.getName());
            probe.getMethod("initialize").invoke(null);
            probe.getMethod(name).invoke(null);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("Isolated Forge query probe failed", e.getCause());
        }
    }

    public static class Probe {
        public static void initialize() throws Exception {
            Field side = Class.forName("cpw.mods.fml.relauncher.FMLRelaunchLog").getDeclaredField("side");
            side.setAccessible(true);
            side.set(null, cpw.mods.fml.relauncher.Side.SERVER);
            // GTItemStack uses vanilla feather's getDamage implementation as its hash strategy.
            // Register this minimal item before Items initializes; no vanilla recipes or game startup.
            Method register = Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            register.setAccessible(true);
            register.invoke(Item.itemRegistry, 288, "minecraft:feather", new Item());
        }

        public static void compositeDedupeDoesNotRegisterMissingNames() throws Exception {
            Object state = state("CompositeWildcardPatternWindow");
            Set<String> before = names();
            for (int i = 0; i < 10; i++) {
                String name = "wildcardCompositeMissingQuery" + i;
                assertFalse(OreDictionary.doesOreNameExist(name));
                assertEquals(false, invoke(state, "hasDuplicateOptions", name));
                invoke(state, "getSelectedDedupeLabel", name);
                invoke(state, "cycleDedupeChoice", name);
                assertFalse(OreDictionary.doesOreNameExist(name));
            }
            assertEquals(before, names());
        }

        public static void compositeQueriesLeaveOrdinaryQueriesUnchanged() throws Exception {
            String name = "wildcardMissingDefault";
            Set<String> before = names();
            assertNull(defaultStack(name, true));
            assertEquals(before, names());
            Field field = WildcardPatternEntry.class.getDeclaredField("DEFAULT_ORE_STACK_CACHE");
            field.setAccessible(true);
            assertFalse(((Map<?, ?>) field.get(null)).containsKey(name));
            assertNull(defaultStack(name, false));
            assertTrue(OreDictionary.doesOreNameExist(name));
            assertNull(defaultStack(name, true));
            Object composite = state("CompositeWildcardPatternWindow");
            Object ordinary = state("WildcardPatternWindow");
            String guiName = "wildcardOrdinaryStillRegisters";
            assertEquals(false, invoke(composite, "hasDuplicateOptions", guiName));
            assertFalse(OreDictionary.doesOreNameExist(guiName));
            assertEquals(false, invoke(ordinary, "hasDuplicateOptions", guiName));
            assertTrue(OreDictionary.doesOreNameExist(guiName));
        }

        public static void defaultSelectionPreservesRegisteredItemsAndCopies() throws Exception {
            Item item = new Item();
            Method register = Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            register.setAccessible(true);
            register.invoke(Item.itemRegistry, 31000, "ore_query_test:ingredient", item);
            ItemStack original = new ItemStack(item, 257, 7);
            original.setTagCompound(new NBTTagCompound());
            original.getTagCompound().setString("marker", "preserve");
            NBTTagCompound beforeStack = original.writeToNBT(new NBTTagCompound());
            String name = "wildcardRegisteredOptions";
            int id = OreDictionary.getOreID(name);
            // Seed only fixture contents; bypass Forge registration events requiring an FML game.
            Field lists = OreDictionary.class.getDeclaredField("idToStack");
            lists.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ItemStack> entries = ((List<List<ItemStack>>) lists.get(null)).get(id);
            entries.add(original);
            Object state = state("CompositeWildcardPatternWindow");
            assertEquals(false, invoke(state, "hasDuplicateOptions", name));
            ItemStack other = new ItemStack(item, 1, 8);
            entries.add(other);
            assertEquals(true, invoke(state, "hasDuplicateOptions", name));
            Set<String> before = names();
            ItemStack readOnly = defaultStack(name, true);
            ItemStack ordinary = defaultStack(name, false);
            assertNotNull(readOnly);
            assertEquals(beforeStack, readOnly.writeToNBT(new NBTTagCompound()));
            assertEquals(beforeStack, ordinary.writeToNBT(new NBTTagCompound()));
            assertEquals(257, readOnly.stackSize);
            ordinary.stackSize = 1;
            ordinary.getTagCompound().setString("marker", "changed");
            assertEquals(beforeStack, original.writeToNBT(new NBTTagCompound()));
            assertEquals(beforeStack, defaultStack(name, true).writeToNBT(new NBTTagCompound()));
            assertEquals(2, entries.size());
            assertEquals(before, names());
            String empty = "wildcardRegisteredEmpty";
            OreDictionary.getOreID(empty);
            before = names();
            assertNull(defaultStack(empty, true));
            assertEquals(false, invoke(state, "hasDuplicateOptions", empty));
            assertEquals(before, names());
        }

        public static void gtOnlyEntriesAndDisabledComponentsKeepTheirBehavior() throws Exception {
            ItemStack source = fixtureItem("GT fixture", 0);
            String name = OrePrefixes.ingot.get(Materials.Iron).toString();
            GTOreDictUnificator.getName2StackMap().put(name, source);
            Set<String> before = names();
            assertFalse(OreDictionary.doesOreNameExist(name));
            ItemStack actual = GTCompat.getExistingOreStack(OrePrefixes.ingot, Materials.Iron, 257);
            assertEquals(source.getItem(), actual.getItem());
            assertEquals(GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Iron, 257).stackSize, actual.stackSize);
            assertEquals(1, source.stackSize);
            assertEquals(before, names());
            OrePrefixes.mPreventableComponents.add(OrePrefixes.ingot);
            OrePrefixes.ingot.mDisabledItems.add(Materials.Iron);
            assertNull(GTCompat.getExistingOreStack(OrePrefixes.ingot, Materials.Iron, 257));
            assertEquals(before, names());
            // Each probe uses a fresh loader, so GT fixture mutations cannot escape this test.
        }

        public static void nameFallbackAndConfiguredPreferencesPreserveResults() throws Exception {
            ItemStack ingot = fixtureItem("Fixture Ingot", 0);
            ItemStack plate = fixtureItem("Fixture Plate", 1);
            plate.setTagCompound(new NBTTagCompound());
            plate.getTagCompound().setString("marker", "plate data");
            GTOreDictUnificator.setItemData(ingot, new ItemData(OrePrefixes.ingot, Materials.Iron));
            seed("ingotIron", ingot);
            seed("plateIron", ingot, plate);
            WildcardPatternEntry entry = WildcardPatternEntry.fromStack(ingot);
            entry.setMatcher("*Plate*");
            entry.setAmount(257);
            Set<String> before = names();
            ItemStack selected = createReadOnly(entry, "Iron", null);
            assertNotNull(selected);
            assertEquals(plate.getItem(), selected.getItem());
            assertEquals(257, selected.stackSize);
            assertEquals(plate.getTagCompound(), selected.getTagCompound());
            assertEquals(before, names());
            // An explicit preference must retain the same result in the ordinary and composite paths.
            ItemStack config = fixtureItem("Config", 2);
            WildcardPatternConfig.setPreferredOreStack(config, "plateIron", plate);
            NBTTagCompound rule = new NBTTagCompound();
            rule.setBoolean("Mode", true);
            rule.setString("Matcher", "plate*");
            rule.setLong("Amount", 256);
            entry = WildcardPatternEntry.fromNbt(rule);
            selected = createReadOnly(entry, "Iron", config);
            assertEquals(plate.getItem(), selected.getItem());
            assertEquals(256, selected.stackSize);
            assertEquals(entry.createStack("Iron", config).writeToNBT(new NBTTagCompound()), selected.writeToNBT(new NBTTagCompound()));
            selected.getTagCompound().setString("marker", "changed");
            assertEquals("plate data", plate.getTagCompound().getString("marker"));
            assertEquals(before, names());
        }

        public static void previewAndActualExpansionUseReadOnlyQueries() throws Exception {
            ItemStack config = fixtureItem("Composite", 0);
            seed("ingotIron", fixtureItem("Iron", 1));
            CompositeWildcardPatternGenerator.markAsCompositeWildcard(config);
            NBTTagCompound input = new NBTTagCompound();
            input.setBoolean("Mode", true);
            input.setString("Matcher", "ingot*");
            input.setLong("Amount", 1);
            NBTTagCompound output = (NBTTagCompound) input.copy();
            output.setString("Matcher", "rotor*");
            config.getTagCompound().setTag("CompositeWildcardInput", input);
            config.getTagCompound().setTag("CompositeWildcardOutput", output);
            Set<String> before = names();
            assertEquals(0, CompositeWildcardPatternGenerator.countPreviewPatterns(config));
            assertTrue(CompositeWildcardPatternGenerator.generateAllDetails(config, null).isEmpty());
            assertEquals(before, names());
            seed("rotorIron", fixtureItem("Rotor", 2));
            java.util.List<ItemStack> fixed = java.util.Collections.singletonList(fixtureItem("Fixed", 3));
            fixed.get(0).stackSize = 257;
            CompositeWildcardPatternState.setFixedInputs(config, fixed);
            before = names();
            java.util.List<CompositeWildcardPatternGenerator.GeneratedPattern> patterns = CompositeWildcardPatternGenerator.generatePreviewPatterns(config);
            assertEquals(1, patterns.size());
            assertEquals(257, patterns.get(0).inputStacks.get(1).stackSize);
            assertEquals(before, names());
        }

        static ItemStack fixtureItem(final String label, int offset) throws Exception {
            Item item = new Item() {
                @Override
                public String getItemStackDisplayName(ItemStack stack) { return label; }
            };
            Method register = Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            register.setAccessible(true);
            register.invoke(Item.itemRegistry, 31000 + offset, "ore_query_test:item_" + offset, item);
            return new ItemStack(item);
        }

        static void seed(String name, ItemStack... stacks) throws Exception {
            int id = OreDictionary.getOreID(name);
            Field lists = OreDictionary.class.getDeclaredField("idToStack");
            lists.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ItemStack> entries = ((List<List<ItemStack>>) lists.get(null)).get(id);
            entries.addAll(Arrays.asList(stacks));
        }

        static ItemStack createReadOnly(WildcardPatternEntry entry, String material, ItemStack config) throws Exception {
            Method create = WildcardPatternEntry.class.getDeclaredMethod("createStackReadOnly", String.class, ItemStack.class);
            create.setAccessible(true);
            return (ItemStack) create.invoke(entry, material, config);
        }

        public static void compositeGenerationAndGtFallbackDoNotRegisterMissingNames() throws Exception {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("Mode", true);
            tag.setString("Matcher", "ingot*");
            tag.setLong("Amount", 257);
            WildcardPatternEntry entry = WildcardPatternEntry.fromNbt(tag);
            Method create = WildcardPatternEntry.class.getDeclaredMethod("createStackReadOnly", String.class, ItemStack.class);
            create.setAccessible(true);
            // Force GT initialization before taking the snapshot.
            OrePrefixes prefix = OrePrefixes.ingot;
            Materials material = Materials.Iron;
            Set<String> before = names();
            assertNull(create.invoke(entry, "WildcardMissingMaterial", null));
            assertNull(GTCompat.getExistingOreStack(prefix, material, 257));
            assertEquals(before, names());
        }

        static ItemStack defaultStack(String name, boolean readOnly) throws Exception {
            Method method = WildcardPatternEntry.class.getDeclaredMethod("getDefaultPreferredOreStack", String.class, boolean.class);
            method.setAccessible(true);
            return (ItemStack) method.invoke(null, name, readOnly);
        }

        static Object state(String window) throws Exception {
            Class<?> type = Class.forName("com.myname.wildcardpattern.gui." + window + "$WindowState");
            Class<?> factoryType = Class.forName("sun.reflect.ReflectionFactory");
            Object factory = factoryType.getMethod("getReflectionFactory").invoke(null);
            Constructor<?> constructor = (Constructor<?>) factoryType.getMethod(
                "newConstructorForSerialization", Class.class, Constructor.class)
                .invoke(factory, type, Object.class.getDeclaredConstructor());
            constructor.setAccessible(true);
            return constructor.newInstance();
        }

        static Object invoke(Object state, String method, String name) throws Exception {
            Method target = state.getClass().getDeclaredMethod(method, String.class);
            target.setAccessible(true);
            return target.invoke(state, name);
        }

        static Set<String> names() {
            return new HashSet<>(Arrays.asList(OreDictionary.getOreNames()));
        }
    }
}
