package com.myname.wildcardpattern.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.myname.wildcardpattern.crafting.CompositeWildcardPatternGenerator;
import com.myname.wildcardpattern.crafting.WildcardPatternEntry;
import com.myname.wildcardpattern.item.CompositeWildcardPatternState;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

class CompositeWildcardRuleEditingTest {

    private static Item patternItem;
    private static Item unknownItem;
    private static Item ingotItem;

    @BeforeAll
    static void registerItems() throws Exception {
        patternItem = new Item();
        unknownItem = new Item().setUnlocalizedName("unknown_composite_test_item");
        ingotItem = new Item().setUnlocalizedName("ingot_composite_test_item");
        Method register = Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        int id = 32_000;
        for (Item item : new Item[] { patternItem, unknownItem, ingotItem }) {
            while (Item.itemRegistry.getObjectById(id) != null) {
                id++;
            }
            register.invoke(Item.itemRegistry, id, "composite_rule_test:item_" + id, item);
            id++;
        }
    }

    @Test
    void unidentifiedRulesStayEmptyAcrossConfigTransmission() {
        WildcardPatternEntry dropped = CompositeWildcardRuleEditing.toOreDict(recognitionResult(unknownItem, 257, false));
        assertEmptyRule(dropped, 257);
        assertEmptyAfterTransmission(dropped, 257);
        WildcardPatternEntry named = recognitionResult(unknownItem, 128, false);
        WildcardPatternEntry switched = CompositeWildcardRuleEditing.toOreDict(named);
        assertEmptyRule(switched, 128);
        assertTrue(switched.isOreDict());
        assertEmptyAfterTransmission(switched, 128);
    }

    @Test
    void recognizedRulesKeepSpecificPrefixAndQuantity() {
        WildcardPatternEntry dropped = CompositeWildcardRuleEditing.toOreDict(recognitionResult(ingotItem, 64, true));
        assertEquals("ingot*", dropped.getMatcher());
        assertTrue(dropped.isOreDict());
        assertEquals(64L, dropped.getAmountLong());
        assertEquals(ingotItem, dropped.getDisplayStack().getItem());
        WildcardPatternEntry switched = CompositeWildcardRuleEditing.toOreDict(
            recognitionResult(ingotItem, 128, true));
        assertEquals("ingot*", switched.getMatcher());
        assertEquals(128L, switched.getAmountLong());
    }

    @Test
    void clearingRulesDropsHiddenStacksWithoutChangingModeOrQuantity() {
        for (boolean oreDict : new boolean[] { false, true }) {
            WildcardPatternEntry original = recognitionResult(ingotItem, 256, true);
            if (oreDict) {
                original = CompositeWildcardRuleEditing.toOreDict(original);
            }
            WildcardPatternEntry cleared = CompositeWildcardRuleEditing.updateMatcher(original, "   ");
            assertEmptyRule(cleared, 256);
            assertEquals(oreDict, cleared.isOreDict());
            assertEmptyAfterTransmission(cleared, 256);
        }
    }

    @Test
    void manualWildcardTextCanBeCompletedWithoutBeingDiscarded() {
        WildcardPatternEntry entry = CompositeWildcardRuleEditing.updateMatcher(WildcardPatternEntry.fromStack(null), "*");
        assertEquals("*", entry.getMatcher());
        entry = CompositeWildcardRuleEditing.updateMatcher(entry, "*Iron");
        assertEquals("*Iron", WildcardPatternEntry.fromNbt(entry.toNbt()).getMatcher());
        entry = CompositeWildcardRuleEditing.updateMatcher(entry, "ingot*");
        assertEquals("ingot*", entry.getMatcher());
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidRulesReturnBeforeEitherSideCollectsCandidates() throws Exception {
        Field cacheField = WildcardPatternEntry.class.getDeclaredField("ORE_CANDIDATE_CACHE");
        cacheField.setAccessible(true);
        Map<String, Set<String>> cache = (Map<String, Set<String>>) cacheField.get(null);
        String probe = "compositeGuardProbe*";
        Set<String> previous = cache.put(probe, new AbstractSet<String>() {
            @Override
            public Iterator<String> iterator() {
                throw new AssertionError("Candidate collection must not run for an invalid composite rule");
            }

            @Override
            public int size() {
                throw new AssertionError("Candidate collection must not run for an invalid composite rule");
            }
        });
        try {
            for (String invalid : new String[] { "", "   ", "*", "**", "?", "*?*", " * ? " }) {
                for (boolean invalidInput : new boolean[] { false, true }) {
                    for (boolean legacy : new boolean[] { false, true }) {
                        ItemStack pattern = pattern();
                        pattern.getTagCompound().setTag("CompositeWildcardInput", ruleTag(invalidInput ? invalid : probe, legacy));
                        pattern.getTagCompound().setTag("CompositeWildcardOutput", ruleTag(invalidInput ? probe : invalid, legacy));
                        assertTrue(CompositeWildcardPatternGenerator.generatePreviewPatterns(pattern).isEmpty());
                        assertTrue(CompositeWildcardPatternGenerator.generateAllDetails(pattern, null).isEmpty());
                        assertEquals(0, CompositeWildcardPatternGenerator.countPreviewPatterns(pattern));
                    }
                }
            }
        } finally {
            if (previous == null) {
                cache.remove(probe);
            } else {
                cache.put(probe, previous);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void concreteRulesStillReachCandidateCollection() throws Exception {
        Field cacheField = WildcardPatternEntry.class.getDeclaredField("ORE_CANDIDATE_CACHE");
        cacheField.setAccessible(true);
        Map<String, Set<String>> cache = (Map<String, Set<String>>) cacheField.get(null);
        for (String rule : new String[] { "ingot*", "*Iron", "plate?", "铁*" }) {
            Set<String> previous = cache.put(rule, new AbstractSet<String>() {
                @Override
                public Iterator<String> iterator() {
                    throw new AssertionError("Reached candidate collection");
                }

                @Override
                public int size() {
                    throw new AssertionError("Reached candidate collection");
                }
            });
            try {
                ItemStack pattern = pattern();
                pattern.getTagCompound().setTag("CompositeWildcardInput", ruleTag(rule, false));
                pattern.getTagCompound().setTag("CompositeWildcardOutput", ruleTag(rule, false));
                AssertionError reached = assertThrows(AssertionError.class,
                    () -> CompositeWildcardPatternGenerator.generatePreviewPatterns(pattern));
                assertEquals("Reached candidate collection", reached.getMessage());
            } finally {
                if (previous == null) {
                    cache.remove(rule);
                } else {
                    cache.put(rule, previous);
                }
            }
        }
    }

    private static WildcardPatternEntry recognitionResult(Item item, int amount, boolean recognized) {
        // Only ore-prefix recognition/conversion is substituted: Forge/GT registries need game startup.
        // Entry data, emptying, serialization, configuration transport and generators remain real.
        WildcardPatternEntry entry = new WildcardPatternEntry() {
            private boolean oreDict;

            @Override
            public boolean canOreDict() {
                return recognized;
            }

            @Override
            public void convertToOreDict() {
                this.oreDict = true;
                setMatcher("ingot*");
            }

            @Override
            public boolean isOreDict() {
                return this.oreDict;
            }
        };
        entry.setStack(new ItemStack(item, amount, 0));
        entry.setAmount(amount);
        return entry;
    }

    private static void assertEmptyRule(WildcardPatternEntry entry, long amount) {
        assertTrue(entry.isEmpty());
        assertEquals("", entry.getMatcher());
        assertEquals(amount, entry.getAmountLong());
        assertNull(entry.getStack());
        assertNull(entry.getDisplayStack());
        assertFalse(entry.toNbt().hasKey("Stack"));
        assertFalse(entry.toNbt().hasKey("Display"));
    }

    private static void assertEmptyAfterTransmission(WildcardPatternEntry entry, long amount) {
        assertEmptyRule(WildcardPatternEntry.fromNbt(entry.toNbt()), amount);
        ItemStack source = pattern();
        CompositeWildcardPatternState.setWildcardInput(source, entry);
        CompositeWildcardPatternState.setWildcardOutput(source, entry);
        ByteBuf buffer = Unpooled.buffer();
        try {
            ByteBufUtils.writeTag(buffer, CompositeWildcardPatternState.exportConfig(source));
            ItemStack destination = pattern();
            CompositeWildcardPatternState.applyConfig(destination, ByteBufUtils.readTag(buffer));
            ItemStack reloaded = ItemStack.loadItemStackFromNBT(destination.writeToNBT(new NBTTagCompound()));
            assertEmptyRule(CompositeWildcardPatternState.getWildcardInput(reloaded), amount);
            assertEmptyRule(CompositeWildcardPatternState.getWildcardOutput(reloaded), amount);
        } finally {
            buffer.release();
        }
    }

    private static ItemStack pattern() {
        ItemStack pattern = new ItemStack(patternItem);
        CompositeWildcardPatternGenerator.markAsCompositeWildcard(pattern);
        return pattern;
    }

    private static NBTTagCompound ruleTag(String matcher, boolean legacy) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Mode", true);
        tag.setString(legacy ? "Prefix" : "Matcher", matcher);
        tag.setLong("Amount", 1L);
        return tag;
    }
}
