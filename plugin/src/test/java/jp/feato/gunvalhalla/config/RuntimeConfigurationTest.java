package jp.feato.gunvalhalla.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeConfigurationTest {
    @TempDir Path directory;
    private String defaults() throws IOException {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private Path file() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, defaults());
        return file;
    }
    @Test void acceptsPackagedDefaultsAndReplacesAllRuntimeSettingsTogether() throws Exception {
        Path file = file();
        var store = new RuntimeConfiguration(file);
        var original = store.reload();
        assertEquals(0.85, original.magazineMultiplier());
        assertEquals(3, original.baseDamage()); assertEquals(1.1, original.explosionRadiusMultiplier());
        assertEquals(40, original.activeTicks()); assertEquals(0.4, original.knockbackResistanceAdd());
        assertTrue(original.tacticalReloadEnabled()); assertTrue(original.weaponBashEnabled());
        assertTrue(original.deadeyeNightVisionEnabled()); assertFalse(original.debugEnabled());
        assertFalse(original.shotContextDebug()); assertFalse(original.damageIntegrationDebug());
        var next = new YamlConfiguration(); next.load(file.toFile());
        next.set("tactical-reload.enabled", false); next.set("tactical-reload.magazine-multiplier", 1);
        next.set("weapon-bash.enabled", false); next.set("weapon-bash.base-damage", 0);
        next.set("deadeye.night-vision.enabled", false); next.set("demolitionist.explosion-radius-multiplier", 2);
        next.set("bulwark.active-ticks", 0); next.set("bulwark.knockback-resistance-add", 0);
        next.set("debug.enabled", true); next.set("debug.shot-context", true); next.set("debug.damage-integration", true);
        String edited = next.saveToString(); Files.writeString(file, edited);
        var reloaded = store.reload();
        assertSame(reloaded, store.settings()); assertNotSame(original, reloaded);
        assertEquals(new RuntimeSettings(false, 1, false, 0, false, 2, 0, 0, true, true, true), reloaded);
        assertEquals(edited, Files.readString(file)); assertFalse(original.debugEnabled());
    }
    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("tactical-reload.magazine-multiplier", 0),
                Arguments.of("tactical-reload.magazine-multiplier", 1.5),
                Arguments.of("tactical-reload.magazine-multiplier", -0.1),
                Arguments.of("weapon-bash.base-damage", -1),
                Arguments.of("demolitionist.explosion-radius-multiplier", 0),
                Arguments.of("demolitionist.explosion-radius-multiplier", -1),
                Arguments.of("bulwark.active-ticks", -1),
                Arguments.of("bulwark.active-ticks", 1.5),
                Arguments.of("bulwark.active-ticks", 2147483648L),
                Arguments.of("bulwark.knockback-resistance-add", -0.1),
                Arguments.of("weapon-bash.base-damage", Double.NaN),
                Arguments.of("demolitionist.explosion-radius-multiplier", Double.POSITIVE_INFINITY),
                Arguments.of("bulwark.knockback-resistance-add", Double.NEGATIVE_INFINITY),
                Arguments.of("weapon-bash.base-damage", "3.0"),
                Arguments.of("tactical-reload.enabled", "true"),
                Arguments.of("weapon-bash.enabled", 1),
                Arguments.of("deadeye.night-vision.enabled", "yes"),
                Arguments.of("debug.enabled", "false"),
                Arguments.of("debug.shot-context", "false"),
                Arguments.of("debug.damage-integration", "true"),
                Arguments.of("bulwark.active-ticks", (Object) null),
                Arguments.of("debug.enabled", (Object) null));
    }
    @ParameterizedTest @MethodSource("invalidValues")
    void rejectsInvalidValuesWithoutChangingActiveSnapshotOrFile(String key, Object value) throws Exception {
        Path file = file(); var store = new RuntimeConfiguration(file); var original = store.reload();
        var candidate = new YamlConfiguration(); candidate.load(file.toFile());
        candidate.set("debug.enabled", true); candidate.set(key, value);
        String invalid = candidate.saveToString(); Files.writeString(file, invalid);
        var failure = assertThrows(IllegalArgumentException.class, store::reload);
        assertTrue(failure.getMessage().contains(key));
        assertSame(original, store.settings()); assertEquals(invalid, Files.readString(file));
    }
    @Test void rejectsMalformedYamlAndPreservesValidDebugSettingAndBrokenFile() throws Exception {
        Path file = file(); var store = new RuntimeConfiguration(file); var original = store.reload();
        String invalid = "debug: [unterminated\n"; Files.writeString(file, invalid);
        assertThrows(InvalidConfigurationException.class, store::reload);
        assertSame(original, store.settings()); assertFalse(store.debugEnabled());
        assertEquals(invalid, Files.readString(file));
    }
    @Test void missingFilePreservesCurrentConfigurationAndIsNotRecreated() throws Exception {
        Path file = file(); var store = new RuntimeConfiguration(file); var original = store.reload();
        Files.delete(file);
        assertThrows(IOException.class, store::reload);
        assertSame(original, store.settings()); assertFalse(Files.exists(file));
    }
    @Test void debugDefaultsToOffBeforeAnyValidConfigurationIsLoaded() {
        var store = new RuntimeConfiguration(directory.resolve("config.yml"));
        assertFalse(store.debugEnabled()); assertThrows(IllegalStateException.class, store::settings);
    }
}
