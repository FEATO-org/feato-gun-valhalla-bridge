package jp.feato.gunvalhalla.config;

import org.bukkit.configuration.ConfigurationSection;

/** Validated, immutable Bridge settings. Effect switches remain inactive in Phase 1. */
public record RuntimeSettings(
        boolean tacticalReloadEnabled, double magazineMultiplier,
        boolean weaponBashEnabled, double baseDamage, boolean deadeyeNightVisionEnabled,
        double explosionRadiusMultiplier, int activeTicks, double knockbackResistanceAdd,
        boolean debugEnabled, boolean shotContextDebug, boolean damageIntegrationDebug) {
    public static RuntimeSettings from(ConfigurationSection config) {
        double magazine = number(config, "tactical-reload.magazine-multiplier");
        require(magazine > 0 && magazine <= 1, "tactical-reload.magazine-multiplier must be > 0 and <= 1");
        double damage = number(config, "weapon-bash.base-damage");
        require(damage >= 0, "weapon-bash.base-damage must be >= 0");
        double radius = number(config, "demolitionist.explosion-radius-multiplier");
        require(radius > 0, "demolitionist.explosion-radius-multiplier must be > 0");
        Object ticks = config.get("bulwark.active-ticks");
        require((ticks instanceof Integer || ticks instanceof Long) && ((Number) ticks).longValue() >= 0 &&
                ((Number) ticks).longValue() <= Integer.MAX_VALUE, "bulwark.active-ticks must be an integer >= 0 and <= 2147483647");
        double knockback = number(config, "bulwark.knockback-resistance-add");
        require(knockback >= 0, "bulwark.knockback-resistance-add must be >= 0");
        return new RuntimeSettings(bool(config, "tactical-reload.enabled"), magazine,
                bool(config, "weapon-bash.enabled"), damage, bool(config, "deadeye.night-vision.enabled"),
                radius, ((Number) ticks).intValue(), knockback, bool(config, "debug.enabled"),
                bool(config, "debug.shot-context"), bool(config, "debug.damage-integration"));
    }
    private static boolean bool(ConfigurationSection config, String path) {
        Object value = config.get(path);
        require(value instanceof Boolean, path + " must be a boolean");
        return (Boolean) value;
    }
    private static double number(ConfigurationSection config, String path) {
        Object value = config.get(path);
        require(value instanceof Number, path + " must be a finite number");
        double number = ((Number) value).doubleValue();
        require(Double.isFinite(number), path + " must be a finite number");
        return number;
    }
    private static void require(boolean accepted, String message) {
        if (!accepted) throw new IllegalArgumentException(message);
    }
}
