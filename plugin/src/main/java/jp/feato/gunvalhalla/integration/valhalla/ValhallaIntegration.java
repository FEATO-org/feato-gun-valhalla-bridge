package jp.feato.gunvalhalla.integration.valhalla;

import java.util.List;
import java.util.function.BooleanSupplier;
import me.athlaeos.valhallammo.event.PlayerSkillExperienceGainEvent.ExperienceGainReason;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileRegistry;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileCache;
import me.athlaeos.valhallammo.playerstats.profiles.implementations.PowerProfile;
import me.athlaeos.valhallammo.skills.skills.Perk;
import org.bukkit.entity.Player;

/** Public API boundary used by commands; no registry or profile API calls elsewhere. */
public final class ValhallaIntegration {
    private final FirearmsRegistrationService registration = new FirearmsRegistrationService();
    public void register(BooleanSupplier healthy) { registration.register(healthy); }
    public void verifyIdentity() { registration.verifyIdentity(); }
    public void addExperience(Player player, double amount) {
        ensureLoaded(player);
        registration.skill().addEXP(player, amount, true, ExperienceGainReason.COMMAND);
    }
    public String profile(Player player) {
        ensureLoaded(player);
        FirearmsProfile profile = ProfileRegistry.getPersistentProfile(player, FirearmsProfile.class);
        PowerProfile power = ProfileRegistry.getPersistentProfile(player, PowerProfile.class);
        PowerProfile effective = ProfileCache.getOrCache(player, PowerProfile.class);
        List<String> ids = registration.skill().getPerks().stream().map(Perk::getName).toList();
        return "player=" + player.getUniqueId() + " level=" + profile.getLevel() + " exp=" + profile.getEXP() +
                " totalExp=" + profile.getTotalEXP() + " forcePersist=" + profile.shouldForcePersist() +
                " unlocked=" + power.getUnlockedPerks().stream().filter(ids::contains).sorted().toList() +
                " lockedPersistent=" + power.getPermanentlyLockedPerks().stream().filter(ids::contains).sorted().toList() +
                " lockedEffective=" + effective.getPermanentlyLockedPerks().stream().filter(ids::contains).sorted().toList();
    }
    private void ensureLoaded(Player player) {
        registration.verifyIdentity();
        if (!ProfileRegistry.isLoaded(player)) throw new IllegalStateException("Player's Valhalla profiles are not loaded yet");
        if (ProfileRegistry.getPersistentProfile(player, FirearmsProfile.class) == null)
            throw new IllegalStateException("Player has no FIREARMS profile; do not synthesize it after login");
    }
}
