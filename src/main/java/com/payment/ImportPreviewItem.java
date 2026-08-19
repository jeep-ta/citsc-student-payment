package com.payment;

import java.time.LocalDate;
import java.util.List;

/**
 * Represents a single row in the import preview.
 * Contains the parsed data, match result, and any conflicts/warnings.
 */
public class ImportPreviewItem {

    // Preview statuses
    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_DUPLICATE = "DUPLICATE";
    public static final String STATUS_CONFLICT = "CONFLICT";
    public static final String STATUS_AMBIGUOUS = "AMBIGUOUS";
    public static final String STATUS_ERROR = "ERROR";

    private int rowNumber;              // Excel row number (1-based, excluding header)
    private int receiptNumber;
    private String studentName;
    private String program;
    private Double intelFee;
    private Double tshirtSizing;
    private Double penalties;
    private Double citNight;
    private String receivedBy;
    private String remarks;
    private LocalDate remittanceDate;

    // Match results
    private String status;              // NEW, DUPLICATE, CONFLICT, AMBIGUOUS, ERROR
    private String matchedStudentCode;  // Student code if matched (STU-XXXXXX)
    private String matchedStudentName;  // Name of matched student
    private List<Student> ambiguousMatches; // Multiple students with same normalized name
    private Payment conflictingPayment; // Existing payment if conflict
    private String errorMessage;        // Error details if ERROR

    // For NEW students - the student code that would be assigned
    private String proposedStudentCode;

    public ImportPreviewItem() {
        this.status = STATUS_NEW;
    }

    public ImportPreviewItem(int rowNumber, int receiptNumber, String studentName, String program,
                             Double intelFee, Double tshirtSizing, Double penalties, Double citNight,
                             String receivedBy, String remarks, LocalDate remittanceDate) {
        this();
        this.rowNumber = rowNumber;
        this.receiptNumber = receiptNumber;
        this.studentName = studentName;
        this.program = program;
        this.intelFee = intelFee;
        this.tshirtSizing = tshirtSizing;
        this.penalties = penalties;
        this.citNight = citNight;
        this.receivedBy = receivedBy;
        this.remarks = remarks;
        this.remittanceDate = remittanceDate;
    }

    // --- Getters ---
    public int getRowNumber() { return rowNumber; }
    public int getReceiptNumber() { return receiptNumber; }
    public String getStudentName() { return studentName; }
    public String getProgram() { return program; }
    public Double getIntelFee() { return intelFee; }
    public Double getTshirtSizing() { return tshirtSizing; }
    public Double getPenalties() { return penalties; }
    public Double getCitNight() { return citNight; }
    public String getReceivedBy() { return receivedBy; }
    public String getRemarks() { return remarks; }
    public LocalDate getRemittanceDate() { return remittanceDate; }
    public String getStatus() { return status; }
    public String getMatchedStudentCode() { return matchedStudentCode; }
    public String getMatchedStudentName() { return matchedStudentName; }
    public List<Student> getAmbiguousMatches() { return ambiguousMatches; }
    public Payment getConflictingPayment() { return conflictingPayment; }
    public String getErrorMessage() { return errorMessage; }
    public String getProposedStudentCode() { return proposedStudentCode; }

    // --- Setters ---
    public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }
    public void setReceiptNumber(int receiptNumber) { this.receiptNumber = receiptNumber; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public void setProgram(String program) { this.program = program; }
    public void setIntelFee(Double intelFee) { this.intelFee = intelFee; }
    public void setTshirtSizing(Double tshirtSizing) { this.tshirtSizing = tshirtSizing; }
    public void setPenalties(Double penalties) { this.penalties = penalties; }
    public void setCitNight(Double citNight) { this.citNight = citNight; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public void setRemittanceDate(LocalDate remittanceDate) { this.remittanceDate = remittanceDate; }
    public void setStatus(String status) { this.status = status; }
    public void setMatchedStudentCode(String matchedStudentCode) { this.matchedStudentCode = matchedStudentCode; }
    public void setMatchedStudentName(String matchedStudentName) { this.matchedStudentName = matchedStudentName; }
    public void setAmbiguousMatches(List<Student> ambiguousMatches) { this.ambiguousMatches = ambiguousMatches; }
    public void setConflictingPayment(Payment conflictingPayment) { this.conflictingPayment = conflictingPayment; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setProposedStudentCode(String proposedStudentCode) { this.proposedStudentCode = proposedStudentCode; }

    // --- Helpers ---
    public boolean isNew() { return STATUS_NEW.equals(status); }
    public boolean isDuplicate() { return STATUS_DUPLICATE.equals(status); }
    public boolean isConflict() { return STATUS_CONFLICT.equals(status); }
    public boolean isAmbiguous() { return STATUS_AMBIGUOUS.equals(status); }
    public boolean isError() { return STATUS_ERROR.equals(status); }
    public boolean requiresReview() { return isConflict() || isAmbiguous() || isError(); }

    public double getTotalAmount() {
        double total = 0;
        if (intelFee != null) total += intelFee;
        if (tshirtSizing != null) total += tshirtSizing;
        if (penalties != null) total += penalties;
        if (citNight != null) total += citNight;
        return total;
    }

    @Override
    public String toString() {
        return String.format("ImportPreviewItem[row=%d, receipt=%d, name=%s, status=%s, matched=%s]",
            rowNumber, receiptNumber, studentName, status, matchedStudentCode);
    }
}