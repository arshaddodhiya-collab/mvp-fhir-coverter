package com.nhcx.fhirconverter.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.nhcx.fhirconverter.mapping.MappingProfile;
import com.nhcx.fhirconverter.model.Hl7Data;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Builds a FHIR R4 Bundle using HAPI FHIR Libraries
 *
 * HOW FHIR JSON IS CONSTRUCTED:
 * =============================
 * We use strongly-typed Java objects (e.g. org.hl7.fhir.r4.model.Patient)
 * instead of raw HashMaps.
 * This guarantees that we follow the FHIR R4 specification exactly and
 * provides compile-time safety against typos or incorrect data types.
 */
@Component
public class FhirBundleBuilder {

    private final IParser fhirJsonParser;

    public FhirBundleBuilder() {
        // Initialize the standard HAPI FHIR R4 Context
        FhirContext ctx = FhirContext.forR4();
        this.fhirJsonParser = ctx.newJsonParser();
        this.fhirJsonParser.setPrettyPrint(true);
    }

    public String buildBundle(Hl7Data data, MappingProfile profile) {
        try {
            Bundle bundle = new Bundle();
            bundle.setType(Bundle.BundleType.COLLECTION);

            // Entry 1: Patient resource
            bundle.addEntry()
                    .setFullUrl("urn:uuid:" + UUID.randomUUID())
                    .setResource(buildPatient(data));

            // Entry 2: Coverage resource
            bundle.addEntry()
                    .setFullUrl("urn:uuid:" + UUID.randomUUID())
                    .setResource(buildCoverage(data));

            // Entry 3: CoverageEligibilityRequest resource
            bundle.addEntry()
                    .setFullUrl("urn:uuid:" + UUID.randomUUID())
                    .setResource(buildCoverageEligibilityRequest(data));

            // ========== Claims & Pre-Auth Extensions ==========

            // Add Encounter if we have admission data
            if (data.getAdmitDate() != null) {
                bundle.addEntry()
                        .setFullUrl("urn:uuid:" + UUID.randomUUID())
                        .setResource(buildEncounter(data));
            }

            // Add Conditions (Diagnoses)
            for (Hl7Data.Diagnosis diag : data.getDiagnoses()) {
                bundle.addEntry()
                        .setFullUrl("urn:uuid:" + UUID.randomUUID())
                        .setResource(buildCondition(data, diag));
            }

            // Add Procedures
            for (Hl7Data.Procedure proc : data.getProcedures()) {
                bundle.addEntry()
                        .setFullUrl("urn:uuid:" + UUID.randomUUID())
                        .setResource(buildProcedure(data, proc));
            }

            // Add Claim if we have diagnoses or procedures
            if (!data.getDiagnoses().isEmpty() || !data.getProcedures().isEmpty()) {
                bundle.addEntry()
                        .setFullUrl("urn:uuid:" + UUID.randomUUID())
                        .setResource(buildClaim(data));
            }

            // Let HAPI FHIR serialize the entire object tree into strict JSON
            return fhirJsonParser.encodeResourceToString(bundle);

        } catch (Exception e) {
            throw new RuntimeException("Failed to build FHIR Bundle: " + e.getMessage(), e);
        }
    }

    // ==================== FHIR Resource Builders ====================

    /**
     * Builds the Patient resource.
     *
     * Maps from HL7 PID fields:
     * ABHA ID → Patient.identifier[0].value
     * Family name → Patient.name[0].family
     * Given name → Patient.name[0].given[0]
     * DOB → Patient.birthDate
     * Gender → Patient.gender (M→male, F→female)
     */
    private Patient buildPatient(Hl7Data data) {
        Patient patient = new Patient();

        // Identifier — ABHA ID
        patient.addIdentifier()
                .setSystem("https://ndhm.gov.in/abha")
                .setValue(data.getAbhaId());

        // Name
        if (data.getFamilyName() != null || data.getGivenName() != null) {
            HumanName name = patient.addName();
            if (data.getFamilyName() != null)
                name.setFamily(data.getFamilyName());
            if (data.getGivenName() != null)
                name.addGiven(data.getGivenName());
        }

        // Birth Date
        patient.setBirthDate(parseDate(data.getDateOfBirth()));

        // Gender — convert M/F to male/female
        patient.setGender(mapGender(data.getGender()));

        return patient;
    }

    /**
     * Builds the Coverage resource.
     *
     * Represents the patient's insurance/health coverage under NHCX.
     */
    private Coverage buildCoverage(Hl7Data data) {
        Coverage coverage = new Coverage();
        coverage.setStatus(Coverage.CoverageStatus.ACTIVE);

        // Reference back to the Patient
        coverage.getBeneficiary().setReference("Patient/" + data.getAbhaId());

        // Payor is the insurance organization (NHCX in this case)
        coverage.addPayor().setReference("Organization/NHCX");

        return coverage;
    }

    /**
     * Builds the CoverageEligibilityRequest resource.
     *
     * This is what gets sent to check if a patient is eligible for coverage.
     */
    private CoverageEligibilityRequest buildCoverageEligibilityRequest(Hl7Data data) {
        CoverageEligibilityRequest request = new CoverageEligibilityRequest();
        request.setStatus(CoverageEligibilityRequest.EligibilityRequestStatus.ACTIVE);
        request.addPurpose(CoverageEligibilityRequest.EligibilityRequestPurpose.VALIDATION);

        // Reference to the Patient
        request.getPatient().setReference("Patient/" + data.getAbhaId());

        // Created date = today
        request.setCreated(new Date());

        // Insurer — the organization that provides coverage
        request.getInsurer().setReference("Organization/NHCX");

        return request;
    }

    // ==================== Claim & Clinical Builders ====================

    private Encounter buildEncounter(Hl7Data data) {
        Encounter encounter = new Encounter();
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        encounter.getSubject().setReference("Patient/" + data.getAbhaId());

        Period period = new Period();
        if (data.getAdmitDate() != null)
            period.setStart(parseDate(data.getAdmitDate()));
        if (data.getDischargeDate() != null)
            period.setEnd(parseDate(data.getDischargeDate()));
        encounter.setPeriod(period);

        return encounter;
    }

    private Condition buildCondition(Hl7Data data, Hl7Data.Diagnosis diag) {
        Condition condition = new Condition();
        condition.getSubject().setReference("Patient/" + data.getAbhaId());

        CodeableConcept codeMap = new CodeableConcept();
        codeMap.addCoding().setCode(diag.getCode());
        codeMap.setText(diag.getDescription());
        condition.setCode(codeMap);

        return condition;
    }

    private Procedure buildProcedure(Hl7Data data, Hl7Data.Procedure proc) {
        Procedure procedure = new Procedure();
        procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
        procedure.getSubject().setReference("Patient/" + data.getAbhaId());

        CodeableConcept codeMap = new CodeableConcept();
        codeMap.addCoding().setCode(proc.getCode());
        codeMap.setText(proc.getDescription());
        procedure.setCode(codeMap);

        Date procDate = parseDate(proc.getDate());
        if (procDate != null)
            procedure.setPerformed(new DateTimeType(procDate));

        return procedure;
    }

    private Claim buildClaim(Hl7Data data) {
        Claim claim = new Claim();
        claim.setStatus(Claim.ClaimStatus.ACTIVE);
        claim.setUse(Claim.Use.CLAIM);
        claim.setCreated(new Date());

        if (data.getPolicyNumber() != null) {
            claim.addIdentifier().setValue(data.getPolicyNumber());
        }

        claim.getPatient().setReference("Patient/" + data.getAbhaId());
        claim.getProvider().setReference("Organization/Hospital");

        // Set Mandatory Fields for standard Claim
        claim.getType().addCoding().setCode("institutional")
                .setSystem("http://terminology.hl7.org/CodeSystem/claim-type");
        claim.getPriority().addCoding().setCode("normal")
                .setSystem("http://terminology.hl7.org/CodeSystem/processpriority");

        String coverageRef = data.getPolicyNumber() != null ? data.getPolicyNumber() : "unknown-policy";
        claim.addInsurance().setSequence(1).setFocal(true).getCoverage()
                .setReference("Coverage/" + coverageRef);

        return claim;
    }

    // ==================== Helper Methods ====================

    private Date parseDate(String hl7Date) {
        if (hl7Date == null || hl7Date.length() < 8)
            return null;
        try {
            LocalDate date = LocalDate.of(
                    Integer.parseInt(hl7Date.substring(0, 4)),
                    Integer.parseInt(hl7Date.substring(4, 6)),
                    Integer.parseInt(hl7Date.substring(6, 8)));
            return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            return null;
        }
    }

    private Enumerations.AdministrativeGender mapGender(String hl7Gender) {
        if (hl7Gender == null)
            return Enumerations.AdministrativeGender.UNKNOWN;
        return switch (hl7Gender.toUpperCase()) {
            case "M" -> Enumerations.AdministrativeGender.MALE;
            case "F" -> Enumerations.AdministrativeGender.FEMALE;
            case "O" -> Enumerations.AdministrativeGender.OTHER;
            default -> Enumerations.AdministrativeGender.UNKNOWN;
        };
    }
}
