package com.nhcx.fhirconverter.parser;

import com.nhcx.fhirconverter.model.Hl7Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonInputParserTest {

    private JsonInputParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonInputParser();
    }

    @Test
    void parseFullJson_allFieldsMapped() {
        String json = """
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
                """;

        Hl7Data data = parser.parse(json);

        assertEquals("ABHA123", data.getAbhaId());
        assertEquals("Sharma", data.getFamilyName());
        assertEquals("Rahul", data.getGivenName());
        assertEquals("19900415", data.getDateOfBirth());
        assertEquals("M", data.getGender());
        assertEquals("20231001", data.getAdmitDate());
        assertEquals("20231005", data.getDischargeDate());
        assertEquals("POL999", data.getPolicyNumber());

        assertEquals(2, data.getDiagnoses().size());
        assertEquals("COVID19", data.getDiagnoses().get(0).getCode());
        assertEquals("A00", data.getDiagnoses().get(1).getCode());

        assertEquals(1, data.getProcedures().size());
        assertEquals("PR01", data.getProcedures().get(0).getCode());
        assertEquals("20231002", data.getProcedures().get(0).getDate());
    }

    @Test
    void parseMinimalJson_optionalFieldsNull() {
        String json = """
                {
                  "abhaId": "ABHA456",
                  "gender": "F"
                }
                """;

        Hl7Data data = parser.parse(json);

        assertEquals("ABHA456", data.getAbhaId());
        assertEquals("F", data.getGender());
        assertNull(data.getFamilyName());
        assertNull(data.getGivenName());
        assertNull(data.getDateOfBirth());
        assertNull(data.getPolicyNumber());
        assertTrue(data.getDiagnoses().isEmpty());
        assertTrue(data.getProcedures().isEmpty());
    }

    @Test
    void parseInvalidJson_throwsException() {
        String badJson = "this is not json";

        assertThrows(RuntimeException.class, () -> parser.parse(badJson));
    }
}
