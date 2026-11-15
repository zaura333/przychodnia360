package pl.przychodnia360.tenant.domain;

/** Placówka widoczna publicznie (wybór placówki przy logowaniu i rejestracji). */
public record Facility(String slug, String name) {
}
