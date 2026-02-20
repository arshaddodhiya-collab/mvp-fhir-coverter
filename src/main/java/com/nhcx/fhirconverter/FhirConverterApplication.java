package com.nhcx.fhirconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the FHIR Converter application.
 *
 * @SpringBootApplication enables:
 *                        - Component scanning
 *                        (finds @Controller, @Service, @Repository, etc.)
 *                        - Auto-configuration (sets up JPA, web server, etc.)
 *                        - Configuration properties loading
 */
@SpringBootApplication
public class FhirConverterApplication {

    public static void main(String[] args) {
        SpringApplication.run(FhirConverterApplication.class, args);
    }
}
