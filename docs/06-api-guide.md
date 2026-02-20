# 6. REST API Guide

## Endpoints Summary

| Method | URL | Input | Output |
|--------|-----|-------|--------|
| `POST` | `/api/convert/coverage` | HL7 plain text | FHIR Bundle JSON |
| `GET` | `/api/convert/history` | *(none)* | List of conversion records |

Base URL: `http://localhost:8080`

---

## POST /api/convert/coverage

Converts an HL7 ADT PID message to a FHIR CoverageEligibilityRequest Bundle.

### Request

```
POST /api/convert/coverage
Content-Type: text/plain
```

**Body** — raw HL7 message (plain text):
```
PID|1||ABHA123||Sharma^Rahul||19900415|M
```

### Success Response (200 OK)

```json
{
  "resourceType" : "Bundle",
  "type" : "collection",
  "entry" : [
    {
      "resource" : {
        "resourceType" : "Patient",
        "identifier" : [ {
          "system" : "https://ndhm.gov.in/abha",
          "value" : "ABHA123"
        } ],
        "name" : [ {
          "family" : "Sharma",
          "given" : [ "Rahul" ]
        } ],
        "birthDate" : "1990-04-15",
        "gender" : "male"
      }
    },
    {
      "resource" : {
        "resourceType" : "Coverage",
        "status" : "active",
        "beneficiary" : {
          "reference" : "Patient/ABHA123"
        },
        "payor" : [ {
          "reference" : "Organization/NHCX"
        } ]
      }
    },
    {
      "resource" : {
        "resourceType" : "CoverageEligibilityRequest",
        "status" : "active",
        "purpose" : [ "validation" ],
        "patient" : {
          "reference" : "Patient/ABHA123"
        },
        "created" : "2026-02-20",
        "insurer" : {
          "reference" : "Organization/NHCX"
        }
      }
    }
  ]
}
```

### Error Response (500 Internal Server Error)

```json
{
  "error": "Conversion failed",
  "message": "Conversion failed: No PID segment found in the HL7 message."
}
```

### curl Examples

```bash
# Basic conversion
curl -X POST http://localhost:8080/api/convert/coverage \
  -H "Content-Type: text/plain" \
  -d "PID|1||ABHA123||Sharma^Rahul||19900415|M"

# Female patient
curl -X POST http://localhost:8080/api/convert/coverage \
  -H "Content-Type: text/plain" \
  -d "PID|1||ABHA456||Patel^Priya||19850722|F"

# Multi-line HL7 message (only PID is used)
curl -X POST http://localhost:8080/api/convert/coverage \
  -H "Content-Type: text/plain" \
  -d "MSH|^~\&|HIS|HOSPITAL|||20260220||ADT^A01|12345|P|2.5
PID|1||ABHA789||Kumar^Amit||20000101|M"

# Missing PID (will return error)
curl -X POST http://localhost:8080/api/convert/coverage \
  -H "Content-Type: text/plain" \
  -d "MSH|^~\&|HIS|HOSPITAL"
```

---

## GET /api/convert/history

Returns all past conversion records from the database.

### Request

```
GET /api/convert/history
```

### Response (200 OK)

```json
[
  {
    "id": 1,
    "rawHl7": "PID|1||ABHA123||Sharma^Rahul||19900415|M",
    "fhirJson": "{\"resourceType\":\"Bundle\",...}",
    "status": "SUCCESS",
    "errorMessage": null,
    "createdAt": "2026-02-20T15:48:00"
  },
  {
    "id": 2,
    "rawHl7": "MSH|^~\\&|HIS|HOSPITAL",
    "fhirJson": null,
    "status": "ERROR",
    "errorMessage": "No PID segment found in the HL7 message.",
    "createdAt": "2026-02-20T15:49:30"
  }
]
```

### curl Example

```bash
curl http://localhost:8080/api/convert/history
```

---

## Controller Code Explained

**File:** `src/main/java/com/nhcx/fhirconverter/controller/ConversionController.java`

```java
@RestController                       // Returns JSON (not HTML pages)
@RequestMapping("/api/convert")       // All URLs start with /api/convert
public class ConversionController {

    private final ConversionService conversionService;

    // Spring injects ConversionService automatically
    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @PostMapping(
        value = "/coverage",
        consumes = MediaType.TEXT_PLAIN_VALUE,       // Accepts text/plain
        produces = MediaType.APPLICATION_JSON_VALUE  // Returns application/json
    )
    public ResponseEntity<String> convertCoverage(@RequestBody String rawHl7) {
        try {
            String fhirJson = conversionService.convertCoverage(rawHl7);
            return ResponseEntity.ok(fhirJson);       // 200 OK + JSON body
        } catch (Exception e) {
            String errorJson = String.format(
                "{\"error\": \"Conversion failed\", \"message\": \"%s\"}",
                e.getMessage().replace("\"", "'")
            );
            return ResponseEntity.internalServerError().body(errorJson);  // 500
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<ConversionRecord>> getHistory() {
        return ResponseEntity.ok(conversionService.getHistory());
    }
}
```

### Key Annotations Explained

| Annotation | Purpose |
|------------|---------|
| `@RestController` | Combines `@Controller` + `@ResponseBody` — returns data, not views |
| `@RequestMapping("/api/convert")` | Base URL prefix for all methods in this class |
| `@PostMapping("/coverage")` | Handles `POST /api/convert/coverage` |
| `@GetMapping("/history")` | Handles `GET /api/convert/history` |
| `@RequestBody` | Takes the HTTP request body and passes it as a Java parameter |
| `consumes = TEXT_PLAIN_VALUE` | Only accepts requests with `Content-Type: text/plain` |
| `produces = APPLICATION_JSON_VALUE` | Sets response `Content-Type: application/json` |
