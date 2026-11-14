package pl.przychodnia360;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pl.przychodnia360.identity.application.MailSender;

/** Zamiast SMTP w testach: zapamiętuje wiadomości, żeby testy mogły odczytać jednorazowe linki. */
public class RecordingMailSender implements MailSender {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9._-]+)");

    private final List<MailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(MailMessage message) {
        sent.add(message);
    }

    public List<MailMessage> sentTo(String email) {
        return sent.stream().filter(m -> m.to().equalsIgnoreCase(email)).toList();
    }

    /** Token z ostatniej wiadomości do adresu (link ustawienia hasła). */
    public String lastTokenFor(String email) {
        List<MailMessage> messages = sentTo(email);
        if (messages.isEmpty()) {
            throw new IllegalStateException("Brak wiadomości do " + email);
        }
        Matcher m = TOKEN.matcher(messages.getLast().body());
        if (!m.find()) {
            throw new IllegalStateException("Brak tokenu w wiadomości do " + email);
        }
        return m.group(1);
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }
}
