package pl.przychodnia360.identity.domain;

import java.util.List;
import java.util.Set;
import pl.przychodnia360.shared.ValidationException;
import pl.przychodnia360.shared.ValidationException.FieldError;

/** Polityka haseł (plan 1a, 8): długość 12-128, bez wymogu klas znaków, odrzucenie najpopularniejszych haseł. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    /** Niewielka lista najpopularniejszych haseł o długości co najmniej 12 znaków (porównanie bez wielkości liter). */
    private static final Set<String> COMMON = Set.of(
            "123456789012", "1234567890123", "password1234", "password12345", "passw0rd1234", "qwertyuiop12",
            "qwertyuiopas", "qwerty123456", "1q2w3e4r5t6y", "abcdefghijkl", "iloveyou1234", "administrator",
            "administrator1", "welcome12345", "letmein12345", "changeme1234", "przychodnia360", "przychodnia1234",
            "haslo1234567", "mojehaslo1234", "zaq12wsxcde3", "000000000000", "111111111111", "aaaaaaaaaaaa");

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        String message = null;
        if (password == null || password.length() < MIN_LENGTH) {
            message = "Hasło musi mieć co najmniej " + MIN_LENGTH + " znaków.";
        } else if (password.length() > MAX_LENGTH) {
            message = "Hasło może mieć najwyżej " + MAX_LENGTH + " znaków.";
        } else if (COMMON.contains(password.toLowerCase()) || password.chars().distinct().count() < 4) {
            message = "To hasło jest zbyt popularne lub zbyt proste.";
        }
        if (message != null) {
            throw new ValidationException(message, List.of(new FieldError("password", message)));
        }
    }
}
