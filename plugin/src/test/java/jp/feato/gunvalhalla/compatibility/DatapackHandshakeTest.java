package jp.feato.gunvalhalla.compatibility;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DatapackHandshakeTest {
    private static final Map<String, Integer> IDENTITY = Map.of("#release", 1, "#protocol", 1);
    @Test void savedMarkerAloneCannotVerifyAndEventuallyFails() {
        var h = new DatapackHandshake(IDENTITY);
        assertEquals(DatapackHandshake.State.WAITING, h.sample(IDENTITY, 42));
        for (int i = 0; i < 2; i++) assertEquals(DatapackHandshake.State.WAITING, h.sample(IDENTITY, 42));
        assertEquals(DatapackHandshake.State.FAILED, h.sample(IDENTITY, 42));
    }
    @Test void movingHeartbeatVerifiesAndStoppedHeartbeatFailsClosed() {
        var h = new DatapackHandshake(IDENTITY);
        h.sample(IDENTITY, 0);
        assertEquals(DatapackHandshake.State.VERIFIED, h.sample(IDENTITY, 20));
        h.sample(IDENTITY, 20);
        h.sample(IDENTITY, 20);
        assertEquals(DatapackHandshake.State.FAILED, h.sample(IDENTITY, 20));
        assertEquals(DatapackHandshake.State.FAILED, h.sample(IDENTITY, 100));
    }
    @Test void missingPackTimesOutAndDoesNotRetry() {
        var h = new DatapackHandshake(IDENTITY);
        for (int i = 0; i < 4; i++) assertEquals(DatapackHandshake.State.WAITING, h.sample(null, null));
        assertEquals(DatapackHandshake.State.FAILED, h.sample(null, null));
        assertEquals(DatapackHandshake.State.FAILED, h.sample(IDENTITY, 1));
    }
    @Test void wrongProtocolAndWrongReleaseAreRejected() {
        assertEquals(DatapackHandshake.State.FAILED, new DatapackHandshake(IDENTITY).sample(Map.of("#release", 1, "#protocol", 2), 1));
        assertEquals(DatapackHandshake.State.FAILED, new DatapackHandshake(IDENTITY).sample(Map.of("#release", 2, "#protocol", 1), 1));
        assertEquals(DatapackHandshake.State.FAILED, new DatapackHandshake(IDENTITY).sample(Map.of("#release", 1), 1));
    }
    @Test void packRemovalFailsImmediatelyAfterVerification() {
        var h = new DatapackHandshake(IDENTITY);
        h.sample(IDENTITY, 0); h.sample(IDENTITY, 20);
        assertEquals(DatapackHandshake.State.FAILED, h.sample(null, null));
    }
    @Test void heartbeatMayResetOnWorldReloadOrWrapAround() {
        var h = new DatapackHandshake(IDENTITY);
        h.sample(IDENTITY, Integer.MAX_VALUE);
        assertEquals(DatapackHandshake.State.VERIFIED, h.sample(IDENTITY, Integer.MIN_VALUE));
        assertEquals(DatapackHandshake.State.VERIFIED, h.sample(IDENTITY, 1));
    }
}
