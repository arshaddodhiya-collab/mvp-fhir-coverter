package com.nhcx.fhirconverter.parser;

import com.nhcx.fhirconverter.model.Hl7Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CsvInputParserTest {

    private CsvInputParser parser;

    @BeforeEach
    void setUp() {
        parser = new CsvInputParser();
    }

    @Test
    void parseFullCsv_allFieldsMapped() {
        String csv = """
                abhaId,familyName,givenName,dateOfBirth,gender,admitDate,dischargeDate,policyNumber,diagCode,diagDesc,procCode,procDesc,procDate
                ABHA123,Sharma,Rahul,19900415,M,20231001,20231005,POL999,COVID19,COVID-19,PR01,Ventilation,20231002
                """;

        Hl7Data data = parser.parse(csv);

        assertEquals("ABHA123", data.getAbhaId());
        assertEquals("Sharma", data.getFamilyName());
        assertEquals("Rahul", data.getGivenName());
        assertEquals("19900415", data.getDateOfBirth());
        assertEquals("M", data.getGender());
        assertEquals("20231001", data.getAdmitDate());
        assertEquals("20231005", data.getDischargeDate());
        assertEquals("POL999", data.getPolicyNumber());

        assertEquals(1, data.getDiagnoses().size());
        assertEquals("COVID19", data.getDiagnoses().get(0).getCode());
        assertEquals("COVID-19", data.getDiagnoses().get(0).getDescription());

        assertEquals(1, data.getProcedures().size());
        assertEquals("PR01", data.getProcedures().get(0).getCode());
    }

    @Test
    void parseMultipleDiagnoses_semicolonSeparated() {
        String csv = """
                abhaId,familyName,givenName,dateOfBirth,gender,diagCode,diagDesc
                ABHA456,Kumar,Priya,19920305,F,COVID19;A00,COVID-19;Cholera
                """;

        Hl7Data data = parser.parse(csv);

        assertEquals("ABHA456", data.getAbhaId());
        assertEquals(2, data.getDiagnoses().size());
        assertEquals("COVID19", data.getDiagnoses().get(0).getCode());
        assertEquals("COVID-19", data.getDiagnoses().get(0).getDescription());
        assertEquals("A00", data.getDiagnoses().get(1).getCode());
        assertEquals("Cholera", data.getDiagnoses().get(1).getDescription());
    }

    @Test
    void parseMinimalCsv_optionalFieldsNull() {
        String csv = """
                abhaId,gender
                ABHA789,M
                """;

        Hl7Data data = parser.parse(csv);

        assertEquals("ABHA789", data.getAbhaId());
        assertEquals("M", data.getGender());
        assertNull(data.getFamilyName());
        assertTrue(data.getDiagnoses().isEmpty());
    }

    @Test
    void parseEmptyCsv_throwsException() {
        String emptyCsv = "";
        assertThrows(RuntimeException.class, () -> parser.parse(emptyCsv));
    }

    @Test
    void parseHeaderOnly_throwsException() {
        String headerOnly = "abhaId,familyName,givenName\n";
        assertThrows(RuntimeException.class, () -> parser.parse(headerOnly));
    }
}
