package com.nhcx.fhirconverter.parser;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ADT_A01; // Using ADT A01 structure as general representation
import ca.uhn.hl7v2.model.v25.segment.*;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import com.nhcx.fhirconverter.model.Hl7Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HL7 v2 Message Parser using HAPI HL7v2 Library
 *
 * HOW HAPI PARSING WORKS:
 * =======================
 * Instead of manually splitting strings by "|" and "^", HAPI handles all escape
 * characters, sub-components, and varying segment orders seamlessly.
 * It builds a strong-typed Java object tree representing the message.
 */
@Component
public class Hl7Parser {

    private final HapiContext context;
    private final Parser parser;

    public Hl7Parser() {
        this.context = new DefaultHapiContext();
        // PipeParser is for standard pipe-and-hat (| ^) delimited HL7
        this.parser = context.getPipeParser();
    }

    public Hl7Data parse(String rawHl7) {
        try {
            // Let HAPI parse the messy string into a formal Message object
            Message message = parser.parse(rawHl7);
            Hl7Data data = new Hl7Data();

            // We use a Terser (HAPI's path-based extractor) to easily pull fields
            // even if we don't know the exact message type (ADT, ORU, etc).
            Terser terser = new Terser(message);

            // 1. Parse PID (Patient)
            try {
                data.setAbhaId(terser.get("/.PID-3-1"));
                data.setFamilyName(terser.get("/.PID-5-1"));
                data.setGivenName(terser.get("/.PID-5-2"));

                // HAPI handles datetime extraction cleanly
                String dob = terser.get("/.PID-7-1");
                if (dob != null && dob.length() >= 8) {
                    data.setDateOfBirth(dob.substring(0, 8)); // YYYYMMDD
                }
                data.setGender(terser.get("/.PID-8-1"));
            } catch (Exception e) {
                // Not all messages will have every field, catch and continue
            }

            // 2. Parse PV1 (Encounter)
            try {
                String admit = terser.get("/.PV1-44-1");
                if (admit != null && admit.length() >= 8)
                    data.setAdmitDate(admit.substring(0, 8));

                String discharge = terser.get("/.PV1-45-1");
                if (discharge != null && discharge.length() >= 8)
                    data.setDischargeDate(discharge.substring(0, 8));
            } catch (Exception e) {
            }

            // 3. Parse IN1 (Insurance)
            try {
                data.setPolicyNumber(terser.get("/.IN1-36-1"));
            } catch (Exception e) {
            }

            // 4. Parse DG1 and PR1 using raw segments if multiple exist
            // (Terser is good for single fields, but looping requires segment access)
            try {
                ca.uhn.hl7v2.model.Structure[] dg1Nodes = message.getAll("DG1");
                for (ca.uhn.hl7v2.model.Structure struct : dg1Nodes) {
                    DG1 dg1 = (DG1) struct;
                    String code = dg1.getDiagnosisCodeDG1().getIdentifier().getValue();
                    String text = dg1.getDiagnosisCodeDG1().getText().getValue();
                    if (code != null || text != null) {
                        data.addDiagnosis(new Hl7Data.Diagnosis(code, text));
                    }
                }
            } catch (ca.uhn.hl7v2.HL7Exception e) {
                // No DG1 segments found, which is fine
            }

            try {
                ca.uhn.hl7v2.model.Structure[] pr1Nodes = message.getAll("PR1");
                for (ca.uhn.hl7v2.model.Structure struct : pr1Nodes) {
                    PR1 pr1 = (PR1) struct;
                    String code = pr1.getProcedureCode().getIdentifier().getValue();
                    String text = pr1.getProcedureCode().getText().getValue();
                    String date = pr1.getProcedureDateTime().getTime().getValue();
                    if (date != null && date.length() >= 8)
                        date = date.substring(0, 8);

                    if (code != null || text != null) {
                        data.addProcedure(new Hl7Data.Procedure(code, text, date));
                    }
                }
            } catch (ca.uhn.hl7v2.HL7Exception e) {
                // No PR1 segments found, which is fine
            }

            return data;

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse HL7 using HAPI: " + e.getMessage(), e);
        }
    }
}
