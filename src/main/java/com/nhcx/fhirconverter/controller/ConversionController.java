package com.nhcx.fhirconverter.controller;

import com.nhcx.fhirconverter.model.ConversionRecord;
import com.nhcx.fhirconverter.model.ErrorLog;
import com.nhcx.fhirconverter.service.ConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller — exposes HTTP endpoints for the FHIR converter.
 *
 * ENDPOINTS:
 * ==========
 * POST /api/convert/coverage → Converts HL7 to FHIR (text/plain input)
 * POST /api/convert/json → Converts JSON to FHIR (application/json input)
 * POST /api/convert/csv → Converts CSV to FHIR (text/plain input)
 * GET /api/convert/history → Returns all past conversion records
 */
@RestController
@RequestMapping("/api/convert")
public class ConversionController {

    private final ConversionService conversionService;

    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    // ==================== HL7 Input ====================

    /**
     * POST /api/convert/coverage
     *
     * Accepts a raw HL7 message as plain text and returns a FHIR Bundle JSON.
     */
    @PostMapping(value = "/coverage", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertCoverage(@RequestBody String rawHl7) {
        try {
            String normalizedHl7 = rawHl7.replaceAll("\\r\\n|\\n", "\r");
            String fhirJson = conversionService.convertCoverage(normalizedHl7);
            return ResponseEntity.ok(fhirJson);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    // ==================== JSON Input ====================

    /**
     * POST /api/convert/json
     *
     * Accepts a JSON object and returns a FHIR Bundle JSON.
     *
     * Example body:
     * {
     * "abhaId": "ABHA123",
     * "familyName": "Sharma",
     * "givenName": "Rahul",
     * "dateOfBirth": "19900415",
     * "gender": "M",
     * "diagnoses": [{ "code": "COVID19", "description": "COVID-19" }]
     * }
     */
    @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertJson(@RequestBody String jsonInput) {
        try {
            String fhirJson = conversionService.convertFromJson(jsonInput);
            return ResponseEntity.ok(fhirJson);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    // ==================== CSV Input ====================

    /**
     * POST /api/convert/csv
     *
     * Accepts CSV text and returns a FHIR Bundle JSON.
     *
     * Expected format (first row = header):
     * abhaId,familyName,givenName,dateOfBirth,gender,diagCode,diagDesc
     * ABHA123,Sharma,Rahul,19900415,M,COVID19,COVID-19
     */
    @PostMapping(value = "/csv", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertCsv(@RequestBody String csvInput) {
        try {
            String fhirJson = conversionService.convertFromCsv(csvInput);
            return ResponseEntity.ok(fhirJson);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    // ==================== History ====================

    /**
     * GET /api/convert/history
     *
     * Returns a list of all past conversion records from the database.
     */
    @GetMapping("/history")
    public ResponseEntity<List<ConversionRecord>> getHistory() {
        List<ConversionRecord> records = conversionService.getHistory();
        return ResponseEntity.ok(records);
    }

    /**
     * GET /api/convert/errors
     *
     * Returns a list of all error logs from the database (Dead Letter Queue).
     */
    @GetMapping("/errors")
    public ResponseEntity<List<ErrorLog>> getErrors() {
        List<ErrorLog> errors = conversionService.getErrors();
        return ResponseEntity.ok(errors);
    }

    // ==================== Helpers ====================

    private ResponseEntity<String> errorResponse(Exception e) {
        String errorJson = String.format(
                "{\"error\": \"Conversion failed\", \"message\": \"%s\"}",
                e.getMessage().replace("\"", "'"));
        return ResponseEntity.internalServerError().body(errorJson);
    }
}
