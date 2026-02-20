# 7. Full Code Walkthrough

A line-by-line explanation of every Java class in the project, in the order they execute during a request.

---

## 1. FhirConverterApplication.java — Entry Point

**Path:** `src/main/java/com/nhcx/fhirconverter/FhirConverterApplication.java`

```java
package com.nhcx.fhirconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication  // ← This one annotation does 3 things:
                        //    1. @Configuration — this class can define beans
                        //    2. @EnableAutoConfiguration — auto-setup JPA, web, etc.
                        //    3. @ComponentScan — scan this package for @Component classes
public class FhirConverterApplication {

    public static void main(String[] args) {
        // SpringApplication.run() does:
        //   1. Creates the Spring IoC container (ApplicationContext)
        //   2. Scans for @Component, @Service, @Controller, @Repository classes
        //   3. Creates instances (beans) and injects dependencies
        //   4. Starts embedded Tomcat on port 8080
        //   5. Loads application.properties
        //   6. Runs schema.sql against the database
        SpringApplication.run(FhirConverterApplication.class, args);
    }
}
```

---

## 2. ConversionController.java — REST Endpoint

**Path:** `src/main/java/com/nhcx/fhirconverter/controller/ConversionController.java`

This is the **entry point for HTTP requests**. When someone sends `POST /api/convert/coverage`, Spring routes the request here.

```java
package com.nhcx.fhirconverter.controller;

import com.nhcx.fhirconverter.model.ConversionRecord;
import com.nhcx.fhirconverter.service.ConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController                           // This class handles HTTP requests and returns data (JSON)
@RequestMapping("/api/convert")           // All URLs in this class start with /api/convert
public class ConversionController {

    // The service that does the actual work
    private final ConversionService conversionService;

    // Constructor injection — Spring passes ConversionService automatically
    // because ConversionService is annotated with @Service
    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    // POST /api/convert/coverage
    // consumes = text/plain → only accepts plain text requests
    // produces = application/json → always returns JSON
    @PostMapping(
            value = "/coverage",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> convertCoverage(@RequestBody String rawHl7) {
        // @RequestBody → Spring reads the HTTP body and passes it as a String

        try {
            // Delegate to service — it handles the entire pipeline
            String fhirJson = conversionService.convertCoverage(rawHl7);

            // ResponseEntity.ok() = HTTP 200 with the JSON body
            return ResponseEntity.ok(fhirJson);

        } catch (Exception e) {
            // If anything goes wrong, return HTTP 500 with error details
            String errorJson = String.format(
                    "{\"error\": \"Conversion failed\", \"message\": \"%s\"}",
                    e.getMessage().replace("\"", "'")
            );
            return ResponseEntity.internalServerError().body(errorJson);
        }
    }

    // GET /api/convert/history
    // Returns all past conversion records from the database
    @GetMapping("/history")
    public ResponseEntity<List<ConversionRecord>> getHistory() {
        // ResponseEntity<List<ConversionRecord>> — Spring auto-serializes the
        // list of ConversionRecord objects to JSON using Jackson
        List<ConversionRecord> records = conversionService.getHistory();
        return ResponseEntity.ok(records);
    }
}
```

---

## 3. ConversionService.java — Orchestrator

**Path:** `src/main/java/com/nhcx/fhirconverter/service/ConversionService.java`

The **brain** of the application. It coordinates all other components in the right order.

```java
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

@Service  // Marks this as a Spring service bean
public class ConversionService {

    // All dependencies — injected via constructor
    private final Hl7Parser hl7Parser;
    private final MappingLoader mappingLoader;
    private final FhirBundleBuilder fhirBundleBuilder;
    private final ConversionRepository conversionRepository;

    public ConversionService(Hl7Parser hl7Parser,
                             MappingLoader mappingLoader,
                             FhirBundleBuilder fhirBundleBuilder,
                             ConversionRepository conversionRepository) {
        this.hl7Parser = hl7Parser;
        this.mappingLoader = mappingLoader;
        this.fhirBundleBuilder = fhirBundleBuilder;
        this.conversionRepository = conversionRepository;
    }

    public String convertCoverage(String rawHl7) {
        // Create a DB record upfront — we'll save it whether it succeeds or fails
        ConversionRecord record = new ConversionRecord();
        record.setRawHl7(rawHl7);

        try {
            // ──── STEP 1: Parse HL7 ────
            // Input:  "PID|1||ABHA123||Sharma^Rahul||19900415|M"
            // Output: Hl7Data{abhaId="ABHA123", familyName="Sharma", ...}
            Hl7Data parsedData = hl7Parser.parse(rawHl7);

            // ──── STEP 2: Load Mapping Profile ────
            // Reads hl7_adt_v2_coverage.yaml from classpath
            // Output: MappingProfile with field→fhirPath rules
            MappingProfile profile = mappingLoader.loadProfile();

            // ──── STEP 3: Build FHIR Bundle ────
            // Takes parsed data + mapping rules
            // Output: JSON string with Patient, Coverage, CoverageEligibilityRequest
            String fhirJson = fhirBundleBuilder.buildBundle(parsedData, profile);

            // ──── STEP 4: Save SUCCESS to database ────
            record.setFhirJson(fhirJson);
            record.setStatus("SUCCESS");
            conversionRepository.save(record);  // JPA generates INSERT SQL

            return fhirJson;

        } catch (Exception e) {
            // ──── Save ERROR to database ────
            // Even failures are recorded for debugging
            record.setStatus("ERROR");
            record.setErrorMessage(e.getMessage());
            conversionRepository.save(record);

            throw new RuntimeException("Conversion failed: " + e.getMessage(), e);
        }
    }

    public List<ConversionRecord> getHistory() {
        // JpaRepository.findAll() → SELECT * FROM conversion_records
        return conversionRepository.findAll();
    }
}
```

---

## 4. Hl7Parser.java — HL7 Text → Structured Data

**Path:** `src/main/java/com/nhcx/fhirconverter/parser/Hl7Parser.java`

```java
package com.nhcx.fhirconverter.parser;

import com.nhcx.fhirconverter.model.Hl7Data;
import org.springframework.stereotype.Component;

@Component  // Spring manages this as a singleton bean
public class Hl7Parser {

    public Hl7Data parse(String rawHl7) {
        // Find the PID line in the message
        String pidLine = findPidSegment(rawHl7);

        // Split by pipe: "PID|1||ABHA123|..." → ["PID", "1", "", "ABHA123", ...]
        String[] fields = pidLine.split("\\|");
        //                              ^^
        //                     Escaped pipe (regex special char)

        Hl7Data data = new Hl7Data();

        // Field 3 → ABHA ID
        if (fields.length > 3) {
            data.setAbhaId(fields[3].trim());
        }

        // Field 5 → Patient Name (has sub-fields)
        if (fields.length > 5) {
            String nameField = fields[5];  // "Sharma^Rahul"

            // Split by caret: "Sharma^Rahul" → ["Sharma", "Rahul"]
            String[] nameParts = nameField.split("\\^");

            if (nameParts.length > 0) data.setFamilyName(nameParts[0].trim());
            if (nameParts.length > 1) data.setGivenName(nameParts[1].trim());
        }

        // Field 7 → Date of Birth
        if (fields.length > 7) {
            data.setDateOfBirth(fields[7].trim());
        }

        // Field 8 → Gender
        if (fields.length > 8) {
            data.setGender(fields[8].trim());
        }

        return data;
    }

    private String findPidSegment(String rawHl7) {
        // Handle multi-line HL7 messages
        String[] lines = rawHl7.split("\\r?\\n");

        for (String line : lines) {
            if (line.trim().startsWith("PID")) {
                return line.trim();
            }
        }

        throw new IllegalArgumentException(
                "No PID segment found in the HL7 message."
        );
    }
}
```

---

## 5. MappingLoader.java — YAML → Java Object

**Path:** `src/main/java/com/nhcx/fhirconverter/mapping/MappingLoader.java`

```java
package com.nhcx.fhirconverter.mapping;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;

@Component
public class MappingLoader {

    private static final String MAPPING_FILE =
        "mapping_profiles/hl7_adt_v2_coverage.yaml";

    public MappingProfile loadProfile() {
        // SnakeYAML parser instance
        Yaml yaml = new Yaml();

        // Load from classpath (= src/main/resources/ folder)
        // getResourceAsStream() looks inside the JAR/classpath
        InputStream inputStream = getClass()
                .getClassLoader()
                .getResourceAsStream(MAPPING_FILE);

        if (inputStream == null) {
            throw new RuntimeException("Mapping profile not found: " + MAPPING_FILE);
        }

        // yaml.loadAs() reads the YAML and maps to MappingProfile class
        // It matches YAML keys to Java field names automatically:
        //   "profile" → profile field
        //   "segments" → segments field (as nested Maps)
        MappingProfile profile = yaml.loadAs(inputStream, MappingProfile.class);

        return profile;
    }
}
```

---

## 6. MappingProfile.java — YAML Structure POJO

**Path:** `src/main/java/com/nhcx/fhirconverter/mapping/MappingProfile.java`

```java
package com.nhcx.fhirconverter.mapping;

import java.util.Map;

public class MappingProfile {
    private String profile;      // "hl7_adt_v2_to_coverage"
    private String version;      // "1.0"
    private String sourceFormat; // "HL7v2_ADT"
    private String targetFormat; // "FHIR_R4"

    // The deeply nested mapping structure:
    // segments → PID → fields → 3 → {fhirPath: "...", description: "..."}
    //
    // Layer 1: Map<String, ...>         → segment name ("PID")
    // Layer 2: Map<String, ...>         → "fields" key
    // Layer 3: Map<String, Object>      → field index → mapping details
    private Map<String, Map<String, Map<String, Object>>> segments;

    // + getters and setters for all fields
}
```

---

## 7. FhirBundleBuilder.java — Data → FHIR JSON

**Path:** `src/main/java/com/nhcx/fhirconverter/fhir/FhirBundleBuilder.java`

```java
package com.nhcx.fhirconverter.fhir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nhcx.fhirconverter.mapping.MappingProfile;
import com.nhcx.fhirconverter.model.Hl7Data;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class FhirBundleBuilder {

    private final ObjectMapper objectMapper;

    public FhirBundleBuilder() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        // INDENT_OUTPUT = pretty-print with newlines and indentation
    }

    public String buildBundle(Hl7Data data, MappingProfile profile) {
        try {
            // Build the Bundle structure as nested Maps
            Map<String, Object> bundle = new LinkedHashMap<>();
            bundle.put("resourceType", "Bundle");
            bundle.put("type", "collection");

            List<Map<String, Object>> entries = new ArrayList<>();
            entries.add(wrapResource(buildPatient(data)));
            entries.add(wrapResource(buildCoverage(data)));
            entries.add(wrapResource(buildCoverageEligibilityRequest(data)));
            bundle.put("entry", entries);

            // Jackson converts Map → JSON string
            return objectMapper.writeValueAsString(bundle);

        } catch (Exception e) {
            throw new RuntimeException("Failed to build FHIR Bundle: " + e.getMessage(), e);
        }
    }

    // Wraps resource in {"resource": {...}} as required by FHIR Bundle spec
    private Map<String, Object> wrapResource(Map<String, Object> resource) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("resource", resource);
        return entry;
    }

    private Map<String, Object> buildPatient(Hl7Data data) {
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("resourceType", "Patient");

        // Identifier (ABHA ID)
        Map<String, Object> identifier = new LinkedHashMap<>();
        identifier.put("system", "https://ndhm.gov.in/abha");
        identifier.put("value", data.getAbhaId());
        patient.put("identifier", Collections.singletonList(identifier));

        // Name
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("family", data.getFamilyName());
        name.put("given", Collections.singletonList(data.getGivenName()));
        patient.put("name", Collections.singletonList(name));

        // Birth date: "19900415" → "1990-04-15"
        patient.put("birthDate", formatDate(data.getDateOfBirth()));

        // Gender: "M" → "male"
        patient.put("gender", mapGender(data.getGender()));

        return patient;
    }

    private Map<String, Object> buildCoverage(Hl7Data data) {
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("resourceType", "Coverage");
        coverage.put("status", "active");

        Map<String, Object> beneficiary = new LinkedHashMap<>();
        beneficiary.put("reference", "Patient/" + data.getAbhaId());
        coverage.put("beneficiary", beneficiary);

        Map<String, Object> payor = new LinkedHashMap<>();
        payor.put("reference", "Organization/NHCX");
        coverage.put("payor", Collections.singletonList(payor));

        return coverage;
    }

    private Map<String, Object> buildCoverageEligibilityRequest(Hl7Data data) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("resourceType", "CoverageEligibilityRequest");
        request.put("status", "active");
        request.put("purpose", Collections.singletonList("validation"));

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("reference", "Patient/" + data.getAbhaId());
        request.put("patient", patient);

        request.put("created", LocalDate.now().toString());

        Map<String, Object> insurer = new LinkedHashMap<>();
        insurer.put("reference", "Organization/NHCX");
        request.put("insurer", insurer);

        return request;
    }

    // "19900415" → "1990-04-15"
    private String formatDate(String hl7Date) {
        if (hl7Date == null || hl7Date.length() != 8) return hl7Date;
        try {
            return LocalDate.parse(hl7Date, DateTimeFormatter.BASIC_ISO_DATE).toString();
        } catch (Exception e) {
            return hl7Date;
        }
    }

    // "M" → "male", "F" → "female"
    private String mapGender(String hl7Gender) {
        if (hl7Gender == null) return "unknown";
        return switch (hl7Gender.toUpperCase()) {
            case "M" -> "male";
            case "F" -> "female";
            case "O" -> "other";
            default -> "unknown";
        };
    }
}
```

---

## 8. Hl7Data.java — Data Transfer Object

**Path:** `src/main/java/com/nhcx/fhirconverter/model/Hl7Data.java`

```java
// Simple POJO — no annotations, no framework dependency
// Just holds the 5 fields extracted from HL7
public class Hl7Data {
    private String abhaId;       // "ABHA123"
    private String familyName;   // "Sharma"
    private String givenName;    // "Rahul"
    private String dateOfBirth;  // "19900415"
    private String gender;       // "M"

    // + getters and setters
    // + toString() for debug logging
}
```

---

## 9. ConversionRecord.java — JPA Entity

**Path:** `src/main/java/com/nhcx/fhirconverter/model/ConversionRecord.java`

```java
@Entity                                    // JPA: this maps to a database table
@Table(name = "conversion_records")        // Table name in MySQL
public class ConversionRecord {

    @Id                                    // Primary key
    @GeneratedValue(strategy = IDENTITY)   // AUTO_INCREMENT in MySQL
    private Long id;

    @Column(name = "raw_hl7", nullable = false, columnDefinition = "TEXT")
    private String rawHl7;

    @Column(name = "fhir_json", columnDefinition = "LONGTEXT")
    private String fhirJson;

    @Column(name = "status", nullable = false, length = 20)
    private String status;                 // "SUCCESS" or "ERROR"

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist                            // Callback: runs before INSERT
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // + getters and setters
}
```

---

## 10. ConversionRepository.java — Database Access

**Path:** `src/main/java/com/nhcx/fhirconverter/repository/ConversionRepository.java`

```java
@Repository
public interface ConversionRepository extends JpaRepository<ConversionRecord, Long> {
    // Empty! JpaRepository gives us save(), findAll(), findById(), etc.
    // Spring generates the implementing class at runtime.
}
```

**How does an empty interface work?**

Spring Data JPA uses **dynamic proxies** at runtime:
1. Sees `ConversionRepository extends JpaRepository<ConversionRecord, Long>`
2. Knows the entity is `ConversionRecord` and the ID type is `Long`
3. Auto-generates an implementation class with methods like:
   - `save()` → `INSERT INTO conversion_records (...) VALUES (...)`
   - `findAll()` → `SELECT * FROM conversion_records`
   - `findById(1L)` → `SELECT * FROM conversion_records WHERE id = 1`

---

## Request Lifecycle — Complete Trace

Here's every method call for a single request:

```
1. curl sends POST /api/convert/coverage with "PID|1||ABHA123||Sharma^Rahul||19900415|M"

2. Tomcat receives HTTP request
   → Matches URL to ConversionController.convertCoverage()

3. ConversionController.convertCoverage("PID|1||ABHA123||Sharma^Rahul||19900415|M")
   → Calls conversionService.convertCoverage(rawHl7)

4. ConversionService.convertCoverage(rawHl7)
   → Creates ConversionRecord, sets rawHl7

   4a. hl7Parser.parse(rawHl7)
       → findPidSegment() → finds "PID|1||ABHA123||..."
       → split("\\|") → ["PID", "1", "", "ABHA123", "", "Sharma^Rahul", ...]
       → split("\\^") on field 5 → ["Sharma", "Rahul"]
       → Returns Hl7Data{ABHA123, Sharma, Rahul, 19900415, M}

   4b. mappingLoader.loadProfile()
       → Reads hl7_adt_v2_coverage.yaml from classpath
       → yaml.loadAs() → Returns MappingProfile object

   4c. fhirBundleBuilder.buildBundle(data, profile)
       → buildPatient() → Map with identifier, name, birthDate, gender
       → buildCoverage() → Map with beneficiary, payor
       → buildCoverageEligibilityRequest() → Map with patient, insurer
       → objectMapper.writeValueAsString() → JSON string

   4d. conversionRepository.save(record)
       → JPA generates: INSERT INTO conversion_records (raw_hl7, fhir_json, status, created_at)
       → MySQL executes the INSERT

   → Returns fhirJson string

5. ConversionController receives fhirJson
   → ResponseEntity.ok(fhirJson) → HTTP 200 with JSON body

6. Tomcat sends HTTP response back to curl
```
