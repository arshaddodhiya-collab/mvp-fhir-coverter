# NHCX Profile Validation — Complete Guide

This document covers the strict FHIR R4 profile validation layer added to the conversion pipeline. It explains what was built, why, and how it works.

---

## 1. Why Strict Validation?

NHCX doesn't accept just any valid FHIR — resources must conform to specific ABDM/NHCX profiles (mandatory fields, specific coding systems like SNOMED-CT or ICD-10).

Without validation, the NHCX gateway **rejects payloads silently** or returns cryptic errors at submission time. By validating locally before sending, we:

- Catch data gaps immediately after conversion
- Get human-readable error messages pinpointing the exact issue
- Save failed conversions with error details in the database for debugging
- Prevent bad data from ever reaching the NHCX gateway

---

## 2. Architecture Overview

```
┌──────────────────────┐
│   Raw HL7 Message     │
└──────────┬───────────┘
           │ Hl7Parser.parse()
           ▼
┌──────────────────────┐
│   Hl7Data (POJO)      │
└──────────┬───────────┘
           │ FhirBundleBuilder.buildBundle()
           ▼
┌──────────────────────┐
│   FHIR Bundle JSON    │
└──────────┬───────────┘
           │ FhirValidatorService.validate()   ← NEW STEP
           ▼
┌──────────────────────┐
│   Validation Result   │
│   ✅ PASS → Save DB   │
│   ❌ FAIL → Error DB  │
└──────────────────────┘
```

The validator sits between **Step 3 (Build)** and **Step 4 (Save)** in `ConversionService`.

---

## 3. Dependencies Added

In `pom.xml`, two new HAPI FHIR libraries were added:

| Dependency | Version | Purpose |
|---|---|---|
| `hapi-fhir-validation` | 6.10.0 | Core validation engine (`FhirValidator`, `FhirInstanceValidator`) |
| `hapi-fhir-validation-resources-r4` | 6.10.0 | Bundled FHIR R4 StructureDefinitions, ValueSets, and CodeSystems |

These are in addition to the existing `hapi-fhir-base` and `hapi-fhir-structures-r4`.

---

## 4. Components

### 4.1 `FhirValidatorService`
**Location:** `src/main/java/com/nhcx/fhirconverter/fhir/FhirValidatorService.java`

This Spring `@Service` initializes and holds the HAPI FHIR Validator. It is constructed once at startup and reused for every request.

#### Validation Support Chain

The validator uses a `ValidationSupportChain` — a stack of modules that each handle a different aspect of validation:

| Module | What It Does |
|---|---|
| `DefaultProfileValidationSupport` | Loads the official FHIR R4 StructureDefinitions (e.g., Patient must have certain fields, Bundle entries need `fullUrl`) |
| `CommonCodeSystemsTerminologyService` | Recognizes global code system URLs (SNOMED-CT, LOINC, ICD-10) so they don't trigger "unknown system" errors |
| `InMemoryTerminologyServerValidationSupport` | Validates codes against small built-in ValueSets (e.g., Gender must be `male`, `female`, `other`, or `unknown`) |

#### How `validate(String fhirJson)` Works

1. Parses the JSON into a FHIR resource object
2. Runs all registered validator modules against it
3. Collects all messages with severity `ERROR` or `FATAL`
4. If any errors exist → throws `ValidationException` with concatenated messages
5. If no errors → returns silently (validation passed)

### 4.2 `ValidationException`
**Location:** `src/main/java/com/nhcx/fhirconverter/exception/ValidationException.java`

A simple `RuntimeException` subclass. Thrown by `FhirValidatorService` when validation fails. Caught by the existing try-catch in `ConversionService`, which saves the error to the database and returns a 500 response.

---

## 5. What Gets Validated

### Structural Rules (enforced now)

| Rule | Example |
|---|---|
| Bundle entries must have `fullUrl` | Each entry gets a `urn:uuid:` identifier |
| Claim must have `type` | Set to `institutional` from `claim-type` CodeSystem |
| Claim must have `priority` | Set to `normal` from `processpriority` CodeSystem |
| Claim must have at least 1 `insurance` | Linked to Coverage resource |
| Patient `gender` must be a valid code | Must be `male`, `female`, `other`, or `unknown` |
| Required fields cannot be missing | e.g., `CoverageEligibilityRequest.created` |

### Terminology Rules (partially enforced)

| System | URL | Status |
|---|---|---|
| SNOMED-CT | `http://snomed.info/sct` | ✅ URL recognized, ⚠️ individual codes NOT verified |
| ICD-10 | `http://hl7.org/fhir/sid/icd-10` | ✅ URL recognized, ⚠️ individual codes NOT verified |
| LOINC | `http://loinc.org` | ✅ URL recognized, ⚠️ individual codes NOT verified |
| FHIR built-in ValueSets | (e.g., gender, status) | ✅ Fully validated against allowed values |

> **Why aren't SNOMED/ICD-10 codes fully verified?**  
> SNOMED-CT alone has 350,000+ concepts. Loading the entire dictionary into memory is impractical. For full code-level verification, a **Remote Terminology Server** (like Ontoserver or a HAPI JPA server) would be needed. This is a Phase 2 enhancement.

---

## 6. Extending with NHCX Implementation Guide (IG)

The `FhirValidatorService` has a commented-out extensibility point for loading an NHCX IG package:

```java
// === EXTENSIBILITY POINT: NHCX Profiles ===
// NpmPackageValidationSupport npmPackageSupport = new NpmPackageValidationSupport(fhirContext);
// npmPackageSupport.loadPackageFromClasspath("classpath:nhcx-ig-1.0.0.tgz");
// validationSupportChain.addValidationSupport(npmPackageSupport);
```

When the official NHCX IG `.tgz` package becomes available:
1. Place it in `src/main/resources/`
2. Uncomment the lines above
3. Update the filename to match
4. The validator will now enforce NHCX-specific profile constraints (mandatory extensions, Indian-specific coding slices, etc.)

---

## 7. Error Handling Flow

```
Request → ConversionService.convertCoverage()
  │
  ├── Parse HL7 ✅
  ├── Load Mapping ✅
  ├── Build FHIR Bundle ✅
  ├── Validate FHIR Bundle
  │     │
  │     ├── PASS → Save to DB (status=SUCCESS) → Return JSON
  │     │
  │     └── FAIL → ValidationException thrown
  │              → Caught by try-catch
  │              → Save to DB (status=ERROR, error_message=details)
  │              → Return 500 with error JSON
```

Database `conversion_records` table stores the full error message, making it easy to debug which profile rules were violated.

---

## 8. Testing

### Postman Test Cases

**Endpoint:** `POST http://localhost:8080/api/convert/coverage`  
**Header:** `Content-Type: text/plain`

#### Test 1: Valid HL7 (should return 200 + FHIR JSON)
```
MSH|^~\&|HOSPITAL|HOSPITAL_SYS|NHCX|NHCX_SYS|20231001120000||ADT^A01|MSG001|P|2.5
PID|1||ABHA12345||Sharma^Rahul||19900415|M
IN1|1|HOSPITAL|NHCX|Star Health
PV1|1|I|WARD-1^^^HOSPITAL||||||||||||||20231001120000
DG1|1|ICD-10|COVID19|COVID-19 Infection
```

#### Test 2: Understanding Base R4 Leniency (will return 200 — this is expected)
```
MSH|^~\&|HOSPITAL|HOSPITAL_SYS|NHCX|NHCX_SYS|20231001120000||ADT^A01|MSG002|P|2.5
PID|1||ABHA54321||||19900415|X
IN1|1|HOSPITAL|NHCX|Star Health
PV1|1|I|WARD-1^^^HOSPITAL||||||||||||||20231001120000
DG1|1|ICD-10|A00|Cholera
```

> **Note:** This test case will **succeed** (200 OK) because:
> - Gender `X` maps to `unknown` in our code, which is a valid FHIR R4 gender code
> - `Patient.name` is **optional** in the base FHIR R4 spec
>
> These fields would only be enforced as mandatory once an **NHCX-specific Implementation Guide** is loaded into the validator. The base FHIR R4 standard is intentionally lenient — NHCX profiles tighten the rules.

### Unit Tests
**Location:** `src/test/java/com/nhcx/fhirconverter/fhir/FhirValidatorServiceTest.java`

Run with: `mvn test`

---

## 9. Files Changed / Created

| File | Action | Description |
|---|---|---|
| `pom.xml` | Modified | Added `hapi-fhir-validation` and `hapi-fhir-validation-resources-r4` |
| `FhirValidatorService.java` | **New** | Core validation service with the validation support chain |
| `ValidationException.java` | **New** | Custom exception for validation failures |
| `ConversionService.java` | Modified | Injected `FhirValidatorService`, added validation step 3.5 |
| `FhirBundleBuilder.java` | Modified | Added `fullUrl` to entries, added `type`/`priority`/`insurance` to Claim |
| `FhirValidatorServiceTest.java` | **New** | Unit tests for valid and invalid FHIR JSON |
