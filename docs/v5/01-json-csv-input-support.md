# JSON and CSV Input Support — Complete Guide

This document covers the multi-format input layer added to the FHIR converter, enabling JSON and CSV inputs alongside the existing HL7 pipe-delimited format.

---

## 1. Architecture

All three input formats produce the same `Hl7Data` POJO. The downstream pipeline is shared:

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│ HL7 (text)  │     │ JSON         │     │ CSV (text)   │
│ /coverage   │     │ /json        │     │ /csv         │
└──────┬──────┘     └──────┬───────┘     └──────┬───────┘
       │                   │                    │
  Hl7Parser          JsonInputParser      CsvInputParser
       │                   │                    │
       └───────────────────┴────────────────────┘
                           │
                      Hl7Data POJO
                           │
              ┌────────────┴────────────┐
              │  Shared Pipeline:       │
              │  Map → Build → Validate │
              │  → Save to DB          │
              └─────────────────────────┘
```

---

## 2. Endpoints

| Endpoint | Method | Content-Type | Input Format |
|---|---|---|---|
| `/api/convert/coverage` | POST | `text/plain` | HL7 pipe-delimited |
| `/api/convert/json` | POST | `application/json` | JSON object |
| `/api/convert/csv` | POST | `text/plain` | CSV with header row |
| `/api/convert/history` | GET | — | Returns all records |

---

## 3. JSON Input Format

**Endpoint:** `POST /api/convert/json`  
**Header:** `Content-Type: application/json`

```json
{
  "abhaId": "ABHA123",
  "familyName": "Sharma",
  "givenName": "Rahul",
  "dateOfBirth": "19900415",
  "gender": "M",
  "admitDate": "20231001",
  "dischargeDate": "20231005",
  "policyNumber": "POL999",
  "diagnoses": [
    { "code": "COVID19", "description": "COVID-19 Infection" },
    { "code": "A00", "description": "Cholera" }
  ],
  "procedures": [
    { "code": "PR01", "description": "Ventilation", "date": "20231002" }
  ]
}
```

| Field | Required | Description |
|---|---|---|
| `abhaId` | Yes | ABHA identifier |
| `familyName` | No | Patient surname |
| `givenName` | No | Patient first name |
| `dateOfBirth` | No | Format: `YYYYMMDD` |
| `gender` | No | `M`, `F`, or `O` |
| `admitDate` | No | Format: `YYYYMMDD` |
| `dischargeDate` | No | Format: `YYYYMMDD` |
| `policyNumber` | No | Insurance policy ID |
| `diagnoses` | No | Array of `{ code, description }` |
| `procedures` | No | Array of `{ code, description, date }` |

---

## 4. CSV Input Format

**Endpoint:** `POST /api/convert/csv`  
**Header:** `Content-Type: text/plain`

```csv
abhaId,familyName,givenName,dateOfBirth,gender,admitDate,dischargeDate,policyNumber,diagCode,diagDesc,procCode,procDesc,procDate
ABHA123,Sharma,Rahul,19900415,M,20231001,20231005,POL999,COVID19,COVID-19,PR01,Ventilation,20231002
```

**Rules:**
- First row = header (column names, case-insensitive)
- Second row = data (single patient per request)
- Multiple diagnoses/procedures: use semicolons within the cell  
  e.g., `diagCode = "COVID19;A00"` and `diagDesc = "COVID-19;Cholera"`

---

## 5. Postman Test Cases

### Test 1: JSON Input (valid)
- **POST** `http://localhost:8080/api/convert/json`
- **Header:** `Content-Type: application/json`
- **Body:**
```json
{
  "abhaId": "ABHA999",
  "familyName": "Patel",
  "givenName": "Ankit",
  "dateOfBirth": "19850212",
  "gender": "M",
  "diagnoses": [{"code": "J06.9", "description": "Acute upper respiratory infection"}]
}
```
- **Expected:** 200 OK + FHIR Bundle JSON

### Test 2: CSV Input (valid)
- **POST** `http://localhost:8080/api/convert/csv`
- **Header:** `Content-Type: text/plain`
- **Body:**
```
abhaId,familyName,givenName,dateOfBirth,gender,diagCode,diagDesc
ABHA777,Kumar,Priya,19920305,F,E11,Type 2 diabetes mellitus
```
- **Expected:** 200 OK + FHIR Bundle JSON

### Test 3: CSV with Multiple Diagnoses
- **Body:**
```
abhaId,familyName,givenName,dateOfBirth,gender,diagCode,diagDesc
ABHA888,Singh,Ravi,19880710,M,COVID19;A00,COVID-19;Cholera
```
- **Expected:** 200 OK + Bundle with 2 Condition resources

### Test 4: Invalid JSON
- **Body:** `this is not json`
- **Expected:** 500 + error message about JSON parsing failure

---

## 6. Files Changed / Created

| File | Action | Description |
|---|---|---|
| `JsonInputParser.java` | **New** | Parses JSON into `Hl7Data` using Jackson |
| `CsvInputParser.java` | **New** | Parses CSV (header + data row) into `Hl7Data` |
| `ConversionService.java` | Modified | Extracted shared pipeline into `convertFromData()`, added `convertFromJson()` and `convertFromCsv()` |
| `ConversionController.java` | Modified | Added `/api/convert/json` and `/api/convert/csv` endpoints |
| `JsonInputParserTest.java` | **New** | 3 unit tests (full, minimal, invalid) |
| `CsvInputParserTest.java` | **New** | 5 unit tests (full, multi-diag, minimal, empty, header-only) |
