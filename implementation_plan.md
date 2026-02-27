# AI Integration — Natural Language to HL7/FHIR & Smart Field Mapper

Implement two AI features from doc [09-ai-integration-ideas.md](file:///home/artem/test/fhir-converter/docs/09-ai-integration-ideas.md):
1. **Natural Language to HL7/FHIR** — users type plain English → get FHIR Bundle
2. **Smart Field Mapper** — paste a sample HL7 → AI auto-generates `mapping.yml`

Both use the **Google Gemini REST API** (no SDK dependency needed — just `RestTemplate`).

---

## Proposed Changes

### Gemini API Client

#### [NEW] [GeminiClient.java](file:///home/artem/test/fhir-converter/src/main/java/com/nhcx/fhirconverter/ai/GeminiClient.java)

Spring `@Service` that calls the Gemini `generateContent` REST endpoint. Reads `gemini.api.key` from properties. Parses the JSON response and extracts the text content.

#### [MODIFY] [application.properties](file:///home/artem/test/fhir-converter/src/main/resources/application.properties)

Add `gemini.api.key=YOUR_API_KEY_HERE` placeholder.

---

### Natural Language to HL7/FHIR

#### [NEW] [NaturalLanguageParser.java](file:///home/artem/test/fhir-converter/src/main/java/com/nhcx/fhirconverter/ai/NaturalLanguageParser.java)

Uses `GeminiClient` to convert plain-English text → valid HL7 v2.5 message via a structured prompt. Output is a pipe-delimited HL7 string that feeds into the existing [convertCoverage()](file:///home/artem/test/fhir-converter/src/main/java/com/nhcx/fhirconverter/controller/ConversionController.java#34-49) pipeline.

#### [MODIFY] [ConversionService.java](file:///home/artem/test/fhir-converter/src/main/java/com/nhcx/fhirconverter/service/ConversionService.java)

Add `convertFromNaturalLanguage(String description)` method that calls `NaturalLanguageParser.naturalLanguageToHl7()`, then pipes output through [convertCoverage()](file:///home/artem/test/fhir-converter/src/main/java/com/nhcx/fhirconverter/controller/ConversionController.java#34-49).

#### [MODIFY] [ConversionController.java](file:///home/artem/test/fhir-converter/src/main/java/com/nhcx/fhirconverter/controller/ConversionController.java)

Add `POST /api/convert/natural-language` endpoint (consumes `text/plain`, produces `application/json`).

---

### Smart Field Mapper (Zero-Config)

#### [NEW] [AiSmartMapperService.java](file:///home/artem/test/fhir-converter/src/main/java/com/nhcx/fhirconverter/ai/AiSmartMapperService.java)

Uses `GeminiClient` to analyze a sample HL7 message + hospital name → generate a YAML mapping profile string.

#### [NEW] [AiMappingController.java](file:///home/artem/test/fhir-converter/src/main/java/com/nhcx/fhirconverter/controller/AiMappingController.java)

Two endpoints:
- `POST /api/mapping/auto-generate?hospitalName=X` — body = sample HL7, returns YAML string
- `POST /api/mapping/apply` — body = YAML content, writes to disk and reloads

#### [MODIFY] [MappingLoader.java](file:///home/artem/test/fhir-converter/src/main/java/com/nhcx/fhirconverter/mapping/MappingLoader.java)

Add `saveProfile(String yamlContent)` to write YAML to `mapping_profiles/` directory (overwriting the active [hl7_adt_v2_coverage.yaml](file:///home/artem/test/fhir-converter/src/main/resources/mapping_profiles/hl7_adt_v2_coverage.yaml)).

---

### Frontend Dashboard

#### [MODIFY] [index.html](file:///home/artem/test/fhir-converter/src/main/resources/static/index.html)

- Add a **"Natural Language"** tab button alongside HL7/JSON/CSV tabs
- Add a new **"AI Mapper"** navigation link in header
- Add the **AI Mapper section** HTML (textarea for sample HL7, hospital name input, generate button, YAML preview, apply button)

#### [MODIFY] [app.js](file:///home/artem/test/fhir-converter/src/main/resources/static/js/app.js)

- Handle "Natural Language" tab — routes to `/api/convert/natural-language` with `text/plain` content type
- Add sample NL text in `SAMPLES`
- Add AI Mapper section logic: call `/api/mapping/auto-generate`, display YAML, call `/api/mapping/apply`
- Add "AI Mapper" section show/hide in navigation

#### [MODIFY] [style.css](file:///home/artem/test/fhir-converter/src/main/resources/static/css/style.css)

Add styles for the AI Mapper section (YAML preview panel, AI-themed accent styling).

---

## Verification Plan

### Automated
- `mvn compile` — verify all new Java files compile without errors

### Manual (Browser)
1. **Natural Language**: Navigate to Converter tab → click "Natural Language" tab → type e.g. "Admit patient Rajesh Sharma, male, age 34, ABHA ID ABHA-1234, diagnosed with Type 2 diabetes, insured by ICICI Lombard policy POL999" → click Convert → verify FHIR Bundle appears
2. **Smart Field Mapper**: Navigate to "AI Mapper" section → paste a sample HL7 message → enter hospital name → click "AI Auto-Map" → verify YAML appears → click "Apply" → verify success toast

> [!IMPORTANT]
> Both features require a valid Gemini API key in [application.properties](file:///home/artem/test/fhir-converter/src/test/resources/application.properties). Without a key, the endpoints will return errors. The user should provide their own key before testing.
