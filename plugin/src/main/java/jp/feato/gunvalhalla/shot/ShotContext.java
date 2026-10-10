package jp.feato.gunvalhalla.shot;

import java.util.List;
import java.util.UUID;

/** Immutable debug snapshots; firing facts never change when hits arrive. */
public record ShotContext(Fired fired, State state, List<Hit> hits) {
    public ShotContext { hits = List.copyOf(hits); }
    public enum State { FIRED, IN_FLIGHT, ENTITY_HIT, BLOCK_HIT, UNKNOWN, EXPIRED }
    public enum Headshot { TRUE, FALSE, UNKNOWN }
    public record Point(UUID world, double x, double y, double z) {}
    public record Fired(long id, UUID shooter, String weapon, int originalProjectileType,
                        int nativeSource, long tick, Point origin) {}
    public record Hit(State type, UUID target, Point location, Headshot headshot) {
        public Hit {
            if (type != State.ENTITY_HIT && type != State.BLOCK_HIT)
                throw new IllegalArgumentException("Not a hit state");
            if ((type == State.ENTITY_HIT) != (target != null))
                throw new IllegalArgumentException("Target does not match hit type");
        }
    }
}
