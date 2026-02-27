# NHCX FHIR Converter — Complete Solution Document

> An AI-powered, production-grade healthcare data transformation platform  
> that converts legacy hospital data (HL7 v2.5 / JSON / CSV) into  
> NHCX-compliant FHIR R4 Bundles — with self-healing pipelines,  
> natural-language input, and intelligent auto-mapping.

---

## Executive Summary

India's Ayushman Bharat Digital Mission (ABDM) mandates that all healthcare
providers exchange clinical and insurance data using the **FHIR R4** standard
through the **National Health Claims Exchange (NHCX)**. Most hospitals still
run legacy systems that speak HL7 v2.x, flat CSV, or proprietary JSON —
creating a massive interoperability gap.

**Our solution** bridges this gap with a three-layer platform:

| Layer                  | What It Does                                                 |
|------------------------|--------------------------------------------------------------|
| **Core Converter**     | Parses HL7/JSON/CSV → maps to FHIR R4 → validates → stores  |
| **Resilience Engine**  | Dead Letter Queue with AI-powered auto-heal & retry          |
| **AI Intelligence**    | Natural-language input, smart auto-mapping, anomaly detection|

```
                         NHCX FHIR Converter Platform
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                         │
│   ┌─────────────┐   ┌──────────────┐   ┌───────────┐   ┌───────────┐  │
│   │  HL7 v2.5   │   │    JSON      │   │    CSV    │   │  Natural  │  │
│   │  (MLLP/TCP) │   │  (REST API)  │   │ (Upload)  │   │  Language │  │
│   └──────┬──────┘   └──────┬───────┘   └─────┬─────┘   └─────┬─────┘  │
│          │                 │                  │               │         │
│          └─────────────────┼──────────────────┼───────────────┘         │
│                            ▼                                            │
│                 ┌─────────────────────┐                                 │
│                 │   Parsing Engine    │                                 │
│                 │   (Format-Aware)    │                                 │
│                 └──────────┬──────────┘                                 │
│                            ▼                                            │
│          ┌─────────────────────────────────────┐                       │
│          │        AI Intelligence Layer        │                       │
│          │  ┌───────────┐  ┌────────────────┐  │                       │
│          │  │ Auto-Code │  │ Anomaly Detect │  │                       │
│          │  │ ICD-10    │  │ Fraud Flags    │  │                       │
│          │  └───────────┘  └────────────────┘  │                       │
│          └─────────────────┬───────────────────┘                       │
│                            ▼                                            │
│                 ┌─────────────────────┐                                 │
│                 │  YAML Mapping Engine│──── mapping.yml                 │
│                 │  (AI Auto-Map)      │     (per hospital)             │
│                 └──────────┬──────────┘                                 │
│                            ▼                                            │
│                 ┌─────────────────────┐                                 │
│                 │  FHIR R4 Bundle     │                                 │
│                 │  Builder (HAPI)     │                                 │
│                 └──────────┬──────────┘                                 │
│                            ▼                                            │
│                 ┌─────────────────────┐                                 │
│                 │  FHIR Validator     │──── NHCX Profiles               │
│                 │  (StructureDef)     │                                 │
│                 └──────┬─────┬────────┘                                 │
│                        │     │                                          │
│               ✅ Pass  │     │ ❌ Fail                                  │
│                        ▼     ▼                                          │
│              ┌──────────┐  ┌──────────────────┐                        │
│              │  MySQL   │  │  Dead Letter Q   │                        │
│              │  (FHIR)  │  │  + AI Auto-Heal  │                        │
│              └──────────┘  └──────────────────┘                        │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [Solution Architecture](#2-solution-architecture)
3. [Core Conversion Engine](#3-core-conversion-engine)
4. [Dead Letter Queue & Self-Healing Pipeline](#4-dead-letter-queue--self-healing-pipeline)
5. [AI Feature 1 — Natural Language to FHIR](#5-ai-feature-1--natural-language-to-fhir)
6. [AI Feature 2 — Smart Field Mapper (Zero-Config)](#6-ai-feature-2--smart-field-mapper-zero-config)
7. [AI Feature 3 — ICD-10 Auto-Coding from Free Text](#7-ai-feature-3--icd-10-auto-coding-from-free-text)
8. [AI Feature 4 — Anomaly Detection & Fraud Flags](#8-ai-feature-4--anomaly-detection--fraud-flags)
9. [AI Feature 5 — Conversational Analytics Dashboard](#9-ai-feature-5--conversational-analytics-dashboard)
10. [Security & Compliance](#10-security--compliance)
11. [Scalability & Deployment](#11-scalability--deployment)
12. [Database Design](#12-database-design)
13. [API Reference](#13-api-reference)
14. [Technology Stack](#14-technology-stack)
15. [Phased Roadmap](#15-phased-roadmap)
16. [Hackathon Pitch](#16-hackathon-pitch)

---

## 1. Problem Statement

### The Gap

| What Hospitals Have          | What NHCX Requires              |
|------------------------------|---------------------------------|
| HL7 v2.5 pipe-delimited     | FHIR R4 JSON Bundles            |
| Free-text diagnoses          | ICD-10 / SNOMED-CT coded        |
| Custom field positions       | Standardized resource structure  |
| No error handling            | Guaranteed delivery              |
| Manual data entry            | Automated, validated pipelines   |

### Real-World Pain Points

1. **3,500+ hospitals** across India's ABDM network must comply with FHIR — most can't.
2. **Rural hospitals** don't have HL7 expertise — they write diagnoses as "sugar problem" or "heart issue".
3. **Claim rejections** due to missing/malformed fields cost the system ₹10,000+ crores annually.
4. **Failed messages** are silently dropped — no retry, no audit trail.
5. **Every hospital** sends data in a slightly different format — manual mapping takes weeks per hospital.

### Our Solution in One Sentence

> An AI-powered converter that accepts **any format** (HL7, JSON, CSV, or plain English),
> **auto-maps** fields using AI, **auto-heals** errors, and outputs **NHCX-compliant FHIR R4 Bundles**
> with full audit trail and fraud detection.

---

## 2. Solution Architecture

### System Components

```
┌──────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER                            │
│                                                                      │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────────┐  │
│  │ Converter  │  │  History   │  │    DLQ     │  │  AI Chat     │  │
│  │    Tab     │  │    Tab     │  │    Tab     │  │  Assistant   │  │
│  └────────────┘  └────────────┘  └────────────┘  └──────────────┘  │
└──────────────────────────┬───────────────────────────────────────────┘
                           │ REST API (JSON)
┌──────────────────────────▼───────────────────────────────────────────┐
│                        APPLICATION LAYER                             │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    Spring Boot Application                    │   │
│  │                                                               │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐    │   │
│  │  │ REST API    │  │ MLLP/TCP     │  │ Kafka Consumer   │    │   │
│  │  │ Controller  │  │ Listener     │  │ (Future)         │    │   │
│  │  └──────┬──────┘  └──────┬───────┘  └────────┬─────────┘    │   │
│  │         └────────────────┼────────────────────┘              │   │
│  │                          ▼                                    │   │
│  │  ┌──────────────────────────────────────────────────────┐    │   │
│  │  │              ConversionService (Pipeline)             │    │   │
│  │  │                                                       │    │   │
│  │  │  Parse → AI Enrich → Map → Build FHIR → Validate    │    │   │
│  │  │    │         │         │         │          │         │    │   │
│  │  │    ▼         ▼         ▼         ▼          ▼         │    │   │
│  │  │  Hl7Parser AiService Mapping  BundleBuilder Validator│    │   │
│  │  │  JsonParser          Loader   (HAPI FHIR)  (NHCX)   │    │   │
│  │  │  CsvParser                                           │    │   │
│  │  │  NLParser (AI)                                       │    │   │
│  │  └──────────────────────────────────────────────────────┘    │   │
│  │                          │                                    │   │
│  │              ┌───────────┼───────────┐                       │   │
│  │              ▼           ▼           ▼                        │   │
│  │  ┌───────────────┐ ┌──────────┐ ┌──────────────────┐        │   │
│  │  │ ConversionRepo│ │ ErrorLog │ │ AiAutoHealService│        │   │
│  │  │ (Success)     │ │ (DLQ)    │ │ (Self-Healing)   │        │   │
│  │  └───────────────┘ └──────────┘ └──────────────────┘        │   │
│  └──────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────────┐
│                          DATA LAYER                                  │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │   MySQL 8.0  │  │  Gemini API  │  │  FHIR Terminology Server│  │
│  │  (Primary DB)│  │  (AI Engine) │  │  (ICD-10, SNOMED-CT)    │  │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Core Conversion Engine

### Supported Input Formats

| Format | Content Type      | Endpoint             | Example Use Case                    |
|--------|-------------------|----------------------|-------------------------------------|
| HL7    | `text/plain`      | `POST /api/convert/coverage` | Direct HIS/EHR integration   |
| JSON   | `application/json`| `POST /api/convert/json`     | Modern API integration       |
| CSV    | `text/plain`      | `POST /api/convert/csv`      | Batch file upload            |
| **Natural Language** | `text/plain` | `POST /api/convert/natural-language` | **AI-powered** — staff types English |

### Conversion Pipeline

```
Input (any format)
       │
       ▼
  ┌──────────┐
  │  PARSE   │──── Format-specific parser extracts into Hl7Data POJO
  └────┬─────┘
       ▼
  ┌──────────┐
  │ AI ENRICH│──── Auto-code free-text diagnoses to ICD-10
  └────┬─────┘     Detect anomalies, flag fraud
       ▼
  ┌──────────┐
  │   MAP    │──── Apply YAML mapping profile (hospital-specific)
  └────┬─────┘     or use AI Smart Mapper for zero-config
       ▼
  ┌──────────┐
  │  BUILD   │──── HAPI FHIR library constructs R4 Bundle
  └────┬─────┘     (Patient, Coverage, Condition, Procedure, Claim)
       ▼
  ┌──────────┐
  │ VALIDATE │──── NHCX StructureDefinition profile validation
  └────┬─────┘
       │
  ┌────┴────┐
  ▼         ▼
 ✅ DB    ❌ DLQ → AI Auto-Heal → Retry
```

### FHIR Resources Generated

| FHIR Resource   | Source Data                | NHCX Profile                |
|-----------------|----------------------------|-----------------------------|
| `Patient`       | PID segment (name, DOB, gender, ABHA ID) | NHCXPatient        |
| `Coverage`      | IN1 segment (policy, insurer, dates)      | NHCXCoverage       |
| `Condition`     | DG1 segment (ICD-10 diagnosis)            | NHCXCondition      |
| `Procedure`     | PR1 segment (CPT/SNOMED procedure)        | NHCXProcedure      |
| `Claim`         | Aggregated from all segments              | NHCXClaim          |
| `Bundle`        | Wraps all above resources                 | NHCXClaimBundle    |

---

## 4. Dead Letter Queue & Self-Healing Pipeline

### Standard DLQ (Implemented)

When any conversion fails, the system:
1. Catches the exception
2. Saves the **raw message** + **exact error cause** to `error_logs` table
3. Hospital staff reviews failed entries via the **DLQ tab** in the dashboard
4. Staff can click **"View Raw"** to see the original payload

### AI Auto-Heal (AI-Powered Enhancement)

This is what transforms our DLQ from a simple error log into a **self-healing pipeline**.

```
┌──────────────────┐                    ┌───────────────────────┐
│  Failed Message  │                    │   AI Auto-Heal Engine │
│                  │                    │                       │
│  MSH|^~\&|...    │───── Prompt ──────►│  Gemini API analyzes: │
│  PID|1|||        │                    │  - Error cause        │
│  (Missing ABHA)  │                    │  - Raw message context│
│                  │◄── Fixed Msg ──────│  - Infers missing data│
└──────────────────┘                    └───────────────────────┘
         │
         ▼
  ┌──────────────┐
  │  Re-convert  │──── If success → mark DLQ entry as "AUTO_HEALED"
  │  Pipeline    │──── If fail → increment retryCount, keep in DLQ
  └──────────────┘
```

#### What AI Can Auto-Heal

| Missing/Malformed Field | AI Inference Strategy                              |
|-------------------------|-----------------------------------------------------|
| **PID-8 (Gender)**      | Infer from patient's given name (Rajesh → M, Priya → F) |
| **PID-7 (DOB format)**  | Fix `15/04/1990` → `19900415`                       |
| **IN1-13 (Policy start)** | Default to current year start if missing           |
| **DG1 (Diagnosis code)**| Map free text to ICD-10 using AI                    |
| **PID-3 (ABHA ID)**     | Cannot infer — flag as `CANNOT_HEAL`, needs human   |

#### Implementation

```java
@Service
public class AiAutoHealService {

    private final GeminiClient geminiClient;
    private final ConversionService conversionService;
    private final ErrorLogRepository errorLogRepository;

    /**
     * Attempt to fix a failed message using AI inference.
     */
    public AutoHealResult attemptHeal(ErrorLog errorLog) {
        String prompt = """
            You are an HL7 v2.5 healthcare data expert. A hospital message
            failed FHIR conversion with this error:

            ERROR: %s

            RAW MESSAGE:
            %s

            Fix the message by inferring the missing/malformed field from
            context clues in the message itself. Follow these rules:
            - If gender is missing, infer from the patient's given name
            - If date format is wrong, convert to YYYYMMDD
            - If insurance dates are missing, use current calendar year
            - If a diagnosis is free text, map to the closest ICD-10 code
            - If you CANNOT confidently infer value, return: CANNOT_HEAL

            Return ONLY the corrected HL7 message, nothing else.
            """.formatted(errorLog.getErrorCause(), errorLog.getRawMessage());

        String fixedMessage = geminiClient.generate(prompt);

        if ("CANNOT_HEAL".equals(fixedMessage.trim())) {
            return AutoHealResult.cannotHeal();
        }

        try {
            String fhirJson = conversionService.convertCoverage(fixedMessage);
            errorLog.setResolved(true);
            errorLog.setAutoHealed(true);
            errorLog.setHealedMessage(fixedMessage);
            errorLogRepository.save(errorLog);
            return AutoHealResult.healed(fhirJson);
        } catch (Exception e) {
            errorLog.setRetryCount(errorLog.getRetryCount() + 1);
            errorLogRepository.save(errorLog);
            return AutoHealResult.failed(e.getMessage());
        }
    }
}
```

#### Scheduled Auto-Heal (Background Job)

```java
@Scheduled(fixedDelay = 300000) // every 5 minutes
public void autoHealDlq() {
    List<ErrorLog> unhealedErrors = errorLogRepository
        .findByResolvedFalseAndRetryCountLessThan(3);

    for (ErrorLog error : unhealedErrors) {
        log.info("Auto-healing DLQ entry #{}", error.getId());
        aiAutoHealService.attemptHeal(error);
    }
}
```

#### DLQ Dashboard Enhancements

| Column        | Description                                    |
|---------------|------------------------------------------------|
| ID            | Auto-generated primary key                     |
| Error Cause   | Exact reason for failure (e.g., "Missing PID-8")|
| Status        | `PENDING` / `AUTO_HEALED` / `CANNOT_HEAL`     |
| Retry Count   | Number of auto-heal attempts (max 3)           |
| Timestamp     | When the error occurred                        |
| Actions       | **View Raw** · **Retry** · **AI Heal**         |

---

## 5. AI Feature 1 — Natural Language to FHIR

### The Problem
Hospital staff — nurses, reception clerks, billing operators — don't know HL7 syntax. They shouldn't need to. They should be able to describe a patient admission in plain English and get a valid FHIR Bundle.

### How It Works

```
┌─────────────────────────────────────────────────────────────────────┐
│  Dashboard — Natural Language Input                                  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  "Admit patient Rajesh Sharma, male, age 34, ABHA ID          │ │
│  │   ABHA-1234-5678, diagnosed with Type 2 diabetes,             │ │
│  │   insured by ICICI Lombard, policy number POL999,             │ │
│  │   underwent blood glucose test on 15th January 2026"          │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                           │                                          │
│                    [ 🪄 Convert ]                                    │
│                           │                                          │
│                           ▼                                          │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  {                                                             │ │
│  │    "resourceType": "Bundle",                                   │ │
│  │    "type": "collection",                                       │ │
│  │    "entry": [                                                  │ │
│  │      { "resource": { "resourceType": "Patient", ... } },      │ │
│  │      { "resource": { "resourceType": "Coverage", ... } },     │ │
│  │      { "resource": { "resourceType": "Condition", ... } }     │ │
│  │    ]                                                           │ │
│  │  }                                                             │ │
│  └────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

### Implementation — Two-Step AI Pipeline

**Step 1:** LLM converts natural language → structured HL7 message.  
**Step 2:** Existing pipeline converts HL7 → FHIR (already built).

```java
@Service
public class NaturalLanguageParser {

    private final GeminiClient geminiClient;

    /**
     * Converts a plain-English patient description into a valid HL7 v2.5 message.
     */
    public String naturalLanguageToHl7(String description) {
        String prompt = """
            You are an expert in HL7 v2.5 messaging. Convert this plain-English
            patient description into a valid HL7 v2.5 message.

            Description:
            "%s"

            Generate a message with these segments (as applicable):
            - MSH: Message header (sender=HIS, receiver=NHCX, type=ADT^A01)
            - PID: Patient demographics (ID, name, DOB in YYYYMMDD, gender M/F)
            - IN1: Insurance info (policy number, insurer name, dates)
            - DG1: Diagnoses (use ICD-10 codes, e.g. E11.9 for Type 2 diabetes)
            - PR1: Procedures (use CPT codes if identifiable)

            Rules:
            - Use pipe (|) as field separator
            - Use ^ as component separator
            - Use \\r as segment terminator
            - Calculate DOB from age if only age is given (current date: 2026-02-27)
            - Map common disease names to ICD-10 codes
            - Return ONLY the HL7 message, no explanations

            Example output format:
            MSH|^~\\&|HIS|HOSP|NHCX|GOV|20260227120000||ADT^A01|MSG001|P|2.5
            PID|1||ABHA-1234||Sharma^Rajesh||19920227|M
            IN1|1|POL999||ICICI Lombard|||||||||20260101|20261231
            DG1|1||E11.9^Type 2 diabetes mellitus^ICD10|||A
            """.formatted(description);

        return geminiClient.generate(prompt);
    }
}
```

**Controller Endpoint:**

```java
@PostMapping(value = "/natural-language",
             consumes = MediaType.TEXT_PLAIN_VALUE,
             produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<String> convertNaturalLanguage(@RequestBody String description) {
    try {
        String hl7Message = naturalLanguageParser.naturalLanguageToHl7(description);
        String fhirJson = conversionService.convertCoverage(hl7Message);
        return ResponseEntity.ok(fhirJson);
    } catch (Exception e) {
        return errorResponse(e);
    }
}
```

### Example Conversions

| Natural Language Input | AI-Generated HL7 | Final FHIR Resource |
|------------------------|-------------------|---------------------|
| *"Admit Priya Verma, female, born 20 Aug 1985, ABHA-9999, diabetes"* | `PID\|1\|\|ABHA-9999\|\|Verma^Priya\|\|19850820\|F` + `DG1\|1\|\|E11.9^...` | Patient + Condition |
| *"Rajesh, 34, male, COVID positive, Star Health policy SH-100"* | `PID\|1\|\|...\|\|^Rajesh\|\|19920227\|M` + `IN1\|1\|SH-100\|\|Star Health` + `DG1\|1\|\|U07.1^COVID-19` | Patient + Coverage + Condition |
| *"Baby girl, 2 days old, jaundice, no insurance"* | `PID\|1\|\|...\|\|^Baby\|\|20260225\|F` + `DG1\|1\|\|P59.9^Neonatal jaundice` | Patient + Condition |

### Why This Feature Wins

- **Zero learning curve** — anyone can type English
- **Incredible live demo** — type a sentence, get a FHIR Bundle in 2 seconds
- **Real impact** — rural hospitals with minimal IT staff can use it immediately
- **Differentiator** — no other FHIR converter in India has this

---

## 6. AI Feature 2 — Smart Field Mapper (Zero-Config)

### The Problem
Every hospital structures their HL7 messages differently:

| Field            | Hospital A (Apollo) | Hospital B (AIIMS) | Hospital C (Rural PHC) |
|------------------|--------------------|--------------------|------------------------|
| ABHA ID          | PID-3              | PID-4              | PID-2                  |
| Patient Name     | PID-5              | PID-5              | PID-9                  |
| Gender           | PID-8              | PID-8              | PID-11                 |
| Insurance Policy | IN1-2              | IN1-36             | IN1-2                  |

Currently, a developer must **manually write** a `mapping.yml` for each hospital. With 3,500+ hospitals, that's impossible.

### The AI Solution

Feed the AI a **single sample message** from a new hospital → it auto-generates the entire mapping configuration.

```
┌───────────────────────────────────────────────────────────────────┐
│  Dashboard — Smart Mapper                                         │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Paste a sample HL7 message from the new hospital:          │ │
│  │                                                              │ │
│  │  MSH|^~\&|APOLLO_HIS|APOLLO_DEL|...                        │ │
│  │  PID|1||ABHA-7777||Gupta^Anil||19800101|M|||New Delhi       │ │
│  │  IN1|1|PLY888||National Insurance|||||||||20250101|20251231  │ │
│  │  DG1|1||I10^Essential hypertension^ICD10|||A                │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│                    [ 🧠 AI Auto-Map ]                             │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Generated mapping.yml:                                      │ │
│  │                                                              │ │
│  │  version: "1.0"                                             │ │
│  │  hospital: "Apollo Delhi"                                   │ │
│  │  mappings:                                                  │ │
│  │    patient:                                                 │ │
│  │      abhaId:     { source: "PID-3" }                       │ │
│  │      familyName: { source: "PID-5.1" }                     │ │
│  │      givenName:  { source: "PID-5.2" }                     │ │
│  │      gender:     { source: "PID-8" }                       │ │
│  │      dob:        { source: "PID-7" }                       │ │
│  │    coverage:                                                │ │
│  │      policyId:   { source: "IN1-2" }                       │ │
│  │      insurer:    { source: "IN1-4" }                       │ │
│  │    diagnosis:                                               │ │
│  │      code:       { source: "DG1-3.1" }                     │ │
│  │      desc:       { source: "DG1-3.2" }                     │ │
│  │      system:     { source: "DG1-3.3" }                     │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│            [ ✅ Apply ]        [ ✏️ Edit ]                       │
└───────────────────────────────────────────────────────────────────┘
```

### Implementation

```java
@Service
public class AiSmartMapperService {

    private final GeminiClient geminiClient;

    /**
     * Given a sample HL7 message, generate a YAML mapping profile.
     */
    public String generateMappingProfile(String sampleHl7, String hospitalName) {
        String prompt = """
            You are an HL7 v2.5 to FHIR R4 mapping expert.

            Analyze this sample HL7 message from "%s" and generate a YAML
            mapping profile that maps HL7 fields to FHIR R4 resources.

            SAMPLE MESSAGE:
            %s

            Generate a YAML file with this exact structure:
            ```yaml
            version: "1.0"
            hospital: "<hospital name>"
            mappings:
              patient:
                abhaId:
                  source: "<PID subfield containing identifier>"
                familyName:
                  source: "<PID subfield with family name>"
                givenName:
                  source: "<PID subfield with given name>"
                dateOfBirth:
                  source: "<PID subfield with DOB>"
                  format: "YYYYMMDD"
                gender:
                  source: "<PID subfield with gender>"
                  valueMap:
                    M: "male"
                    F: "female"
                    O: "other"
              coverage:
                policyId:
                  source: "<IN1 subfield with policy number>"
                insurerName:
                  source: "<IN1 subfield with insurer name>"
                validFrom:
                  source: "<IN1 subfield with start date>"
                validTo:
                  source: "<IN1 subfield with end date>"
              diagnosis:
                code:
                  source: "<DG1 subfield with code>"
                description:
                  source: "<DG1 subfield with description>"
                system:
                  source: "<DG1 subfield with coding system>"
              procedure:
                code:
                  source: "<PR1 subfield with code>"
                description:
                  source: "<PR1 subfield with description>"
            ```

            Rules:
            - Analyze the data in each field to determine what it represents
            - ABHA IDs start with "ABHA" — find which PID subfield has it
            - Names use ^ as component separator (Family^Given)
            - Dates are in YYYYMMDD format
            - Return ONLY the YAML, no explanations
            """.formatted(hospitalName, sampleHl7);

        return geminiClient.generate(prompt);
    }
}
```

**Controller Endpoint:**

```java
@PostMapping("/api/mapping/auto-generate")
public ResponseEntity<String> autoGenerateMapping(
        @RequestParam String hospitalName,
        @RequestBody String sampleHl7) {
    String yaml = aiSmartMapperService.generateMappingProfile(sampleHl7, hospitalName);
    return ResponseEntity.ok(yaml);
}

@PostMapping("/api/mapping/apply")
public ResponseEntity<String> applyMapping(@RequestBody String yamlContent) {
    // Write to mapping.yml (or hospital-specific profile)
    mappingLoader.saveProfile(yamlContent);
    return ResponseEntity.ok("{\"status\": \"Mapping applied successfully\"}");
}
```

### Onboarding Flow (Before vs After)

| Step                       | Without AI (Manual)        | With AI Smart Mapper       |
|----------------------------|-----------------------------|----------------------------|
| Receive sample message     | ✅ 1 minute                | ✅ 1 minute                |
| Analyze field positions    | ⏱ 2–4 hours (developer)   | ⏱ 5 seconds (AI)          |
| Write mapping.yml          | ⏱ 1–2 hours (developer)   | ⏱ Auto-generated          |
| Test & validate            | ⏱ 1–2 hours               | ⏱ 10 minutes (staff)      |
| **Total per hospital**     | **5–8 hours**              | **~15 minutes**            |
| **3,500 hospitals**        | **~25,000 dev hours**      | **~875 staff hours**       |

### Why This Feature Wins

- **Massive scale unlock** — onboard thousands of hospitals without developers
- **Self-service** — hospital IT teams do it themselves, no vendor dependency
- **Practical** — solves a real, painful bottleneck in India's ABDM rollout

---

## 7. AI Feature 3 — ICD-10 Auto-Coding from Free Text

### The Problem
Rural hospitals write diagnoses as free text. NHCX requires ICD-10 codes.

### Mapping Examples

| Free Text (Hindi/English)       | ICD-10 Code | Standardized Description                |
|---------------------------------|-------------|------------------------------------------|
| "sugar problem"                 | E11.9       | Type 2 diabetes mellitus                 |
| "heart attack"                  | I21.9       | Acute myocardial infarction              |
| "TB"                            | A15.0       | Tuberculosis of lung                     |
| "dengue fever"                  | A90         | Dengue fever [classical dengue]          |
| "kidney stone"                  | N20.0       | Calculus of kidney                       |
| "breathing problem in child"    | J06.9       | Acute upper respiratory infection        |
| "pregnancy check"               | Z34.00      | Encounter for supervision of normal pregnancy |

### Implementation

```java
@Service
public class AiDiagnosisCoder {

    public DiagnosisCode mapToIcd10(String freeText) {
        String prompt = """
            Map this clinical diagnosis to ICD-10-CM code.

            Input: "%s"

            Return JSON:
            {
              "code": "E11.9",
              "description": "Type 2 diabetes mellitus without complications",
              "confidence": 0.95,
              "alternatives": [
                {"code": "E11.65", "description": "Type 2 DM with hyperglycemia", "confidence": 0.80}
              ]
            }

            Rules:
            - Use ICD-10-CM (2024 edition)
            - If input is in Hindi/regional language, translate first
            - Provide confidence score (0.0 to 1.0)
            - If confidence < 0.7, provide top 3 alternatives
            """.formatted(freeText);

        return geminiClient.generateStructured(prompt, DiagnosisCode.class);
    }
}
```

### Integration in Pipeline
- If `DG1-3` contains a valid ICD-10 code → use it directly
- If `DG1-3` contains free text → call `AiDiagnosisCoder.mapToIcd10()`
- If confidence > 0.85 → auto-apply code
- If confidence < 0.85 → flag for human review, show alternatives

---

## 8. AI Feature 4 — Anomaly Detection & Fraud Flags

### Red Flags Detected

| Type                          | Example                                                  | Risk   |
|-------------------------------|----------------------------------------------------------|--------|
| Age–Diagnosis mismatch        | 5-year-old with "Age-related macular degeneration"       | 🔴 HIGH |
| Gender–Procedure mismatch     | Male patient with "Cesarean section"                     | 🔴 HIGH |
| Impossible dates              | Treatment date before admission date                     | 🟡 MED  |
| Duplicate claims              | Same patient, diagnosis, and day — submitted twice       | 🟡 MED  |
| Unusual frequency             | 50 COVID claims from one doctor in one hour              | 🔴 HIGH |
| Cost outlier                  | Claimed ₹5,00,000 for a ₹5,000 procedure                | 🔴 HIGH |

### Dashboard Integration

Each conversion in the History table gets a **risk badge**:

```
┌────┬──────────┬───────────────┬───────────────┬──────────┐
│ ID │  Status  │  Timestamp    │  Risk Level   │ Actions  │
├────┼──────────┼───────────────┼───────────────┼──────────┤
│ #1 │ ✅ SUCCESS│ 27 Feb 15:30 │ 🟢 LOW        │ View     │
│ #2 │ ✅ SUCCESS│ 27 Feb 15:35 │ 🟡 MEDIUM     │ View     │
│ #3 │ ✅ SUCCESS│ 27 Feb 15:40 │ 🔴 HIGH       │ View     │
│ #4 │ ❌ ERROR  │ 27 Feb 15:45 │ —             │ View DLQ │
└────┴──────────┴───────────────┴───────────────┴──────────┘
```

---

## 9. AI Feature 5 — Conversational Analytics Dashboard

### Chat Interface

A floating chat bubble (💬) on the dashboard lets staff ask questions in plain English:

```
┌─────────────────────────────────────────────────────┐
│ 💬 AI Assistant                              [  ✕ ] │
├─────────────────────────────────────────────────────┤
│                                                     │
│  👤 How many conversions failed this week?           │
│                                                     │
│  🤖 This week, 17 out of 234 conversions failed     │
│     (92.7% success rate). The top failure reasons:  │
│     • Missing ABHA ID — 9 failures                  │
│     • Invalid date format — 5 failures              │
│     • Malformed HL7 header — 3 failures             │
│                                                     │
│     💡 Tip: 9 failures could be auto-healed by      │
│     enabling the AI Auto-Heal feature.              │
│                                                     │
│  👤 Which hospital has the most errors?              │
│                                                     │
│  🤖 Hospital "PHC Ratnagiri" has 11 errors this     │
│     month — all due to missing ABHA IDs. I          │
│     recommend contacting their IT team to update    │
│     their HIS configuration.                        │
│                                                     │
│  ┌───────────────────────────────┐  ┌──────┐       │
│  │ Ask me anything...            │  │ Send │       │
│  └───────────────────────────────┘  └──────┘       │
└─────────────────────────────────────────────────────┘
```

### Available Queries

| Query Type          | Example                                          |
|---------------------|--------------------------------------------------|
| Volume stats        | "How many conversions today?"                    |
| Error analysis      | "What's the most common error?"                  |
| Hospital-specific   | "Show all errors from Apollo hospital"           |
| Trend analysis      | "Is our success rate improving?"                 |
| Recommendations     | "How can we reduce DLQ entries?"                 |
| Data lookup         | "Find conversions for patient ABHA-1234"         |

---

## 10. Security & Compliance

| Feature                    | Technology                     | Status       |
|----------------------------|--------------------------------|--------------|
| Authentication             | Spring Security + JWT          | Planned      |
| Role-Based Access Control  | ADMIN / OPERATOR / VIEWER      | Planned      |
| HTTPS / TLS                | SSL on Tomcat or Nginx proxy   | Planned      |
| PII Encryption (at rest)   | AES-256 on patient data        | Planned      |
| Audit Logging              | Who converted what, when       | Planned      |
| API Rate Limiting          | Bucket4j / Spring Gateway      | Planned      |
| ABDM Consent Framework     | NHA consent artifact           | Future       |

### Role Definitions

| Role       | Can Convert | View History | View DLQ | AI Chat | Manage Mapping |
|------------|-------------|--------------|----------|---------|----------------|
| ADMIN      | ✅          | ✅           | ✅       | ✅      | ✅             |
| OPERATOR   | ✅          | ✅           | ❌       | ✅      | ❌             |
| VIEWER     | ❌          | ✅           | ❌       | ✅      | ❌             |

---

## 11. Scalability & Deployment

### Development (Current)
```
mvn spring-boot:run → http://localhost:8080
```

### Docker Compose (Staging)
```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpass
      MYSQL_DATABASE: fhir_converter
    ports: ["3306:3306"]
    volumes: [mysql_data:/var/lib/mysql]

  fhir-converter:
    build: .
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/fhir_converter
      GEMINI_API_KEY: ${GEMINI_API_KEY}
    depends_on: [mysql]

volumes:
  mysql_data:
```

### Kubernetes (Production)

```
                    ┌─────────────────────┐
                    │   Nginx Ingress     │
                    └────────┬────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
     ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
     │  Pod 1       │ │  Pod 2       │ │  Pod 3       │
     │  (Converter) │ │  (Converter) │ │  (Converter) │
     └──────────────┘ └──────────────┘ └──────────────┘
              │              │              │
              └──────────────┼──────────────┘
                             ▼
              ┌──────────────────────────┐
              │  MySQL (RDS / CloudSQL)  │
              └──────────────────────────┘
```

### Monitoring Stack

```
Spring Boot Actuator → Prometheus → Grafana
         │
         └──► Alert Manager → Slack / Email
```

---

## 12. Database Design

### Entity-Relationship Diagram

```
┌─────────────────────────┐     ┌──────────────────────────┐
│    conversion_records   │     │       error_logs         │
├─────────────────────────┤     ├──────────────────────────┤
│ id           BIGINT PK  │     │ id             BIGINT PK │
│ hl7_hash     VARCHAR(64)│     │ raw_message    LONGTEXT  │
│ raw_hl7      TEXT       │     │ error_cause    TEXT       │
│ fhir_json    LONGTEXT   │     │ retry_count    INT       │
│ status       VARCHAR(20)│     │ resolved       BOOLEAN   │
│ error_message TEXT       │     │ auto_healed    BOOLEAN   │
│ risk_level   VARCHAR(10)│     │ healed_message LONGTEXT  │
│ created_at   DATETIME   │     │ created_at     DATETIME  │
└─────────────────────────┘     │ last_retry_at  DATETIME  │
                                └──────────────────────────┘

┌─────────────────────────┐
│    mapping_profiles     │
├─────────────────────────┤
│ id           BIGINT PK  │
│ hospital_name VARCHAR   │
│ yaml_content  TEXT      │
│ ai_generated  BOOLEAN   │
│ created_at   DATETIME   │
│ updated_at   DATETIME   │
└─────────────────────────┘
```

---

## 13. API Reference

| Method | Endpoint                        | Description                        | Auth       |
|--------|---------------------------------|------------------------------------|------------|
| POST   | `/api/convert/coverage`         | Convert HL7 → FHIR                | OPERATOR   |
| POST   | `/api/convert/json`             | Convert JSON → FHIR               | OPERATOR   |
| POST   | `/api/convert/csv`              | Convert CSV → FHIR                | OPERATOR   |
| POST   | `/api/convert/natural-language` | Convert English → FHIR (AI)       | OPERATOR   |
| GET    | `/api/convert/history`          | List all conversions               | VIEWER     |
| GET    | `/api/convert/errors`           | List all DLQ entries               | ADMIN      |
| POST   | `/api/dlq/{id}/retry`           | Retry a DLQ entry                  | ADMIN      |
| POST   | `/api/dlq/{id}/ai-heal`         | AI auto-heal a DLQ entry           | ADMIN      |
| POST   | `/api/mapping/auto-generate`    | AI auto-generate mapping (AI)      | ADMIN      |
| POST   | `/api/mapping/apply`            | Apply a mapping profile            | ADMIN      |
| POST   | `/api/chat`                     | Conversational analytics (AI)      | ALL        |
| GET    | `/actuator/health`              | Health check                       | PUBLIC     |
| GET    | `/actuator/prometheus`          | Metrics for monitoring             | INTERNAL   |

---

## 14. Technology Stack

| Layer            | Technology                                    |
|------------------|-----------------------------------------------|
| Language         | Java 17                                       |
| Framework        | Spring Boot 3.x                               |
| FHIR Library     | HAPI FHIR 6.10.0 (R4)                        |
| AI Engine        | Google Gemini API (gemini-pro)                |
| Database         | MySQL 8.0                                     |
| ORM              | Hibernate / Spring Data JPA                   |
| DB Migrations    | Flyway (planned)                              |
| Security         | Spring Security + JWT (planned)               |
| Monitoring       | Spring Boot Actuator + Prometheus + Grafana   |
| Containerisation | Docker + Docker Compose + Kubernetes          |
| CI/CD            | GitHub Actions                                |
| Frontend         | Vanilla HTML/CSS/JS (embedded dashboard)      |
| Messaging        | Apache Kafka / RabbitMQ (planned)             |

---

## 15. Phased Roadmap

### Phase 1 — Core MVP ✅ (Completed)
- [x] HL7 v2.5 parser
- [x] JSON and CSV parsers
- [x] YAML-driven field mapping
- [x] FHIR R4 Bundle builder (HAPI FHIR)
- [x] FHIR validation (StructureDefinition)
- [x] MySQL persistence (conversion records)
- [x] REST API (4 endpoints)
- [x] Visual dashboard (Converter + History tabs)
- [x] Dead Letter Queue (error_logs table + DLQ tab)

### Phase 2 — AI Integration (Next)
- [ ] Gemini API client service
- [ ] **Natural Language → HL7/FHIR** conversion
- [ ] **Smart Field Mapper** — AI auto-generate mapping.yml
- [ ] ICD-10 auto-coding from free text
- [ ] AI Auto-Heal for DLQ entries
- [ ] Conversational analytics chat

### Phase 3 — Security & Hardening
- [ ] Spring Security + JWT authentication
- [ ] Role-based access control
- [ ] HTTPS/TLS
- [ ] PII encryption at rest
- [ ] Replace System.out with SLF4J
- [ ] Flyway database migrations

### Phase 4 — Scale & Deploy
- [ ] Dockerfile + Docker Compose
- [ ] CI/CD with GitHub Actions
- [ ] Prometheus + Grafana monitoring
- [ ] Kubernetes deployment manifests
- [ ] Anomaly detection & fraud flagging

### Phase 5 — Enterprise Integration
- [ ] MLLP/TCP listener for direct HL7 ingestion
- [ ] Apache Kafka consumer for high-throughput
- [ ] Multi-hospital mapping profile management
- [ ] ABDM/NHCX sandbox certification
- [ ] FHIR Terminology Server integration

---

## 16. Hackathon Pitch

### 30-Second Elevator Pitch

> *"India's 3,500+ ABDM hospitals must speak FHIR — but most still speak HL7,*
> *CSV, or even plain Hindi. Our platform doesn't just convert data — it*
> *understands it. Type 'Admit Rajesh, diabetic, insured by ICICI' and get a*
> *valid FHIR Bundle in 2 seconds. When a message fails because a rural hospital*
> *forgot the gender field, our AI infers 'Male' from the name 'Rajesh' and*
> *auto-heals the pipeline. Need to onboard a new hospital? Paste one sample*
> *message and AI generates the entire mapping config — zero developer time.*
> *It's not a converter. It's India's first AI-powered healthcare data bridge."*

### Key Differentiators

| Feature                              | Other Solutions      | Our Solution              |
|--------------------------------------|----------------------|---------------------------|
| HL7/JSON/CSV → FHIR conversion      | ✅ Manual config     | ✅ + **AI auto-map**      |
| Natural language input               | ❌                   | ✅ Type English → FHIR    |
| Self-healing error pipeline          | ❌ Manual retry      | ✅ AI auto-heal DLQ       |
| Hospital onboarding                  | Weeks per hospital   | **15 minutes** (AI)       |
| Free-text → ICD-10 coding           | ❌                   | ✅ AI auto-code           |
| Fraud / anomaly detection            | ❌                   | ✅ AI risk scoring        |
| Conversational dashboard             | ❌                   | ✅ Chat with your data    |

---

*Document Version: 1.0 — Created 27 Feb 2026*  
*Project: NHCX FHIR Converter — AI-Powered Healthcare Data Platform*
