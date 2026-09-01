package com.payment;

import java.time.LocalDateTime;

/**
 * Per-spreadsheet provenance and outcome summary within a parent import batch.
 */
public class ImportBatchFile {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private int id;
    private String batchCode;
    private String fileName;
    private String fileHash;
    private String receiptAcademicYear;
    private ChargeAcademicTerm receiptTerm = ChargeAcademicTerm.UNASSIGNED;
    private int totalRows;
    private int newRecords;
    private int duplicateRecords;
    private int conflictRecords;
    private int errorRecords;
    private String status = STATUS_PENDING;
    private LocalDateTime createdAt = LocalDateTime.now();

    public ImportBatchFile() {}

    public ImportBatchFile(String fileName, String fileHash) {
        this.fileName = fileName;
        this.fileHash = fileHash;
    }

    public int getId() { return id; }
    public String getBatchCode() { return batchCode; }
    public String getFileName() { return fileName; }
    public String getFileHash() { return fileHash; }
    public String getReceiptAcademicYear() { return receiptAcademicYear; }
    public ChargeAcademicTerm getReceiptTerm() {
        return receiptTerm != null ? receiptTerm : ChargeAcademicTerm.UNASSIGNED;
    }
    public ReceiptKey getReceiptKey(int receiptNumber) {
        return new ReceiptKey(receiptNumber, receiptAcademicYear, getReceiptTerm());
    }
    public String getReceiptPeriodDisplay() {
        ReceiptKey key = getReceiptKey(1);
        return key.hasDefinedScope() ? key.displayScope() : "Unassigned";
    }
    public int getTotalRows() { return totalRows; }
    public int getNewRecords() { return newRecords; }
    public int getDuplicateRecords() { return duplicateRecords; }
    public int getConflictRecords() { return conflictRecords; }
    public int getErrorRecords() { return errorRecords; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(int id) { this.id = id; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public void setReceiptAcademicYear(String receiptAcademicYear) {
        this.receiptAcademicYear = new ReceiptKey(1, receiptAcademicYear, getReceiptTerm()).academicYear();
    }
    public void setReceiptTerm(ChargeAcademicTerm receiptTerm) {
        this.receiptTerm = receiptTerm != null ? receiptTerm : ChargeAcademicTerm.UNASSIGNED;
    }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public void setNewRecords(int newRecords) { this.newRecords = newRecords; }
    public void setDuplicateRecords(int duplicateRecords) { this.duplicateRecords = duplicateRecords; }
    public void setConflictRecords(int conflictRecords) { this.conflictRecords = conflictRecords; }
    public void setErrorRecords(int errorRecords) { this.errorRecords = errorRecords; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
