package jp.feato.gunvalhalla;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackagedConfigurationTest {
    private YamlConfiguration load(String path) throws Exception {
        var config = new YamlConfiguration();
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        return config;
    }
    @Test void debugAndFutureTracingAreOffByDefaultAndPocSettingsParse() throws Exception {
        var config = load("/config.yml");
        assertFalse(config.getBoolean("debug.enabled"));
        assertFalse(config.getBoolean("debug.shot-context")); assertFalse(config.getBoolean("debug.damage-integration"));
        assertEquals(0.85, config.getDouble("tactical-reload.magazine-multiplier"));
        assertEquals(3.0, config.getDouble("weapon-bash.base-damage"));
        assertEquals(1.10, config.getDouble("demolitionist.explosion-radius-multiplier"));
        assertEquals(40, config.getInt("bulwark.active-ticks"));
        assertEquals(0.40, config.getDouble("bulwark.knockback-resistance-add"));
    }
    @Test void commandPermissionsAreSeparatedAndRootCommandDoesNotRequireDebug() throws Exception {
        var config = load("/plugin.yml");
        assertEquals("/firearms <reload|debug>", config.getString("commands.firearms.usage"));
        assertFalse(config.contains("commands.firearms.permission"));
        assertEquals("op", config.getString("permissions.feato.gunvalhalla.debug.default"));
        assertEquals("op", config.getString("permissions.feato.gunvalhalla.reload.default"));
    }
    @Test void packagedSkillHasNoCommandsAndAbilityLocksCoverBothOtherChoices() throws Exception {
        var skill = load("/firearms.yml"); assertEquals("銃器", skill.getString("display_name"));
        var config = load("/firearms_progression.yml"); assertEquals(100, config.getInt("experience.max_level"));
        var ids = List.of("firearms_poc_quick_draw", "firearms_poc_suppressive_fire", "firearms_poc_deadeye_focus");
        for (String id : ids) {
            String prefix = "perks." + id;
            assertEquals(ids.stream().filter(other -> !other.equals(id)).sorted().toList(),
                    config.getStringList(prefix + ".perk_rewards.perks_locked_add").stream().sorted().toList());
        }
        assertFalse(config.contains("commands"));
        for (String key : Objects.requireNonNull(config.getConfigurationSection("perks")).getKeys(false)) {
            assertFalse(config.contains("perks." + key + ".commands"));
            assertFalse(config.contains("perks." + key + ".undo_commands"));
        }
    }
}
