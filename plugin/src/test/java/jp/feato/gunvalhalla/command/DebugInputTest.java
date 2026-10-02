package jp.feato.gunvalhalla.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DebugInputTest {
    @ParameterizedTest @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "0", "-1", "1000001", "1e309", "nope", "1;op attacker"})
    void nonFiniteOutOfBoundsAndCommandLikeInputAreRejected(String value) {
        assertThrows(IllegalArgumentException.class, () -> DebugInput.experience(value));
    }
    @Test void smallAndBoundedPositiveExperienceIsAccepted() {
        assertEquals(1.5, DebugInput.experience("1.5"));
        assertEquals(1_000_000, DebugInput.experience("1000000"));
    }
}
