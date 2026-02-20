-- ============================================================
-- Table: conversion_records
-- Stores every HL7-to-FHIR conversion attempt with its result.
-- ============================================================
CREATE TABLE IF NOT EXISTS conversion_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hl7_hash VARCHAR(64) NOT NULL COMMENT 'SHA-256 hash of raw HL7 for uniqueness',
    raw_hl7 TEXT NOT NULL COMMENT 'Original HL7 message received',
    fhir_json LONGTEXT COMMENT 'Generated FHIR Bundle JSON',
    status VARCHAR(20) NOT NULL COMMENT 'SUCCESS or ERROR',
    error_message TEXT COMMENT 'Error details if conversion failed',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'When the conversion happened',
    UNIQUE KEY uk_hl7_hash (hl7_hash)
);