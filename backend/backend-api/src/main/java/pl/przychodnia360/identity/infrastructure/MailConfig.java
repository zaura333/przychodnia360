package pl.przychodnia360.identity.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import pl.przychodnia360.identity.application.MailSender;

@Configuration
class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    /** SMTP, gdy skonfigurowano spring.mail.host (Mailpit lokalnie). */
    @Bean
    @ConditionalOnProperty("spring.mail.host")
    MailSender smtpMailSender(JavaMailSender javaMailSender, @org.springframework.beans.factory.annotation.Value(
            "${app.mail.from:no-reply@przychodnia360.example}") String from) {
        return message -> {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(message.to());
            mail.setSubject(message.subject());
            mail.setText(message.body());
            javaMailSender.send(mail);
        };
    }

    /** Bez konfiguracji poczty: tylko ślad w logu (bez adresu i treści, bo zawiera jednorazowe linki). */
    @Bean
    @ConditionalOnMissingBean(MailSender.class)
    MailSender loggingMailSender() {
        return message -> log.info("Wiadomość e-mail nie została wysłana (brak konfiguracji SMTP): temat={}",
                message.subject());
    }
}
