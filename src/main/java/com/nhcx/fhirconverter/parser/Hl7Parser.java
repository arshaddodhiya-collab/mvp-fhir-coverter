package com.nhcx.fhirconverter.parser;

import com.nhcx.fhirconverter.model.Hl7Data;
import org.springframework.stereotype.Component;

/**
 * HL7 v2 Message Parser
 *
 * HOW HL7 PARSING WORKS:
 * ======================
 * An HL7 v2 message is a plain-text format where:
 * - Each line is a "segment" (e.g., PID, MSH, OBX)
 * - Fields within a segment are separated by pipe "|"
 * - Sub-fields (components) are separated by caret "^"
 *
 * Example PID segment:
 * PID|1||ABHA123||Sharma^Rahul||19900415|M
 *
 * Splitting by "|" gives us this array:
 * Index 0: "PID" ← segment name
 * Index 1: "1" ← set ID
 * Index 2: "" ← empty (patient ID)
 * Index 3: "ABHA123" ← ABHA identifier
 * Index 4: "" ← empty (alternate ID)
 * Index 5: "Sharma^Rahul" ← patient name (family^given)
 * Index 6: "" ← empty (mother's maiden name)
 * Index 7: "19900415" ← date of birth
 * Index 8: "M" ← gender
 *
 * For field 5, splitting by "^" gives:
 * Subfield 0: "Sharma" ← family name
 * Subfield 1: "Rahul" ← given name
 */
@Component // Marks this as a Spring-managed bean
public class Hl7Parser {

    public Hl7Data parse(String rawHl7) {
        String pidLine = findSegment(rawHl7, "PID");
        if (pidLine == null) {
            throw new IllegalArgumentException("No PID segment found");
        }

        Hl7Data data = new Hl7Data();

        // 1. Parse PID (Patient)
        String[] pidFields = pidLine.split("\\|");
        if (pidFields.length > 3)
            data.setAbhaId(pidFields[3].trim());
        if (pidFields.length > 5) {
            String[] nameParts = pidFields[5].split("\\^");
            if (nameParts.length > 0)
                data.setFamilyName(nameParts[0].trim());
            if (nameParts.length > 1)
                data.setGivenName(nameParts[1].trim());
        }
        if (pidFields.length > 7)
            data.setDateOfBirth(pidFields[7].trim());
        if (pidFields.length > 8)
            data.setGender(pidFields[8].trim());

        // 2. Parse PV1 (Encounter)
        String pv1Line = findSegment(rawHl7, "PV1");
        if (pv1Line != null) {
            String[] pv1Fields = pv1Line.split("\\|");
            if (pv1Fields.length > 44)
                data.setAdmitDate(pv1Fields[44].trim());
            if (pv1Fields.length > 45)
                data.setDischargeDate(pv1Fields[45].trim());
        }

        // 3. Parse IN1 (Insurance)
        String in1Line = findSegment(rawHl7, "IN1");
        if (in1Line != null) {
            String[] in1Fields = in1Line.split("\\|");
            if (in1Fields.length > 36)
                data.setPolicyNumber(in1Fields[36].trim());
        }

        // 4. Parse all DG1 (Diagnoses)
        for (String dg1Line : findAllSegments(rawHl7, "DG1")) {
            String[] dg1Fields = dg1Line.split("\\|");
            if (dg1Fields.length > 3) {
                // Usually field 3 has code^text^codingSystem
                String[] dCode = dg1Fields[3].split("\\^");
                String code = dCode.length > 0 ? dCode[0].trim() : "";
                String text = dCode.length > 1 ? dCode[1].trim() : (dg1Fields.length > 4 ? dg1Fields[4].trim() : "");
                data.addDiagnosis(new Hl7Data.Diagnosis(code, text));
            }
        }

        // 5. Parse all PR1 (Procedures)
        for (String pr1Line : findAllSegments(rawHl7, "PR1")) {
            String[] pr1Fields = pr1Line.split("\\|");
            if (pr1Fields.length > 3) {
                String[] pCode = pr1Fields[3].split("\\^");
                String code = pCode.length > 0 ? pCode[0].trim() : "";
                String text = pCode.length > 1 ? pCode[1].trim() : (pr1Fields.length > 4 ? pr1Fields[4].trim() : "");
                String date = pr1Fields.length > 5 ? pr1Fields[5].trim() : "";
                data.addProcedure(new Hl7Data.Procedure(code, text, date));
            }
        }

        return data;
    }

    private String findSegment(String rawHl7, String segmentName) {
        String[] lines = rawHl7.split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().startsWith(segmentName + "|")) {
                return line.trim();
            }
        }
        return null;
    }

    private java.util.List<String> findAllSegments(String rawHl7, String segmentName) {
        java.util.List<String> segments = new java.util.ArrayList<>();
        String[] lines = rawHl7.split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().startsWith(segmentName + "|")) {
                segments.add(line.trim());
            }
        }
        return segments;
    }
}
