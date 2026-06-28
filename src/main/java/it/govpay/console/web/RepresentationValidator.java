package it.govpay.console.web;

import java.util.Set;

import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Applica la Bean Validation (vincoli OpenAPI: {@code @Pattern}, {@code @Size},
 * {@code @NotNull}, ...) su una rappresentazione costruita a runtime, p.es. il
 * risultato di un PATCH ricomposto via JSON Patch. Le create/replace sono gia'
 * validate al boundary del controller ({@code @Valid}); il PATCH no, perche' il
 * documento risultante e' deserializzato manualmente, quindi va rivalidato qui
 * "come una replace".
 */
@Component
public class RepresentationValidator {

    private final Validator validator;

    public RepresentationValidator(Validator validator) {
        this.validator = validator;
    }

    public <T> void validate(T representation) {
        Set<ConstraintViolation<T>> violations = validator.validate(representation);
        if (violations.isEmpty()) {
            return;
        }
        ConstraintViolation<T> first = violations.iterator().next();
        String field = first.getPropertyPath().toString();
        String where = field.isBlank() ? "" : " sul campo '" + field + "'";
        throw new BadRequestException(
                "La rappresentazione risultante dal PATCH viola un vincolo" + where + ": " + first.getMessage() + ".");
    }
}
