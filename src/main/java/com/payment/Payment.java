package com.payment;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Payment {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_VOID = "VOID";

    private int id;                   // Database primary key
    private int receiptNumber;
    private String name;              // Denormalized student name at creation time
    private String studentId;         // FK to Student.studentCode (internal record number)
    private String program;
    private Double intelFee;
    private Double tshirtSizing;
    private Double penalties;
    private Double citNight;
    private String receivedBy;
    private String remarks;
    private LocalDate remittanceDate;
    private String status;            // ACTIVE or VOID (no hard delete)

    // Period in which this receipt number was issued. This is intentionally
    // separate from the term/AY to which individual fees are attributed.
    private String receiptAcademicYear;
    private ChargeAcademicTerm receiptTerm;

    // Import provenance (null for manually-created or legacy records)
    private String importBatchCode;
    private String importSourceFile;
    private Integer importSourceRow;

    // Receipt-level default term and AY
    private ChargeAcademicTerm chargeAcademicTerm; // Default term
    private String academicYear;                  // Default AY e.g. "2025-2026"

    // Itemized per-fee category terms and AY (allows different fees in same receipt to have different terms)
    private ChargeAcademicTerm intelFeeTerm;
    private String intelFeeAy;

    private ChargeAcademicTerm tshirtTerm;
    private String tshirtAy;

    private ChargeAcademicTerm penaltiesTerm;
    private String penaltiesAy;

    private ChargeAcademicTerm citNightTerm;
    private String citNightAy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Payment(int receiptNumber, String name, String program,
                   Double intelFee, Double tshirtSizing, Double penalties,
                   Double citNight, String receivedBy, String remarks) {
        this.receiptNumber = receiptNumber;
        this.name = name;
        this.program = program;
        this.intelFee = intelFee;
        this.tshirtSizing = tshirtSizing;
        this.penalties = penalties;
        this.citNight = citNight;
        this.receivedBy = receivedBy;
        this.remarks = remarks;
        this.remittanceDate = LocalDate.now(); // Default to today
        this.status = STATUS_ACTIVE;
        this.receiptTerm = ChargeAcademicTerm.UNASSIGNED;
        this.chargeAcademicTerm = ChargeAcademicTerm.UNASSIGNED;
        this.academicYear = null;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // No-arg constructor for Gson/JSON deserialization
    public Payment() {
        this.remittanceDate = LocalDate.now();
        this.status = STATUS_ACTIVE;
        this.receiptTerm = ChargeAcademicTerm.UNASSIGNED;
        this.chargeAcademicTerm = ChargeAcademicTerm.UNASSIGNED;
        this.academicYear = null;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // --- Getters ---
    public int getId() {
        return id;
    }

    public int getReceiptNumber() {
        return receiptNumber;
    }

    public String getName() {
        return name;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getProgram() {
        return program;
    }

    public Double getIntelFee() {
        return intelFee;
    }

    public Double getTshirtSizing() {
        return tshirtSizing;
    }

    public Double getPenalties() {
        return penalties;
    }

    public Double getCitNight() {
        return citNight;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public String getRemarks() {
        return remarks;
    }

    public LocalDate getRemittanceDate() {
        return remittanceDate;
    }

    public String getStatus() {
        return status;
    }

    public String getReceiptAcademicYear() { return receiptAcademicYear; }
    public ChargeAcademicTerm getReceiptTerm() {
        return receiptTerm != null ? receiptTerm : ChargeAcademicTerm.UNASSIGNED;
    }
    public String getReceiptTermCode() { return getReceiptTerm().getCode(); }
    public String getImportBatchCode() { return importBatchCode; }
    public String getImportSourceFile() { return importSourceFile; }
    public Integer getImportSourceRow() { return importSourceRow; }
    public ReceiptKey getReceiptKey() { return ReceiptKey.from(this); }

    public boolean isVoid() {
        return STATUS_VOID.equals(status);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public ChargeAcademicTerm getChargeAcademicTerm() {
        return chargeAcademicTerm;
    }

    public String getChargeAcademicTermCode() {
        return chargeAcademicTerm != null ? chargeAcademicTerm.getCode() : ChargeAcademicTerm.DB_UNASSIGNED;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    // --- Itemized Term & AY Getters & Setters ---

    public ChargeAcademicTerm getIntelFeeTerm() {
        return intelFeeTerm;
    }

    public void setIntelFeeTerm(ChargeAcademicTerm intelFeeTerm) {
        this.intelFeeTerm = intelFeeTerm;
        this.updatedAt = LocalDateTime.now();
    }

    public String getIntelFeeAy() {
        return intelFeeAy;
    }

    public void setIntelFeeAy(String intelFeeAy) {
        this.intelFeeAy = intelFeeAy;
        this.updatedAt = LocalDateTime.now();
    }

    public ChargeAcademicTerm getTshirtTerm() {
        return tshirtTerm;
    }

    public void setTshirtTerm(ChargeAcademicTerm tshirtTerm) {
        this.tshirtTerm = tshirtTerm;
        this.updatedAt = LocalDateTime.now();
    }

    public String getTshirtAy() {
        return tshirtAy;
    }

    public void setTshirtAy(String tshirtAy) {
        this.tshirtAy = tshirtAy;
        this.updatedAt = LocalDateTime.now();
    }

    public ChargeAcademicTerm getPenaltiesTerm() {
        return penaltiesTerm;
    }

    public void setPenaltiesTerm(ChargeAcademicTerm penaltiesTerm) {
        this.penaltiesTerm = penaltiesTerm;
        this.updatedAt = LocalDateTime.now();
    }

    public String getPenaltiesAy() {
        return penaltiesAy;
    }

    public void setPenaltiesAy(String penaltiesAy) {
        this.penaltiesAy = penaltiesAy;
        this.updatedAt = LocalDateTime.now();
    }

    public ChargeAcademicTerm getCitNightTerm() {
        return citNightTerm;
    }

    public void setCitNightTerm(ChargeAcademicTerm citNightTerm) {
        this.citNightTerm = citNightTerm;
        this.updatedAt = LocalDateTime.now();
    }

    public String getCitNightAy() {
        return citNightAy;
    }

    public void setCitNightAy(String citNightAy) {
        this.citNightAy = citNightAy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Effective Term for a specific fee category.
     * Falls back to general chargeAcademicTerm if specific is null.
     */
    public ChargeAcademicTerm getEffectiveTermForCategory(String category) {
        if ("Intel Fee".equalsIgnoreCase(category) && intelFeeTerm != null) return intelFeeTerm;
        if (("T-Shirt".equalsIgnoreCase(category) || "T-Shirt Sizing".equalsIgnoreCase(category)) && tshirtTerm != null) return tshirtTerm;
        if ("Penalties".equalsIgnoreCase(category) && penaltiesTerm != null) return penaltiesTerm;
        if ("CIT Night".equalsIgnoreCase(category) && citNightTerm != null) return citNightTerm;
        return chargeAcademicTerm != null ? chargeAcademicTerm : ChargeAcademicTerm.UNASSIGNED;
    }

    /**
     * Effective Academic Year for a specific fee category.
     * Falls back to general academicYear if specific is null.
     */
    public String getEffectiveAyForCategory(String category) {
        if ("Intel Fee".equalsIgnoreCase(category) && intelFeeAy != null && !intelFeeAy.trim().isEmpty()) return intelFeeAy.trim();
        if (("T-Shirt".equalsIgnoreCase(category) || "T-Shirt Sizing".equalsIgnoreCase(category)) && tshirtAy != null && !tshirtAy.trim().isEmpty()) return tshirtAy.trim();
        if ("Penalties".equalsIgnoreCase(category) && penaltiesAy != null && !penaltiesAy.trim().isEmpty()) return penaltiesAy.trim();
        if ("CIT Night".equalsIgnoreCase(category) && citNightAy != null && !citNightAy.trim().isEmpty()) return citNightAy.trim();
        return academicYear != null && !academicYear.trim().isEmpty() ? academicYear.trim() : null;
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

    public void setReceiptNumber(int receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public void setProgram(String program) {
        this.program = program;
    }

    public void setIntelFee(Double intelFee) {
        this.intelFee = intelFee;
    }

    public void setTshirtSizing(Double tshirtSizing) {
        this.tshirtSizing = tshirtSizing;
    }

    public void setPenalties(Double penalties) {
        this.penalties = penalties;
    }

    public void setCitNight(Double citNight) {
        this.citNight = citNight;
    }

    public void setReceivedBy(String receivedBy) {
        this.receivedBy = receivedBy;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public void setRemittanceDate(LocalDate remittanceDate) {
        this.remittanceDate = remittanceDate;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void setReceiptAcademicYear(String receiptAcademicYear) {
        this.receiptAcademicYear = receiptAcademicYear != null && !receiptAcademicYear.isBlank()
            ? receiptAcademicYear.trim() : null;
        this.updatedAt = LocalDateTime.now();
    }

    public void setReceiptTerm(ChargeAcademicTerm receiptTerm) {
        this.receiptTerm = receiptTerm != null ? receiptTerm : ChargeAcademicTerm.UNASSIGNED;
        this.updatedAt = LocalDateTime.now();
    }

    public void setReceiptTermCode(String code) {
        setReceiptTerm(ChargeAcademicTerm.fromCode(code));
    }

    public void setImportBatchCode(String importBatchCode) { this.importBatchCode = importBatchCode; }
    public void setImportSourceFile(String importSourceFile) { this.importSourceFile = importSourceFile; }
    public void setImportSourceRow(Integer importSourceRow) { this.importSourceRow = importSourceRow; }

    public void setChargeAcademicTerm(ChargeAcademicTerm chargeAcademicTerm) {
        this.chargeAcademicTerm = chargeAcademicTerm != null ? chargeAcademicTerm : ChargeAcademicTerm.UNASSIGNED;
        this.updatedAt = LocalDateTime.now();
    }

    public void setChargeAcademicTermCode(String code) {
        this.chargeAcademicTerm = ChargeAcademicTerm.fromCode(code);
        this.updatedAt = LocalDateTime.now();
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
        this.updatedAt = LocalDateTime.now();
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // --- Business logic ---
    public double getTotalAmount() {
        double total = 0;
        if (intelFee != null) total += intelFee;
        if (tshirtSizing != null) total += tshirtSizing;
        if (penalties != null) total += penalties;
        if (citNight != null) total += citNight;
        return total;
    }

    public boolean hasPayment() {
        return intelFee != null || tshirtSizing != null || penalties != null || citNight != null;
    }

    /**
     * Compare this payment's financial fields against another payment with same receipt number.
     * Used for CONFLICT detection.
     */
    public boolean isExactDuplicateOf(Payment other) {
        if (other == null) return false;
        return receiptNumber == other.receiptNumber
            && equalsNullable(receiptAcademicYear, other.receiptAcademicYear)
            && equalsNullable(getReceiptTerm(), other.getReceiptTerm())
            && equalsNullable(program, other.program)
            && equalsNullable(intelFee, other.intelFee)
            && equalsNullable(tshirtSizing, other.tshirtSizing)
            && equalsNullable(penalties, other.penalties)
            && equalsNullable(citNight, other.citNight)
            && equalsNullable(receivedBy, other.receivedBy)
            && equalsNullable(remarks, other.remarks)
            && equalsNullable(remittanceDate, other.remittanceDate)
            && equalsNullable(chargeAcademicTerm, other.chargeAcademicTerm)
            && equalsNullable(academicYear, other.academicYear);
    }

    private static boolean equalsNullable(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    @Override
    public String toString() {
        String voidTag = isVoid() ? " [VOID]" : "";
        String scope = getReceiptKey().hasDefinedScope() ? " [" + getReceiptKey().displayScope() + "]" : "";
        return "Receipt #" + receiptNumber + scope + " - " + program + " - Total: ₱" + String.format("%,.2f", getTotalAmount()) + voidTag;
    }
}
