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
    public static final String STATUS_VOID = "VOID";
    public static final String STATUS_REFUNDED = "REFUNDED";

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
    private ChargeAcademicTerm chargeAcademicTerm; // Academic term for CIT Night/Penalty charges
    private String academicYear;
    private ChargeAcademicTerm intelFeeTerm, tshirtTerm, penaltiesTerm, citNightTerm;
    private String intelFeeAy, tshirtAy, penaltiesAy, citNightAy;
    private String receiptAcademicYear;
    private ChargeAcademicTerm receiptTerm;
    private String sourceFileName;

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
        this.chargeAcademicTerm = ChargeAcademicTerm.UNASSIGNED;
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
    public String getReceiptDisplay() { return receiptNumber > 0 ? "#" + receiptNumber : "No receipt"; }
    public String getStudentName() { return studentName; }
    public String getProgram() { return program; }
    public Double getIntelFee() { return intelFee; }
    public Double getTshirtSizing() { return tshirtSizing; }
    public Double getPenalties() { return penalties; }
    public Double getCitNight() { return citNight; }
    public String getReceivedBy() { return receivedBy; }
    public String getRemarks() { return remarks; }
    public LocalDate getRemittanceDate() { return remittanceDate; }
    public String getReceiptAcademicYear() { return receiptAcademicYear; }
    public ChargeAcademicTerm getReceiptTerm() {
        return receiptTerm != null ? receiptTerm : ChargeAcademicTerm.UNASSIGNED;
    }
    public ReceiptKey getReceiptKey() {
        return new ReceiptKey(receiptNumber, receiptAcademicYear, getReceiptTerm());
    }
    public String getSourceFileName() { return sourceFileName; }
    public String getStatus() { return status; }
    public String getMatchedStudentCode() { return matchedStudentCode; }
    public String getMatchedStudentName() { return matchedStudentName; }
    public List<Student> getAmbiguousMatches() { return ambiguousMatches; }
    public Payment getConflictingPayment() { return conflictingPayment; }
    public String getErrorMessage() { return errorMessage; }
    public String getProposedStudentCode() { return proposedStudentCode; }
    public ChargeAcademicTerm getChargeAcademicTerm() { return chargeAcademicTerm != null ? chargeAcademicTerm : ChargeAcademicTerm.UNASSIGNED; }
    public String getAcademicYear() { return academicYear; }
    public ChargeAcademicTerm getIntelFeeTerm() { return intelFeeTerm; }
    public String getIntelFeeAy() { return intelFeeAy; }
    public ChargeAcademicTerm getTshirtTerm() { return tshirtTerm; }
    public String getTshirtAy() { return tshirtAy; }
    public ChargeAcademicTerm getPenaltiesTerm() { return penaltiesTerm; }
    public String getPenaltiesAy() { return penaltiesAy; }
    public ChargeAcademicTerm getCitNightTerm() { return citNightTerm; }
    public String getCitNightAy() { return citNightAy; }

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
    public void setReceiptAcademicYear(String receiptAcademicYear) {
        this.receiptAcademicYear = new ReceiptKey(1, receiptAcademicYear, getReceiptTerm()).academicYear();
    }
    public void setReceiptTerm(ChargeAcademicTerm receiptTerm) {
        this.receiptTerm = receiptTerm != null ? receiptTerm : ChargeAcademicTerm.UNASSIGNED;
    }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public void setStatus(String status) { this.status = status; }
    public void setMatchedStudentCode(String matchedStudentCode) { this.matchedStudentCode = matchedStudentCode; }
    public void setMatchedStudentName(String matchedStudentName) { this.matchedStudentName = matchedStudentName; }
    public void setAmbiguousMatches(List<Student> ambiguousMatches) { this.ambiguousMatches = ambiguousMatches; }
    public void setConflictingPayment(Payment conflictingPayment) { this.conflictingPayment = conflictingPayment; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setProposedStudentCode(String proposedStudentCode) { this.proposedStudentCode = proposedStudentCode; }
    public void setChargeAcademicTerm(ChargeAcademicTerm chargeAcademicTerm) { this.chargeAcademicTerm = chargeAcademicTerm != null ? chargeAcademicTerm : ChargeAcademicTerm.UNASSIGNED; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear == null || academicYear.isBlank() ? null : academicYear.trim(); }
    public void setIntelFeeTerm(ChargeAcademicTerm value) { this.intelFeeTerm = value; }
    public void setIntelFeeAy(String value) { this.intelFeeAy = value == null || value.isBlank() ? null : value.trim(); }
    public void setTshirtTerm(ChargeAcademicTerm value) { this.tshirtTerm = value; }
    public void setTshirtAy(String value) { this.tshirtAy = value == null || value.isBlank() ? null : value.trim(); }
    public void setPenaltiesTerm(ChargeAcademicTerm value) { this.penaltiesTerm = value; }
    public void setPenaltiesAy(String value) { this.penaltiesAy = value == null || value.isBlank() ? null : value.trim(); }
    public void setCitNightTerm(ChargeAcademicTerm value) { this.citNightTerm = value; }
    public void setCitNightAy(String value) { this.citNightAy = value == null || value.isBlank() ? null : value.trim(); }

    // --- Helpers ---
    public boolean isNew() { return STATUS_NEW.equals(status); }
    public boolean isDuplicate() { return STATUS_DUPLICATE.equals(status); }
    public boolean isConflict() { return STATUS_CONFLICT.equals(status); }
    public boolean isAmbiguous() { return STATUS_AMBIGUOUS.equals(status); }
    public boolean isError() { return STATUS_ERROR.equals(status); }
    public boolean isVoid() { return STATUS_VOID.equals(status); }
    public boolean isRefunded() { return STATUS_REFUNDED.equals(status); }
    public boolean requiresReview() { return isConflict() || isAmbiguous() || isError(); }

    public double getTotalAmount() {
        if (isVoid()) return 0.0;
        if (isRefunded()) return -getFaceAmount();
        return getFaceAmount();
    }

    public double getFaceAmount() {
        double total = 0;
        if (intelFee != null) total += intelFee;
        if (tshirtSizing != null) total += tshirtSizing;
        if (penalties != null) total += penalties;
        if (citNight != null) total += citNight;
        return total;
    }

    @Override
    public String toString() {
        return String.format("ImportPreviewItem[file=%s, row=%d, receipt=%d, scope=%s, name=%s, status=%s, matched=%s]",
            sourceFileName, rowNumber, receiptNumber, getReceiptKey().displayScope(), studentName, status, matchedStudentCode);
    }
}
