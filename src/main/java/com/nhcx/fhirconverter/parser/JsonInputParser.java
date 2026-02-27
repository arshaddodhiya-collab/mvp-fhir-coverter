package com.nhcx.fhirconverter.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhcx.fhirconverter.model.Hl7Data;
import org.springframework.stereotype.Component;

/**
 * Parses JSON input into Hl7Data.
 *
 * Expected JSON structure:
 * {
 * "abhaId": "ABHA123",
 * "familyName": "Sharma",
 * "givenName": "Rahul",
 * "dateOfBirth": "19900415",
 * "gender": "M",
 * "admitDate": "20231001",
 * "dischargeDate": "20231005",
 * "policyNumber": "POL123",
 * "diagnoses": [{ "code": "COVID19", "description": "COVID-19" }],
 * "procedures": [{ "code": "PR01", "description": "Ventilation", "date":
 * "20231002" }]
 * }
 */
@Component
public class JsonInputParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Hl7Data parse(String json) {
        Hl7Data data = new Hl7Data();

        try {
            JsonNode root = objectMapper.readTree(json);

            // Core patient fields
            data.setAbhaId(getText(root, "abhaId"));
            data.setFamilyName(getText(root, "familyName"));
            data.setGivenName(getText(root, "givenName"));
            data.setDateOfBirth(getText(root, "dateOfBirth"));
            data.setGender(getText(root, "gender"));

            // Clinical / admission fields
            data.setAdmitDate(getText(root, "admitDate"));
            data.setDischargeDate(getText(root, "dischargeDate"));
            data.setPolicyNumber(getText(root, "policyNumber"));
            data.setTotalClaimAmount(getText(root, "totalClaimAmount"));

            // Diagnoses array
            JsonNode diagArray = root.get("diagnoses");
            if (diagArray != null && diagArray.isArray()) {
                for (JsonNode diag : diagArray) {
                    String code = getText(diag, "code");
                    String desc = getText(diag, "description");
                    if (code != null) {
                        data.addDiagnosis(new Hl7Data.Diagnosis(code, desc != null ? desc : ""));
                    }
                }
            }

            // Procedures array
            JsonNode procArray = root.get("procedures");
            if (procArray != null && procArray.isArray()) {
                for (JsonNode proc : procArray) {
                    String code = getText(proc, "code");
                    String desc = getText(proc, "description");
                    String date = getText(proc, "date");
                    if (code != null) {
                        data.addProcedure(new Hl7Data.Procedure(code, desc != null ? desc : "", date));
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON input: " + e.getMessage(), e);
        }

        return data;
    }

    private String getText(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child != null && !child.isNull() && child.isTextual()) {
            String val = child.asText().trim();
            return val.isEmpty() ? null : val;
        }
        return null;
    }
}
