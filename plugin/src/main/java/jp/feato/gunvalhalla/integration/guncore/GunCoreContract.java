package jp.feato.gunvalhalla.integration.guncore;

import java.util.UUID;
import org.bukkit.Location;
import jp.feato.gunvalhalla.shot.ShotContext.Point;

/** Fixed V1.0.15 internals, shared with the documented/generated datapack contract. */
public final class GunCoreContract {
    public static final String RECORD_TAG = "fgv.record", OBJECTIVE = "fgv_shot";
    public static final String SLOWCAST_TAG = "gbg.slowcast", EXPLOSION_TAG = "gbz.zombie";
    public static final String SOURCE = "gbg.id", RANGE = "gbg.slowcast.range";
    public static final String TYPE = "gbg.slowcast.projectile_type";
    public static final String ADAPT = "feato_gun_valhalla:shot/adapt";
    public static final String RESTORE = "feato_gun_valhalla:shot/restore_inventory";
    public static final String INTERNAL_COMMAND = "fgvnotify";
    private GunCoreContract() {}
    public static UUID uuid(String[] args, int offset) {
        return new UUID(((long) Integer.parseInt(args[offset]) << 32) | (Integer.parseInt(args[offset + 1]) & 0xffffffffL),
                ((long) Integer.parseInt(args[offset + 2]) << 32) | (Integer.parseInt(args[offset + 3]) & 0xffffffffL));
    }
    public static Point point(Location location) {
        return new Point(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ());
    }
}
