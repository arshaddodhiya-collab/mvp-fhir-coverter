package com.nhcx.fhirconverter.service;

import com.nhcx.fhirconverter.fhir.FhirBundleBuilder;
import com.nhcx.fhirconverter.mapping.MappingLoader;
import com.nhcx.fhirconverter.mapping.MappingProfile;
import com.nhcx.fhirconverter.model.ConversionRecord;
import com.nhcx.fhirconverter.model.Hl7Data;
import com.nhcx.fhirconverter.parser.Hl7Parser;
import com.nhcx.fhirconverter.repository.ConversionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service layer that orchestrates the full HL7 → FHIR conversion flow.
 *
 * THE CONVERSION FLOW (step by step):
 * ====================================
 *
 * 1. PARSE HL7
 * ┌──────────────────────────────────┐
 * │ Raw HL7 text │
 * │ "PID|1||ABHA123||Sharma^Rahul.." │
 * └──────────┬───────────────────────┘
 * │ Hl7Parser.parse()
 * ▼
 * ┌──────────────────────────────────┐
 * │ Hl7Data { │
 * │ abhaId: "ABHA123" │
 * │ familyName: "Sharma" │
 * │ givenName: "Rahul" │
 * │ dateOfBirth: "19900415" │
 * │ gender: "M" │
 * │ } │
 * └──────────┬───────────────────────┘
 * │
 * 2. LOAD MAPPING PROFILE (from YAML)
 * │ MappingLoader.loadProfile()
 * ▼
 * ┌──────────────────────────────────┐
 * │ MappingProfile { │
 * │ profile: "hl7_adt_v2_to_cov.."│
 * │ segments: { PID: { ... } } │
 * │ } │
 * └──────────┬───────────────────────┘
 * │
 * 3. BUILD FHIR BUNDLE
 * │ FhirBundleBuilder.buildBundle()
 * ▼
 * ┌──────────────────────────────────┐
 * │ FHIR Bundle JSON (String) │
 * │ { "resourceType": "Bundle", ... }│
 * └──────────┬───────────────────────┘
 * │
 * 4. SAVE TO DATABASE
 * │ ConversionRepository.save()
 * ▼
 * ┌──────────────────────────────────┐
 * │ MySQL: conversion_records table │
 * │ raw_hl7, fhir_json, status, ... │
 * └──────────────────────────────────┘
 */
@Service
public class ConversionService {

    // Dependencies — injected by Spring via constructor injection
    private final Hl7Parser hl7Parser;
    private final MappingLoader mappingLoader;
    private final FhirBundleBuilder fhirBundleBuilder;
    private final ConversionRepository conversionRepository;

    /**
     * Constructor injection: Spring automatically provides all dependencies.
     * This is the recommended way to inject dependencies in Spring Boot.
     */
    public ConversionService(Hl7Parser hl7Parser,
            MappingLoader mappingLoader,
            FhirBundleBuilder fhirBundleBuilder,
            ConversionRepository conversionRepository) {
        this.hl7Parser = hl7Parser;
        this.mappingLoader = mappingLoader;
        this.fhirBundleBuilder = fhirBundleBuilder;
        this.conversionRepository = conversionRepository;
    }

    /**
     * Converts a raw HL7 message to a FHIR Bundle JSON.
     *
     * This method orchestrates the entire conversion pipeline:
     * 1. Parse the HL7 message
     * 2. Load the mapping profile from YAML
     * 3. Build the FHIR Bundle
     * 4. Save the result to the database
     * 5. Return the FHIR JSON
     *
     * @param rawHl7 The raw HL7 message (plain text)
     * @return The generated FHIR Bundle JSON string
     */
    public String convertCoverage(String rawHl7) {
        // Compute a unique hash of the HL7 message for deduplication
        String hl7Hash = ConversionRecord.computeHash(rawHl7);

        // Check if this exact HL7 message was already converted successfully
        var existing = conversionRepository.findByHl7Hash(hl7Hash);
        if (existing.isPresent() && "SUCCESS".equals(existing.get().getStatus())) {
            System.out.println("♻️  Duplicate HL7 detected — returning existing FHIR JSON.");
            return existing.get().getFhirJson();
        }

        // Create a record to track this conversion attempt
        ConversionRecord record = new ConversionRecord();
        record.setRawHl7(rawHl7);
        record.setHl7Hash(hl7Hash);

        try {
            // Step 1: Parse the HL7 message to extract structured data
            System.out.println("📋 Step 1: Parsing HL7 message...");
            Hl7Data parsedData = hl7Parser.parse(rawHl7);
            System.out.println("   Parsed: " + parsedData);

            // Step 2: Load the YAML mapping profile
            System.out.println("📄 Step 2: Loading mapping profile from YAML...");
            MappingProfile profile = mappingLoader.loadProfile();

            // Step 3: Build the FHIR Bundle JSON
            System.out.println("🏗️  Step 3: Building FHIR Bundle...");
            String fhirJson = fhirBundleBuilder.buildBundle(parsedData, profile);

            // Step 4: Save successful conversion to database
            System.out.println("💾 Step 4: Saving to database...");
            record.setFhirJson(fhirJson);
            record.setStatus("SUCCESS");
            conversionRepository.save(record);

            System.out.println("✅ Conversion completed successfully!");
            return fhirJson;

        } catch (Exception e) {
            // If anything goes wrong, save the error to the database
            System.out.println("❌ Conversion failed: " + e.getMessage());
            record.setStatus("ERROR");
            record.setErrorMessage(e.getMessage());
            conversionRepository.save(record);

            // Re-throw so the controller can return an error response
            throw new RuntimeException("Conversion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns all conversion records from the database.
     * Useful for checking conversion history.
     */
    public List<ConversionRecord> getHistory() {
        return conversionRepository.findAll();
    }
}
