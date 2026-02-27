package com.nhcx.fhirconverter.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import com.nhcx.fhirconverter.exception.ValidationException;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Validates FHIR Bundles against specific implementation guide profiles.
 *
 * Currently configured with standard FHIR R4 standard profiles.
 * Can be extended with NpmPackageValidationSupport to load specific NHCX
 * profiles.
 */
@Service
public class FhirValidatorService {

    private final FhirValidator validator;
    private final FhirContext fhirContext;

    public FhirValidatorService() {
        // 1. Initialize FHIR context
        this.fhirContext = FhirContext.forR4();

        // 2. Create Validation Support Chain
        ValidationSupportChain validationSupportChain = new ValidationSupportChain();

        // Add standard R4 structural profiles
        validationSupportChain.addValidationSupport(new DefaultProfileValidationSupport(fhirContext));

        // Add common terminologies (e.g. SNOMED CT, LOINC, ICD-10 codes logic support)
        validationSupportChain.addValidationSupport(new CommonCodeSystemsTerminologyService(fhirContext));

        // Add an in-memory terminology server to handle value set expansions and code
        // validation
        validationSupportChain.addValidationSupport(new InMemoryTerminologyServerValidationSupport(fhirContext));

        // === EXTENSIBILITY POINT: NHCX Profiles ===
        // To add NHCX specific validation rules (.tgz / NPM packages):
        // try {
        // NpmPackageValidationSupport npmPackageSupport = new
        // NpmPackageValidationSupport(fhirContext);
        // npmPackageSupport.loadPackageFromClasspath("classpath:nhcx-ig-1.0.0.tgz");
        // validationSupportChain.addValidationSupport(npmPackageSupport);
        // } catch (Exception e) {
        // throw new ValidationException("Failed to load NHCX IG: " + e.getMessage(),
        // e);
        // }
        // ===========================================

        // 3. Create the instance validator and configure it with the support chain
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupportChain);

        // Disable warning about unknown ValueSets for unconfigured code systems to
        // reduce noise
        instanceValidator.setAnyExtensionsAllowed(true);
        instanceValidator.setNoTerminologyChecks(false);

        // 4. Create the validator and register the instance validator module
        this.validator = fhirContext.newValidator();
        this.validator.registerValidatorModule(instanceValidator);
    }

    /**
     * Validates a FHIR JSON String.
     * Throws a ValidationException if there are any ERROR or FATAL issues.
     *
     * @param fhirJson the generated FHIR JSON
     * @throws ValidationException if the validation fails
     */
    public void validate(String fhirJson) {
        ValidationResult result = validator.validateWithResult(fhirJson);

        if (!result.isSuccessful()) {
            // Filter only ERROR and FATAL messages
            String errors = result.getMessages().stream()
                    .filter(msg -> msg.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.ERROR ||
                            msg.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.FATAL)
                    .map(SingleValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));

            if (!errors.isEmpty()) {
                throw new ValidationException("FHIR Validation failed: " + errors);
            }
        }
    }
}
