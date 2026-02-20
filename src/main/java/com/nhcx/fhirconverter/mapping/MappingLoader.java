package com.nhcx.fhirconverter.mapping;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

/**
 * Loads the YAML mapping profile from the classpath.
 *
 * HOW YAML IS LOADED:
 * ===================
 * 1. We use SnakeYAML library (org.yaml.snakeyaml.Yaml)
 * 2. The YAML file sits in:
 * src/main/resources/mapping_profiles/hl7_adt_v2_coverage.yaml
 * 3. At runtime, Spring Boot packages resources into the classpath
 * 4. We use getResourceAsStream() to read the file as an InputStream
 * 5. SnakeYAML's loadAs() method parses the YAML and maps it to our
 * MappingProfile POJO
 *
 * The mapping is automatic:
 * - YAML key "profile" → MappingProfile.profile
 * - YAML key "segments" → MappingProfile.segments (as nested Maps)
 */
@Component
public class MappingLoader {

        // Path to the YAML file inside the classpath (src/main/resources/)
        private static final String MAPPING_FILE = "mapping_profiles/hl7_adt_v2_coverage.yaml";

        /**
         * Loads and parses the YAML mapping profile.
         *
         * @return MappingProfile object populated from the YAML file
         * @throws RuntimeException if the YAML file cannot be found or parsed
         */
        public MappingProfile loadProfile() {
                // Create a new SnakeYAML parser instance
                Yaml yaml = new Yaml();

                // Load the YAML file from the classpath as an InputStream
                InputStream inputStream = getClass()
                                .getClassLoader()
                                .getResourceAsStream(MAPPING_FILE);

                // Check that the file was found
                if (inputStream == null) {
                        throw new RuntimeException(
                                        "Mapping profile not found at: " + MAPPING_FILE +
                                                        ". Make sure the file exists in src/main/resources/mapping_profiles/");
                }

                // Parse the YAML into our MappingProfile POJO
                // SnakeYAML automatically maps YAML keys to Java fields by name
                MappingProfile profile = yaml.loadAs(inputStream, MappingProfile.class);

                System.out.println("✅ Loaded mapping profile: " + profile.getProfile()
                                + " v" + profile.getVersion());

                return profile;
        }
}
