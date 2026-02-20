package com.nhcx.fhirconverter.model;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * JPA Entity that maps to the "conversion_records" table in MySQL.
 *
 * Each row represents one HL7 → FHIR conversion attempt.
 * We store:
 * - The original raw HL7 message
 * - The generated FHIR JSON (if successful)
 * - The status (SUCCESS or ERROR)
 * - Any error message (if conversion failed)
 * - The timestamp of when the conversion happened
 */
@Entity
@Table(name = "conversion_records")
public class ConversionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SHA-256 hash of the raw HL7 message — used for uniqueness check
    @Column(name = "hl7_hash", nullable = false, unique = true, length = 64)
    private String hl7Hash;

    // The original HL7 message exactly as received
    @Column(name = "raw_hl7", nullable = false, columnDefinition = "TEXT")
    private String rawHl7;

    // The generated FHIR Bundle JSON
    @Column(name = "fhir_json", columnDefinition = "LONGTEXT")
    private String fhirJson;

    // Conversion status: "SUCCESS" or "ERROR"
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    // Error details if the conversion failed
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Automatically set to the current time when the record is created
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * JPA lifecycle callback: sets createdAt before the entity is first saved.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHl7Hash() {
        return hl7Hash;
    }

    public void setHl7Hash(String hl7Hash) {
        this.hl7Hash = hl7Hash;
    }

    public String getRawHl7() {
        return rawHl7;
    }

    public void setRawHl7(String rawHl7) {
        this.rawHl7 = rawHl7;
    }

    // ==================== Utility ====================

    /**
     * Computes a SHA-256 hash of the given HL7 message.
     * Used to detect duplicate messages.
     */
    public static String computeHash(String rawHl7) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawHl7.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public String getFhirJson() {
        return fhirJson;
    }

    public void setFhirJson(String fhirJson) {
        this.fhirJson = fhirJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
