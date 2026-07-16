package com.myname.wildcardpattern.compat.gtnl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

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
        long activeRevision = state.getRevision();

        assertEquals(REPRESENTATIVE, state.current(false));
        assertEquals("", state.getPersistentId(false));
        assertTrue(state.getRevision() > activeRevision);
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
    void manualOnlySlotBlocksProcessingUntilAeBufferArrives() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");

        assertTrue(state.shouldBlockProcessing(false, true));
        assertFalse(state.shouldBlockProcessing(false, false));
        assertTrue(state.activate("bronze", false));
        assertFalse(state.shouldBlockProcessing(true, true));
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
    void cacheRevisionChangesForIdentityAndResolutionTransitions() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");

        long initial = state.getRevision();
        assertTrue(state.activate("bronze", false));
        long active = state.getRevision();
        state.replaceExpandedDetails(Arrays.asList("aluminium"));
        long unresolved = state.getRevision();
        state.current(false);
        long cleared = state.getRevision();

        assertTrue(active > initial);
        assertTrue(unresolved > active);
        assertTrue(cleared > unresolved);
    }

    @Test
    void duplicateGeneratedIdsAreRegisteredOnce() {
        GTNLPatternSlotState<String> state = new GTNLPatternSlotState<>(REPRESENTATIVE, value -> value);

        state.replaceExpandedDetails(Arrays.asList("bronze", "bronze", "aluminium", null, ""));

        assertEquals(Arrays.asList("bronze", "aluminium"), state.getExpandedDetails());
    }

    @Test
    void cachedIdLookupDoesNotReextractExpandedDetails() {
        AtomicInteger extractions = new AtomicInteger();
        GTNLPatternSlotState<String> state =
            new GTNLPatternSlotState<>(REPRESENTATIVE, value -> {
                extractions.incrementAndGet();
                return value;
            });
        state.replaceExpandedDetails(Arrays.asList("bronze", "aluminium"));
        extractions.set(0);

        assertTrue(state.containsId("bronze"));
        assertFalse(state.containsId("steel"));
        assertEquals(0, extractions.get());

        extractions.set(0);
        assertTrue(state.activate("aluminium", false));
        assertEquals(2, extractions.get(), "Activation should extract only the requested and canonical IDs");
    }

    private static GTNLPatternSlotState<String> state(String... details) {
        GTNLPatternSlotState<String> state = new GTNLPatternSlotState<>(REPRESENTATIVE, value -> value);
        state.replaceExpandedDetails(Arrays.asList(details));
        return state;
    }
}
