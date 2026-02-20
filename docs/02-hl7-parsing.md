# 2. HL7 Parsing — How It Works

## What Is HL7 v2?

**HL7 v2** (Health Level Seven, Version 2) is a messaging standard used by hospitals since the 1990s. It's a **pipe-delimited text format** — think of it as a CSV but using `|` instead of commas.

### Example Message
```
MSH|^~\&|HIS|HOSPITAL|||20260220||ADT^A01|12345|P|2.5
PID|1||ABHA123||Sharma^Rahul||19900415|M
PV1|1|I||||||DR001^Kumar^Anil
```

Each line is called a **segment**:
- `MSH` = Message Header (metadata about the message)
- `PID` = Patient Identification (patient demographics)
- `PV1` = Patient Visit (encounter info)

For our MVP, we only care about the **PID** segment.

---

## How PID Fields Are Organized

The PID segment uses two levels of delimiters:

### Level 1: Pipe `|` — separates fields
```
PID | 1  |   | ABHA123 |   | Sharma^Rahul |   | 19900415 | M
 ↑    ↑   ↑      ↑      ↑        ↑          ↑      ↑       ↑
 0    1   2      3      4        5          6      7       8
```

| Index | Field Name | Value | Description |
|-------|-----------|-------|-------------|
| 0 | Segment ID | `PID` | Always "PID" for this segment |
| 1 | Set ID | `1` | Sequence number (usually 1) |
| 2 | Patient ID | *(empty)* | External patient ID |
| 3 | **Patient ID List** | `ABHA123` | **← ABHA identifier** |
| 4 | Alternate ID | *(empty)* | Alternate patient ID |
| 5 | **Patient Name** | `Sharma^Rahul` | **← Family^Given name** |
| 6 | Mother's Maiden | *(empty)* | Mother's maiden name |
| 7 | **Date of Birth** | `19900415` | **← DOB in YYYYMMDD** |
| 8 | **Gender** | `M` | **← M=Male, F=Female** |

### Level 2: Caret `^` — separates sub-fields
```
Sharma ^ Rahul
  ↑        ↑
  0        1
  Family   Given
```

---

## Our Parser Code — Step by Step

**File:** `src/main/java/com/nhcx/fhirconverter/parser/Hl7Parser.java`

### Step 1: Find the PID line

A real HL7 message can have many segments (MSH, PID, PV1, etc.). We need to find the PID line:

```java
private String findPidSegment(String rawHl7) {
    // Split the message into lines
    String[] lines = rawHl7.split("\\r?\\n");

    // Look for the line that starts with "PID"
    for (String line : lines) {
        if (line.trim().startsWith("PID")) {
            return line.trim();
        }
    }

    // If no PID found, throw an error
    throw new IllegalArgumentException(
        "No PID segment found in the HL7 message."
    );
}
```

**Why `\\r?\\n`?** HL7 messages can use `\r\n` (Windows) or `\n` (Unix) line endings. The regex `\r?\n` handles both.

### Step 2: Split by pipe to get fields

```java
String[] fields = pidLine.split("\\|");
```

**Why `\\|`?** In Java regex, `|` is a special character (means "OR"). We escape it with `\\` to match a literal pipe character.

After splitting `PID|1||ABHA123||Sharma^Rahul||19900415|M`:
```
fields[0] = "PID"
fields[1] = "1"
fields[2] = ""
fields[3] = "ABHA123"       ← ABHA ID
fields[4] = ""
fields[5] = "Sharma^Rahul"  ← Patient name (needs further splitting)
fields[6] = ""
fields[7] = "19900415"      ← Date of birth
fields[8] = "M"             ← Gender
```

### Step 3: Extract ABHA ID (field 3)

```java
if (fields.length > 3) {
    data.setAbhaId(fields[3].trim());
}
```

Simple — just grab index 3 from the array.

### Step 4: Split name sub-fields by caret

```java
if (fields.length > 5) {
    String nameField = fields[5];              // "Sharma^Rahul"
    String[] nameParts = nameField.split("\\^"); // Split by ^

    if (nameParts.length > 0) {
        data.setFamilyName(nameParts[0].trim()); // "Sharma"
    }
    if (nameParts.length > 1) {
        data.setGivenName(nameParts[1].trim());  // "Rahul"
    }
}
```

**Why `\\^`?** Same reason as pipe — `^` has special meaning in regex, so we escape it.

### Step 5: Extract DOB and Gender

```java
if (fields.length > 7) {
    data.setDateOfBirth(fields[7].trim());  // "19900415"
}
if (fields.length > 8) {
    data.setGender(fields[8].trim());       // "M"
}
```

### Step 6: Return the DTO

All extracted data is stored in an `Hl7Data` object:

```java
Hl7Data {
    abhaId = "ABHA123"
    familyName = "Sharma"
    givenName = "Rahul"
    dateOfBirth = "19900415"
    gender = "M"
}
```

---

## The Hl7Data DTO

**File:** `src/main/java/com/nhcx/fhirconverter/model/Hl7Data.java`

This is a simple POJO (Plain Old Java Object) with getters and setters:

```java
public class Hl7Data {
    private String abhaId;       // "ABHA123"
    private String familyName;   // "Sharma"
    private String givenName;    // "Rahul"
    private String dateOfBirth;  // "19900415"
    private String gender;       // "M"

    // + getters and setters for each field
}
```

**Why a separate class?** Instead of passing 5 separate variables around, we bundle them into one object. This makes the code cleaner:
```java
// Without DTO — messy
String buildBundle(String abhaId, String family, String given, String dob, String gender)

// With DTO — clean
String buildBundle(Hl7Data data, MappingProfile profile)
```

---

## What Happens If Parsing Fails?

If the input doesn't contain a `PID` segment, the parser throws an `IllegalArgumentException`. This is caught by the `ConversionService`, which:
1. Saves a record with `status = "ERROR"` and the error message
2. Re-throws the exception
3. The controller catches it and returns HTTP 500 with an error JSON
