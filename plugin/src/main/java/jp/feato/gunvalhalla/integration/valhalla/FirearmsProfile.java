package jp.feato.gunvalhalla.integration.valhalla;

import java.util.UUID;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileRegistry;
import me.athlaeos.valhallammo.skills.skills.Skill;

/** All progression is persisted by Valhalla; Bridge has no player database. */
public final class FirearmsProfile extends Profile {
    public FirearmsProfile(UUID owner) { super(owner); }
    @Override public String getTableName() { return "profiles_firearms"; }
    @Override public Class<? extends Skill> getSkillType() { return FirearmsSkill.class; }
    @Override public Profile getBlankProfile(UUID owner) {
        return ProfileRegistry.copyDefaultStats(new FirearmsProfile(owner));
    }
    @Override public void initStats() {
        super.initStats();
        // Valhalla 1.10.3 otherwise skips level-zero profiles with partial XP.
        setShouldForcePersist(true);
    }
}
