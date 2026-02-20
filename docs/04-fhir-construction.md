# 4. FHIR Construction — How the Bundle Is Built

## What Is FHIR?

**FHIR** (Fast Healthcare Interoperability Resources, pronounced "fire") is a modern standard for exchanging healthcare data. It uses **JSON** (or XML) and organizes data into **Resources** like Patient, Coverage, Encounter, etc.

NHCX (National Health Claims Exchange) uses FHIR R4 as its data format.

---

## What We Build

Our converter creates a **FHIR Bundle** containing three resources:

```
Bundle (type: collection)
├── Patient                          ← Who the patient is
├── Coverage                         ← What insurance coverage they have
└── CoverageEligibilityRequest       ← Request to check if they're eligible
```

---

## The Bundle Structure

**File:** `src/main/java/com/nhcx/fhirconverter/fhir/FhirBundleBuilder.java`

### Outer Bundle

```java
Map<String, Object> bundle = new LinkedHashMap<>();
bundle.put("resourceType", "Bundle");
bundle.put("type", "collection");
bundle.put("entry", entries);  // List of resource entries
```

Produces:
```json
{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": [ ... ]
}
```

**Why `LinkedHashMap`?** Regular `HashMap` doesn't preserve insertion order. `LinkedHashMap` keeps keys in the order we add them, which makes the JSON output predictable and readable.

### Each Entry Wraps a Resource

```java
private Map<String, Object> wrapResource(Map<String, Object> resource) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("resource", resource);
    return entry;
}
```

FHIR requires each resource in a Bundle to be inside `{ "resource": { ... } }`.

---

## Resource 1: Patient

Maps HL7 data to FHIR Patient fields:

```java
private Map<String, Object> buildPatient(Hl7Data data) {
    Map<String, Object> patient = new LinkedHashMap<>();
    patient.put("resourceType", "Patient");

    // ABHA Identifier
    Map<String, Object> identifier = new LinkedHashMap<>();
    identifier.put("system", "https://ndhm.gov.in/abha");
    identifier.put("value", data.getAbhaId());   // "ABHA123"
    patient.put("identifier", Collections.singletonList(identifier));

    // Name
    Map<String, Object> name = new LinkedHashMap<>();
    name.put("family", data.getFamilyName());             // "Sharma"
    name.put("given", Collections.singletonList(
        data.getGivenName()));                             // ["Rahul"]
    patient.put("name", Collections.singletonList(name));

    // Birth Date (YYYYMMDD → YYYY-MM-DD)
    patient.put("birthDate", formatDate(data.getDateOfBirth()));

    // Gender (M → male, F → female)
    patient.put("gender", mapGender(data.getGender()));

    return patient;
}
```

**Output:**
```json
{
  "resourceType": "Patient",
  "identifier": [{ "system": "https://ndhm.gov.in/abha", "value": "ABHA123" }],
  "name": [{ "family": "Sharma", "given": ["Rahul"] }],
  "birthDate": "1990-04-15",
  "gender": "male"
}
```

### Why Arrays for `identifier`, `name`, and `given`?

FHIR allows a patient to have:
- Multiple identifiers (ABHA, Aadhaar, MRN, etc.)
- Multiple names (legal name, nickname, etc.)
- Multiple given names (first + middle names)

So these fields are **always arrays** in FHIR, even if we only have one value.

---

## Resource 2: Coverage

Represents the patient's health insurance coverage:

```java
private Map<String, Object> buildCoverage(Hl7Data data) {
    Map<String, Object> coverage = new LinkedHashMap<>();
    coverage.put("resourceType", "Coverage");
    coverage.put("status", "active");

    // Who is covered
    Map<String, Object> beneficiary = new LinkedHashMap<>();
    beneficiary.put("reference", "Patient/" + data.getAbhaId());
    coverage.put("beneficiary", beneficiary);

    // Who pays (NHCX organization)
    Map<String, Object> payor = new LinkedHashMap<>();
    payor.put("reference", "Organization/NHCX");
    coverage.put("payor", Collections.singletonList(payor));

    return coverage;
}
```

**Output:**
```json
{
  "resourceType": "Coverage",
  "status": "active",
  "beneficiary": { "reference": "Patient/ABHA123" },
  "payor": [{ "reference": "Organization/NHCX" }]
}
```

### What Are References?

In FHIR, resources reference each other using `{ "reference": "ResourceType/ID" }`. This is like a foreign key in a database:
- `"Patient/ABHA123"` → points to the Patient with ID ABHA123
- `"Organization/NHCX"` → points to the NHCX organization

---

## Resource 3: CoverageEligibilityRequest

A request sent to an insurer to check if a patient is eligible for coverage:

```java
private Map<String, Object> buildCoverageEligibilityRequest(Hl7Data data) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("resourceType", "CoverageEligibilityRequest");
    request.put("status", "active");
    request.put("purpose", Collections.singletonList("validation"));

    // Which patient
    Map<String, Object> patient = new LinkedHashMap<>();
    patient.put("reference", "Patient/" + data.getAbhaId());
    request.put("patient", patient);

    // When created
    request.put("created", LocalDate.now().toString());  // "2026-02-20"

    // Which insurer
    Map<String, Object> insurer = new LinkedHashMap<>();
    insurer.put("reference", "Organization/NHCX");
    request.put("insurer", insurer);

    return request;
}
```

**Output:**
```json
{
  "resourceType": "CoverageEligibilityRequest",
  "status": "active",
  "purpose": ["validation"],
  "patient": { "reference": "Patient/ABHA123" },
  "created": "2026-02-20",
  "insurer": { "reference": "Organization/NHCX" }
}
```

---

## Helper Methods

### Date Formatting

HL7 uses `YYYYMMDD` (e.g., `19900415`), FHIR uses `YYYY-MM-DD` (e.g., `1990-04-15`):

```java
private String formatDate(String hl7Date) {
    if (hl7Date == null || hl7Date.length() != 8) {
        return hl7Date;  // Return as-is if not 8 chars
    }
    // Java's BASIC_ISO_DATE parser handles YYYYMMDD
    LocalDate date = LocalDate.parse(hl7Date, DateTimeFormatter.BASIC_ISO_DATE);
    return date.toString();  // Outputs "1990-04-15"
}
```

### Gender Mapping

HL7 uses single letters, FHIR uses full words:

```java
private String mapGender(String hl7Gender) {
    return switch (hl7Gender.toUpperCase()) {
        case "M" -> "male";
        case "F" -> "female";
        case "O" -> "other";
        default  -> "unknown";
    };
}
```

---

## How Jackson Serializes to JSON

The final step converts the Java `Map` tree to a JSON string:

```java
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.enable(SerializationFeature.INDENT_OUTPUT);  // Pretty-print

String json = objectMapper.writeValueAsString(bundle);
```

Jackson traverses the nested Maps/Lists and produces:
```
Map{"resourceType": "Bundle", "entry": List[...]}
                    ↓ Jackson
{"resourceType": "Bundle", "entry": [...]}
```

---

## Why Not Use HAPI FHIR Library?

| Approach | Pros | Cons |
|---------|------|------|
| **Manual Maps (our approach)** | Simple, no extra dependencies, full control, beginner-friendly | Must build structure manually |
| **HAPI FHIR library** | Type-safe, validates FHIR rules, rich API | Heavy dependency (~30MB), steeper learning curve |

For an MVP focused on learning, manual construction is clearer. For production, consider HAPI FHIR.
