package com.nhcx.fhirconverter.parser;

import com.nhcx.fhirconverter.model.Hl7Data;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class Hl7ParserTest {

    @Test
    public void testParse() {
        Hl7Parser parser = new Hl7Parser();

        // Construct a standard HL7 message with enough pipes to satisfy indices
        StringBuilder hl7 = new StringBuilder();
        hl7.append("MSH|^~\\&|HIS|HOSP|NHCX|NHCX|20231010101010||ADT^A01|123456|P|2.5\r");

        // PID-3: ABHA ID, PID-5: Name, PID-7: DOB, PID-8: Gender
        hl7.append("PID|||ABHA1234567890||DOE^JOHN||19800101|M\r");

        // PV1: Need 44 pipes to reach Admit Date
        hl7.append("PV1|"); // seg name (0)
        for (int i = 1; i < 44; i++)
            hl7.append("|");
        hl7.append("20231010101010|20231015101010\r"); // 44, 45

        // IN1: Need 36 pipes to reach Policy Number
        hl7.append("IN1|");
        for (int i = 1; i < 36; i++)
            hl7.append("|");
        hl7.append("POL123456\r"); // 36

        // DG1: DG1-3 is Diagnosis
        hl7.append("DG1|1||J01.9^Acute sinusitis|||A\r");

        // PR1: PR1-3 is Procedure, PR1-5 is Procedure Date
        hl7.append("PR1|1||473.9^Operations on nose||20231010101010");

        Hl7Data data = parser.parse(hl7.toString());

        assertEquals("ABHA1234567890", data.getAbhaId());
        assertEquals("DOE", data.getFamilyName());
        assertEquals("JOHN", data.getGivenName());
        assertEquals("19800101", data.getDateOfBirth());
        assertEquals("M", data.getGender());
        assertEquals("20231010", data.getAdmitDate());
        assertEquals("20231015", data.getDischargeDate());
        assertEquals("POL123456", data.getPolicyNumber());

        assertEquals(1, data.getDiagnoses().size());
        assertEquals("J01.9", data.getDiagnoses().get(0).getCode());
        assertEquals("Acute sinusitis", data.getDiagnoses().get(0).getDescription());

        assertEquals(1, data.getProcedures().size());
        assertEquals("473.9", data.getProcedures().get(0).getCode());
        assertEquals("20231010", data.getProcedures().get(0).getDate());
    }
}
