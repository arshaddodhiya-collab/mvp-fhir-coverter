package com.nhcx.fhirconverter.repository;

import com.nhcx.fhirconverter.model.ConversionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for ConversionRecord entities.
 *
 * By extending JpaRepository, we automatically get these methods (no code
 * needed!):
 * - save(entity) → INSERT or UPDATE a record
 * - findById(id) → SELECT by primary key
 * - findAll() → SELECT all records
 * - deleteById(id) → DELETE by primary key
 * - count() → COUNT all records
 *
 * Spring Boot auto-detects this interface and creates an implementation at
 * runtime.
 */
@Repository
public interface ConversionRepository extends JpaRepository<ConversionRecord, Long> {

    /**
     * Find an existing conversion record by its HL7 message hash.
     * Spring Data JPA auto-generates:
     * SELECT * FROM conversion_records WHERE hl7_hash = ?
     */
    Optional<ConversionRecord> findByHl7Hash(String hl7Hash);
}
