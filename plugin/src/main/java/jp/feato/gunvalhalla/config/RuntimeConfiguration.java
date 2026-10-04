package jp.feato.gunvalhalla.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Read once, validate, then publish one immutable snapshot. Never writes config.yml. */
public final class RuntimeConfiguration {
    private final Path file;
    private volatile RuntimeSettings current;
    public RuntimeConfiguration(Path file) { this.file = file; }
    public synchronized RuntimeSettings reload() throws IOException, InvalidConfigurationException {
        var candidate = new YamlConfiguration();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            candidate.load(reader);
        }
        RuntimeSettings next = RuntimeSettings.from(candidate);
        current = next;
        return next;
    }
    public RuntimeSettings settings() {
        RuntimeSettings active = current;
        if (active == null) throw new IllegalStateException("Bridge runtime configuration is not loaded");
        return active;
    }
    public boolean debugEnabled() {
        RuntimeSettings active = current;
        return active != null && active.debugEnabled();
    }
}
