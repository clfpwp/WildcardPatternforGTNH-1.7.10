package com.myname.wildcardpattern.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.storage.data.IAEStack;
import com.myname.wildcardpattern.ModItems;
import com.myname.wildcardpattern.crafting.CompositeWildcardPatternGenerator;
import com.myname.wildcardpattern.crafting.WildcardPatternEntry;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import com.myname.wildcardpattern.mixin.ItemEncodedPatternMixin;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraft.util.StringTranslate;

class WildcardPatternPresentationTest {

    private static Item previousWildcard;
    private static Item previousComposite;
    private static Item wildcard;
    private static ItemCompositeWildcardPattern composite;
    private static Item ingredient;
    private static Method displayHook;
    private static final Map<String, String> previousTranslations = new LinkedHashMap<>();
    private final ItemEncodedPatternMixin hooks = new ItemEncodedPatternMixin() {};

    @BeforeAll
    static void registerItems() throws Exception {
        try (InputStream language = WildcardPatternPresentationTest.class
            .getResourceAsStream("/assets/wildcardpattern/lang/zh_CN.lang")) {
            Map<String, String> translations = StringTranslate.parseLangFile(language);
            for (String key : new String[] { "item.composite_wildcard_pattern.name",
                "tooltip.composite_wildcardpattern.rule", "tooltip.composite_wildcardpattern.expand_count",
                "tooltip.composite_wildcardpattern.usage", "tooltip.wildcardpattern.expand_count" }) {
                previousTranslations.put(key, StatCollector.translateToLocal(key));
                setTranslation(key, translations.get(key));
            }
        }
        previousWildcard = ModItems.wildcardPattern;
        previousComposite = ModItems.compositeWildcardPattern;
        wildcard = new Item();
        composite = compositeWithoutGameStartup();
        composite.setUnlocalizedName("composite_wildcard_pattern");
        ingredient = new Item();
        Method register = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        int id = 31_000;
        for (Item item : new Item[] { wildcard, composite, ingredient }) {
            while (Item.itemRegistry.getObjectById(id) != null) {
                id++;
            }
            register.invoke(Item.itemRegistry, id, "wildcard_display_test:item_" + id, item);
            id++;
        }
        ModItems.wildcardPattern = wildcard;
        ModItems.compositeWildcardPattern = composite;
        displayHook = ItemEncodedPatternMixin.class.getDeclaredMethod(
            "wildcardpattern$useRepresentativeOutputAe",
            ItemStack.class,
            CallbackInfoReturnable.class);
        displayHook.setAccessible(true);
    }

    @AfterAll
    static void restoreItems() {
        previousTranslations.forEach(WildcardPatternPresentationTest::setTranslation);
        ModItems.wildcardPattern = previousWildcard;
        ModItems.compositeWildcardPattern = previousComposite;
    }

    @Test
    void draggedAndTypedTemplatesKeepTheirOwnIconWithoutChangingNbt() throws Exception {
        for (Item item : new Item[] { wildcard, composite }) {
            for (boolean dragged : new boolean[] { false, true }) {
                ItemStack pattern = configured(item, dragged);
                NBTTagCompound before = (NBTTagCompound) pattern.getTagCompound().copy();
                CallbackInfoReturnable<IAEStack<?>> result = invokeDisplay(pattern);
                assertTrue(result.isCancelled());
                assertNull(result.getReturnValue());
                assertSame(item, pattern.getItem());
                assertEquals(1, pattern.stackSize);
                assertEquals(before, pattern.getTagCompound());
            }
        }
    }

    @Test
    void emptyTemplatesDoNotGainNbtDuringRendering() throws Exception {
        for (Item item : new Item[] { wildcard, composite }) {
            ItemStack pattern = new ItemStack(item, 1, 0);
            CallbackInfoReturnable<IAEStack<?>> result = invokeDisplay(pattern);
            assertTrue(result.isCancelled());
            assertNull(result.getReturnValue());
            assertNull(pattern.getTagCompound());
        }
    }

    @Test
    void generatedPatternsKeepTheOriginalAe2OutputPath() throws Exception {
        for (Item item : new Item[] { wildcard, composite }) {
            ItemStack pattern = configured(item, true);
            NBTTagCompound tag = pattern.getTagCompound();
            tag.setString(WildcardPatternGenerator.KEY_GENERATED_PATTERN_ID, "display-test-generated");
            tag.setBoolean("crafting", false);
            tag.setTag("in", patternList(new ItemStack(ingredient, 1, 0)));
            tag.setTag("out", patternList(new ItemStack(ingredient, 3, 4)));
            NBTTagCompound before = (NBTTagCompound) tag.copy();
            CallbackInfoReturnable<IAEStack<?>> result = invokeDisplay(pattern);
            assertFalse(result.isCancelled());
            assertEquals(before, pattern.getTagCompound());
        }
    }

    @Test
    void unrelatedItemsLeaveTheOriginalDisplayMethodUntouched() throws Exception {
        for (ItemStack stack : new ItemStack[] { null, new ItemStack(ingredient, 1, 0) }) {
            assertFalse(invokeDisplay(stack).isCancelled());
        }
    }

    @Test
    void compositeBlockUsePreservesTypeRulesAndFullFixedCount() {
        ItemStack pattern = configured(composite, true);
        CompositeWildcardPatternState.setFixedInputs(
            pattern,
            Arrays.asList(null, new ItemStack(ingredient, 257, 4)));
        NBTTagCompound before = (NBTTagCompound) pattern.getTagCompound().copy();
        // This entry must not call AE2's clearing method or inspect the player/world.
        Item baseReference = composite;
        assertFalse(baseReference.onItemUseFirst(pattern, null, null, 1, 2, 3, 1, 0.5F, 0.5F, 0.5F));
        assertSame(composite, pattern.getItem());
        assertEquals(before, pattern.getTagCompound());
        assertNull(CompositeWildcardPatternState.getFixedInputs(pattern).get(0));
        assertEquals(257, CompositeWildcardPatternState.getFixedInputs(pattern).get(1).stackSize);
    }

    @Test
    void emptyCompositeBlockUseInitializesWithoutChangingTheItem() {
        ItemStack pattern = new ItemStack(composite, 1, 0);
        assertFalse(composite.onItemUseFirst(pattern, null, null, 1, 2, 3, 1, 0, 0, 0));
        assertSame(composite, pattern.getItem());
        assertTrue(pattern.getTagCompound().getBoolean("CompositeWildcardPattern"));
        NBTTagCompound initialized = (NBTTagCompound) pattern.getTagCompound().copy();
        assertFalse(composite.onItemUseFirst(pattern, null, null, 1, 2, 3, 1, 0, 0, 0));
        assertEquals(initialized, pattern.getTagCompound());
    }

    @Test
    void compositeTooltipsUseSavedRulesWithoutChangingConfiguration() {
        for (boolean dragged : new boolean[] { false, true }) {
            ItemStack pattern = configured(composite, dragged);
            CompositeWildcardPatternState.setFixedInputs(pattern, Collections.singletonList(new ItemStack(ingredient, 257, 4)));
            CompositeWildcardPatternState.setExpandedPatternCount(pattern, 361);
            NBTTagCompound before = (NBTTagCompound) pattern.getTagCompound().copy();
            assertEquals("复合通配样板", pattern.getDisplayName());
            List<String> expected = Arrays.asList(
                "规则：ingot* → dust*", "当前已展开 361 个 AE2 加工样板", "右键打开复合配置界面");
            assertEquals(expected, tooltip(pattern, false));
            assertEquals(expected, tooltip(pattern, true));
            assertEquals(before, pattern.getTagCompound());
            assertFalse(pattern.hasDisplayName());

            pattern.getTagCompound().getCompoundTag("CompositeWildcardInput").setString("Matcher", "  \u94c1*  ");
            pattern.getTagCompound().getCompoundTag("CompositeWildcardOutput").setString("Matcher", "  \u677f*  ");
            before = (NBTTagCompound) pattern.getTagCompound().copy();
            assertEquals("规则：铁* → 板*", tooltip(pattern, false).get(0));
            assertEquals("复合通配样板", pattern.getDisplayName());
            assertEquals(before, pattern.getTagCompound());
        }
    }

    @Test
    void incompleteCompositeTooltipsHideRulesWithoutInitializingNbt() {
        ItemStack empty = new ItemStack(composite, 1, 0);
        String defaultName = empty.getDisplayName();
        assertEquals(Arrays.asList("当前已展开 0 个 AE2 加工样板", "右键打开复合配置界面"), tooltip(empty, false));
        assertNull(empty.getTagCompound());
        for (String missingSide : new String[] { "CompositeWildcardInput", "CompositeWildcardOutput" }) {
            for (boolean absent : new boolean[] { false, true }) {
                ItemStack pattern = configured(composite, false);
                if (absent) {
                    pattern.getTagCompound().removeTag(missingSide);
                } else {
                    pattern.getTagCompound().getCompoundTag(missingSide).setString("Matcher", "   ");
                }
                NBTTagCompound before = (NBTTagCompound) pattern.getTagCompound().copy();
                assertEquals(defaultName, pattern.getDisplayName());
                assertEquals(2, tooltip(pattern, false).size());
                assertEquals(before, pattern.getTagCompound());
            }
        }
    }

    @Test
    void legacyRulesSupportPrefixAndPreferPresentMatcher() {
        ItemStack pattern = configured(composite, false);
        NBTTagCompound input = pattern.getTagCompound().getCompoundTag("CompositeWildcardInput");
        NBTTagCompound output = pattern.getTagCompound().getCompoundTag("CompositeWildcardOutput");
        input.removeTag("Matcher");
        output.removeTag("Matcher");
        input.setString("Prefix", " ingot* ");
        output.setString("Prefix", " plate* ");
        NBTTagCompound before = (NBTTagCompound) pattern.getTagCompound().copy();
        assertEquals("规则：ingot* → plate*", tooltip(pattern, false).get(0));
        assertEquals(before, pattern.getTagCompound());
        input.setString("Matcher", "dust*");
        assertEquals("规则：dust* → plate*", tooltip(pattern, false).get(0));
        input.setString("Matcher", "");
        assertEquals(2, tooltip(pattern, false).size());
    }

    @Test
    void customNamesKeepRulesInTheTooltip() {
        ItemStack pattern = configured(composite, true);
        pattern.setStackDisplayName("My custom pattern");
        NBTTagCompound before = (NBTTagCompound) pattern.getTagCompound().copy();
        assertEquals("My custom pattern", pattern.getDisplayName());
        assertEquals("规则：ingot* → dust*", tooltip(pattern, false).get(0));
        assertEquals(before, pattern.getTagCompound());
        pattern.func_135074_t();
        assertEquals("复合通配样板", pattern.getDisplayName());
        assertEquals("规则：ingot* → dust*", tooltip(pattern, false).get(0));
    }

    @Test
    void generatedAndOrdinaryPatternsKeepTheirNames() {
        ItemStack generated = configured(composite, false);
        generated.getTagCompound().setString(WildcardPatternGenerator.KEY_GENERATED_PATTERN_ID, "name-test-generated");
        NBTTagCompound before = (NBTTagCompound) generated.getTagCompound().copy();
        assertEquals(new ItemStack(composite, 1, 0).getDisplayName(), generated.getDisplayName());
        assertEquals(Arrays.asList("当前已展开 0 个 AE2 加工样板", "右键打开复合配置界面"), tooltip(generated, false));
        assertEquals(before, generated.getTagCompound());
        ItemStack ordinary = configured(wildcard, false);
        before = (NBTTagCompound) ordinary.getTagCompound().copy();
        assertEquals(new ItemStack(wildcard, 1, 0).getDisplayName(), ordinary.getDisplayName());
        assertEquals(before, ordinary.getTagCompound());
        assertEquals("当前可生成 361 个 AE2 加工样板",
            StatCollector.translateToLocalFormatted("tooltip.wildcardpattern.expand_count", 361));
    }

    private CallbackInfoReturnable<IAEStack<?>> invokeDisplay(ItemStack pattern) throws Exception {
        // Invoke the injection handler directly; actual Mixin application and rendering need game validation.
        CallbackInfoReturnable<IAEStack<?>> result = new CallbackInfoReturnable<>("getOutputAE", true);
        displayHook.invoke(this.hooks, pattern, result);
        return result;
    }

    private static void setTranslation(String key, String format) {
        String translation = key + "=" + format + "\n";
        StringTranslate.inject(new ByteArrayInputStream(translation.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> tooltip(ItemStack pattern, boolean advanced) {
        List<String> lines = new ArrayList<>();
        composite.addCheckedInformation(pattern, null, lines, advanced);
        return lines;
    }

    private static ItemStack configured(Item item, boolean dragged) {
        ItemStack pattern = new ItemStack(item, 1, 0);
        WildcardPatternEntry input = entry("ingot*", dragged);
        WildcardPatternEntry output = entry("dust*", dragged);
        if (item == composite) {
            CompositeWildcardPatternState.setWildcardInput(pattern, input);
            CompositeWildcardPatternState.setWildcardOutput(pattern, output);
            CompositeWildcardPatternGenerator.markAsCompositeWildcard(pattern);
        } else {
            WildcardPatternState.setInputEntries(pattern, Arrays.asList(input, input));
            WildcardPatternState.setOutputEntries(pattern, Arrays.asList(output, output));
            WildcardPatternGenerator.markAsWildcard(pattern);
        }
        WildcardPatternConfig.apply(
            pattern,
            "Gold",
            Collections.singletonList("Iron,Copper"),
            Collections.singletonList("Tin"));
        return pattern;
    }

    private static WildcardPatternEntry entry(String matcher, boolean dragged) {
        NBTTagCompound tag = dragged
            ? WildcardPatternEntry.fromStack(new ItemStack(ingredient, 1, 0)).toNbt()
            : new NBTTagCompound();
        tag.setBoolean("Mode", true);
        tag.setString("Matcher", matcher);
        tag.setLong("Amount", 1L);
        return WildcardPatternEntry.fromNbt(tag);
    }

    private static NBTTagList patternList(ItemStack stack) {
        NBTTagCompound entry = stack.writeToNBT(new NBTTagCompound());
        entry.setLong("Cnt", stack.stackSize);
        NBTTagList list = new NBTTagList();
        list.appendTag(entry);
        return list;
    }

    private static ItemCompositeWildcardPattern compositeWithoutGameStartup() throws Exception {
        // Run Item's constructor (including its Forge delegate), skipping AE2's client/FML startup.
        // The interaction method under test only uses the supplied stack and composite state.
        Class<?> factoryClass = Class.forName("sun.reflect.ReflectionFactory");
        Object factory = factoryClass.getMethod("getReflectionFactory").invoke(null);
        Constructor<?> constructor = (Constructor<?>) factoryClass
            .getMethod("newConstructorForSerialization", Class.class, Constructor.class)
            .invoke(factory, ItemCompositeWildcardPattern.class, Item.class.getDeclaredConstructor());
        constructor.setAccessible(true);
        return (ItemCompositeWildcardPattern) constructor.newInstance();
    }

}
