package com.nhcx.fhirconverter.parser;

import com.nhcx.fhirconverter.model.Hl7Data;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.StringReader;

/**
 * Parses CSV input into Hl7Data.
 *
 * Expected CSV format (first row = header):
 * abhaId,familyName,givenName,dateOfBirth,gender,admitDate,dischargeDate,policyNumber,diagCode,diagDesc,procCode,procDesc,procDate
 * ABHA123,Sharma,Rahul,19900415,M,20231001,20231005,POL123,COVID19,COVID-19,PR01,Ventilation,20231002
 *
 * Notes:
 * - Multiple diagnoses/procedures can be semicolon-separated within their cells
 * e.g. diagCode = "COVID19;A00" and diagDesc = "COVID-19;Cholera"
 * - Only the first data row is processed (single patient per request)
 */
@Component
public class CsvInputParser {

    public Hl7Data parse(String csv) {
        Hl7Data data = new Hl7Data();

        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            // Read header line
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                throw new RuntimeException("CSV input is empty — no header row found");
            }

            String[] headers = headerLine.split(",", -1);

            // Read first data line
            String dataLine = reader.readLine();
            if (dataLine == null || dataLine.trim().isEmpty()) {
                throw new RuntimeException("CSV input has no data row after header");
            }

            String[] values = dataLine.split(",", -1);

            // Map columns by header name
            for (int i = 0; i < headers.length && i < values.length; i++) {
                String header = headers[i].trim().toLowerCase();
                String value = values[i].trim();

                if (value.isEmpty())
                    continue;

                switch (header) {
                    case "abhaid" -> data.setAbhaId(value);
                    case "familyname" -> data.setFamilyName(value);
                    case "givenname" -> data.setGivenName(value);
                    case "dateofbirth" -> data.setDateOfBirth(value);
                    case "gender" -> data.setGender(value);
                    case "admitdate" -> data.setAdmitDate(value);
                    case "dischargedate" -> data.setDischargeDate(value);
                    case "policynumber" -> data.setPolicyNumber(value);
                    case "totalclaimamount" -> data.setTotalClaimAmount(value);
                    case "diagcode" -> {
                        // Support multiple diagnoses separated by semicolon
                        String descHeader = findValue(headers, values, "diagdesc");
                        String[] codes = value.split(";");
                        String[] descs = descHeader != null ? descHeader.split(";") : new String[0];
                        for (int j = 0; j < codes.length; j++) {
                            String code = codes[j].trim();
                            String desc = j < descs.length ? descs[j].trim() : "";
                            if (!code.isEmpty()) {
                                data.addDiagnosis(new Hl7Data.Diagnosis(code, desc));
                            }
                        }
                    }
                    case "proccode" -> {
                        // Support multiple procedures separated by semicolon
                        String procDescHeader = findValue(headers, values, "procdesc");
                        String procDateHeader = findValue(headers, values, "procdate");
                        String[] codes = value.split(";");
                        String[] descs = procDescHeader != null ? procDescHeader.split(";") : new String[0];
                        String[] dates = procDateHeader != null ? procDateHeader.split(";") : new String[0];
                        for (int j = 0; j < codes.length; j++) {
                            String code = codes[j].trim();
                            String desc = j < descs.length ? descs[j].trim() : "";
                            String date = j < dates.length ? dates[j].trim() : null;
                            if (!code.isEmpty()) {
                                data.addProcedure(new Hl7Data.Procedure(code, desc, date));
                            }
                        }
                    }
                    // diagdesc, procdesc, procdate handled within diagcode/proccode cases
                }
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV input: " + e.getMessage(), e);
        }

        return data;
    }

    /**
     * Helper to find a value by header name.
     */
    private String findValue(String[] headers, String[] values, String targetHeader) {
        for (int i = 0; i < headers.length && i < values.length; i++) {
            if (headers[i].trim().toLowerCase().equals(targetHeader)) {
                String val = values[i].trim();
                return val.isEmpty() ? null : val;
            }
        }
        return null;
    }
}
