package jp.feato.gunvalhalla.compatibility;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Shared release identity; runtime Gun Core detection is a separate Phase 2 task. */
public record BridgeCompatibility(String version, int release, int protocol, String minecraftVersion,
        int paperBuild, String valhallaVersion, String gunCoreVersion, String modernGunsVersion,
        Map<String, Integer> marker) {
    public static BridgeCompatibility load(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing bridge.properties");
        Properties p = new Properties();
        p.load(input);
        Map<String, Integer> marker = new LinkedHashMap<>();
        marker.put("#release", positive(p, "bridge.release"));
        marker.put("#protocol", positive(p, "bridge.protocol"));
        marker.put("#gun_core", positive(p, "gun-core.marker"));
        marker.put("#modern_guns", positive(p, "modern-guns.marker"));
        marker.put("#valhalla", positive(p, "valhalla.marker"));
        return new BridgeCompatibility(required(p, "bridge.version"), marker.get("#release"), marker.get("#protocol"),
                required(p, "minecraft.version"), positive(p, "paper.build"), required(p, "valhalla.version"),
                required(p, "gun-core.version"), required(p, "modern-guns.version"), Map.copyOf(marker));
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing compatibility field: " + key);
        return value;
    }

    private static int positive(Properties p, String key) {
        int value = Integer.parseInt(required(p, key));
        if (value <= 0) throw new IllegalArgumentException("Compatibility field must be positive: " + key);
        return value;
    }
}
