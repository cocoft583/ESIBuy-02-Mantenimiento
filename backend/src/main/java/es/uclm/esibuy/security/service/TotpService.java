package es.uclm.esibuy.security.service;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.TimeProviderException;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

@Service
public class TotpService {

    private static final int TIME_PERIOD_SECONDS = 30;
    private static final int ALLOWED_TIME_PERIOD_DISCREPANCY = 1; // Ventana de tolerancia ±1 paso (30s)

    private final DefaultSecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final DefaultCodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
    private final DefaultCodeVerifier codeVerifier;
    private final TimeProvider timeProvider;

    @Autowired
    public TotpService() {
        this(new SystemTimeProvider());
    }

    TotpService(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        this.codeVerifier.setAllowedTimePeriodDiscrepancy(ALLOWED_TIME_PERIOD_DISCREPANCY);
    }

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null) {
            return false;
        }
        return codeVerifier.isValidCode(secret, code.trim());
    }

    /**
     * Devuelve el periodo de 30 segundos al que corresponde el código, dentro de la ventana de
     * tolerancia, o vacío si no coincide con ninguno. Permite saber si un código ya se usó.
     */
    public OptionalLong findMatchingTimeStep(String secret, String code) {
        if (secret == null || code == null) {
            return OptionalLong.empty();
        }

        try {
            long currentStep = Math.floorDiv(timeProvider.getTime(), TIME_PERIOD_SECONDS);
            String candidate = code.trim();

            for (long step = currentStep + ALLOWED_TIME_PERIOD_DISCREPANCY;
                    step >= currentStep - ALLOWED_TIME_PERIOD_DISCREPANCY; step--) {
                if (codeMatches(secret, candidate, step)) {
                    return OptionalLong.of(step);
                }
            }
        } catch (CodeGenerationException | TimeProviderException exception) {
            return OptionalLong.empty();
        }
        return OptionalLong.empty();
    }

    private boolean codeMatches(String secret, String code, long step) throws CodeGenerationException {
        byte[] expected = codeGenerator.generate(secret, step).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, code.getBytes(StandardCharsets.UTF_8));
    }

    public List<String> generateBackupCodes(int count) {
        SecureRandom random = new SecureRandom();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int code = 10000000 + random.nextInt(90000000);
            codes.add(String.valueOf(code));
        }
        return codes;
    }
}