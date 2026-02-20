# 3. YAML Mapping — How It Works

## Why Use YAML for Mapping?

Instead of hardcoding "field 3 = ABHA ID" inside Java code, we define mapping rules in a **YAML configuration file**. This gives us:

| Benefit | Explanation |
|---------|-------------|
| **No recompilation** | Change mappings without touching Java code |
| **Readability** | YAML is human-readable — even non-developers can understand it |
| **Extensibility** | Add new segment mappings by editing a text file |
| **Documentation** | The YAML itself documents what maps to what |

---

## The Mapping File

**File:** `src/main/resources/mapping_profiles/hl7_adt_v2_coverage.yaml`

```yaml
# Metadata about this mapping profile
profile: hl7_adt_v2_to_coverage
version: "1.0"
sourceFormat: HL7v2_ADT
targetFormat: FHIR_R4

# The actual mapping rules
segments:
  PID:                                    # HL7 segment name
    fields:
      3:                                  # HL7 field index (pipe position)
        fhirPath: "Patient.identifier[abha].value"
        description: "ABHA ID"

      5:                                  # Field 5 has sub-fields (split by ^)
        subfields:
          1:                              # Sub-field index (caret position)
            fhirPath: "Patient.name.family"
            description: "Family name (surname)"
          2:
            fhirPath: "Patient.name.given"
            description: "Given name (first name)"

      7:
        fhirPath: "Patient.birthDate"
        description: "Date of birth (YYYYMMDD → YYYY-MM-DD)"

      8:
        fhirPath: "Patient.gender"
        description: "Gender (M/F → male/female)"
```

### How to Read This

The YAML says:
- **PID field 3** → maps to `Patient.identifier[abha].value` (the ABHA ID)
- **PID field 5, sub-field 1** → maps to `Patient.name.family` (surname)
- **PID field 5, sub-field 2** → maps to `Patient.name.given` (first name)
- **PID field 7** → maps to `Patient.birthDate` (date of birth)
- **PID field 8** → maps to `Patient.gender` (gender code)

---

## How YAML Is Loaded into Java

### The MappingProfile POJO

**File:** `src/main/java/com/nhcx/fhirconverter/mapping/MappingProfile.java`

SnakeYAML maps YAML keys directly to Java field names:

```yaml
# YAML                          # Java class
profile: hl7_adt_v2_to_coverage   →   private String profile;
version: "1.0"                    →   private String version;
sourceFormat: HL7v2_ADT           →   private String sourceFormat;
targetFormat: FHIR_R4             →   private String targetFormat;
segments:                         →   private Map<String, Map<String, Map<String, Object>>> segments;
  PID:
    fields:
      3:
        fhirPath: "..."
```

**Why nested Maps?** The YAML has a deep, flexible structure:
```
segments → PID → fields → 3 → {fhirPath: "...", description: "..."}
```
Using `Map<String, Map<String, Map<String, Object>>>` allows SnakeYAML to map this automatically without creating dozens of tiny classes.

### The MappingLoader

**File:** `src/main/java/com/nhcx/fhirconverter/mapping/MappingLoader.java`

```java
@Component
public class MappingLoader {

    private static final String MAPPING_FILE =
        "mapping_profiles/hl7_adt_v2_coverage.yaml";

    public MappingProfile loadProfile() {
        // 1. Create a SnakeYAML parser
        Yaml yaml = new Yaml();

        // 2. Load the file from the classpath
        //    "classpath" = everything inside src/main/resources/
        //    At runtime, Maven packages resources into the JAR
        InputStream inputStream = getClass()
            .getClassLoader()
            .getResourceAsStream(MAPPING_FILE);

        // 3. Check file exists
        if (inputStream == null) {
            throw new RuntimeException("Mapping file not found!");
        }

        // 4. Parse YAML → Java object
        //    SnakeYAML matches YAML keys to Java field names automatically
        MappingProfile profile = yaml.loadAs(inputStream, MappingProfile.class);

        return profile;
    }
}
```

### How `loadAs()` Works Internally

```
YAML File                          Java Object
─────────                          ───────────
profile: "hl7_adt_v2..."    →     profile = "hl7_adt_v2..."
version: "1.0"              →     version = "1.0"
segments:                    →     segments = {
  PID:                                 "PID": {
    fields:                                "fields": {
      3:                                       3: {
        fhirPath: "..."                            "fhirPath": "...",
        description: "..."                         "description": "..."
                                               }
                                           }
                                       }
                                   }
```

SnakeYAML:
1. Reads the YAML text
2. Sees `profile: "hl7_adt_v2..."` → looks for `setProfile()` in `MappingProfile` → calls it
3. Sees `segments:` → it's a nested structure → creates `Map<String, Map<...>>` → calls `setSegments()`
4. Returns the fully populated `MappingProfile` object

---

## How Mapping Is Applied

In the MVP, the `FhirBundleBuilder` uses the `MappingProfile` to **validate/confirm** that the right fields are being mapped. The builder "knows" the structure:

```java
// The builder reads data from Hl7Data and places it where
// the mapping profile says it should go:
//
// profile says: field 3 → Patient.identifier[abha].value
// builder does: patient.put("identifier", [{system: "abha", value: data.getAbhaId()}])
//
// profile says: field 5.1 → Patient.name.family
// builder does: name.put("family", data.getFamilyName())
```

In a production system, the mapping engine would be **dynamic** — reading `fhirPath` values and constructing the JSON programmatically. For this MVP, we keep it explicit and readable.

---

## Adding New Mappings

To map a new HL7 field, just add it to the YAML:

```yaml
segments:
  PID:
    fields:
      # ... existing fields ...
      13:
        fhirPath: "Patient.telecom.value"
        description: "Phone number"
```

Then update `Hl7Parser` to extract field 13, `Hl7Data` to hold it, and `FhirBundleBuilder` to include it in the Patient resource.
