package com.nhcx.fhirconverter.mapping;

import java.util.Map;

/**
 * POJO (Plain Old Java Object) that represents the YAML mapping profile
 * structure.
 *
 * HOW THIS MAPS TO YAML:
 * ======================
 * The YAML file has this structure:
 *
 * profile: hl7_adt_v2_to_coverage
 * version: "1.0"
 * sourceFormat: HL7v2_ADT
 * targetFormat: FHIR_R4
 * segments:
 * PID:
 * fields:
 * 3:
 * fhirPath: "Patient.identifier[abha].value"
 * description: "ABHA ID"
 * 5:
 * subfields:
 * 1:
 * fhirPath: "Patient.name.family"
 * 2:
 * fhirPath: "Patient.name.given"
 *
 * SnakeYAML automatically maps YAML keys to Java field names.
 * Nested structures become nested Maps — we keep it simple and
 * use Map<String, Object> for the deeply nested parts.
 */
public class MappingProfile {

    private String profile; // e.g., "hl7_adt_v2_to_coverage"
    private String version; // e.g., "1.0"
    private String sourceFormat; // e.g., "HL7v2_ADT"
    private String targetFormat; // e.g., "FHIR_R4"

    // segments → PID → fields → { 3: {fhirPath: ...}, 5: {subfields: ...}, ... }
    // We use nested Maps to represent the flexible YAML structure
    private Map<String, Map<String, Map<String, Object>>> segments;

    // ==================== Getters & Setters ====================

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat;
    }

    public Map<String, Map<String, Map<String, Object>>> getSegments() {
        return segments;
    }

    public void setSegments(Map<String, Map<String, Map<String, Object>>> segments) {
        this.segments = segments;
    }
}
