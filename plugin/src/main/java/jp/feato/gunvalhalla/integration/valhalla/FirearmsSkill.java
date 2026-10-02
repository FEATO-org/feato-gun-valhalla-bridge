package jp.feato.gunvalhalla.integration.valhalla;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.skills.skills.Skill;
import org.bukkit.configuration.file.YamlConfiguration;

/** Dedicated Java skill; YAML only supplies the PoC tree to Skill's public parser. */
public final class FirearmsSkill extends Skill {
    private final BooleanSupplier healthy;
    public FirearmsSkill(String type, BooleanSupplier healthy) { super(type); this.healthy = healthy; }
    @Override public void loadConfiguration() {
        loadCommonConfig(resource("firearms.yml"), resource("firearms_progression.yml"));
        if (maxLevel != 100 || !"銃器".equals(displayName)) throw new IllegalStateException("Unexpected FIREARMS definition");
    }
    private static YamlConfiguration resource(String path) {
        InputStream input = FirearmsSkill.class.getClassLoader().getResourceAsStream(path);
        if (input == null) throw new IllegalStateException("Missing skill resource: " + path);
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(reader);
            return config;
        } catch (Exception failure) { throw new IllegalStateException("Invalid skill resource: " + path, failure); }
    }
    @Override public boolean isLevelableSkill() { return healthy.getAsBoolean(); }
    @Override public boolean isNavigable() { return healthy.getAsBoolean() && super.isNavigable(); }
    @Override public Class<? extends Profile> getProfileType() { return FirearmsProfile.class; }
    @Override public int getSkillTreeMenuOrderPriority() { return 999; }
}
