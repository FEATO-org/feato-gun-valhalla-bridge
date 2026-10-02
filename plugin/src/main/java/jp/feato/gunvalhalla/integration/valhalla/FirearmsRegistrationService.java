package jp.feato.gunvalhalla.integration.valhalla;

import java.util.function.BooleanSupplier;
import java.util.HashSet;
import java.util.Locale;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileRegistry;
import me.athlaeos.valhallammo.skills.skills.SkillRegistry;
import org.bukkit.Bukkit;

/** The only Bridge class that mutates Valhalla registries. No retry/overwrite on partial failure. */
public final class FirearmsRegistrationService {
    private FirearmsSkill skill;
    public void register(BooleanSupplier healthy) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Registration must run on the server thread");
        RegistrationRequirements.verify(SkillRegistry.getAllSkillsByType().keySet(),
                SkillRegistry.isRegistered("FIREARMS") || SkillRegistry.isRegistered(FirearmsSkill.class),
                ProfileRegistry.getRegisteredProfiles().values().stream().anyMatch(p ->
                        p.getClass() == FirearmsProfile.class || p.getTableName().equals("profiles_firearms")),
                !Bukkit.getOnlinePlayers().isEmpty());
        if (ProfileRegistry.getPersistence() == null) throw new IllegalStateException("Valhalla persistence is unavailable");
        FirearmsProfile template = new FirearmsProfile(null);
        ProfileRegistry.registerProfileType(template);
        if (!ProfileRegistry.getPersistence().hasProfileTable(template))
            throw new IllegalStateException("FIREARMS SQL table could not be created; full restart required");
        // Valhalla's createProfileTable logs some SQL failures instead of throwing.
        // Read schema through its public connection before accepting the registration.
        try (var columns = ProfileRegistry.getPersistence().getConnection().getMetaData()
                .getColumns(null, null, template.getTableName(), null)) {
            var names = new HashSet<String>();
            while (columns.next()) names.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            if (!names.contains("owner") || !template.getAllStatNames().stream()
                    .allMatch(s -> names.contains(s.toLowerCase(Locale.ROOT))))
                throw new IllegalStateException("FIREARMS SQL schema is incomplete; full restart required");
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("Cannot verify FIREARMS SQL schema; full restart required", failure);
        }
        FirearmsSkill candidate = new FirearmsSkill("FIREARMS", healthy);
        SkillRegistry.registerSkill(candidate);
        if (SkillRegistry.getSkill("FIREARMS") != candidate || !ProfileRegistry.getRegisteredProfiles().containsKey(FirearmsProfile.class))
            throw new IllegalStateException("FIREARMS registry verification failed; full restart required");
        skill = candidate;
    }
    public FirearmsSkill skill() { return skill; }
    public void verifyIdentity() {
        if (skill == null || SkillRegistry.getSkill("FIREARMS") != skill ||
                !ProfileRegistry.getRegisteredProfiles().containsKey(FirearmsProfile.class))
            throw new IllegalStateException("Valhalla registry changed; no automatic re-registration; full restart required");
        RegistrationRequirements.REQUIRED_SKILLS.forEach(name -> {
            if (!SkillRegistry.isRegistered(name)) throw new IllegalStateException("Required skill disappeared: " + name);
        });
    }
}
