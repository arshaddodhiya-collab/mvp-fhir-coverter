package com.nhcx.fhirconverter.model;

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
