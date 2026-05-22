package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import java.util.List;

/**
 * Immutable resultaat van FHIR-validatie (NFR 6 — berichtontvangst en validatie / ACK-patroon).
 * Bevat of het bericht geldig is en een lijst van eventuele foutmeldingen.
 */
public class ValidationResult {

    private final boolean valid;
    private final List<String> errors;

    public ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = List.copyOf(errors);
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult failed(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    @Override
    public String toString() {
        return valid ? "ValidationResult{valid}" : "ValidationResult{invalid, errors=" + errors + "}";
    }
}
