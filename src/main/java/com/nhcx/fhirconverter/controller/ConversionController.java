package com.nhcx.fhirconverter.controller;

import com.nhcx.fhirconverter.model.ConversionRecord;
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
 * POST /api/convert/coverage → Converts HL7 to FHIR CoverageEligibilityRequest
 * GET /api/convert/history → Returns all past conversion records
 *
 * HOW IT WORKS:
 * 1. Client sends an HTTP POST with the raw HL7 message in the body
 * 2. Controller receives the request and delegates to ConversionService
 * 3. ConversionService runs the full pipeline (parse → map → build → save)
 * 4. Controller returns the FHIR JSON response to the client
 */
@RestController // Marks this as a REST controller (returns JSON, not HTML)
@RequestMapping("/api/convert") // Base path for all endpoints in this controller
public class ConversionController {

    private final ConversionService conversionService;

    /**
     * Constructor injection: Spring provides the ConversionService automatically.
     */
    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * POST /api/convert/coverage
     *
     * Accepts a raw HL7 message as plain text and returns a FHIR Bundle JSON.
     *
     * Example usage with curl:
     * curl -X POST http://localhost:8080/api/convert/coverage \
     * -H "Content-Type: text/plain" \
     * -d "PID|1||ABHA123||Sharma^Rahul||19900415|M"
     *
     * @param rawHl7 The raw HL7 message in the request body
     * @return FHIR Bundle JSON (200 OK) or error message (500)
     */
    @PostMapping(value = "/coverage", consumes = MediaType.TEXT_PLAIN_VALUE, // Accepts plain text input
            produces = MediaType.APPLICATION_JSON_VALUE // Returns JSON output
    )
    public ResponseEntity<String> convertCoverage(@RequestBody String rawHl7) {
        try {
            // Delegate to service layer for the actual conversion
            String fhirJson = conversionService.convertCoverage(rawHl7);

            // Return the FHIR JSON with 200 OK status
            return ResponseEntity.ok(fhirJson);

        } catch (Exception e) {
            // Return an error response with 500 status
            String errorJson = String.format(
                    "{\"error\": \"Conversion failed\", \"message\": \"%s\"}",
                    e.getMessage().replace("\"", "'"));
            return ResponseEntity
                    .internalServerError()
                    .body(errorJson);
        }
    }

    /**
     * GET /api/convert/history
     *
     * Returns a list of all past conversion records from the database.
     * Useful for debugging and auditing conversion attempts.
     *
     * Example usage:
     * curl http://localhost:8080/api/convert/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<ConversionRecord>> getHistory() {
        List<ConversionRecord> records = conversionService.getHistory();
        return ResponseEntity.ok(records);
    }
}
