package pl.przychodnia360.patients.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ContactRequest(@Email @Size(max = 254) String contactEmail) {
}
