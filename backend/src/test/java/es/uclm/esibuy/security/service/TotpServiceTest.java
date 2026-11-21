package es.uclm.esibuy.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.OptionalLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;

class TotpServiceTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final long TIME_PERIOD_SECONDS = 30;
    private static final long CURRENT_STEP = 1000;

    private TotpService service;

    @BeforeEach
    void setUp() {
        service = new TotpService();
    }

    @Test
    void generatesNonEmptySecret() {
        String secret = service.generateSecret();

        assertNotNull(secret);
        assertFalse(secret.isBlank());
    }

    @Test
    void generatesDifferentSecrets() {
        String first = service.generateSecret();
        String second = service.generateSecret();

        assertNotNull(first);
        assertNotNull(second);
        assertFalse(first.isBlank());
        assertFalse(second.isBlank());
        assertNotEquals(first, second);
    }

    @Test
    void rejectsNullSecret() {
        assertFalse(service.verifyCode(null, "123456"));
    }

    @Test
    void rejectsNullCode() {
        assertFalse(service.verifyCode("JBSWY3DPEHPK3PXP", null));
    }

    @Test
    void rejectsInvalidCode() {
        assertFalse(service.verifyCode("JBSWY3DPEHPK3PXP", "invalid"));
    }

    @Test
    void rejectsCodeWithWrongLength() {
        assertFalse(service.verifyCode("JBSWY3DPEHPK3PXP", "123"));
    }

    @Test
    void generatesRequestedNumberOfBackupCodes() {
        List<String> codes = service.generateBackupCodes(8);

        assertEquals(8, codes.size());

        for (String code : codes) {
            assertNotNull(code);
            assertTrue(code.matches("\\d{8}"));
        }
    }

    @Test
    @DisplayName("HU10-CP01: el código del periodo actual devuelve ese periodo")
    void findsCurrentTimeStepForCurrentCode() throws Exception {
        TotpService fixedClock = serviceAtStep(CURRENT_STEP);

        OptionalLong step = fixedClock.findMatchingTimeStep(SECRET, codeForStep(CURRENT_STEP));

        assertEquals(OptionalLong.of(CURRENT_STEP), step);
    }

    @Test
    @DisplayName("HU10-CP02: la ventana de tolerancia acepta el periodo anterior y el siguiente")
    void findsPreviousAndNextTimeStepsInsideTheToleranceWindow() throws Exception {
        TotpService fixedClock = serviceAtStep(CURRENT_STEP);

        assertEquals(OptionalLong.of(CURRENT_STEP - 1),
                fixedClock.findMatchingTimeStep(SECRET, codeForStep(CURRENT_STEP - 1)));
        assertEquals(OptionalLong.of(CURRENT_STEP + 1),
                fixedClock.findMatchingTimeStep(SECRET, codeForStep(CURRENT_STEP + 1)));
    }

    @Test
    @DisplayName("HU10-CP03: un código fuera de la ventana de tolerancia no coincide con ningún periodo")
    void doesNotFindTimeStepOutsideTheToleranceWindow() throws Exception {
        TotpService fixedClock = serviceAtStep(CURRENT_STEP);

        assertTrue(fixedClock.findMatchingTimeStep(SECRET, codeForStep(CURRENT_STEP - 2)).isEmpty());
        assertTrue(fixedClock.findMatchingTimeStep(SECRET, codeForStep(CURRENT_STEP + 2)).isEmpty());
    }

    @Test
    @DisplayName("HU10-CP04: el periodo se encuentra aunque el código lleve espacios")
    void findsTimeStepWhenCodeHasSurroundingSpaces() throws Exception {
        TotpService fixedClock = serviceAtStep(CURRENT_STEP);

        OptionalLong step = fixedClock.findMatchingTimeStep(SECRET, " " + codeForStep(CURRENT_STEP) + " ");

        assertEquals(OptionalLong.of(CURRENT_STEP), step);
    }

    @Test
    @DisplayName("HU10-CP05: sin secreto o sin código no hay periodo")
    void findsNoTimeStepWithoutSecretOrCode() {
        TotpService fixedClock = serviceAtStep(CURRENT_STEP);

        assertTrue(fixedClock.findMatchingTimeStep(null, "123456").isEmpty());
        assertTrue(fixedClock.findMatchingTimeStep(SECRET, null).isEmpty());
        assertTrue(fixedClock.findMatchingTimeStep(SECRET, "invalid").isEmpty());
    }

    private TotpService serviceAtStep(long step) {
        return new TotpService(() -> step * TIME_PERIOD_SECONDS + 5);
    }

    private String codeForStep(long step) throws Exception {
        return new DefaultCodeGenerator(HashingAlgorithm.SHA1).generate(SECRET, step);
    }

    @Test
    void generatesEmptyListWhenCountIsZero() {
        assertTrue(service.generateBackupCodes(0).isEmpty());
    }

    @Test
    void generatesEmptyListWhenCountIsNegative() {
        assertTrue(service.generateBackupCodes(-1).isEmpty());
    }

    @Test
    void generatesRequestedNumberOfBackupCodesForDifferentCounts() {
        assertEquals(1, service.generateBackupCodes(1).size());
        assertEquals(3, service.generateBackupCodes(3).size());
    }
}