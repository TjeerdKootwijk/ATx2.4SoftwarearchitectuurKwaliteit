package com.example.atx24softwarearchitectuurkwaliteit.fhir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests voor het ValidationResult value object.
 * Verifieert dat het object correct aangemaakt wordt via de factory-methoden
 * en dat de errors-lijst immutable is (defensieve kopie).
 */
class ValidationResultTest {

    @Test
    void ok_isValid_true() {
        ValidationResult result = ValidationResult.ok();

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void failed_isValid_false() {
        ValidationResult result = ValidationResult.failed(List.of("Fout 1", "Fout 2"));

        assertThat(result.isValid()).isFalse();
    }

    @Test
    void failed_bevatAlleOpgegevenFouten() {
        List<String> fouten = List.of("Appointment.id is required", "Appointment.status is required");

        ValidationResult result = ValidationResult.failed(fouten);

        assertThat(result.getErrors()).containsExactlyElementsOf(fouten);
    }

    @Test
    void failed_errorsLijstIsImmutable() {
        ValidationResult result = ValidationResult.failed(List.of("Fout"));

        assertThat(result.getErrors())
                .as("Errors lijst mag niet aanpasbaar zijn")
                .isUnmodifiable();
    }

    @Test
    void toString_geldig_bevatValidLabel() {
        assertThat(ValidationResult.ok().toString()).contains("valid");
    }

    @Test
    void toString_ongeldig_bevatFouten() {
        ValidationResult result = ValidationResult.failed(List.of("Ontbrekend veld"));

        assertThat(result.toString()).contains("Ontbrekend veld");
    }
}
