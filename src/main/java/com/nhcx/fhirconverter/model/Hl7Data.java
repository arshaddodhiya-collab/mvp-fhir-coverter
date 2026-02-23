package com.nhcx.fhirconverter.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple DTO (Data Transfer Object) that holds the parsed HL7 data.
 *
 * This is NOT a database entity — it's just a plain Java object
 * used to pass parsed patient data between the parser and the FHIR builder.
 *
 * Fields extracted from the HL7 PID segment:
 * PID|1||ABHA123||Sharma^Rahul||19900415|M
 * ^ ^ ^ ^ ^ ^
 * seq abhaId family given dob gender
 */
public class Hl7Data {

    private String abhaId; // ABHA identifier (e.g., "ABHA123")
    private String familyName; // Surname (e.g., "Sharma")
    private String givenName; // First name (e.g., "Rahul")
    private String dateOfBirth; // DOB in YYYYMMDD format (e.g., "19900415")
    private String gender; // Gender code (e.g., "M" or "F")

    // ========== Extensions for Claims & Pre-Auth ==========
    private String admitDate;
    private String dischargeDate;

    private List<Diagnosis> diagnoses = new ArrayList<>();
    private List<Procedure> procedures = new ArrayList<>();

    private String policyNumber;
    private String totalClaimAmount;

    // Nested classes
    public static class Diagnosis {
        private String code;
        private String description;

        public Diagnosis(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class Procedure {
        private String code;
        private String description;
        private String date;

        public Procedure(String code, String description, String date) {
            this.code = code;
            this.description = description;
            this.date = date;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public String getDate() {
            return date;
        }
    }

    // ==================== Getters & Setters ====================

    public String getAbhaId() {
        return abhaId;
    }

    public void setAbhaId(String abhaId) {
        this.abhaId = abhaId;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    // ==================== Claims Getters & Setters ====================

    public String getAdmitDate() {
        return admitDate;
    }

    public void setAdmitDate(String admitDate) {
        this.admitDate = admitDate;
    }

    public String getDischargeDate() {
        return dischargeDate;
    }

    public void setDischargeDate(String dischargeDate) {
        this.dischargeDate = dischargeDate;
    }

    public List<Diagnosis> getDiagnoses() {
        return diagnoses;
    }

    public void addDiagnosis(Diagnosis diagnosis) {
        this.diagnoses.add(diagnosis);
    }

    public List<Procedure> getProcedures() {
        return procedures;
    }

    public void addProcedure(Procedure procedure) {
        this.procedures.add(procedure);
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public void setPolicyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
    }

    public String getTotalClaimAmount() {
        return totalClaimAmount;
    }

    public void setTotalClaimAmount(String totalClaimAmount) {
        this.totalClaimAmount = totalClaimAmount;
    }

    @Override
    public String toString() {
        return "Hl7Data{" +
                "abhaId='" + abhaId + '\'' +
                ", familyName='" + familyName + '\'' +
                ", givenName='" + givenName + '\'' +
                ", dateOfBirth='" + dateOfBirth + '\'' +
                ", gender='" + gender + '\'' +
                '}';
    }
}
