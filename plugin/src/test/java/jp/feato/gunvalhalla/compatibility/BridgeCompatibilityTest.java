package jp.feato.gunvalhalla.compatibility;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BridgeCompatibilityTest {
    @Test void exactTargetMetadataIsPackaged() throws Exception {
        try (var input = getClass().getResourceAsStream("/bridge.properties")) {
            var c = BridgeCompatibility.load(input);
            assertEquals("26.2", c.minecraftVersion()); assertEquals(126, c.paperBuild());
            assertEquals("1.10.3", c.valhallaVersion()); assertEquals("1.0.15", c.gunCoreVersion());
            assertEquals("1.9.3", c.modernGunsVersion()); assertEquals(5, c.marker().size());
            assertThrows(UnsupportedOperationException.class, () -> c.marker().put("#release", 2));
        }
    }
    @Test void incompleteMetadataFailsClosed() {
        assertThrows(java.io.IOException.class, () -> BridgeCompatibility.load(null));
        assertThrows(IllegalArgumentException.class, () -> BridgeCompatibility.load(
                new ByteArrayInputStream("bridge.release=0".getBytes(StandardCharsets.UTF_8))));
    }
}
