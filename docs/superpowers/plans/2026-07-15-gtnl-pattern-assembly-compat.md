# GTNL Pattern Assembly Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make one wildcard pattern in GTNL's Super Pattern Input Assembly (ME) advertise and execute every generated material recipe, independent of the seed item used to encode it.

**Architecture:** Keep GTNL optional by targeting its outer assembly and nested `PatternSlot` with string-based `@Pseudo` mixins guarded by a config plugin. Put mapping and active-detail decisions in testable local helpers; the outer mixin rebuilds AE registration and dispatch maps, while the slot mixin changes GTNL's active `patternDetails` only while buffered inputs belong to a generated recipe and persists that generated ID.

**Tech Stack:** Java 17, Minecraft Forge 1.7.10, AE2 `rv3-beta-977-GTNH`, GT5U `5.09.52.594`, Sponge Mixin/UniMixins, Gradle Kotlin DSL, JUnit Jupiter 5.

**Pinned compatibility target:** GT-Not-Leisure `dev-290` commit `06cc841398ea4f7d6129fb8584002f0584ea05a8`, class `com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME`.

---

## File Map

- Modify `build.gradle.kts`: enable JUnit Platform for Gradle's `test` task.
- Modify `dependencies.gradle`: add JUnit Jupiter test dependencies only.
- Create `src/main/java/com/myname/wildcardpattern/compat/gtnl/GTNLPatternSlotState.java`: pure active-ID state machine, rollback, and conservative reload recovery.
- Create `src/test/java/com/myname/wildcardpattern/compat/gtnl/GTNLPatternSlotStateTest.java`: state transition and recovery tests.
- Create `src/main/java/com/myname/wildcardpattern/compat/gtnl/GTNLPatternCompat.java`: GTNL names, generated-ID helpers, identity-based map cleanup, input matching, and reflective cache invalidation.
- Create `src/test/java/com/myname/wildcardpattern/compat/gtnl/GTNLPatternCompatTest.java`: map ownership and optional-target decision tests.
- Create `src/main/java/com/myname/wildcardpattern/mixin/WildcardPatternMixinPlugin.java`: skip both GTNL mixins when either pinned target class is absent.
- Create `src/test/java/com/myname/wildcardpattern/mixin/WildcardPatternMixinPluginTest.java`: plugin gating tests without loading GTNL.
- Create `src/main/java/com/myname/wildcardpattern/mixin/GTNLPatternSlotBridge.java`: typed local contract between the two string-targeted mixins.
- Create `src/main/java/com/myname/wildcardpattern/mixin/GTNLPatternSlotMixin.java`: expand details, select the active child recipe, persist/recover its ID, and protect ambiguous buffered inputs.
- Create `src/main/java/com/myname/wildcardpattern/mixin/GTNLSuperCraftingInputHatchMEMixin.java`: advertise all child recipes, map them to physical slots, safely dispatch, and clear GTNL recipe caches.
- Modify `src/main/resources/mixins.wildcardpattern.json`: install the plugin and register the two optional mixins.
- Modify `README.md`: document the GTNL 2.9.0 compatibility boundary and smoke-test expectation.

### Task 1: Add A Test Runtime

**Files:**
- Modify: `build.gradle.kts`
- Modify: `dependencies.gradle`

- [ ] **Step 1: Add the JUnit Jupiter dependency**

Append this entry inside the existing `dependencies { ... }` block in `dependencies.gradle`:

```groovy
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
```

- [ ] **Step 2: Enable JUnit Platform**

Make `build.gradle.kts` exactly:

```kotlin
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Verify Gradle discovers the test task**

Run:

```powershell
./gradlew test --dry-run
```

Expected: exit code `0` and output includes `:test SKIPPED`. No production dependency on GTNL appears in `dependencies.gradle`.

- [ ] **Step 4: Commit the test infrastructure**

```powershell
git add build.gradle.kts dependencies.gradle
git commit -m "test: add JUnit platform"
```

### Task 2: Build The Active-Pattern State Machine With TDD

**Files:**
- Create: `src/main/java/com/myname/wildcardpattern/compat/gtnl/GTNLPatternSlotState.java`
- Create: `src/test/java/com/myname/wildcardpattern/compat/gtnl/GTNLPatternSlotStateTest.java`

- [ ] **Step 1: Write the failing state tests**

Create `GTNLPatternSlotStateTest.java`:

```java
package com.myname.wildcardpattern.compat.gtnl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class GTNLPatternSlotStateTest {

    private static final String REPRESENTATIVE = "display";

    @Test
    void emptySlotCanSelectEitherGeneratedDetail() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");

        assertTrue(state.activate("bronze", false));
        assertEquals("bronze", state.current(true));
        assertTrue(state.activate("aluminium", false));
        assertEquals("aluminium", state.current(true));
    }

    @Test
    void occupiedSlotAcceptsOnlyItsActiveGeneratedId() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");
        assertTrue(state.activate("bronze", false));

        assertTrue(state.activate("bronze", true));
        assertFalse(state.activate("aluminium", true));
        assertEquals("bronze", state.current(true));
    }

    @Test
    void emptyingSlotRestoresRepresentativeDetail() {
        GTNLPatternSlotState<String> state = state("bronze");
        assertTrue(state.activate("bronze", false));

        assertEquals(REPRESENTATIVE, state.current(false));
        assertEquals("", state.getPersistentId(false));
    }

    @Test
    void savedGeneratedIdWinsDuringReload() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");

        assertEquals("aluminium", state.recover("aluminium", true, value -> false));
        assertEquals("aluminium", state.getPersistentId(true));
        assertFalse(state.isUnresolved());
    }

    @Test
    void missingSavedIdUsesOneUniqueInputMatch() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");

        assertEquals("bronze", state.recover("removed-id", true, "bronze"::equals));
        assertFalse(state.isUnresolved());
    }

    @Test
    void ambiguousInputMatchNeverChoosesAnArbitraryDetail() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");

        assertNull(state.recover("", true, value -> true));
        assertNull(state.current(true));
        assertTrue(state.isUnresolved());
        assertFalse(state.activate("bronze", true));
    }

    @Test
    void failedInsertionCanRestorePreviousSelection() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");
        assertTrue(state.activate("bronze", false));
        GTNLPatternSlotState.Snapshot<String> before = state.snapshot();
        assertTrue(state.activate("aluminium", false));

        state.restore(before);

        assertEquals("bronze", state.current(true));
        assertEquals("bronze", state.getActiveId());
    }

    @Test
    void duplicateGeneratedIdsAreRegisteredOnce() {
        GTNLPatternSlotState<String> state = new GTNLPatternSlotState<>(REPRESENTATIVE, value -> value);

        state.replaceExpandedDetails(Arrays.asList("bronze", "bronze", "aluminium", null, ""));

        assertEquals(Arrays.asList("bronze", "aluminium"), state.getExpandedDetails());
    }

    private static GTNLPatternSlotState<String> state(String... details) {
        GTNLPatternSlotState<String> state = new GTNLPatternSlotState<>(REPRESENTATIVE, value -> value);
        state.replaceExpandedDetails(Arrays.asList(details));
        return state;
    }
}
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```powershell
./gradlew test --tests "*.GTNLPatternSlotStateTest"
```

Expected: compilation fails because `GTNLPatternSlotState` does not exist.

- [ ] **Step 3: Implement the minimal state machine**

Create `GTNLPatternSlotState.java`:

```java
package com.myname.wildcardpattern.compat.gtnl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public final class GTNLPatternSlotState<T> {

    private final T representative;
    private final Function<T, String> idExtractor;
    private List<T> expandedDetails = Collections.emptyList();
    private T active;
    private String activeId = "";
    private boolean unresolved;

    public GTNLPatternSlotState(T representative, Function<T, String> idExtractor) {
        this.representative = representative;
        this.idExtractor = idExtractor;
    }

    public void replaceExpandedDetails(Collection<T> details) {
        Map<String, T> unique = new LinkedHashMap<>();
        if (details != null) {
            for (T detail : details) {
                String id = idOf(detail);
                if (!id.isEmpty()) {
                    unique.putIfAbsent(id, detail);
                }
            }
        }
        this.expandedDetails = Collections.unmodifiableList(new ArrayList<>(unique.values()));
        if (!this.activeId.isEmpty()) {
            T replacement = findById(this.activeId);
            if (replacement == null) {
                this.active = null;
                this.activeId = "";
                this.unresolved = true;
            } else {
                this.active = replacement;
            }
        }
    }

    public List<T> getExpandedDetails() {
        return this.expandedDetails;
    }

    public boolean activate(T requested, boolean occupied) {
        T canonical = findById(idOf(requested));
        if (canonical == null) {
            return false;
        }
        String requestedId = idOf(canonical);
        if (occupied && (this.unresolved || this.activeId.isEmpty() || !this.activeId.equals(requestedId))) {
            return false;
        }
        this.active = canonical;
        this.activeId = requestedId;
        this.unresolved = false;
        return true;
    }

    public T recover(String savedId, boolean occupied, Predicate<T> inputsMatch) {
        if (!occupied) {
            clear();
            return this.representative;
        }

        T byId = findById(normalize(savedId));
        if (byId != null) {
            setActive(byId);
            return byId;
        }

        T unique = null;
        for (T candidate : this.expandedDetails) {
            if (!inputsMatch.test(candidate)) {
                continue;
            }
            if (unique != null && !idOf(unique).equals(idOf(candidate))) {
                this.active = null;
                this.activeId = "";
                this.unresolved = true;
                return null;
            }
            unique = candidate;
        }

        if (unique != null) {
            setActive(unique);
            return unique;
        }
        this.active = null;
        this.activeId = "";
        this.unresolved = true;
        return null;
    }

    public T current(boolean occupied) {
        if (!occupied) {
            clear();
            return this.representative;
        }
        return this.unresolved ? null : this.active;
    }

    public String getPersistentId(boolean occupied) {
        return occupied && !this.unresolved ? this.activeId : "";
    }

    public String getActiveId() {
        return this.activeId;
    }

    public boolean isUnresolved() {
        return this.unresolved;
    }

    public Snapshot<T> snapshot() {
        return new Snapshot<>(this.active, this.activeId, this.unresolved);
    }

    public void restore(Snapshot<T> snapshot) {
        this.active = snapshot.active;
        this.activeId = snapshot.activeId;
        this.unresolved = snapshot.unresolved;
    }

    private T findById(String id) {
        if (id.isEmpty()) {
            return null;
        }
        for (T detail : this.expandedDetails) {
            if (id.equals(idOf(detail))) {
                return detail;
            }
        }
        return null;
    }

    private void setActive(T detail) {
        this.active = detail;
        this.activeId = idOf(detail);
        this.unresolved = false;
    }

    private void clear() {
        this.active = null;
        this.activeId = "";
        this.unresolved = false;
    }

    private String idOf(T value) {
        return value == null ? "" : normalize(this.idExtractor.apply(value));
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    public static final class Snapshot<T> {

        private final T active;
        private final String activeId;
        private final boolean unresolved;

        private Snapshot(T active, String activeId, boolean unresolved) {
            this.active = active;
            this.activeId = activeId;
            this.unresolved = unresolved;
        }
    }
}
```

- [ ] **Step 4: Run the focused test to verify GREEN**

Run:

```powershell
./gradlew test --tests "*.GTNLPatternSlotStateTest"
```

Expected: `8 tests completed` with no failures.

- [ ] **Step 5: Commit the state machine**

```powershell
git add src/main/java/com/myname/wildcardpattern/compat/gtnl/GTNLPatternSlotState.java src/test/java/com/myname/wildcardpattern/compat/gtnl/GTNLPatternSlotStateTest.java
git commit -m "feat: track GTNL wildcard slot state"
```

### Task 3: Build Mapping And Input-Matching Helpers With TDD

**Files:**
- Create: `src/main/java/com/myname/wildcardpattern/compat/gtnl/GTNLPatternCompat.java`
- Create: `src/test/java/com/myname/wildcardpattern/compat/gtnl/GTNLPatternCompatTest.java`

- [ ] **Step 1: Write failing identity-map tests**

Create `GTNLPatternCompatTest.java`:

```java
package com.myname.wildcardpattern.compat.gtnl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

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
    void onlyNamedGTNLMixinsUseOptionalTargetGate() {
        assertTrue(
            GTNLPatternCompat.isOptionalGTNLMixin(
                "com.myname.wildcardpattern.mixin.GTNLSuperCraftingInputHatchMEMixin"));
        assertTrue(
            GTNLPatternCompat.isOptionalGTNLMixin("com.myname.wildcardpattern.mixin.GTNLPatternSlotMixin"));
        assertFalse(
            GTNLPatternCompat.isOptionalGTNLMixin("com.myname.wildcardpattern.mixin.DualityInterfaceMixin"));
    }
}
```

- [ ] **Step 2: Run the tests to verify RED**

Run:

```powershell
./gradlew test --tests "*.GTNLPatternCompatTest"
```

Expected: compilation fails because `GTNLPatternCompat` does not exist.

- [ ] **Step 3: Implement names, expansion, mapping, input matching, and cache invalidation**

Create `GTNLPatternCompat.java`:

```java
package com.myname.wildcardpattern.compat.gtnl;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import com.glodblock.github.common.item.ItemFluidDrop;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

public final class GTNLPatternCompat {

    public static final String HATCH_TARGET =
        "com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME";
    public static final String SLOT_TARGET = HATCH_TARGET + "$PatternSlot";
    public static final String ACTIVE_PATTERN_ID_KEY = "WildcardActivePatternId";
    private static final String MIXIN_PREFIX = "com.myname.wildcardpattern.mixin.GTNL";

    private GTNLPatternCompat() {}

    public static boolean isOptionalGTNLMixin(String mixinClassName) {
        return mixinClassName != null && mixinClassName.startsWith(MIXIN_PREFIX);
    }

    public static List<ICraftingPatternDetails> expand(ItemStack pattern, World world) {
        return WildcardPatternGenerator.generateAllDetails(pattern, world);
    }

    public static String generatedId(ICraftingPatternDetails details) {
        return details == null ? "" : WildcardPatternGenerator.getGeneratedPatternId(details.getPattern());
    }

    public static boolean isGeneratedWildcardDetail(ICraftingPatternDetails details) {
        return !generatedId(details).isEmpty();
    }

    public static <D, S> void replaceOwnedMappings(Map<D, S> mappings, S slot, Collection<D> details) {
        mappings.entrySet().removeIf(entry -> entry.getValue() == slot);
        if (details == null) {
            return;
        }
        for (D detail : details) {
            if (detail != null) {
                mappings.put(detail, slot);
            }
        }
    }

    public static <D, S> void removeDetachedMappings(Map<D, S> mappings, Object[] attachedSlots) {
        mappings.entrySet().removeIf(entry -> !containsIdentity(attachedSlots, entry.getValue()));
    }

    public static boolean containsIdentity(Object[] values, Object candidate) {
        if (values == null) {
            return false;
        }
        for (Object value : values) {
            if (value == candidate) {
                return true;
            }
        }
        return false;
    }

    public static boolean inputsMatch(
        ICraftingPatternDetails details,
        ItemStack[] storedItems,
        FluidStack[] storedFluids) {
        IAEItemStack[] expected = details == null ? null : details.getInputs();
        if (expected == null || expected.length == 0) {
            return false;
        }
        boolean sawInput = false;
        for (IAEItemStack input : expected) {
            ItemStack stack = input == null ? null : input.getItemStack();
            if (stack == null) {
                continue;
            }
            sawInput = true;
            if (stack.getItem() instanceof ItemFluidDrop) {
                FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
                if (fluid == null || countFluid(storedFluids, fluid) < requiredFluid(expected, fluid)) {
                    return false;
                }
            } else if (countItem(storedItems, stack) < requiredItem(expected, stack)) {
                return false;
            }
        }
        return sawInput && allStoredItemsCovered(storedItems, expected) && allStoredFluidsCovered(storedFluids, expected);
    }

    public static void invalidateRecipeCache(Iterable<?> processingLogics, Object slot) {
        if (processingLogics == null || slot == null) {
            return;
        }
        for (Object logic : processingLogics) {
            if (logic == null) {
                continue;
            }
            for (Method method : logic.getClass().getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!"removeInventoryRecipeCache".equals(method.getName())
                    || parameters.length != 1
                    || !parameters[0].isInstance(slot)) {
                    continue;
                }
                try {
                    method.invoke(logic, slot);
                } catch (ReflectiveOperationException | SecurityException ignored) {
                    // Optional GTNL cache invalidation must never crash crafting dispatch.
                }
                break;
            }
        }
    }

    private static long requiredItem(IAEItemStack[] expected, ItemStack target) {
        long amount = 0;
        for (IAEItemStack input : expected) {
            ItemStack stack = input == null ? null : input.getItemStack();
            if (stack != null && !(stack.getItem() instanceof ItemFluidDrop)
                && GTUtility.areStacksEqual(stack, target)) {
                amount += Math.max(Math.max(1L, input.getStackSize()), stack.stackSize);
            }
        }
        return amount;
    }

    private static long requiredFluid(IAEItemStack[] expected, FluidStack target) {
        long amount = 0;
        for (IAEItemStack input : expected) {
            ItemStack stack = input == null ? null : input.getItemStack();
            if (stack == null || !(stack.getItem() instanceof ItemFluidDrop)) {
                continue;
            }
            FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
            if (fluid != null && GTUtility.areFluidsEqual(fluid, target)) {
                amount += Math.max(1L, fluid.amount);
            }
        }
        return amount;
    }

    private static long countItem(ItemStack[] stored, ItemStack target) {
        long amount = 0;
        if (stored != null) {
            for (ItemStack stack : stored) {
                if (stack != null && stack.stackSize > 0 && GTUtility.areStacksEqual(stack, target)) {
                    amount += stack.stackSize;
                }
            }
        }
        return amount;
    }

    private static long countFluid(FluidStack[] stored, FluidStack target) {
        long amount = 0;
        if (stored != null) {
            for (FluidStack fluid : stored) {
                if (fluid != null && fluid.amount > 0 && GTUtility.areFluidsEqual(fluid, target)) {
                    amount += fluid.amount;
                }
            }
        }
        return amount;
    }

    private static boolean allStoredItemsCovered(ItemStack[] stored, IAEItemStack[] expected) {
        if (stored == null) {
            return true;
        }
        for (ItemStack stack : stored) {
            if (stack != null && stack.stackSize > 0 && !hasItem(expected, stack)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allStoredFluidsCovered(FluidStack[] stored, IAEItemStack[] expected) {
        if (stored == null) {
            return true;
        }
        for (FluidStack fluid : stored) {
            if (fluid != null && fluid.amount > 0 && !hasFluid(expected, fluid)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasItem(IAEItemStack[] expected, ItemStack target) {
        for (IAEItemStack input : expected) {
            ItemStack stack = input == null ? null : input.getItemStack();
            if (stack != null && !(stack.getItem() instanceof ItemFluidDrop)
                && GTUtility.areStacksEqual(stack, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFluid(IAEItemStack[] expected, FluidStack target) {
        for (IAEItemStack input : expected) {
            ItemStack stack = input == null ? null : input.getItemStack();
            if (stack == null || !(stack.getItem() instanceof ItemFluidDrop)) {
                continue;
            }
            FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
            if (fluid != null && GTUtility.areFluidsEqual(fluid, target)) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: Run focused helper tests**

Run:

```powershell
./gradlew test --tests "*.GTNLPatternCompatTest"
```

Expected: `4 tests completed` with no failures.

- [ ] **Step 5: Run all helper tests**

Run:

```powershell
./gradlew test
```

Expected: `12 tests completed` with no failures.

- [ ] **Step 6: Commit the compatibility helpers**

```powershell
git add src/main/java/com/myname/wildcardpattern/compat/gtnl/GTNLPatternCompat.java src/test/java/com/myname/wildcardpattern/compat/gtnl/GTNLPatternCompatTest.java
git commit -m "feat: add GTNL pattern compatibility helpers"
```

### Task 4: Gate The Optional Mixins Without A GTNL Dependency

**Files:**
- Create: `src/main/java/com/myname/wildcardpattern/mixin/WildcardPatternMixinPlugin.java`
- Create: `src/test/java/com/myname/wildcardpattern/mixin/WildcardPatternMixinPluginTest.java`

- [ ] **Step 1: Write the failing plugin decision tests**

Create `WildcardPatternMixinPluginTest.java`:

```java
package com.myname.wildcardpattern.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.myname.wildcardpattern.compat.gtnl.GTNLPatternCompat;

class WildcardPatternMixinPluginTest {

    private static final String HATCH_MIXIN =
        "com.myname.wildcardpattern.mixin.GTNLSuperCraftingInputHatchMEMixin";

    @Test
    void ordinaryMixinsAlwaysApply() {
        WildcardPatternMixinPlugin plugin = new WildcardPatternMixinPlugin(name -> false);

        assertTrue(plugin.shouldApplyMixin("appeng.helpers.DualityInterface", "ordinary.Mixin"));
    }

    @Test
    void gtnlMixinsAreSkippedWhenGTNLIsAbsent() {
        WildcardPatternMixinPlugin plugin = new WildcardPatternMixinPlugin(name -> false);

        assertFalse(plugin.shouldApplyMixin(GTNLPatternCompat.HATCH_TARGET, HATCH_MIXIN));
    }

    @Test
    void bothTargetClassesMustExist() {
        Set<String> present = new HashSet<>();
        present.add(GTNLPatternCompat.HATCH_TARGET);
        WildcardPatternMixinPlugin plugin = new WildcardPatternMixinPlugin(present::contains);

        assertFalse(plugin.shouldApplyMixin(GTNLPatternCompat.HATCH_TARGET, HATCH_MIXIN));
    }

    @Test
    void gtnlMixinsApplyWhenBothTargetClassesExist() {
        Set<String> present = new HashSet<>();
        present.add(GTNLPatternCompat.HATCH_TARGET);
        present.add(GTNLPatternCompat.SLOT_TARGET);
        WildcardPatternMixinPlugin plugin = new WildcardPatternMixinPlugin(present::contains);

        assertTrue(plugin.shouldApplyMixin(GTNLPatternCompat.HATCH_TARGET, HATCH_MIXIN));
    }
}
```

- [ ] **Step 2: Run the plugin tests to verify RED**

Run:

```powershell
./gradlew test --tests "*.WildcardPatternMixinPluginTest"
```

Expected: compilation fails because `WildcardPatternMixinPlugin` does not exist.

- [ ] **Step 3: Implement the config plugin**

Create `WildcardPatternMixinPlugin.java`:

```java
package com.myname.wildcardpattern.mixin;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.myname.wildcardpattern.WildcardPatternMod;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternCompat;

import net.minecraft.launchwrapper.Launch;

public final class WildcardPatternMixinPlugin implements IMixinConfigPlugin {

    private final Predicate<String> targetCompatible;
    private boolean reportedUnavailable;

    public WildcardPatternMixinPlugin() {
        this(WildcardPatternMixinPlugin::hasCompatibleStructure);
    }

    WildcardPatternMixinPlugin(Predicate<String> targetCompatible) {
        this.targetCompatible = targetCompatible;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!GTNLPatternCompat.isOptionalGTNLMixin(mixinClassName)) {
            return true;
        }
        boolean apply = this.targetCompatible.test(GTNLPatternCompat.HATCH_TARGET)
            && this.targetCompatible.test(GTNLPatternCompat.SLOT_TARGET);
        if (!apply && !this.reportedUnavailable) {
            this.reportedUnavailable = true;
            WildcardPatternMod.LOG.debug("GTNL pattern assembly compatibility is not applicable");
        }
        return apply;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    private static boolean hasCompatibleStructure(String className) {
        byte[] classBytes;
        try {
            classBytes = Launch.classLoader == null ? null : Launch.classLoader.getClassBytes(className);
        } catch (IOException | LinkageError | RuntimeException ignored) {
            return false;
        }
        if (classBytes == null) {
            return false;
        }

        Set<String> requiredFields = requiredFields(className);
        Set<String> requiredMethods = requiredMethods(className);
        if (requiredFields == null || requiredMethods == null) {
            return false;
        }

        Set<String> fields = new HashSet<>();
        Set<String> methods = new HashSet<>();
        try {
            new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM5) {

                @Override
                public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value) {
                    fields.add(name + descriptor);
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                    methods.add(name + descriptor);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
        return fields.containsAll(requiredFields) && methods.containsAll(requiredMethods);
    }

    private static Set<String> requiredFields(String className) {
        if (GTNLPatternCompat.HATCH_TARGET.equals(className)) {
            return setOf(
                "internalInventory[Lcom/science/gtnl/common/machine/hatch/"
                    + "SuperCraftingInputHatchME$PatternSlot;",
                "patternDetailsPatternSlotMapLjava/util/Map;",
                "processingLogicsLjava/util/List;",
                "justHadNewItemsZ",
                "supportFluidsZ");
        }
        if (GTNLPatternCompat.SLOT_TARGET.equals(className)) {
            return setOf(
                "patternLnet/minecraft/item/ItemStack;",
                "patternDetailsLappeng/api/networking/crafting/ICraftingPatternDetails;",
                "parentMTELgregtech/api/interfaces/metatileentity/IMetaTileEntity;");
        }
        return null;
    }

    private static Set<String> requiredMethods(String className) {
        if (GTNLPatternCompat.HATCH_TARGET.equals(className)) {
            return setOf(
                "isActive()Z",
                "provideCrafting(Lappeng/api/networking/crafting/ICraftingProviderHelper;)V",
                "pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;"
                    + "Lnet/minecraft/inventory/InventoryCrafting;)Z",
                "onPatternChange(ILnet/minecraft/item/ItemStack;)V",
                "loadNBTData(Lnet/minecraft/nbt/NBTTagCompound;)V");
        }
        if (GTNLPatternCompat.SLOT_TARGET.equals(className)) {
            return setOf(
                "<init>(Lnet/minecraft/item/ItemStack;Lnet/minecraft/nbt/NBTTagCompound;"
                    + "Lgregtech/api/interfaces/metatileentity/IMetaTileEntity;I)V",
                "getPatternDetails()Lappeng/api/networking/crafting/ICraftingPatternDetails;",
                "getPatternInputs()Lgregtech/api/objects/GTDualInputPattern;",
                "writeToNBT(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
                "isEmpty()Z",
                "getItemInputs()[Lnet/minecraft/item/ItemStack;",
                "getFluidInputs()[Lnet/minecraftforge/fluids/FluidStack;",
                "insertItemsAndFluids(Lnet/minecraft/inventory/InventoryCrafting;)Z");
        }
        return null;
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
```

- [ ] **Step 4: Run the focused tests to verify GREEN**

Run:

```powershell
./gradlew test --tests "*.WildcardPatternMixinPluginTest"
```

Expected: `4 tests completed` with no failures and no attempt to resolve a GTNL artifact.

- [ ] **Step 5: Commit the optional loader**

```powershell
git add src/main/java/com/myname/wildcardpattern/mixin/WildcardPatternMixinPlugin.java src/test/java/com/myname/wildcardpattern/mixin/WildcardPatternMixinPluginTest.java
git commit -m "feat: gate optional GTNL mixins"
```

### Task 5: Make GTNL Pattern Slots Track The Selected Child Recipe

**Files:**
- Create: `src/main/java/com/myname/wildcardpattern/mixin/GTNLPatternSlotBridge.java`
- Create: `src/main/java/com/myname/wildcardpattern/mixin/GTNLPatternSlotMixin.java`

- [ ] **Step 1: Add the local bridge contract**

Create `GTNLPatternSlotBridge.java`:

```java
package com.myname.wildcardpattern.mixin;

import java.util.List;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternSlotState;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.world.World;

public interface GTNLPatternSlotBridge {

    boolean wildcardpattern$isWildcard();

    List<ICraftingPatternDetails> wildcardpattern$getRegistrationDetails(World world);

    boolean wildcardpattern$owns(ICraftingPatternDetails details, World world);

    GTNLPatternSlotState.Snapshot<ICraftingPatternDetails> wildcardpattern$beginActivation(
        ICraftingPatternDetails details);

    void wildcardpattern$rollbackActivation(GTNLPatternSlotState.Snapshot<ICraftingPatternDetails> snapshot);

    String wildcardpattern$getActiveId();

    boolean wildcardpattern$insert(InventoryCrafting table);
}
```

- [ ] **Step 2: Add a compile-first slot mixin shell**

Create `GTNLPatternSlotMixin.java` with its target, shared-type shadows, and bridge signature:

```java
package com.myname.wildcardpattern.mixin;

import java.util.Collections;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternCompat;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternSlotState;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.objects.GTDualInputPattern;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

@Pseudo
@Mixin(targets = GTNLPatternCompat.SLOT_TARGET, remap = false)
public abstract class GTNLPatternSlotMixin implements GTNLPatternSlotBridge {

    @Shadow
    @Final
    public ItemStack pattern;

    @Shadow
    @Final
    @Mutable
    public ICraftingPatternDetails patternDetails;

    @Shadow
    @Final
    public IMetaTileEntity parentMTE;

    @Shadow
    public abstract boolean isEmpty();

    @Shadow
    public abstract ItemStack[] getItemInputs();

    @Shadow
    public abstract FluidStack[] getFluidInputs();

    @Shadow
    public abstract boolean insertItemsAndFluids(InventoryCrafting table);

    @Unique
    private ICraftingPatternDetails wildcardpattern$representative;

    @Unique
    private GTNLPatternSlotState<ICraftingPatternDetails> wildcardpattern$state;

    @Inject(
        method = "<init>(Lnet/minecraft/item/ItemStack;Lnet/minecraft/nbt/NBTTagCompound;"
            + "Lgregtech/api/interfaces/metatileentity/IMetaTileEntity;I)V",
        at = @At("RETURN"),
        require = 0)
    private void wildcardpattern$initialize(
        ItemStack pattern,
        NBTTagCompound saved,
        IMetaTileEntity parent,
        int index,
        CallbackInfo ci) {
        this.wildcardpattern$representative = this.patternDetails;
        if (!this.wildcardpattern$isWildcard()) {
            return;
        }
        this.wildcardpattern$state =
            new GTNLPatternSlotState<>(this.wildcardpattern$representative, GTNLPatternCompat::generatedId);
        this.wildcardpattern$refreshDetails(parent.getBaseMetaTileEntity().getWorld());
        String savedId = saved == null ? "" : saved.getString(GTNLPatternCompat.ACTIVE_PATTERN_ID_KEY);
        this.wildcardpattern$state.recover(
            savedId,
            !isEmpty(),
            details -> GTNLPatternCompat.inputsMatch(details, getItemInputs(), getFluidInputs()));
        this.wildcardpattern$syncActiveDetail();
    }

    @Inject(method = "getPatternDetails", at = @At("HEAD"), require = 0)
    private void wildcardpattern$resetDetailWhenEmpty(CallbackInfoReturnable<ICraftingPatternDetails> cir) {
        this.wildcardpattern$syncActiveDetail();
    }

    @Inject(method = "getPatternInputs", at = @At("HEAD"), cancellable = true, require = 0)
    private void wildcardpattern$guardAmbiguousInputs(CallbackInfoReturnable<GTDualInputPattern> cir) {
        this.wildcardpattern$syncActiveDetail();
        if (this.wildcardpattern$state == null
            || !this.wildcardpattern$state.isUnresolved()
            || isEmpty()) {
            return;
        }
        GTDualInputPattern empty = new GTDualInputPattern();
        empty.inputItems = GTValues.emptyItemStackArray;
        empty.inputFluid = GTValues.emptyFluidStackArray;
        cir.setReturnValue(empty);
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), cancellable = true, require = 0)
    private void wildcardpattern$saveActiveId(
        NBTTagCompound target,
        CallbackInfoReturnable<NBTTagCompound> cir) {
        if (this.wildcardpattern$state == null) {
            return;
        }
        NBTTagCompound written = cir.getReturnValue();
        String activeId = this.wildcardpattern$state.getPersistentId(!isEmpty());
        if (activeId.isEmpty()) {
            written.removeTag(GTNLPatternCompat.ACTIVE_PATTERN_ID_KEY);
        } else {
            written.setString(GTNLPatternCompat.ACTIVE_PATTERN_ID_KEY, activeId);
        }
        cir.setReturnValue(written);
    }

    @Override
    public boolean wildcardpattern$isWildcard() {
        return com.myname.wildcardpattern.crafting.WildcardPatternGenerator.isWildcardPattern(this.pattern);
    }

    @Override
    public List<ICraftingPatternDetails> wildcardpattern$getRegistrationDetails(World world) {
        if (!wildcardpattern$isWildcard()) {
            return this.patternDetails == null
                ? Collections.emptyList()
                : Collections.singletonList(this.patternDetails);
        }
        wildcardpattern$refreshDetails(world);
        return this.wildcardpattern$state == null
            ? Collections.emptyList()
            : this.wildcardpattern$state.getExpandedDetails();
    }

    @Override
    public boolean wildcardpattern$owns(ICraftingPatternDetails details, World world) {
        String requestedId = GTNLPatternCompat.generatedId(details);
        if (requestedId.isEmpty()) {
            return false;
        }
        for (ICraftingPatternDetails candidate : wildcardpattern$getRegistrationDetails(world)) {
            if (requestedId.equals(GTNLPatternCompat.generatedId(candidate))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public GTNLPatternSlotState.Snapshot<ICraftingPatternDetails> wildcardpattern$beginActivation(
        ICraftingPatternDetails details) {
        if (this.wildcardpattern$state == null) {
            return null;
        }
        if (!isEmpty() && this.wildcardpattern$state.getActiveId().isEmpty()) {
            this.wildcardpattern$state.recover(
                "",
                true,
                candidate -> GTNLPatternCompat.inputsMatch(candidate, getItemInputs(), getFluidInputs()));
        }
        GTNLPatternSlotState.Snapshot<ICraftingPatternDetails> before = this.wildcardpattern$state.snapshot();
        if (!this.wildcardpattern$state.activate(details, !isEmpty())) {
            return null;
        }
        // The slot is still empty until pushPattern inserts the table. Keep the
        // selected detail active across that short interval instead of applying
        // the normal empty-slot reset.
        this.patternDetails = this.wildcardpattern$state.current(true);
        return before;
    }

    @Override
    public void wildcardpattern$rollbackActivation(
        GTNLPatternSlotState.Snapshot<ICraftingPatternDetails> snapshot) {
        if (this.wildcardpattern$state != null && snapshot != null) {
            this.wildcardpattern$state.restore(snapshot);
            this.wildcardpattern$syncActiveDetail();
        }
    }

    @Override
    public String wildcardpattern$getActiveId() {
        return this.wildcardpattern$state == null ? "" : this.wildcardpattern$state.getActiveId();
    }

    @Override
    public boolean wildcardpattern$insert(InventoryCrafting table) {
        return table != null && insertItemsAndFluids(table);
    }

    @Unique
    private void wildcardpattern$refreshDetails(World world) {
        if (this.wildcardpattern$state == null || world == null) {
            return;
        }
        this.wildcardpattern$state.replaceExpandedDetails(GTNLPatternCompat.expand(this.pattern, world));
        this.wildcardpattern$syncActiveDetail();
    }

    @Unique
    private void wildcardpattern$syncActiveDetail() {
        if (this.wildcardpattern$state == null) {
            return;
        }
        ICraftingPatternDetails current = this.wildcardpattern$state.current(!isEmpty());
        this.patternDetails = current == null ? this.wildcardpattern$representative : current;
    }
}
```

- [ ] **Step 3: Compile the string-targeted slot mixin**

Run:

```powershell
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL` without a GTNL jar on the compile classpath. If the Mixin annotation processor reports it cannot resolve the pseudo target, retain `@Pseudo`, `targets = ...`, `remap = false`, and `require = 0`; do not add GTNL as a dependency.

- [ ] **Step 4: Re-run the state and plugin tests**

Run:

```powershell
./gradlew test --tests "*.GTNLPatternSlotStateTest" --tests "*.WildcardPatternMixinPluginTest"
```

Expected: `12 tests completed` with no failures.

- [ ] **Step 5: Commit the slot bridge**

```powershell
git add src/main/java/com/myname/wildcardpattern/mixin/GTNLPatternSlotBridge.java src/main/java/com/myname/wildcardpattern/mixin/GTNLPatternSlotMixin.java
git commit -m "feat: track active GTNL wildcard details"
```

### Task 6: Expand Registration And Route Dispatch In The GTNL Assembly

**Files:**
- Create: `src/main/java/com/myname/wildcardpattern/mixin/GTNLSuperCraftingInputHatchMEMixin.java`

- [ ] **Step 1: Implement registration, map rebuilding, and dispatch**

Create `GTNLSuperCraftingInputHatchMEMixin.java`:

```java
package com.myname.wildcardpattern.mixin;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import com.glodblock.github.common.item.ItemFluidPacket;
import com.myname.wildcardpattern.WildcardPatternMod;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternCompat;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternSlotState;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

@Pseudo
@Mixin(targets = GTNLPatternCompat.HATCH_TARGET, remap = false)
public abstract class GTNLSuperCraftingInputHatchMEMixin {

    @Shadow
    public Map<ICraftingPatternDetails, Object> patternDetailsPatternSlotMap;

    @Shadow
    public List<?> processingLogics;

    @Shadow
    public boolean justHadNewItems;

    @Shadow
    public boolean supportFluids;

    @Shadow
    public abstract boolean isActive();

    @Unique
    private Field wildcardpattern$internalInventoryField;

    @Unique
    private boolean wildcardpattern$reportedIncompatibleStructure;

    @Inject(method = "provideCrafting", at = @At("HEAD"), cancellable = true, require = 0)
    private void wildcardpattern$provideExpandedPatterns(
        ICraftingProviderHelper craftingTracker,
        CallbackInfo ci) {
        Object[] slots = wildcardpattern$getInternalSlots();
        if (slots == null || !wildcardpattern$hasWildcard(slots)) {
            return;
        }
        Map<ICraftingPatternDetails, Object> rebuilt = wildcardpattern$collectMappings(slots);
        if (rebuilt == null) {
            return;
        }

        ci.cancel();
        if (!isActive()) {
            return;
        }
        this.patternDetailsPatternSlotMap.clear();
        this.patternDetailsPatternSlotMap.putAll(rebuilt);
        ICraftingProvider provider = (ICraftingProvider) (Object) this;
        for (ICraftingPatternDetails details : rebuilt.keySet()) {
            craftingTracker.addCraftingOption(provider, details);
        }
    }

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true, require = 0)
    private void wildcardpattern$pushExpandedPattern(
        ICraftingPatternDetails requested,
        InventoryCrafting table,
        CallbackInfoReturnable<Boolean> cir) {
        if (!GTNLPatternCompat.isGeneratedWildcardDetail(requested)) {
            return;
        }

        Object[] slots = wildcardpattern$getInternalSlots();
        if (slots == null) {
            cir.setReturnValue(false);
            return;
        }
        Object rawSlot = this.patternDetailsPatternSlotMap.get(requested);
        if (!GTNLPatternCompat.containsIdentity(slots, rawSlot)) {
            GTNLPatternCompat.removeDetachedMappings(this.patternDetailsPatternSlotMap, slots);
            rawSlot = wildcardpattern$findOwningSlot(slots, requested);
        }
        if (!(rawSlot instanceof GTNLPatternSlotBridge slot) || !slot.wildcardpattern$isWildcard()) {
            cir.setReturnValue(false);
            return;
        }
        if (!isActive() || table == null || wildcardpattern$hasUnsupportedFluidPacket(table)) {
            cir.setReturnValue(false);
            return;
        }

        String previousId = slot.wildcardpattern$getActiveId();
        GTNLPatternSlotState.Snapshot<ICraftingPatternDetails> snapshot =
            slot.wildcardpattern$beginActivation(requested);
        if (snapshot == null) {
            cir.setReturnValue(false);
            return;
        }
        String selectedId = slot.wildcardpattern$getActiveId();
        if (!slot.wildcardpattern$insert(table)) {
            slot.wildcardpattern$rollbackActivation(snapshot);
            cir.setReturnValue(false);
            return;
        }
        if (!previousId.equals(selectedId)) {
            GTNLPatternCompat.invalidateRecipeCache(this.processingLogics, rawSlot);
        }
        this.justHadNewItems = true;
        cir.setReturnValue(true);
    }

    @Inject(method = "onPatternChange", at = @At("RETURN"), require = 0)
    private void wildcardpattern$rebuildChangedMappings(int index, ItemStack newItem, CallbackInfo ci) {
        wildcardpattern$rebuildCurrentMappings();
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), require = 0)
    private void wildcardpattern$rebuildLoadedMappings(NBTTagCompound tag, CallbackInfo ci) {
        wildcardpattern$rebuildCurrentMappings();
    }

    @Unique
    private void wildcardpattern$rebuildCurrentMappings() {
        Object[] slots = wildcardpattern$getInternalSlots();
        Map<ICraftingPatternDetails, Object> rebuilt = wildcardpattern$collectMappings(slots);
        if (rebuilt == null) {
            return;
        }
        this.patternDetailsPatternSlotMap.clear();
        this.patternDetailsPatternSlotMap.putAll(rebuilt);
    }

    @Unique
    private Map<ICraftingPatternDetails, Object> wildcardpattern$collectMappings(Object[] slots) {
        if (slots == null) {
            return null;
        }
        World world = wildcardpattern$getWorld();
        if (world == null) {
            return null;
        }
        Map<ICraftingPatternDetails, Object> rebuilt = new LinkedHashMap<>();
        for (Object rawSlot : slots) {
            if (rawSlot == null) {
                continue;
            }
            if (!(rawSlot instanceof GTNLPatternSlotBridge slot)) {
                wildcardpattern$reportIncompatibleStructure(
                    "GTNL PatternSlot bridge was not applied",
                    null);
                return null;
            }
            GTNLPatternCompat.replaceOwnedMappings(
                rebuilt,
                rawSlot,
                slot.wildcardpattern$getRegistrationDetails(world));
        }
        return rebuilt;
    }

    @Unique
    private Object wildcardpattern$findOwningSlot(Object[] slots, ICraftingPatternDetails requested) {
        World world = wildcardpattern$getWorld();
        if (world == null) {
            return null;
        }
        for (Object rawSlot : slots) {
            if (rawSlot instanceof GTNLPatternSlotBridge slot
                && slot.wildcardpattern$isWildcard()
                && slot.wildcardpattern$owns(requested, world)) {
                this.patternDetailsPatternSlotMap.put(requested, rawSlot);
                return rawSlot;
            }
        }
        return null;
    }

    @Unique
    private boolean wildcardpattern$hasWildcard(Object[] slots) {
        for (Object rawSlot : slots) {
            if (rawSlot instanceof GTNLPatternSlotBridge slot && slot.wildcardpattern$isWildcard()) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean wildcardpattern$hasUnsupportedFluidPacket(InventoryCrafting table) {
        if (this.supportFluids) {
            return false;
        }
        for (int index = 0; index < table.getSizeInventory(); index++) {
            ItemStack stack = table.getStackInSlot(index);
            if (stack != null && stack.getItem() instanceof ItemFluidPacket) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private World wildcardpattern$getWorld() {
        try {
            return ((IMetaTileEntity) (Object) this).getBaseMetaTileEntity().getWorld();
        } catch (LinkageError | RuntimeException failure) {
            wildcardpattern$reportIncompatibleStructure("GTNL hatch no longer implements IMetaTileEntity", failure);
            return null;
        }
    }

    @Unique
    private Object[] wildcardpattern$getInternalSlots() {
        try {
            Field field = this.wildcardpattern$internalInventoryField;
            if (field == null) {
                field = wildcardpattern$findField(((Object) this).getClass(), "internalInventory");
                if (field == null) {
                    wildcardpattern$reportIncompatibleStructure("GTNL internalInventory field is missing", null);
                    return null;
                }
                field.setAccessible(true);
                this.wildcardpattern$internalInventoryField = field;
            }
            Object array = field.get(this);
            if (array == null || !array.getClass().isArray()) {
                wildcardpattern$reportIncompatibleStructure("GTNL internalInventory is not an array", null);
                return null;
            }
            int length = Array.getLength(array);
            Object[] result = new Object[length];
            for (int index = 0; index < length; index++) {
                result[index] = Array.get(array, index);
            }
            return result;
        } catch (IllegalAccessException | LinkageError | RuntimeException failure) {
            wildcardpattern$reportIncompatibleStructure("Cannot read GTNL internalInventory", failure);
            return null;
        }
    }

    @Unique
    private static Field wildcardpattern$findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @Unique
    private void wildcardpattern$reportIncompatibleStructure(String message, Throwable failure) {
        if (this.wildcardpattern$reportedIncompatibleStructure) {
            return;
        }
        this.wildcardpattern$reportedIncompatibleStructure = true;
        if (failure == null) {
            WildcardPatternMod.LOG.error("[wildcardpattern] {}. GTNL compatibility remains disabled.", message);
        } else {
            WildcardPatternMod.LOG.error(
                "[wildcardpattern] {}. GTNL compatibility remains disabled.",
                message,
                failure);
        }
    }
}
```

- [ ] **Step 2: Compile all production sources**

Run:

```powershell
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`. There are no imports from `com.science.gtnl` and no new GTNL dependency.

- [ ] **Step 3: Run all automated tests**

Run:

```powershell
./gradlew test
```

Expected: `16 tests completed` with no failures.

- [ ] **Step 4: Verify the pinned GTNL methods still match the injections**

Run:

```powershell
$source = (Invoke-WebRequest -UseBasicParsing "https://raw.githubusercontent.com/ABKQPO/GT-Not-Leisure/06cc841398ea4f7d6129fb8584002f0584ea05a8/src/main/java/com/science/gtnl/common/machine/hatch/SuperCraftingInputHatchME.java").Content
$source | Select-String "void provideCrafting\(ICraftingProviderHelper"
$source | Select-String "boolean pushPattern\(ICraftingPatternDetails patternDetails, InventoryCrafting table\)"
$source | Select-String "void onPatternChange\(int index, ItemStack newItem\)"
$source | Select-String "void loadNBTData\(NBTTagCompound aNBT\)"
$source | Select-String "class PatternSlot"
$source | Select-String "boolean insertItemsAndFluids\(InventoryCrafting inventoryCrafting\)"
```

Expected: every search prints one matching declaration from the pinned source. Record an upstream signature change as a compatibility-version change instead of adding a hard dependency.

- [ ] **Step 5: Commit the provider integration**

```powershell
git add src/main/java/com/myname/wildcardpattern/mixin/GTNLSuperCraftingInputHatchMEMixin.java
git commit -m "feat: expand patterns in GTNL assemblies"
```

### Task 7: Register The Mixins And Document The Compatibility Boundary

**Files:**
- Modify: `src/main/resources/mixins.wildcardpattern.json`
- Modify: `README.md`

- [ ] **Step 1: Register the config plugin and optional mixins**

Make `mixins.wildcardpattern.json` exactly:

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.myname.wildcardpattern.mixin",
  "plugin": "com.myname.wildcardpattern.mixin.WildcardPatternMixinPlugin",
  "refmap": "mixins.wildcardpattern.refmap.json",
  "compatibilityLevel": "JAVA_8",
  "mixins": [
    "DualityInterfaceMixin",
    "GTNLPatternSlotMixin",
    "GTNLSuperCraftingInputHatchMEMixin",
    "ItemEncodedPatternMixin",
    "MTEHatchCraftingInputMEMixin",
    "PatternMultiplierHelperMixin"
  ],
  "client": [],
  "server": []
}
```

- [ ] **Step 2: Validate the JSON**

Run:

```powershell
Get-Content -Raw src/main/resources/mixins.wildcardpattern.json | ConvertFrom-Json | Out-Null
```

Expected: exit code `0` with no parser error.

- [ ] **Step 3: Document GTNL support**

Under `README.md`'s `## 兼容性说明` heading, add these bullets:

```markdown
- `v1.1.0` 的 GTNL 兼容以 `GT-Not-Leisure` `dev-290`（提交 `06cc841`）的“超级样板输入总成 (ME)”为目标；未安装 GTNL 时兼容 Mixin 会自动跳过。
- GTNL 总成中的通配样板会向 AE 注册全部生成配方；总成内已有输入时只接受同一生成配方，存档重载后按生成 ID 恢复，无法唯一恢复时不会猜测材料。
```

- [ ] **Step 4: Commit configuration and documentation**

```powershell
git add src/main/resources/mixins.wildcardpattern.json README.md
git commit -m "docs: describe GTNL pattern assembly support"
```

### Task 8: Verify Packaging And The 290beta1 Workflow

**Files:**
- Verify: `build/libs/*.jar`
- Verify: a GTNH `2.9.0-beta-1` instance containing GTNL `dev-290`

- [ ] **Step 1: Run a clean automated verification**

Run:

```powershell
./gradlew clean test build
```

Expected: `BUILD SUCCESSFUL`; all `16` unit tests pass; `compileJava`, `processResources`, and `jar` complete successfully.

- [ ] **Step 2: Verify the built jar contains every compatibility class**

Run:

```powershell
$jar = Get-ChildItem build/libs/*.jar |
    Where-Object { $_.Name -notmatch "sources|dev|javadoc" } |
    Select-Object -First 1
jar tf $jar.FullName | Select-String "GTNLPatternCompat|GTNLPatternSlotState|GTNLPatternSlotBridge|GTNLPatternSlotMixin|GTNLSuperCraftingInputHatchMEMixin|WildcardPatternMixinPlugin|mixins.wildcardpattern.json"
```

Expected: each of the six classes and `mixins.wildcardpattern.json` appears in the jar listing.

- [ ] **Step 3: Verify GTNL was not added to the dependency graph**

Run:

```powershell
./gradlew dependencies --configuration runtimeClasspath | Select-String "GT-Not-Leisure|ScienceNotLeisure"
```

Expected: no matching dependency line.

- [ ] **Step 4: Smoke-test seed independence in game**

In the same `2.9.0-beta-1` world and AE network:

1. Encode an `ingot* -> plate*` wildcard using aluminium ingot and aluminium plate as the dragged seed.
2. Put only that wildcard into one GTNL “超级样板输入总成 (ME)” slot.
3. Confirm the AE terminal exposes both aluminium plate and bronze plate crafting recipes, and that the advertised count agrees with the wildcard GUI.
4. Request bronze plate and confirm bronze ingots enter that physical slot and the connected plate machine processes bronze.
5. Let the slot drain, request aluminium plate, and confirm aluminium inputs and output use the same physical slot.
6. Repeat with a wildcard encoded from bronze; confirm the terminal exposes the same generated recipe set.
7. Insert one composite wildcard containing two material rules and confirm both rules use the same expansion and dispatch path.

Expected: both seed patterns expose and execute the same aluminium and bronze recipes, and composite wildcard rules expand without a separate GTNL code path.

- [ ] **Step 5: Smoke-test occupancy and reload recovery**

1. Start a bronze request and stop the downstream machine while bronze inputs remain buffered in the GTNL slot.
2. Attempt an aluminium request through the same slot.
3. Confirm the second request is rejected or routed elsewhere and does not mix aluminium into the occupied bronze slot.
4. Exit and reload the world with bronze inputs still buffered.
5. Restart the downstream machine and confirm GTNL still reports bronze pattern inputs and completes bronze plate.
6. Repeat after removing `WildcardActivePatternId` from a copied test save: verify unique bronze inputs recover bronze; create an intentionally ambiguous buffered test case and verify no recipe runs.

Expected: active IDs survive normal reloads, unique input recovery is deterministic, and ambiguous input recovery remains inactive.

- [ ] **Step 6: Smoke-test unaffected paths**

1. Insert an ordinary AE encoded processing pattern beside the wildcard and craft it successfully.
2. Confirm existing AE interface wildcard expansion still works.
3. Confirm the standard GregTech ME crafting input hatch wildcard expansion still works.
4. Launch a copy of the pack without GTNL and confirm startup completes with one debug message indicating the optional compatibility is not applicable.

Expected: ordinary patterns and both existing compatibility paths retain their prior behavior; GTNL remains optional.

- [ ] **Step 7: Inspect the final diff and working tree**

Run:

```powershell
git diff HEAD~7 --check
git status --short
```

Expected: `git diff --check` prints no whitespace errors. `git status --short` is empty after all task commits.

## Completion Criteria

- One wildcard in a GTNL Super Pattern Input Assembly advertises all generated details, regardless of aluminium or bronze seed.
- Every advertised detail maps to its current physical slot and stale requests return `false` instead of dereferencing `null`.
- GTNL's `getPatternInputs()` observes the selected generated detail while inputs remain buffered.
- A non-empty slot rejects a different generated ID.
- `WildcardActivePatternId` restores the recipe across a normal save/reload; missing IDs recover only from one unique input match.
- Ordinary patterns, AE interfaces, standard GregTech crafting input hatches, and installations without GTNL retain existing behavior.
- Regular and composite wildcard patterns both use the existing generator dispatch.
- Unit tests, full build, jar inspection, and all applicable in-game checks pass before release.
