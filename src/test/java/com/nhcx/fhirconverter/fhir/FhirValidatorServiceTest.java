package com.nhcx.fhirconverter.fhir;

import com.nhcx.fhirconverter.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FhirValidatorServiceTest {

    private FhirValidatorService validatorService;

    @BeforeEach
    void setUp() {
        validatorService = new FhirValidatorService();
    }

    @Test
    void validateValidFhirJson_success() {
        // A minimal valid Patient resource
        String validJson = "{\n" +
                "  \"resourceType\": \"Patient\",\n" +
                "  \"text\": {\n" +
                "    \"status\": \"generated\",\n" +
                "    \"div\": \"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\">Patient</div>\"\n" +
                "  }\n" +
                "}";

        assertDoesNotThrow(() -> validatorService.validate(validJson));
    }

    @Test
    void validateInvalidFhirJson_throwsValidationException() {
        // An invalid Patient resource (e.g. invalid status)
        String invalidJson = "{\n" +
                "  \"resourceType\": \"Patient\",\n" +
                "  \"gender\": \"invalid-gender\"\n" +
                "}";

        ValidationException exception = assertThrows(ValidationException.class,
                () -> validatorService.validate(invalidJson));

        assertTrue(exception.getMessage().contains("FHIR Validation failed"));
    }
}
