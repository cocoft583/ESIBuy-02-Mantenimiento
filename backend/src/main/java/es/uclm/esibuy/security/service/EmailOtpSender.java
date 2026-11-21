package es.uclm.esibuy.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.common.exception.OtpDeliveryException;

@Service
public class EmailOtpSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailOtpSender.class);
    private static final String SUBJECT = "Tu código de verificación | ESIBuy";

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final long validityMinutes;
    private final boolean logOtpCode;

    public EmailOtpSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:no-reply@esibuy.com}") String fromAddress,
            @Value("${app.security.email-otp.validity-seconds}") long validitySeconds,
            @Value("${app.security.email-otp.log-code:false}") boolean logOtpCode) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.validityMinutes = validitySeconds / 60;
        this.logOtpCode = logOtpCode;
    }

    public void sendOtp(String email, String code) {
        if (logOtpCode) {
            LOGGER.info("[DEV ONLY] Código OTP generado: {} para el usuario: {}", code, email);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("ESIBuy <" + fromAddress + ">");
        message.setTo(email);
        message.setSubject(SUBJECT);
        message.setText(buildText(code));

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            if (logOtpCode) {
                LOGGER.warn("[DEV ONLY] No se pudo enviar el correo; usa el código del log");
                return;
            }
            throw new OtpDeliveryException(exception);
        }
    }

    private String buildText(String code) {
        return """
                Hola,

                Tu código para entrar en ESIBuy es:

                %s

                Caduca en %d minutos y solo se puede usar una vez.

                Si no has intentado iniciar sesión, cambia tu contraseña.

                ESIBuy
                Este es un mensaje automático. No respondas a este correo.
                """.formatted(code, validityMinutes);
    }
}