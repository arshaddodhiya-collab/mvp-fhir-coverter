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

    /**
     * Parses a raw HL7 PID segment and extracts patient data.
     *
     * @param rawHl7 The raw HL7 message (one or more lines)
     * @return Hl7Data object containing the extracted fields
     * @throws IllegalArgumentException if the message doesn't contain a PID segment
     */
    public Hl7Data parse(String rawHl7) {
        // Step 1: Find the PID segment line
        // An HL7 message can have multiple segments (MSH, PID, PV1, etc.)
        // We only care about the PID segment for patient data
        String pidLine = findPidSegment(rawHl7);

        // Step 2: Split the PID line by "|" to get individual fields
        String[] fields = pidLine.split("\\|");

        // Step 3: Create our data object and fill it in
        Hl7Data data = new Hl7Data();

        // Field 3 = ABHA ID (index 3 in the array)
        if (fields.length > 3) {
            data.setAbhaId(fields[3].trim());
        }

        // Field 5 = Patient Name (index 5), which contains sub-fields separated by "^"
        if (fields.length > 5) {
            String nameField = fields[5];
            String[] nameParts = nameField.split("\\^");
            // Subfield 0 = Family name (surname)
            if (nameParts.length > 0) {
                data.setFamilyName(nameParts[0].trim());
            }
            // Subfield 1 = Given name (first name)
            if (nameParts.length > 1) {
                data.setGivenName(nameParts[1].trim());
            }
        }

        // Field 7 = Date of Birth (index 7)
        if (fields.length > 7) {
            data.setDateOfBirth(fields[7].trim());
        }

        // Field 8 = Gender (index 8)
        if (fields.length > 8) {
            data.setGender(fields[8].trim());
        }

        return data;
    }

    /**
     * Finds the PID segment from a (possibly multi-line) HL7 message.
     *
     * @param rawHl7 The full HL7 message
     * @return The PID segment line
     * @throws IllegalArgumentException if no PID segment is found
     */
    private String findPidSegment(String rawHl7) {
        // Split by newline to handle multi-segment messages
        String[] lines = rawHl7.split("\\r?\\n");

        for (String line : lines) {
            // Check if this line starts with "PID"
            if (line.trim().startsWith("PID")) {
                return line.trim();
            }
        }

        throw new IllegalArgumentException(
                "No PID segment found in the HL7 message. " +
                        "The message must contain a line starting with 'PID'.");
    }
}
