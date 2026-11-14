package pl.przychodnia360.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DomainExceptionsTest {

    @Test
    void problemTypesAreStableSlugs() {
        assertThat(new NotFoundException("x").problemType()).isEqualTo("not-found");
        assertThat(new ConflictException("x").problemType()).isEqualTo("conflict");
        assertThat(new ConflictException("slot-taken", "x").problemType()).isEqualTo("slot-taken");
        assertThat(new ForbiddenException("x").problemType()).isEqualTo("forbidden");
        assertThat(new ValidationException("x").problemType()).isEqualTo("validation");
    }

    @Test
    void validationKeepsFieldErrorsImmutably() {
        var errors = new ArrayList<>(List.of(new ValidationException.FieldError("email", "zły format")));
        var ex = new ValidationException("x", errors);
        errors.clear();
        assertThat(ex.errors()).hasSize(1);
    }
}
