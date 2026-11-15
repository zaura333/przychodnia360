package pl.przychodnia360.identity.application;

/** Wysyłka wiadomości e-mail: SMTP (Mailpit lokalnie), SES w AWS dochodzi z infrastrukturą. */
public interface MailSender {

    /** Wiadomość tekstowa. Treść może zawierać jednorazowy link, więc nie jest logowana. */
    record MailMessage(String to, String subject, String body) {
    }

    void send(MailMessage message);
}
