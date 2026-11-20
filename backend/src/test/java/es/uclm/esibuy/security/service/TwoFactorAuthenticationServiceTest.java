package es.uclm.esibuy.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.service.UserService;

class TwoFactorAuthenticationServiceTest {

    private final UserService userService = mock(UserService.class);
    private final TotpService totpService = mock(TotpService.class);
    private final EncryptionService encryptionService = mock(EncryptionService.class);
    // BCrypt real con coste bajo: los tests comprueban de verdad que se guarda el hash
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private final TwoFactorAuthenticationService service = new TwoFactorAuthenticationService(
            userService, totpService, encryptionService, passwordEncoder);

    private User user;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        when(userService.findById("user-1")).thenReturn(user);
    }

    @Test
    void setupGeneratesEncryptsAndPersistsSecret() {
        when(user.isTotpEnabled()).thenReturn(false);
        when(totpService.generateSecret()).thenReturn("raw-secret");
        when(encryptionService.encrypt("raw-secret")).thenReturn("encrypted-secret");

        TwoFactorAuthenticationService.SetupResult result = service.setup("user-1", null);

        assertEquals(TwoFactorAuthenticationService.Status.SUCCESS, result.status());
        assertEquals("raw-secret", result.secret());
        verify(user).setTotpSecret("encrypted-secret");
        verify(userService).save(user);
    }

    // HU08: con el 2FA activo, cambiar la app de autenticación pide un código válido
    @Test
    void rejectsReconfigurationWithoutCodeWhenTwoFactorIsEnabled() {
        when(user.isTotpEnabled()).thenReturn(true);

        TwoFactorAuthenticationService.SetupResult nullCodeResult = service.setup("user-1", null);
        TwoFactorAuthenticationService.SetupResult blankCodeResult = service.setup("user-1", "  ");

        assertEquals(TwoFactorAuthenticationService.Status.INVALID_CODE, nullCodeResult.status());
        assertEquals(TwoFactorAuthenticationService.Status.INVALID_CODE, blankCodeResult.status());
        assertNull(nullCodeResult.secret());
        assertNull(blankCodeResult.secret());

        verify(totpService, never()).generateSecret();
        verify(user, never()).setTotpSecret(anyString());
        verify(userService, never()).save(user);
    }

    @Test
    void rejectsReconfigurationWithInvalidCodeWhenTwoFactorIsEnabled() {
        when(user.isTotpEnabled()).thenReturn(true);
        prepareSecret("raw-secret");
        when(totpService.verifyCode("raw-secret", "000000")).thenReturn(false);

        TwoFactorAuthenticationService.SetupResult result = service.setup("user-1", "000000");

        assertEquals(TwoFactorAuthenticationService.Status.INVALID_CODE, result.status());
        assertNull(result.secret());

        verify(totpService, never()).generateSecret();
        verify(user, never()).setTotpSecret(anyString());
        verify(userService, never()).save(user);
    }

    @Test
    void reconfiguresWithValidCodeWhenTwoFactorIsEnabled() {
        when(user.isTotpEnabled()).thenReturn(true);
        prepareSecret("old-secret");
        when(totpService.verifyCode("old-secret", "123456")).thenReturn(true);
        when(totpService.generateSecret()).thenReturn("new-secret");
        when(encryptionService.encrypt("new-secret")).thenReturn("encrypted-new-secret");

        TwoFactorAuthenticationService.SetupResult result = service.setup("user-1", "123456");

        assertEquals(TwoFactorAuthenticationService.Status.SUCCESS, result.status());
        assertEquals("new-secret", result.secret());
        verify(user).setTotpSecret("encrypted-new-secret");
        verify(userService).save(user);
    }

    @Test
    void consumesAndPersistsBackupCodeAfterValidFallback() {
        List<String> backupCodes = hashed("12345678");
        prepareSecret("raw-secret");
        when(totpService.verifyCode("raw-secret", "12345678")).thenReturn(false);
        when(user.getBackupCodeHashes()).thenReturn(backupCodes);

        assertTrue(service.verifyLoginCode(user, " 12345678 "));

        assertTrue(backupCodes.isEmpty());
        verify(userService).save(user);
    }

    @Test
    void doesNotConsumeBackupCodeWhenTotpIsValid() {
        List<String> backupCodes = hashed("12345678");
        prepareSecret("raw-secret");
        when(totpService.findMatchingTimeStep("raw-secret", "654321")).thenReturn(OptionalLong.of(100));
        when(user.getBackupCodeHashes()).thenReturn(backupCodes);

        assertTrue(service.verifyLoginCode(user, "654321"));

        assertFalse(backupCodes.isEmpty());
        verify(user).setTotpLastUsedStep(100);
        verify(userService).save(user);
    }

    @Test
    void rejectsNullOrBlankLoginCode() {
        assertFalse(service.verifyLoginCode(user, null));
        assertFalse(service.verifyLoginCode(user, "   "));

        verify(encryptionService, never()).decrypt(org.mockito.ArgumentMatchers.any());
        verify(userService, never()).save(user);
    }

    @Test
    void rejectsUnknownBackupCodeWithoutPersisting() {
        prepareSecret("raw-secret");
        when(totpService.verifyCode("raw-secret", "000000")).thenReturn(false);
        when(user.getBackupCodeHashes()).thenReturn(hashed("12345678"));

        assertFalse(service.verifyLoginCode(user, "000000"));

        verify(userService, never()).save(user);
    }

    @Test
    void rejectsBackupCodeWhenBackupCodeListIsNull() {
        prepareSecret("raw-secret");
        when(totpService.verifyCode("raw-secret", "000000")).thenReturn(false);
        when(user.getBackupCodeHashes()).thenReturn(null);

        assertFalse(service.verifyLoginCode(user, "000000"));

        verify(userService, never()).save(user);
    }

    @Test
    void enablesTwoFactorAndGeneratesEightBackupCodes() {
        prepareSecret("raw-secret");
        when(totpService.verifyCode("raw-secret", "123456")).thenReturn(true);

        List<String> generatedCodes = List.of(
                "11111111", "22222222", "33333333", "44444444",
                "55555555", "66666666", "77777777", "88888888");

        when(totpService.generateBackupCodes(8)).thenReturn(generatedCodes);

        TwoFactorAuthenticationService.EnableResult result = service.enable("user-1", "123456");

        assertTrue(result.enabled());
        assertEquals(generatedCodes, result.backupCodes());

        verify(user).setTotpEnabled(true);
        assertStoresOnlyHashes(generatedCodes);
        verify(userService).save(user);
    }

    @Test
    void doesNotEnableTwoFactorWhenCodeIsInvalid() {
        prepareSecret("raw-secret");
        when(totpService.verifyCode("raw-secret", "000000")).thenReturn(false);

        TwoFactorAuthenticationService.EnableResult result = service.enable("user-1", "000000");

        assertFalse(result.enabled());
        assertTrue(result.backupCodes().isEmpty());

        verify(totpService, never()).generateBackupCodes(8);
        verify(userService, never()).save(user);
    }

    @Test
    void doesNotEnableTwoFactorWhenCodeIsBlank() {
        TwoFactorAuthenticationService.EnableResult result = service.enable("user-1", " ");

        assertFalse(result.enabled());
        assertTrue(result.backupCodes().isEmpty());

        verify(encryptionService, never()).decrypt(org.mockito.ArgumentMatchers.any());
        verify(userService, never()).save(user);
    }

    @Test
    void doesNotEnableTwoFactorWhenSecretIsNull() {
        when(user.getTotpSecret()).thenReturn(null);

        TwoFactorAuthenticationService.EnableResult result = service.enable("user-1", "123456");

        assertFalse(result.enabled());
        verify(encryptionService, never()).decrypt(org.mockito.ArgumentMatchers.any());
        verify(userService, never()).save(user);
    }

    @Test
    void returnsDisabledWhenRegeneratingCodesWithoutTwoFactor() {
        when(user.isTotpEnabled()).thenReturn(false);

        TwoFactorAuthenticationService.BackupCodesResult result = service.regenerateBackupCodes("user-1", "123456");

        assertEquals(
                TwoFactorAuthenticationService.Status.TWO_FACTOR_DISABLED,
                result.status());
        assertTrue(result.backupCodes().isEmpty());

        verify(totpService, never()).generateBackupCodes(8);
        verify(userService, never()).save(user);
    }

    @Test
    void regeneratesBackupCodesWhenCodeIsValid() {
        when(user.isTotpEnabled()).thenReturn(true);
        prepareSecret("raw-secret");
        when(totpService.verifyCode("raw-secret", "123456")).thenReturn(true);

        List<String> generatedCodes = List.of(
                "11111111", "22222222", "33333333", "44444444",
                "55555555", "66666666", "77777777", "88888888");
        when(totpService.generateBackupCodes(8)).thenReturn(generatedCodes);

        TwoFactorAuthenticationService.BackupCodesResult result = service.regenerateBackupCodes("user-1", "123456");

        assertEquals(TwoFactorAuthenticationService.Status.SUCCESS, result.status());
        assertEquals(generatedCodes, result.backupCodes());

        assertStoresOnlyHashes(generatedCodes);
        verify(userService).save(user);
    }

    // HU08: antes se regeneraban sin código si no llegaba ninguno; ahora el código es obligatorio
    @Test
    void rejectsRegenerationWhenCodeIsNullOrBlank() {
        when(user.isTotpEnabled()).thenReturn(true);

        TwoFactorAuthenticationService.BackupCodesResult nullCodeResult = service.regenerateBackupCodes("user-1", null);
        TwoFactorAuthenticationService.BackupCodesResult blankCodeResult = service.regenerateBackupCodes("user-1",
                "  ");

        assertEquals(TwoFactorAuthenticationService.Status.INVALID_CODE, nullCodeResult.status());
        assertEquals(TwoFactorAuthenticationService.Status.INVALID_CODE, blankCodeResult.status());
        assertTrue(nullCodeResult.backupCodes().isEmpty());
        assertTrue(blankCodeResult.backupCodes().isEmpty());

        verify(totpService, never()).generateBackupCodes(8);
        verify(userService, never()).save(user);
    }

    @Test
    void rejectsRegenerationWhenProvidedCodeIsInvalid() {
        when(user.isTotpEnabled()).thenReturn(true);
        prepareSecret("raw-secret");
        when(totpService.verifyCode("raw-secret", "000000")).thenReturn(false);

        TwoFactorAuthenticationService.BackupCodesResult result = service.regenerateBackupCodes("user-1", "000000");

        assertEquals(
                TwoFactorAuthenticationService.Status.INVALID_CODE,
                result.status());
        assertTrue(result.backupCodes().isEmpty());

        verify(totpService, never()).generateBackupCodes(8);
        verify(userService, never()).save(user);
    }

    @Test
    void returnsDisabledWhenDisablingAlreadyDisabledTwoFactor() {
        when(user.isTotpEnabled()).thenReturn(false);

        TwoFactorAuthenticationService.Status result = service.disable("user-1", "123456");

        assertEquals(
                TwoFactorAuthenticationService.Status.TWO_FACTOR_DISABLED,
                result);

        verify(userService, never()).save(user);
    }

    @Test
    void rejectsDisablingWhenCodeIsInvalid() {
        when(user.isTotpEnabled()).thenReturn(true);
        prepareSecret("raw-secret");
        when(totpService.verifyCode("raw-secret", "000000")).thenReturn(false);

        TwoFactorAuthenticationService.Status result = service.disable("user-1", "000000");

        assertEquals(
                TwoFactorAuthenticationService.Status.INVALID_CODE,
                result);

        verify(user, never()).setTotpEnabled(false);
        verify(userService, never()).save(user);
    }

    @Test
    void disablesTwoFactorAndClearsSecretAndBackupCodes() {
        when(user.isTotpEnabled()).thenReturn(true);
        prepareSecret("raw-secret");
        when(totpService.verifyCode("raw-secret", "123456")).thenReturn(true);

        TwoFactorAuthenticationService.Status result = service.disable("user-1", "123456");

        assertEquals(TwoFactorAuthenticationService.Status.SUCCESS, result);

        verify(user).setTotpEnabled(false);
        verify(user).setTotpSecret(null);
        verify(user).setBackupCodeHashes(anyList());
        verify(userService).save(user);
    }

    @Test
    void rejectsDisablingWithBlankCode() {
        when(user.isTotpEnabled()).thenReturn(true);

        TwoFactorAuthenticationService.Status result = service.disable("user-1", " ");

        assertEquals(
                TwoFactorAuthenticationService.Status.INVALID_CODE,
                result);

        verify(encryptionService, never()).decrypt(org.mockito.ArgumentMatchers.any());
        verify(userService, never()).save(user);
    }

    // HU09: un código de respaldo sirve una sola vez
    @Test
    void rejectsBackupCodeThatWasAlreadyUsed() {
        List<String> backupCodes = hashed("12345678", "87654321");
        prepareSecret("raw-secret");
        when(totpService.verifyCode("raw-secret", "12345678")).thenReturn(false);
        when(user.getBackupCodeHashes()).thenReturn(backupCodes);

        assertTrue(service.verifyLoginCode(user, "12345678"));
        assertFalse(service.verifyLoginCode(user, "12345678"));

        assertEquals(1, backupCodes.size());
        assertTrue(passwordEncoder.matches("87654321", backupCodes.get(0)));
    }

    private List<String> hashed(String... rawCodes) {
        List<String> hashes = new ArrayList<>();
        for (String rawCode : rawCodes) {
            hashes.add(passwordEncoder.encode(rawCode));
        }
        return hashes;
    }

    @SuppressWarnings("unchecked")
    private void assertStoresOnlyHashes(List<String> rawCodes) {
        ArgumentCaptor<List<String>> stored = ArgumentCaptor.forClass(List.class);
        verify(user).setBackupCodeHashes(stored.capture());
        assertEquals(rawCodes.size(), stored.getValue().size());
        for (int i = 0; i < rawCodes.size(); i++) {
            assertNotEquals(rawCodes.get(i), stored.getValue().get(i));
            assertTrue(passwordEncoder.matches(rawCodes.get(i), stored.getValue().get(i)));
        }
    }

    private void prepareSecret(String rawSecret) {
        when(user.getTotpSecret()).thenReturn("encrypted-secret");
        when(encryptionService.decrypt("encrypted-secret")).thenReturn(rawSecret);
    }
}