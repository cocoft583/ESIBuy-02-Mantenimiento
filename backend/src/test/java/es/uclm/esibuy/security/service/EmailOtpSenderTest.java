package es.uclm.esibuy.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import es.uclm.esibuy.common.exception.OtpDeliveryException;

@ExtendWith(OutputCaptureExtension.class)
class EmailOtpSenderTest {

    private static final String FROM = "no-reply@esibuy.com";
    private static final long VALIDITY_SECONDS = 600;

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    void sendsTheCodeToTheUserEmail() {
        sender(false).sendOtp("user@example.com", "123456");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getFrom()).contains(FROM);
        assertThat(message.getText()).contains("123456");
    }

    @Test
    void subjectDoesNotShowTheCodeAndTextSaysHowLongItLasts() {
        sender(false).sendOtp("user@example.com", "123456");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getSubject()).isNotBlank().doesNotContain("123456");
        assertThat(message.getText()).contains("10 minutos");
    }

    @Test
    void failsWithAGenericErrorWhenTheEmailCannotBeSent() {
        doThrow(new MailSendException("smtp.example.com refused the connection"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender(false).sendOtp("user@example.com", "123456"))
                .isInstanceOf(OtpDeliveryException.class)
                .hasMessageNotContaining("smtp");
    }

    @Test
    void devProfileKeepsTheLoggedCodeUsableWhenTheEmailCannotBeSent(CapturedOutput output) {
        doThrow(new MailSendException("smtp.example.com refused the connection"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        sender(true).sendOtp("user@example.com", "123456");

        assertThat(output).contains("123456");
    }

    @Test
    void doesNotLogOtpCodeWhenLoggingIsDisabled(CapturedOutput output) {
        sender(false).sendOtp("user@example.com", "123456");

        assertThat(output).doesNotContain("123456");
    }

    @Test
    void logsOtpCodeOnlyWhenLoggingIsEnabled(CapturedOutput output) {
        sender(true).sendOtp("user@example.com", "123456");

        assertThat(output).contains("123456");
    }

    private EmailOtpSender sender(boolean logOtpCode) {
        return new EmailOtpSender(mailSender, FROM, VALIDITY_SECONDS, logOtpCode);
    }

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}