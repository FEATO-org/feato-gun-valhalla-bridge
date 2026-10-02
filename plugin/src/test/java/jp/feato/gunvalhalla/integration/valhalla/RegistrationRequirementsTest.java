package jp.feato.gunvalhalla.integration.valhalla;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Pure guard tests, not evidence of a real Valhalla registry or database. */
class RegistrationRequirementsTest {
    private static Set<String> all() { return new HashSet<>(RegistrationRequirements.REQUIRED_SKILLS); }
    @ParameterizedTest @ValueSource(strings = {"MINING", "LIGHT_WEAPONS", "LIGHT_ARMOR", "HEAVY_ARMOR", "ARCHERY"})
    void eachMissingRequiredSkillStopsRegistration(String missing) {
        Set<String> available = all(); available.remove(missing);
        var error = assertThrows(IllegalStateException.class, () -> RegistrationRequirements.verify(available, false, false, false));
        assertTrue(error.getMessage().contains(missing));
    }
    @Test void completePrerequisitesPermitInitialRegistration() {
        assertDoesNotThrow(() -> RegistrationRequirements.verify(all(), false, false, false));
    }
    @Test void conflictsAndLateRegistrationNeverOverwriteOrInjectProfiles() {
        assertThrows(IllegalStateException.class, () -> RegistrationRequirements.verify(all(), true, false, false));
        assertThrows(IllegalStateException.class, () -> RegistrationRequirements.verify(all(), false, true, false));
        assertThrows(IllegalStateException.class, () -> RegistrationRequirements.verify(all(), false, false, true));
    }
}
