package jp.feato.gunvalhalla.shot;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jp.feato.gunvalhalla.shot.ShotContext.*;

/** Plugin-owned, synchronous service. No static maps and no inferred slowcast ownership. */
public final class ShotTracker {
    private static final int MAX_ACTIVE = 256, MAX_HITS = 128;
    private static final long ACTIVE_TTL = 10, COMPLETED_TTL = 200;
    private final Map<Long, Active> active = new LinkedHashMap<>();
    private final Deque<Completed> history = new ArrayDeque<>();
    private final int historyLimit;
    private long sequence, duplicates, orphans;
    private boolean debug;
    private static final class Active {
        final Fired fired;
        final List<Hit> hits = new ArrayList<>();
        Active(Fired fired) { this.fired = fired; }
        ShotContext snapshot(State state) { return new ShotContext(fired, state, hits); }
    }
    private record Completed(ShotContext context, long tick) {}
    public ShotTracker(int historyLimit) {
        if (historyLimit < 1 || historyLimit > 100) throw new IllegalArgumentException("Invalid history limit");
        this.historyLimit = historyLimit;
    }
    public void debug(boolean enabled) { debug = enabled; if (!debug) history.clear(); }
    public ShotContext fire(UUID shooter, String weapon, int projectile, int nativeSource, long tick, Point origin) {
        if (sequence == Long.MAX_VALUE) throw new IllegalStateException("Shot ID exhausted; restart required");
        return register(new Fired(sequence + 1, shooter, weapon, projectile, nativeSource, tick, origin));
    }
    ShotContext register(Fired fired) {
        if (fired.id() <= sequence) {
            duplicates++; throw new IllegalStateException("Duplicate FIRED");
        }
        if (active.size() >= MAX_ACTIVE) throw new IllegalStateException("Active context limit exceeded");
        sequence = fired.id();
        var value = new Active(fired); active.put(fired.id(), value);
        return value.snapshot(State.FIRED);
    }
    public void hit(long id, int source, Hit hit) {
        Active value = require(id);
        if (source != value.fired.nativeSource()) throw new IllegalStateException("Shooter changed");
        if (value.hits.size() >= MAX_HITS) throw new IllegalStateException("Hit limit exceeded");
        value.hits.add(hit);
    }
    public ShotContext complete(long id, UUID shooter, long tick) {
        Active value = require(id);
        if (!value.fired.shooter().equals(shooter)) throw new IllegalStateException("Shooter changed");
        State result = value.hits.stream().anyMatch(h -> h.type() == State.ENTITY_HIT) ? State.ENTITY_HIT :
                value.hits.isEmpty() ? State.UNKNOWN : State.BLOCK_HIT;
        return finish(id, result, tick);
    }
    private Active require(long id) {
        Active value = active.get(id);
        if (value == null) { orphans++; throw new IllegalStateException("Unknown or completed shot"); }
        return value;
    }
    private ShotContext finish(long id, State result, long tick) {
        ShotContext snapshot = active.remove(id).snapshot(result);
        if (debug) {
            history.addLast(new Completed(snapshot, tick));
            while (history.size() > historyLimit) history.removeFirst();
        }
        return snapshot;
    }
    public List<ShotContext> expire(long tick) {
        List<Long> ids = active.values().stream().filter(a -> tick - a.fired.tick() >= ACTIVE_TTL)
                .map(a -> a.fired.id()).toList();
        var expired = new ArrayList<ShotContext>();
        for (long id : ids) expired.add(finish(id, State.EXPIRED, tick));
        while (!history.isEmpty() && tick - history.peekFirst().tick() >= COMPLETED_TTL) history.removeFirst();
        return List.copyOf(expired);
    }
    public List<ShotContext> active(UUID shooter) {
        return active.values().stream().filter(a -> a.fired.shooter().equals(shooter))
                .map(a -> a.snapshot(State.IN_FLIGHT)).toList();
    }
    public List<ShotContext> recent(UUID shooter) {
        return history.stream().map(Completed::context).filter(c -> c.fired().shooter().equals(shooter)).toList();
    }
    public long orphanCount() { return orphans; }
    public long duplicateCount() { return duplicates; }
    public void close() { active.clear(); history.clear(); }
}
