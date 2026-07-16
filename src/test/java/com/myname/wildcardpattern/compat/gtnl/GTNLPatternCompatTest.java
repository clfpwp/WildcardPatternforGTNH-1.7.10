package com.myname.wildcardpattern.compat.gtnl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

class GTNLPatternCompatTest {

    @Test
    void allExpandedDetailsMapToOnePhysicalSlot() {
        Object slot = new Object();
        Map<String, Object> mappings = new LinkedHashMap<>();

        GTNLPatternCompat.replaceOwnedMappings(mappings, slot, Arrays.asList("bronze", "aluminium"));

        assertSame(slot, mappings.get("bronze"));
        assertSame(slot, mappings.get("aluminium"));
        assertEquals(2, mappings.size());
    }

    @Test
    void replacingPatternRemovesEveryOldChildMapping() {
        Object slot = new Object();
        Map<String, Object> mappings = new LinkedHashMap<>();
        GTNLPatternCompat.replaceOwnedMappings(mappings, slot, Arrays.asList("bronze", "aluminium"));

        GTNLPatternCompat.replaceOwnedMappings(mappings, slot, Arrays.asList("steel"));

        assertEquals(1, mappings.size());
        assertSame(slot, mappings.get("steel"));
    }

    @Test
    void detachedSlotsAreRemovedByIdentity() {
        Object attached = new Object();
        Object detached = new Object();
        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("bronze", attached);
        mappings.put("aluminium", detached);

        GTNLPatternCompat.removeDetachedMappings(mappings, new Object[] { attached });

        assertEquals(1, mappings.size());
        assertSame(attached, mappings.get("bronze"));
    }

    @Test
    void nullNeverRepresentsAnAttachedPhysicalSlot() {
        assertFalse(GTNLPatternCompat.containsIdentity(new Object[] { null, new Object() }, null));
    }

    @Test
    void duplicatePatternOwnersRemainFallbackDispatchCandidates() {
        Object first = new Object();
        Object preferred = new Object();
        List<Object> ordered = GTNLPatternCompat.orderedAttachedCandidates(new Object[] { first, preferred }, preferred);

        assertEquals(Arrays.asList(preferred, first), ordered);
    }

    @Test
    void onlyNamedGTNLMixinsUseOptionalTargetGate() {
        assertTrue(
            GTNLPatternCompat.isOptionalGTNLMixin(
                "com.myname.wildcardpattern.mixin.GTNLSuperCraftingInputHatchMEMixin"));
        assertTrue(
            GTNLPatternCompat.isOptionalGTNLMixin("com.myname.wildcardpattern.mixin.GTNLPatternSlotMixin"));
        assertFalse(
            GTNLPatternCompat.isOptionalGTNLMixin("com.myname.wildcardpattern.mixin.DualityInterfaceMixin"));
    }

    @Test
    void generatedIdComesFromThePatternStack() {
        ICraftingPatternDetails details = details(patternWithId("bronze"), new IAEItemStack[0]);

        assertEquals("bronze", GTNLPatternCompat.generatedId(details));
        assertTrue(GTNLPatternCompat.isGeneratedWildcardDetail(details));
        assertFalse(GTNLPatternCompat.isGeneratedWildcardDetail(null));
    }

    @Test
    void nonWildcardPatternHasNoExpandedDetails() {
        assertTrue(GTNLPatternCompat.expand(new ItemStack(new Item()), null).isEmpty());
    }

    @Test
    void storedItemsMustExactlyCoverPatternInputs() {
        Item item = new Item();
        ItemStack expected = new ItemStack(item, 1, 0);
        ICraftingPatternDetails details = details(null, new IAEItemStack[] { aeStack(expected, 2) });

        assertTrue(GTNLPatternCompat.inputsMatch(details, new ItemStack[] { new ItemStack(item, 2, 0) }, null));
        assertFalse(GTNLPatternCompat.inputsMatch(details, new ItemStack[] { new ItemStack(item, 1, 0) }, null));
        assertFalse(
            GTNLPatternCompat.inputsMatch(
                details,
                new ItemStack[] { new ItemStack(item, 2, 0), new ItemStack(new Item(), 1, 0) },
                null));
    }

    @Test
    void wildcardDamageMatchesTheConcreteStoredItem() {
        Item item = new Item();
        ICraftingPatternDetails details =
            details(null, new IAEItemStack[] { aeStack(new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE), 1) });

        assertTrue(GTNLPatternCompat.inputsMatch(details, new ItemStack[] { new ItemStack(item, 1, 7) }, null));
    }

    @Test
    void cacheInvalidationInvokesOnlyTheCompatibleSlotMethod() {
        TestSlot slot = new TestSlot();
        RecordingLogic logic = new RecordingLogic();

        GTNLPatternCompat.invalidateRecipeCache(Arrays.asList(logic, null), slot);

        assertEquals(1, logic.calls);
        assertSame(slot, logic.slot);
    }

    @Test
    void cacheInvalidationFailureIsContained() {
        assertDoesNotThrow(
            () -> GTNLPatternCompat.invalidateRecipeCache(Arrays.asList(new ThrowingLogic()), new TestSlot()));
    }

    private static ItemStack patternWithId(String id) {
        ItemStack pattern = new ItemStack(new Item());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(WildcardPatternGenerator.KEY_GENERATED_PATTERN_ID, id);
        pattern.setTagCompound(tag);
        return pattern;
    }

    private static ICraftingPatternDetails details(ItemStack pattern, IAEItemStack[] inputs) {
        return (ICraftingPatternDetails) Proxy.newProxyInstance(
            GTNLPatternCompatTest.class.getClassLoader(),
            new Class<?>[] { ICraftingPatternDetails.class },
            (proxy, method, args) -> {
                if ("getPattern".equals(method.getName())) {
                    return pattern;
                }
                if ("getInputs".equals(method.getName())) {
                    return inputs;
                }
                return defaultValue(method.getReturnType());
            });
    }

    private static IAEItemStack aeStack(ItemStack stack, long amount) {
        return (IAEItemStack) Proxy.newProxyInstance(
            GTNLPatternCompatTest.class.getClassLoader(),
            new Class<?>[] { IAEItemStack.class },
            (proxy, method, args) -> {
                if ("getItemStack".equals(method.getName())) {
                    return stack;
                }
                if ("getStackSize".equals(method.getName())) {
                    return amount;
                }
                return defaultValue(method.getReturnType());
            });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        return 0;
    }

    public static final class TestSlot {}

    public static final class RecordingLogic {

        private int calls;
        private TestSlot slot;

        public void removeInventoryRecipeCache(TestSlot slot) {
            this.calls++;
            this.slot = slot;
        }
    }

    public static final class ThrowingLogic {

        public void removeInventoryRecipeCache(TestSlot slot) {
            throw new IllegalStateException("test failure");
        }
    }
}
