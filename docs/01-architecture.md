# 1. Architecture Overview

## Project Structure

```
fhir-converter/
├── pom.xml                                          ← Maven build config + dependencies
│
├── src/main/java/com/nhcx/fhirconverter/
│   ├── FhirConverterApplication.java                ← Spring Boot entry point
│   │
│   ├── controller/
│   │   └── ConversionController.java                ← REST API endpoints
│   │
│   ├── service/
│   │   └── ConversionService.java                   ← Business logic orchestrator
│   │
│   ├── parser/
│   │   └── Hl7Parser.java                           ← HL7 text → structured data
│   │
│   ├── mapping/
│   │   ├── MappingLoader.java                       ← Reads YAML from classpath
│   │   └── MappingProfile.java                      ← POJO for YAML structure
│   │
│   ├── fhir/
│   │   └── FhirBundleBuilder.java                   ← Builds FHIR Bundle JSON
│   │
│   ├── model/
│   │   ├── Hl7Data.java                             ← DTO for parsed HL7 fields
│   │   └── ConversionRecord.java                    ← JPA entity → MySQL table
│   │
│   └── repository/
│       └── ConversionRepository.java                ← Database CRUD operations
│
├── src/main/resources/
│   ├── application.properties                       ← MySQL + JPA configuration
│   ├── schema.sql                                   ← Table creation DDL
│   └── mapping_profiles/
│       └── hl7_adt_v2_coverage.yaml                 ← Field mapping rules
│
└── src/test/
    ├── java/.../FhirConverterApplicationTests.java  ← Context load test
    └── resources/application.properties             ← H2 test config
```

---

## Layered Architecture

Each layer has a single, clear responsibility:

```
┌─────────────────────────────────────────────────────────────┐
│                     CLIENT (curl, Postman)                   │
│              POST /api/convert/coverage                      │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP Request (plain text HL7)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  CONTROLLER LAYER  (ConversionController.java)              │
│  • Receives HTTP request                                     │
│  • Delegates to service                                      │
│  • Returns HTTP response                                     │
└──────────────────────────┬──────────────────────────────────┘
                           │ raw HL7 string
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  SERVICE LAYER  (ConversionService.java)                     │
│  • Orchestrates the full pipeline                            │
│  • Coordinates parser, mapping, builder, repository          │
│  • Handles errors and sets status                            │
└────┬─────────────┬───────────────┬──────────────┬───────────┘
     │             │               │              │
     ▼             ▼               ▼              ▼
┌─────────┐  ┌──────────┐  ┌────────────┐  ┌───────────┐
│ PARSER  │  │ MAPPING  │  │   FHIR     │  │REPOSITORY │
│Hl7Parser│  │MappingLdr│  │BundleBuildr│  │Conversion │
│         │  │          │  │            │  │Repository │
│Split by │  │Load YAML │  │Build JSON  │  │Save to DB │
│ | and ^ │  │from file │  │Maps→String │  │           │
└─────────┘  └──────────┘  └────────────┘  └─────┬─────┘
                                                  │
                                                  ▼
                                          ┌──────────────┐
                                          │   MySQL DB   │
                                          │conversion_   │
                                          │  records     │
                                          └──────────────┘
```

---

## Why This Architecture?

| Design Choice | Why |
|--------------|-----|
| **Separate layers** | Each class does ONE thing — easy to understand, test, and modify |
| **Controller doesn't contain logic** | If we add a CLI or batch mode later, the service just works |
| **Parser is its own class** | Tomorrow we might support different HL7 segments or formats |
| **YAML mapping config** | Mapping rules are data, not code — change them without recompiling |
| **Repository pattern** | Spring Data JPA auto-generates SQL — zero boilerplate |

---

## End-to-End Data Flow

Here's exactly what happens when you send a request:

### Step 1 — Client sends HL7 text
```
POST /api/convert/coverage
Content-Type: text/plain

PID|1||ABHA123||Sharma^Rahul||19900415|M
```

### Step 2 — Controller receives it
`ConversionController.convertCoverage()` is called with the raw string.

### Step 3 — Service orchestrates
`ConversionService.convertCoverage()` runs these steps in order:

1. **Parse** → `Hl7Parser.parse("PID|1||ABHA123||Sharma^Rahul||19900415|M")`
   - Result: `Hl7Data{abhaId=ABHA123, familyName=Sharma, givenName=Rahul, dob=19900415, gender=M}`

2. **Load mapping** → `MappingLoader.loadProfile()`
   - Reads `hl7_adt_v2_coverage.yaml` from classpath
   - Result: `MappingProfile` object with field-to-FHIR path rules

3. **Build FHIR** → `FhirBundleBuilder.buildBundle(data, profile)`
   - Constructs Patient, Coverage, CoverageEligibilityRequest as Maps
   - Serializes to JSON via Jackson
   - Result: FHIR Bundle JSON string

4. **Save to DB** → `ConversionRepository.save(record)`
   - Creates a `ConversionRecord` with `rawHl7`, `fhirJson`, `status=SUCCESS`
   - JPA generates an INSERT statement

### Step 4 — Controller returns JSON
The FHIR Bundle JSON is returned to the client with HTTP 200.

---

## Dependency Injection

Spring Boot automatically wires everything together:

```
ConversionController
    └── needs ConversionService        (injected via constructor)
            ├── needs Hl7Parser             (injected via constructor)
            ├── needs MappingLoader         (injected via constructor)
            ├── needs FhirBundleBuilder     (injected via constructor)
            └── needs ConversionRepository  (injected via constructor)
```

**How?** Each class is annotated with a Spring stereotype:
- `@RestController` → Controller
- `@Service` → Service
- `@Component` → Parser, MappingLoader, FhirBundleBuilder
- `@Repository` → ConversionRepository

Spring scans for these annotations at startup and creates one instance of each (a "bean"). When a class needs another, Spring passes it via the constructor automatically.
