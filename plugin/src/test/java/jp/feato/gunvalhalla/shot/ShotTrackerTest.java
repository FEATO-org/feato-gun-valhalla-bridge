package jp.feato.gunvalhalla.shot;

import java.util.HashSet;
import java.util.UUID;
import jp.feato.gunvalhalla.integration.guncore.GunCoreContract;
import jp.feato.gunvalhalla.integration.guncore.TransportGuard;
import jp.feato.gunvalhalla.shot.ShotContext.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShotTrackerTest {
    private final UUID shooter = UUID.randomUUID(), other = UUID.randomUUID();
    private final Point point = new Point(UUID.randomUUID(), 1, 2, 3);
    private final ShotTracker tracker = new ShotTracker(3);
    private ShotContext fire(long tick) { return tracker.fire(shooter, "modern_guns:gun/aa12", 3, 7, tick, point); }
    private Hit entity() { return new Hit(State.ENTITY_HIT, other, point, Headshot.TRUE); }
    @Test void idsAreUniqueAcrossSameTickShotsAndCompletedCleanup() {
        var ids = new HashSet<Long>();
        for (int i = 0; i < 1000; i++) {
            ShotContext c = fire(1); assertTrue(ids.add(c.fired().id()));
            tracker.complete(c.fired().id(), shooter, 1);
        }
        assertTrue(tracker.active(shooter).isEmpty());
        assertTrue(tracker.recent(shooter).isEmpty());
    }
    @Test void shotgunMultiTargetUsesOneShotAndKeepsImmutableFireSnapshot() {
        ShotContext shot = fire(1);
        tracker.hit(shot.fired().id(), 7, entity());
        tracker.hit(shot.fired().id(), 7, new Hit(State.ENTITY_HIT, UUID.randomUUID(), point, Headshot.FALSE));
        assertEquals(1, tracker.active(shooter).size());
        assertEquals(State.IN_FLIGHT, tracker.active(shooter).getFirst().state());
        ShotContext done = tracker.complete(shot.fired().id(), shooter, 1);
        assertEquals(State.ENTITY_HIT, done.state()); assertEquals(2, done.hits().size());
        assertEquals(shot.fired(), done.fired()); assertTrue(shot.hits().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> done.hits().clear());
    }
    @Test void duplicateFiredRejectedEvenAfterHistoryPurged() {
        ShotContext shot = fire(1); tracker.complete(shot.fired().id(), shooter, 1);
        assertThrows(IllegalStateException.class, () -> tracker.register(shot.fired()));
        assertEquals(1, tracker.duplicateCount());
    }
    @Test void unknownHitRejected() {
        assertThrows(IllegalStateException.class, () -> tracker.hit(99, 7, entity()));
        assertEquals(1, tracker.orphanCount());
    }
    @Test void hitAfterCompletionRejected() {
        ShotContext shot = fire(1); tracker.complete(shot.fired().id(), shooter, 1);
        assertThrows(IllegalStateException.class, () -> tracker.hit(shot.fired().id(), 7, entity()));
    }
    @Test void shooterCannotChangeMidFlightOrAtCompletion() {
        ShotContext shot = fire(1);
        assertThrows(IllegalStateException.class, () -> tracker.hit(shot.fired().id(), 8, entity()));
        assertThrows(IllegalStateException.class, () -> tracker.complete(shot.fired().id(), other, 1));
    }
    @Test void noObservedHitIsUnknownNotAssumedMiss() {
        ShotContext shot = fire(1);
        assertEquals(State.UNKNOWN, tracker.complete(shot.fired().id(), shooter, 1).state());
    }
    @Test void blockCompletionRetainsImpact() {
        ShotContext shot = fire(1);
        tracker.hit(shot.fired().id(), 7, new Hit(State.BLOCK_HIT, null, point, Headshot.UNKNOWN));
        assertEquals(State.BLOCK_HIT, tracker.complete(shot.fired().id(), shooter, 1).state());
    }
    @Test void activeTtlRemovesUnfinishedShot() {
        fire(1); assertTrue(tracker.expire(10).isEmpty());
        assertEquals(State.EXPIRED, tracker.expire(11).getFirst().state());
        assertTrue(tracker.active(shooter).isEmpty());
    }
    @Test void recentHistoryIsBoundedAndExpires() {
        tracker.debug(true);
        for (int i = 0; i < 10; i++) { var shot = fire(1); tracker.complete(shot.fired().id(), shooter, 1); }
        assertEquals(3, tracker.recent(shooter).size());
        tracker.expire(201); assertTrue(tracker.recent(shooter).isEmpty());
    }
    @Test void disablingDebugImmediatelyClearsHistory() {
        tracker.debug(true); var shot = fire(1); tracker.complete(shot.fired().id(), shooter, 1);
        tracker.debug(false); assertTrue(tracker.recent(shooter).isEmpty());
    }
    @Test void disableCleanupAndReusedServiceDoesNotReuseIds() {
        tracker.debug(true); var first = fire(1); tracker.close();
        assertTrue(tracker.active(shooter).isEmpty()); assertTrue(tracker.recent(shooter).isEmpty());
        assertTrue(fire(2).fired().id() > first.fired().id());
    }
    @Test void playerAndWrongSessionAndUntaggedRecordsCannotNotify() {
        assertFalse(TransportGuard.accepts(false, true, 42, 42));
        assertFalse(TransportGuard.accepts(true, false, 42, 42));
        assertFalse(TransportGuard.accepts(true, true, 41, 42));
        assertFalse(TransportGuard.accepts(true, true, null, 42));
        assertTrue(TransportGuard.accepts(true, true, 42, 42));
    }
    @Test void minecraftUuidIntArrayRoundTripsSignedBits() {
        UUID expected = UUID.fromString("fedcba98-7654-3210-ffff-eeeeccccaaaa");
        String[] args = {String.valueOf((int) (expected.getMostSignificantBits() >> 32)),
                String.valueOf((int) expected.getMostSignificantBits()),
                String.valueOf((int) (expected.getLeastSignificantBits() >> 32)),
                String.valueOf((int) expected.getLeastSignificantBits())};
        assertEquals(expected, GunCoreContract.uuid(args, 0));
    }
    @Test void invalidHitShapeRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Hit(State.FIRED, other, point, Headshot.UNKNOWN));
        assertThrows(IllegalArgumentException.class, () -> new Hit(State.ENTITY_HIT, null, point, Headshot.UNKNOWN));
    }
    @Test void activeCapacityIsBoundedWithoutDiscardingExistingContexts() {
        for (int i = 0; i < 256; i++) fire(1);
        assertThrows(IllegalStateException.class, () -> fire(1));
        assertEquals(256, tracker.active(shooter).size());
    }
    @Test void hitCapacityIsBounded() {
        var shot = fire(1);
        for (int i = 0; i < 128; i++) tracker.hit(shot.fired().id(), 7, entity());
        assertThrows(IllegalStateException.class, () -> tracker.hit(shot.fired().id(), 7, entity()));
        assertEquals(128, tracker.complete(shot.fired().id(), shooter, 1).hits().size());
    }
    @Test void generatedWeaponPropertiesLoadWithoutColonTruncation() throws Exception {
        var weapons = new java.util.Properties();
        try (var input = getClass().getResourceAsStream("/gun-core-weapons.properties")) { weapons.load(input); }
        assertEquals(21, weapons.size());
        assertEquals("1", weapons.getProperty("modern_guns:gun/heckler_and_koch_mp5"));
        assertEquals("3", weapons.getProperty("modern_guns:gun/spas_12"));
        assertNull(weapons.getProperty("modern_guns:gun/rpg_7"));
    }
}
