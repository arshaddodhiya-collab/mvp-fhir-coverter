# AI Integration — Natural Language to HL7/FHIR & Smart Field Mapper

## Backend
- [ ] Add Gemini REST API client (`GeminiClient.java`)
- [ ] Add `gemini.api.key` property to [application.properties](file:///home/artem/test/fhir-converter/src/main/resources/application.properties)
- [ ] Add Gemini dependency or use RestTemplate (REST API approach)
- [ ] Create `NaturalLanguageParser.java` — NL → HL7 via Gemini
- [ ] Create `AiSmartMapperService.java` — Sample HL7 → mapping YAML via Gemini
- [ ] Add `convertNaturalLanguage()` method to [ConversionService](file:///home/artem/test/fhir-converter/src/main/java/com/nhcx/fhirconverter/service/ConversionService.java#30-206)
- [ ] Add `POST /api/convert/natural-language` endpoint to controller
- [ ] Add `POST /api/mapping/auto-generate` endpoint (new `AiMappingController`)
- [ ] Add `POST /api/mapping/apply` endpoint
- [ ] Add `saveProfile()` method to [MappingLoader](file:///home/artem/test/fhir-converter/src/main/java/com/nhcx/fhirconverter/mapping/MappingLoader.java#25-63)

## Frontend (Dashboard)
- [ ] Add "Natural Language" tab to converter section
- [ ] Add "AI Mapper" navigation section in header
- [ ] Build AI Mapper section HTML (paste sample HL7 → generate YAML → apply)
- [ ] Wire up NL tab to new `/api/convert/natural-language` endpoint
- [ ] Wire up AI Mapper to `/api/mapping/auto-generate` and `/api/mapping/apply`

## Verification
- [ ] Restart application and verify build compiles
- [ ] Test Natural Language endpoint via browser UI
- [ ] Test Smart Field Mapper via browser UI
