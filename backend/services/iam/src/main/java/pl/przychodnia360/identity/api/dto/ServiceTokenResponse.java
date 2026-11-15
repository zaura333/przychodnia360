package pl.przychodnia360.identity.api.dto;

public record ServiceTokenResponse(String accessToken, long expiresIn) {
}
