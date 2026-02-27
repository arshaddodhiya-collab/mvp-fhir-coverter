package com.nhcx.fhirconverter.service;

import com.nhcx.fhirconverter.fhir.FhirBundleBuilder;
import com.nhcx.fhirconverter.mapping.MappingLoader;
import com.nhcx.fhirconverter.mapping.MappingProfile;
import com.nhcx.fhirconverter.model.ConversionRecord;
import com.nhcx.fhirconverter.model.ErrorLog;
import com.nhcx.fhirconverter.model.Hl7Data;
import com.nhcx.fhirconverter.parser.CsvInputParser;
import com.nhcx.fhirconverter.parser.Hl7Parser;
import com.nhcx.fhirconverter.parser.JsonInputParser;
import com.nhcx.fhirconverter.repository.ConversionRepository;
import com.nhcx.fhirconverter.repository.ErrorLogRepository;
import com.nhcx.fhirconverter.fhir.FhirValidatorService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service layer that orchestrates the full conversion flow.
 *
 * Supports three input formats:
 * - HL7 pipe-delimited (raw text)
 * - JSON
 * - CSV
 *
 * All three are parsed into the same Hl7Data POJO, then follow the
 * shared pipeline: map → build FHIR → validate → save.
 */
@Service
public class ConversionService {

    private final Hl7Parser hl7Parser;
    private final JsonInputParser jsonInputParser;
    private final CsvInputParser csvInputParser;
    private final MappingLoader mappingLoader;
    private final FhirBundleBuilder fhirBundleBuilder;
    private final ConversionRepository conversionRepository;
    private final FhirValidatorService fhirValidatorService;
    private final ErrorLogRepository errorLogRepository;

    public ConversionService(Hl7Parser hl7Parser,
            JsonInputParser jsonInputParser,
            CsvInputParser csvInputParser,
            MappingLoader mappingLoader,
            FhirBundleBuilder fhirBundleBuilder,
            ConversionRepository conversionRepository,
            FhirValidatorService fhirValidatorService,
            ErrorLogRepository errorLogRepository) {
        this.hl7Parser = hl7Parser;
        this.jsonInputParser = jsonInputParser;
        this.csvInputParser = csvInputParser;
        this.mappingLoader = mappingLoader;
        this.fhirBundleBuilder = fhirBundleBuilder;
        this.conversionRepository = conversionRepository;
        this.fhirValidatorService = fhirValidatorService;
        this.errorLogRepository = errorLogRepository;
    }

    // ==================== Public Entry Points ====================

    /**
     * Converts a raw HL7 message to a FHIR Bundle JSON.
     */
    public String convertCoverage(String rawHl7) {
        String normalizedHl7 = rawHl7.replaceAll("\\r\\n|\\n", "\r");

        System.out.println("📋 Step 1: Parsing HL7 message...");
        try {
            Hl7Data parsedData = hl7Parser.parse(rawHl7);
            System.out.println("   Parsed: " + parsedData);
            return convertFromData(parsedData, normalizedHl7);
        } catch (Exception e) {
            saveToDeadLetterQueue(normalizedHl7, "Parsing failed: " + e.getMessage());
            throw new RuntimeException("Parsing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a JSON input to a FHIR Bundle JSON.
     */
    public String convertFromJson(String json) {
        System.out.println("📋 Step 1: Parsing JSON input...");
        try {
            Hl7Data parsedData = jsonInputParser.parse(json);
            System.out.println("   Parsed: " + parsedData);
            return convertFromData(parsedData, json);
        } catch (Exception e) {
            saveToDeadLetterQueue(json, "JSON Parsing failed: " + e.getMessage());
            throw new RuntimeException("JSON Parsing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a CSV input to a FHIR Bundle JSON.
     */
    public String convertFromCsv(String csv) {
        System.out.println("📋 Step 1: Parsing CSV input...");
        try {
            Hl7Data parsedData = csvInputParser.parse(csv);
            System.out.println("   Parsed: " + parsedData);
            return convertFromData(parsedData, csv);
        } catch (Exception e) {
            saveToDeadLetterQueue(csv, "CSV Parsing failed: " + e.getMessage());
            throw new RuntimeException("CSV Parsing failed: " + e.getMessage(), e);
        }
    }

    public List<ConversionRecord> getHistory() {
        return conversionRepository.findAll();
    }

    /**
     * Returns all error logs (dead letter queue) from the database.
     */
    public List<ErrorLog> getErrors() {
        return errorLogRepository.findAll();
    }

    // ==================== Shared Pipeline ====================

    /**
     * Shared conversion pipeline used by all input formats.
     *
     * Steps:
     * 1. (Already done by caller — parsing)
     * 2. Load mapping profile
     * 3. Build FHIR Bundle
     * 3.5. Validate FHIR Bundle
     * 4. Save to database
     */
    private String convertFromData(Hl7Data parsedData, String rawInput) {
        // Compute hash for deduplication
        String inputHash = ConversionRecord.computeHash(rawInput);

        // Check for duplicate (TEMPORARILY DISABLED FOR TESTING)
        var existingOpt = conversionRepository.findByHl7Hash(inputHash);
        // if (existingOpt.isPresent() &&
        // "SUCCESS".equals(existingOpt.get().getStatus())) {
        // System.out.println("♻️ Duplicate input detected — returning existing FHIR
        // JSON.");
        // return existingOpt.get().getFhirJson();
        // }

        ConversionRecord record = existingOpt.orElseGet(ConversionRecord::new);
        record.setRawHl7(rawInput);
        record.setHl7Hash(inputHash);

        // Early input validation — reject if critical fields are missing
        if (parsedData.getAbhaId() == null || parsedData.getAbhaId().trim().isEmpty()) {
            String errorMsg = "Input validation failed: 'abhaId' is required but was missing or invalid";
            System.out.println("❌ " + errorMsg);
            record.setStatus("ERROR");
            record.setErrorMessage(errorMsg);
            conversionRepository.save(record);
            saveToDeadLetterQueue(rawInput, errorMsg);
            throw new RuntimeException(errorMsg);
        }

        try {
            // Step 2: Load the YAML mapping profile
            System.out.println("📄 Step 2: Loading mapping profile from YAML...");
            MappingProfile profile = mappingLoader.loadProfile();

            // Step 3: Build the FHIR Bundle JSON
            System.out.println("🏗️  Step 3: Building FHIR Bundle...");
            String fhirJson = fhirBundleBuilder.buildBundle(parsedData, profile);

            // Step 3.5: Validate the FHIR Bundle
            System.out.println("🛡️  Step 3.5: Validating FHIR Bundle against NHCX profiles...");
            fhirValidatorService.validate(fhirJson);

            // Step 4: Save successful conversion to database
            System.out.println("💾 Step 4: Saving to database...");
            record.setFhirJson(fhirJson);
            record.setStatus("SUCCESS");
            record.setErrorMessage(null);
            conversionRepository.save(record);

            System.out.println("✅ Conversion completed successfully!");
            return fhirJson;

        } catch (Exception e) {
            System.out.println("❌ Conversion failed: " + e.getMessage());
            record.setStatus("ERROR");
            record.setErrorMessage(e.getMessage());
            conversionRepository.save(record);
            saveToDeadLetterQueue(rawInput, e.getMessage());

            throw new RuntimeException("Conversion failed: " + e.getMessage(), e);
        }
    }

    private void saveToDeadLetterQueue(String rawInput, String errorMsg) {
        try {
            ErrorLog errorLog = new ErrorLog();
            errorLog.setRawMessage(rawInput);
            errorLog.setErrorCause(errorMsg);
            errorLogRepository.save(errorLog);
            System.out.println("📥 Saved failed message to Dead Letter Queue (error_logs)");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to write to Dead Letter Queue: " + e.getMessage());
        }
    }
}
