package com.nhcx.fhirconverter.parser;

import com.nhcx.fhirconverter.model.Hl7Data;
import org.springframework.stereotype.Component;

// import java.util.ArrayList;
// import java.util.List;

/**
 * Robust HL7 Parser.
 *
 * This implementation uses direct string splitting to handle legacy HL7
 * messages.
 * It is more reliable than complex libraries for real-world non-standard
 * messages
 * because it doesn't enforce strict segment hierarchies.
 */
@Component
public class Hl7Parser {

    public Hl7Data parse(String rawHl7) {
        Hl7Data data = new Hl7Data();
        if (rawHl7 == null || rawHl7.isEmpty())
            return data;

        // Normalize newlines and split into segments
        String[] segments = rawHl7.split("[\\r\\n]+");

        for (String segment : segments) {
            String[] fields = segment.split("\\|");
            if (fields.length == 0)
                continue;

            String segmentName = fields[0];

            switch (segmentName) {
                case "PID" -> parsePid(fields, data);
                case "PV1" -> parsePv1(fields, data);
                case "IN1" -> parseIn1(fields, data);
                case "DG1" -> parseDg1(fields, data);
                case "PR1" -> parsePr1(fields, data);
            }
        }

        return data;
    }

    private void parsePid(String[] fields, Hl7Data data) {
        // PID-3: Patient Identifier (ABHA ID)
        data.setAbhaId(getField(fields, 3));

        // PID-5: Patient Name (Family^Given)
        String nameStr = getField(fields, 5);
        if (nameStr != null) {
            String[] nameParts = nameStr.split("\\^");
            if (nameParts.length > 0)
                data.setFamilyName(nameParts[0]);
            if (nameParts.length > 1)
                data.setGivenName(nameParts[1]);
        }

        // PID-7: Date of Birth (YYYYMMDD)
        String dob = getField(fields, 7);
        if (dob != null && dob.length() >= 8) {
            data.setDateOfBirth(dob.substring(0, 8));
        }

        // PID-8: Gender (M/F)
        data.setGender(getField(fields, 8));
    }

    private void parsePv1(String[] fields, Hl7Data data) {
        // PV1-44: Admit Date
        String admit = getField(fields, 44);
        if (admit != null && admit.length() >= 8) {
            data.setAdmitDate(admit.substring(0, 8));
        }

        // PV1-45: Discharge Date
        String discharge = getField(fields, 45);
        if (discharge != null && discharge.length() >= 8) {
            data.setDischargeDate(discharge.substring(0, 8));
        }
    }

    private void parseIn1(String[] fields, Hl7Data data) {
        // IN1-36: Policy Number (Often used for Insurance Plan ID in NHCX)
        data.setPolicyNumber(getField(fields, 36));
    }

    private void parseDg1(String[] fields, Hl7Data data) {
        // DG1-3: Diagnosis Code (Code^Description)
        String diagStr = getField(fields, 3);
        if (diagStr != null) {
            String[] diagParts = diagStr.split("\\^");
            String code = diagParts.length > 0 ? diagParts[0] : "";
            String text = diagParts.length > 1 ? diagParts[1] : "";
            data.addDiagnosis(new Hl7Data.Diagnosis(code, text));
        }
    }

    private void parsePr1(String[] fields, Hl7Data data) {
        // PR1-3: Procedure Code (Code^Description)
        String procStr = getField(fields, 3);
        String code = "", text = "";
        if (procStr != null) {
            String[] procParts = procStr.split("\\^");
            code = procParts.length > 0 ? procParts[0] : "";
            text = procParts.length > 1 ? procParts[1] : "";
        }

        // PR1-5: Procedure Date
        String date = getField(fields, 5);
        if (date != null && date.length() >= 8) {
            date = date.substring(0, 8);
        }

        if (!code.isEmpty() || !text.isEmpty()) {
            data.addProcedure(new Hl7Data.Procedure(code, text, date));
        }
    }

    private String getField(String[] fields, int index) {
        if (index < fields.length) {
            String val = fields[index];
            return (val == null || val.trim().isEmpty()) ? null : val.trim();
        }
        return null;
    }
}
