package jp.feato.gunvalhalla.integration.valhalla;

import java.util.List;
import java.util.Set;

/** Checked before touching either registry; prevents other_levels_required fail-open. */
public final class RegistrationRequirements {
    public static final List<String> REQUIRED_SKILLS = List.of("MINING", "LIGHT_WEAPONS", "LIGHT_ARMOR", "HEAVY_ARMOR", "ARCHERY");
    private RegistrationRequirements() { }
    public static void verify(Set<String> available, boolean skillConflict, boolean profileConflict, boolean onlinePlayers) {
        List<String> missing = REQUIRED_SKILLS.stream().filter(s -> !available.contains(s)).toList();
        if (!missing.isEmpty()) throw new IllegalStateException("Missing required Valhalla skills: " + missing);
        if (skillConflict || profileConflict) throw new IllegalStateException("FIREARMS skill/profile already registered; refusing overwrite");
        if (onlinePlayers) throw new IllegalStateException("Late registration with online players is unsupported; full restart required");
    }
}
