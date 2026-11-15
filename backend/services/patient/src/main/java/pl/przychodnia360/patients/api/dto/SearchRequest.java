package pl.przychodnia360.patients.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchRequest(@NotBlank @Size(max = 200) String query, Integer page, Integer size) {
}
