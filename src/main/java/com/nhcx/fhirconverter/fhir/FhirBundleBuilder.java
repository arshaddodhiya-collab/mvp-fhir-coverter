package com.nhcx.fhirconverter.fhir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nhcx.fhirconverter.mapping.MappingProfile;
import com.nhcx.fhirconverter.model.Hl7Data;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Builds a FHIR R4 Bundle JSON containing:
 * 1. Patient resource
 * 2. Coverage resource
 * 3. CoverageEligibilityRequest resource
 *
 * HOW FHIR JSON IS CONSTRUCTED:
 * =============================
 * Instead of using a full FHIR library (like HAPI FHIR), we manually build
 * the JSON structure using nested Java Maps and Lists. This is simpler for
 * an MVP and helps you understand the FHIR resource structure.
 *
 * The final structure looks like:
 * {
 * "resourceType": "Bundle",
 * "type": "collection",
 * "entry": [
 * { "resource": { ... Patient ... } },
 * { "resource": { ... Coverage ... } },
 * { "resource": { ... CoverageEligibilityRequest ... } }
 * ]
 * }
 *
 * HOW MAPPING IS APPLIED:
 * =======================
 * The MappingProfile tells us WHICH HL7 fields map to WHICH FHIR fields.
 * For example, YAML says:
 * field 3 → Patient.identifier[abha].value
 * field 5.1 → Patient.name.family
 *
 * We read these mappings to understand the INTENDED structure, then build
 * the JSON accordingly. In this MVP, the builder "knows" the mappings
 * conceptually — the YAML serves as the configuration/documentation
 * of the mapping rules.
 */
@Component
public class FhirBundleBuilder {

    private final ObjectMapper objectMapper;

    public FhirBundleBuilder() {
        this.objectMapper = new ObjectMapper();
        // Pretty-print the JSON output for readability
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Builds a FHIR Bundle JSON string from parsed HL7 data.
     *
     * @param data    The parsed patient data from the HL7 message
     * @param profile The mapping profile (used to verify mapping rules)
     * @return A pretty-printed FHIR Bundle JSON string
     */
    public String buildBundle(Hl7Data data, MappingProfile profile) {
        try {
            // Build the FHIR Bundle as a Map structure
            Map<String, Object> bundle = new LinkedHashMap<>();
            bundle.put("resourceType", "Bundle");
            bundle.put("type", "collection");

            // Create the list of entries (each entry wraps a FHIR resource)
            List<Map<String, Object>> entries = new ArrayList<>();

            // Entry 1: Patient resource
            entries.add(wrapResource(buildPatient(data)));

            // Entry 2: Coverage resource
            entries.add(wrapResource(buildCoverage(data)));

            // Entry 3: CoverageEligibilityRequest resource
            entries.add(wrapResource(buildCoverageEligibilityRequest(data)));

            bundle.put("entry", entries);

            // Convert the Map to a JSON string using Jackson
            return objectMapper.writeValueAsString(bundle);

        } catch (Exception e) {
            throw new RuntimeException("Failed to build FHIR Bundle: " + e.getMessage(), e);
        }
    }

    /**
     * Wraps a resource map inside an "entry" object.
     * FHIR Bundles require each resource to be inside: { "resource": { ... } }
     */
    private Map<String, Object> wrapResource(Map<String, Object> resource) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("resource", resource);
        return entry;
    }

    // ==================== FHIR Resource Builders ====================

    /**
     * Builds the Patient resource.
     *
     * Maps from HL7 PID fields:
     * ABHA ID → Patient.identifier[0].value
     * Family name → Patient.name[0].family
     * Given name → Patient.name[0].given[0]
     * DOB → Patient.birthDate (formatted as YYYY-MM-DD)
     * Gender → Patient.gender (M→male, F→female)
     */
    private Map<String, Object> buildPatient(Hl7Data data) {
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("resourceType", "Patient");

        // Identifier — ABHA ID
        Map<String, Object> identifier = new LinkedHashMap<>();
        identifier.put("system", "https://ndhm.gov.in/abha");
        identifier.put("value", data.getAbhaId());
        patient.put("identifier", Collections.singletonList(identifier));

        // Name
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("family", data.getFamilyName());
        name.put("given", Collections.singletonList(data.getGivenName()));
        patient.put("name", Collections.singletonList(name));

        // Birth Date — convert from YYYYMMDD to YYYY-MM-DD
        patient.put("birthDate", formatDate(data.getDateOfBirth()));

        // Gender — convert M/F to male/female
        patient.put("gender", mapGender(data.getGender()));

        return patient;
    }

    /**
     * Builds the Coverage resource.
     *
     * Represents the patient's insurance/health coverage under NHCX.
     */
    private Map<String, Object> buildCoverage(Hl7Data data) {
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("resourceType", "Coverage");
        coverage.put("status", "active");

        // Reference back to the Patient
        Map<String, Object> beneficiary = new LinkedHashMap<>();
        beneficiary.put("reference", "Patient/" + data.getAbhaId());
        coverage.put("beneficiary", beneficiary);

        // Payor is the insurance organization (NHCX in this case)
        Map<String, Object> payor = new LinkedHashMap<>();
        payor.put("reference", "Organization/NHCX");
        coverage.put("payor", Collections.singletonList(payor));

        return coverage;
    }

    /**
     * Builds the CoverageEligibilityRequest resource.
     *
     * This is what gets sent to check if a patient is eligible for coverage.
     */
    private Map<String, Object> buildCoverageEligibilityRequest(Hl7Data data) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("resourceType", "CoverageEligibilityRequest");
        request.put("status", "active");
        request.put("purpose", Collections.singletonList("validation"));

        // Reference to the Patient
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("reference", "Patient/" + data.getAbhaId());
        request.put("patient", patient);

        // Created date = today
        request.put("created", LocalDate.now().toString());

        // Insurer — the organization that provides coverage
        Map<String, Object> insurer = new LinkedHashMap<>();
        insurer.put("reference", "Organization/NHCX");
        request.put("insurer", insurer);

        return request;
    }

    // ==================== Helper Methods ====================

    /**
     * Converts HL7 date format (YYYYMMDD) to FHIR format (YYYY-MM-DD).
     * Example: "19900415" → "1990-04-15"
     */
    private String formatDate(String hl7Date) {
        if (hl7Date == null || hl7Date.length() != 8) {
            return hl7Date; // Return as-is if not in expected format
        }
        try {
            LocalDate date = LocalDate.parse(hl7Date, DateTimeFormatter.BASIC_ISO_DATE);
            return date.toString(); // Outputs YYYY-MM-DD
        } catch (Exception e) {
            return hl7Date; // Return original if parsing fails
        }
    }

    /**
     * Maps HL7 gender code to FHIR gender value.
     * M → male
     * F → female
     * O → other
     * U → unknown
     */
    private String mapGender(String hl7Gender) {
        if (hl7Gender == null)
            return "unknown";
        return switch (hl7Gender.toUpperCase()) {
            case "M" -> "male";
            case "F" -> "female";
            case "O" -> "other";
            default -> "unknown";
        };
    }
}
