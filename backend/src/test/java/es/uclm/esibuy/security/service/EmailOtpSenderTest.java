package es.uclm.esibuy.security.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class EmailOtpSenderTest {

    @Test
    void doesNotLogOtpCodeWhenLoggingIsDisabled(CapturedOutput output) {
        new EmailOtpSender(false).sendOtp("user@example.com", "123456");

        assertThat(output).doesNotContain("123456");
    }

    @Test
    void logsOtpCodeOnlyWhenLoggingIsEnabled(CapturedOutput output) {
        new EmailOtpSender(true).sendOtp("user@example.com", "123456");

        assertThat(output).contains("123456");
    }
}