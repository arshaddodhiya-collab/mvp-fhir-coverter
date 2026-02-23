# Extending the Converter for Claims & Pre-Authorization

In our MVP, we only handled **Coverage Eligibility** (checking if insurance was active). This required parsing the `PID` (Patient Identification) segment to generate `Patient`, `Coverage`, and `CoverageEligibilityRequest` resources.

The next major step in supporting the NHCX architecture is creating **Claims** and **Pre-Authorizations**. 
To submit a digital bill (Claim) to the insurance company, we need much more detail than just the patient's name and ID. We need to know the diagnosis (why they are being treated), the procedures (what was done to them), the encounter details (when they were admitted/discharged), and the insurance policy details.

This extension updates our custom converter to ingest these additional HL7 segments and output their corresponding FHIR R4 resources.

---

## What We Added

1. **New YAML Mapping Profile**
   - `hl7_adt_v2_claim.yaml` was created to define how fields from `DG1`, `PR1`, `PV1`, and `IN1` map to the `Condition`, `Procedure`, `Encounter`, and `Claim` FHIR resources.

2. **Expanded `Hl7Data` Model**
   - `src/main/java/com/nhcx/fhirconverter/model/Hl7Data.java`
   - We added new fields and nested classes (`Diagnosis`, `Procedure`) to temporarily hold the data extracted from the HL7 message before it becomes JSON.

3. **Enhanced `Hl7Parser`**
   - `src/main/java/com/nhcx/fhirconverter/parser/Hl7Parser.java`
   - The parser can now search for and split additional segments:
     - `PV1` (Patient Visit): Specifically fields 44 (Admit Date) and 45 (Discharge Date).
     - `IN1` (Insurance): Field 36 for the Policy Number.
     - `DG1` (Diagnosis): Extracts Diagnosis Code and Description. It uses `findAllSegments` because a patient can have multiple diagnoses.
     - `PR1` (Procedure): Extracts Procedure Code, Description, and Date. Also supports multiple procedures.

4. **New FHIR Builders in `FhirBundleBuilder`**
   - `src/main/java/com/nhcx/fhirconverter/fhir/FhirBundleBuilder.java`
   - Added `buildEncounter()`, `buildCondition()`, `buildProcedure()`, and `buildClaim()`. 
   - The main `buildBundle` method now checks if there are diagnoses, procedures, or admission dates in the parsed data, and if so, constructs the relevant FHIR JSON objects and adds them to the final Bundle list.

---

## How It Works in Practice

### 1. The Raw HL7 Message (Example)

A typical HL7 file for a claim might look like this:

```text
PID|1||ABHA123||Sharma^Rahul||19900415|M
PV1|1|I||||||||||||||||||||||||||||||||||||||||||20231001|20231005
IN1|1|||||||||||||||||||||||||||||||||||POL987654321
DG1|1||A09^Infectious gastroenteritis
PR1|1||90610^Injection
```

### 2. The Parser
When `Hl7Parser` reads this text:
- It finds `PID` and extracts the basic patient info.
- It finds `PV1` and grabs `20231001` (Admit) and `20231005` (Discharge).
- It finds `IN1` and grabs `POL987654321` (Policy Number).
- It finds `DG1` and splits by `^` to extract `A09` (Code) and `Infectious gastroenteritis` (Description).
- It finds `PR1` and splits by `^` to extract `90610` (Code) and `Injection` (Description).

### 3. The Builder
`FhirBundleBuilder` takes that data and constructs nested `LinkedHashMap` structures that perfectly map to the FHIR R4 JSON format.

If you send the above HL7 string to the API endpoint (`/api/convert/coverage`), you will now see `Condition`, `Procedure`, `Encounter`, and `Claim` resources appended directly into the `entry` array of the JSON response.

---

## Why Is This Important?

- **Real-World Utility:** Submitting claims (getting paid) is the primary motivation for hospitals strictly adopting the NHCX format.
- **Complexity Management:** By separating the POJO holding the data (`Hl7Data`), the parsing logic (`Hl7Parser`), and the JSON generation (`FhirBundleBuilder`), the codebase remains highly readable even as the data requirements for Claims become large.
