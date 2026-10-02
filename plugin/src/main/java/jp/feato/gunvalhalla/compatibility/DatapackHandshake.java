package jp.feato.gunvalhalla.compatibility;

import java.util.Map;

/** Requires matching identity and a moving heartbeat, including after world reload. */
public final class DatapackHandshake {
    public enum State { WAITING, VERIFIED, FAILED }
    private final Map<String, Integer> expected;
    private State state = State.WAITING;
    private Integer lastHeartbeat;
    private int waiting;
    private int stale;
    private String reason = "Waiting for datapack marker";

    public DatapackHandshake(Map<String, Integer> expected) { this.expected = Map.copyOf(expected); }

    public State sample(Map<String, Integer> actual, Integer heartbeat) {
        if (state == State.FAILED) return state;
        if (actual == null || heartbeat == null) {
            if (state == State.VERIFIED) fail("Datapack marker disappeared; full restart required");
            else if (++waiting >= 5) fail("Datapack marker absent after 100 ticks");
            return state;
        }
        if (!expected.equals(actual)) {
            fail("Datapack version/protocol mismatch: expected=" + expected + ", actual=" + actual);
            return state;
        }
        if (lastHeartbeat != null && !heartbeat.equals(lastHeartbeat)) {
            state = State.VERIFIED;
            stale = 0;
            reason = "Matching live datapack marker";
        } else if (lastHeartbeat != null && ++stale >= 3) {
            fail("Datapack heartbeat stopped for 60 ticks; full restart required");
        }
        lastHeartbeat = heartbeat;
        return state;
    }

    private void fail(String message) { state = State.FAILED; reason = message; }
    public State state() { return state; }
    public String reason() { return reason; }
}
