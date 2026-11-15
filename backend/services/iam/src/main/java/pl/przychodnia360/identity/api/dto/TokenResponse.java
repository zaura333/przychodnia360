package pl.przychodnia360.identity.api.dto;

/** Token dostępu; token odświeżania jest wyłącznie w ciasteczku HttpOnly. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
