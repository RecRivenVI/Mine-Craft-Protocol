package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class NativeInputOwnershipTest {
    private static final class Mapping {
        int pending;
        boolean down;

        void press() {
            pending++;
            down = true;
        }

        void release() {
            pending = 0;
            down = false;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Mapping;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static void track(
            NativeInputOwnership<Mapping> ownership, List<Mapping> mappings, Runnable dispatch) {
        ownership.track(mappings, mapping -> mapping.pending, mapping -> mapping.down, dispatch);
    }

    @Test
    void mouseUpDoesNotLoseThePendingClickBeforeStrongCleanup() {
        var ownership = new NativeInputOwnership<Mapping>();
        Mapping attack = new Mapping();
        track(ownership, List.of(attack), attack::press);
        track(ownership, List.of(attack), () -> attack.down = false);
        assertFalse(attack.down);
        assertEquals(1, attack.pending);
        assertEquals(1, ownership.size());
        assertEquals(1, ownership.releaseOwned(Mapping::release));
        assertEquals(0, attack.pending);
        assertFalse(attack.down);
        assertEquals(0, ownership.size());
        assertEquals(0, ownership.releaseOwned(Mapping::release));
    }

    @Test
    void unchangedExistingAndUnrelatedMappingsAreNeverReleased() {
        var ownership = new NativeInputOwnership<Mapping>();
        Mapping human = new Mapping(), unchanged = new Mapping(), agent = new Mapping();
        human.pending = 2;
        human.down = true;
        unchanged.pending = 1;
        track(ownership, List.of(human, unchanged, agent), agent::press);
        List<Mapping> released = new ArrayList<>();
        assertEquals(
                1,
                ownership.releaseOwned(
                        mapping -> {
                            released.add(mapping);
                            mapping.release();
                        }));
        assertSame(agent, released.get(0));
        assertEquals(2, human.pending);
        assertTrue(human.down);
        assertEquals(1, unchanged.pending);
        assertFalse(unchanged.down);
    }

    @Test
    void aConsumedCountOrUnchangedHeldStateDoesNotCreateOwnership() {
        var ownership = new NativeInputOwnership<Mapping>();
        Mapping mapping = new Mapping();
        mapping.pending = 2;
        mapping.down = true;
        track(ownership, List.of(mapping), () -> mapping.pending--);
        assertEquals(0, ownership.size());
        assertEquals(0, ownership.releaseOwned(value -> fail("Unowned mapping released")));
    }

    @Test
    void newHeldStateIsTrackedEvenWhenNoClickWasQueued() {
        var ownership = new NativeInputOwnership<Mapping>();
        Mapping movement = new Mapping();
        track(ownership, List.of(movement), () -> movement.down = true);
        assertEquals(1, ownership.size());
        ownership.retireConsumed(mapping -> mapping.pending, mapping -> mapping.down);
        assertEquals(1, ownership.size());
        assertEquals(1, ownership.releaseOwned(Mapping::release));
        assertFalse(movement.down);
    }

    @Test
    void failedDispatchStillRecordsClicksProducedBeforeTheException() {
        var ownership = new NativeInputOwnership<Mapping>();
        Mapping attack = new Mapping();
        var failure = new IllegalStateException("Native callback failed after enqueue");
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                track(
                                        ownership,
                                        List.of(attack),
                                        () -> {
                                            attack.press();
                                            attack.down = false;
                                            throw failure;
                                        })));
        assertEquals(1, attack.pending);
        assertEquals(1, ownership.size());
        ownership.releaseOwned(Mapping::release);
        assertEquals(0, attack.pending);
    }

    @Test
    void failedDispatchWithNoNativeChangeDoesNotAcquireAnotherMappingsState() {
        var ownership = new NativeInputOwnership<Mapping>();
        Mapping human = new Mapping();
        human.pending = 1;
        assertThrows(
                IllegalStateException.class,
                () ->
                        track(
                                ownership,
                                List.of(human),
                                () -> {
                                    throw new IllegalStateException("Rejected before callback");
                                }));
        assertEquals(0, ownership.size());
        assertEquals(1, human.pending);
    }

    @Test
    void normalCompletionRetiresOnlyConsumedAndNoLongerHeldMappings() {
        var ownership = new NativeInputOwnership<Mapping>();
        Mapping pending = new Mapping(), held = new Mapping(), consumed = new Mapping();
        track(
                ownership,
                List.of(pending, held, consumed),
                () -> {
                    pending.press();
                    pending.down = false;
                    held.down = true;
                    consumed.press();
                });
        consumed.pending = 0;
        consumed.down = false;
        ownership.retireConsumed(mapping -> mapping.pending, mapping -> mapping.down);
        assertEquals(2, ownership.size());
        assertEquals(1, pending.pending, "Normal completion must not erase an unconsumed click");
        assertTrue(held.down);
        assertEquals(
                2,
                ownership.releaseOwned(
                        mapping -> {
                            assertNotSame(consumed, mapping);
                            mapping.release();
                        }));
    }

    @Test
    void failedStrongCleanupKeepsTheFailedAndRemainingMappingsForTheNextAttempt() {
        var ownership = new NativeInputOwnership<Mapping>();
        List<Mapping> mappings = List.of(new Mapping(), new Mapping(), new Mapping());
        track(ownership, mappings, () -> mappings.forEach(Mapping::press));
        List<Mapping> released = new ArrayList<>();
        assertThrows(
                IllegalStateException.class,
                () ->
                        ownership.releaseOwned(
                                mapping -> {
                                    if (!released.isEmpty())
                                        throw new IllegalStateException("Release failed");
                                    mapping.release();
                                    released.add(mapping);
                                }));
        assertEquals(1, released.size());
        assertEquals(2, ownership.size());
        assertEquals(
                2,
                ownership.releaseOwned(
                        mapping -> {
                            assertNotSame(released.get(0), mapping);
                            mapping.release();
                            released.add(mapping);
                        }));
        assertEquals(3, released.size());
        assertEquals(0, ownership.size());
    }

    @Test
    void equalMappingsRemainIndependentNativeIdentities() {
        var ownership = new NativeInputOwnership<Mapping>();
        Mapping first = new Mapping(), equalButUnowned = new Mapping(), second = new Mapping();
        assertEquals(first, equalButUnowned);
        track(
                ownership,
                List.of(first, equalButUnowned, second),
                () -> {
                    first.press();
                    second.press();
                });
        Set<Mapping> released = Collections.newSetFromMap(new IdentityHashMap<>());
        assertEquals(
                2,
                ownership.releaseOwned(
                        mapping -> {
                            assertNotSame(equalButUnowned, mapping);
                            released.add(mapping);
                            mapping.release();
                        }));
        assertEquals(2, released.size());
        assertTrue(released.contains(first));
        assertTrue(released.contains(second));
        assertFalse(released.contains(equalButUnowned));
    }

    @Test
    void nestedRuntimeDispatchesRetainEveryChangedNativeIdentityWithoutDuplication() {
        var ownership = new NativeInputOwnership<Mapping>();
        Mapping outer = new Mapping(), inner = new Mapping();
        List<Mapping> mappings = List.of(outer, inner);
        track(
                ownership,
                mappings,
                () -> {
                    outer.press();
                    track(ownership, mappings, inner::press);
                });
        assertEquals(2, ownership.size());
        assertEquals(2, ownership.releaseOwned(Mapping::release));
    }
}
