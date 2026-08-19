package com.payment;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents an Excel import batch with tracking metadata.
 */
public class ImportBatch {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_ERROR = "ERROR";

    private int id;
    private String batchCode;          // e.g., IMP-2026-0001
    private String fileName;
    private LocalDate remittanceDate;
    private LocalDateTime importedAt;
    private String importedBy;
    private int totalRows;
    private int newRecords;
    private int duplicateRecords;
    private int conflictRecords;
    private int errorRecords;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ImportBatch(String fileName, LocalDate remittanceDate, String importedBy) {
        this.fileName = fileName;
        this.remittanceDate = remittanceDate;
        this.importedBy = importedBy;
        this.importedAt = LocalDateTime.now();
        this.totalRows = 0;
        this.newRecords = 0;
        this.duplicateRecords = 0;
        this.conflictRecords = 0;
        this.errorRecords = 0;
        this.status = STATUS_PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // No-arg constructor for deserialization
    public ImportBatch() {
        this.status = STATUS_PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // --- Getters ---
    public int getId() {
        return id;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public String getFileName() {
        return fileName;
    }

    public LocalDate getRemittanceDate() {
        return remittanceDate;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }

    public String getImportedBy() {
        return importedBy;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getNewRecords() {
        return newRecords;
    }

    public int getDuplicateRecords() {
        return duplicateRecords;
    }

    public int getConflictRecords() {
        return conflictRecords;
    }

    public int getErrorRecords() {
        return errorRecords;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // --- Setters ---
    public void setId(int id) {
        this.id = id;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setRemittanceDate(LocalDate remittanceDate) {
        this.remittanceDate = remittanceDate;
    }

    public void setImportedAt(LocalDateTime importedAt) {
        this.importedAt = importedAt;
    }

    public void setImportedBy(String importedBy) {
        this.importedBy = importedBy;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public void setNewRecords(int newRecords) {
        this.newRecords = newRecords;
    }

    public void setDuplicateRecords(int duplicateRecords) {
        this.duplicateRecords = duplicateRecords;
    }

    public void setConflictRecords(int conflictRecords) {
        this.conflictRecords = conflictRecords;
    }

    public void setErrorRecords(int errorRecords) {
        this.errorRecords = errorRecords;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // --- Helpers ---
    public void incrementTotalRows() {
        this.totalRows++;
    }

    public void incrementNewRecords() {
        this.newRecords++;
    }

    public void incrementDuplicateRecords() {
        this.duplicateRecords++;
    }

    public void incrementConflictRecords() {
        this.conflictRecords++;
    }

    public void incrementErrorRecords() {
        this.errorRecords++;
    }

    @Override
    public String toString() {
        return String.format(
            "ImportBatch{batchCode='%s', file='%s', date=%s, rows=%d, new=%d, dup=%d, conflict=%d, error=%d, status='%s'}",
            batchCode, fileName, remittanceDate, totalRows, newRecords, duplicateRecords, conflictRecords, errorRecords, status
        );
    }
}