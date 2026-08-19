package com.payment;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Payment {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_VOID = "VOID";

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
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // No-arg constructor for Gson/JSON deserialization
    public Payment() {
        this.remittanceDate = LocalDate.now();
        this.status = STATUS_ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // --- Getters ---
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

    public boolean isVoid() {
        return STATUS_VOID.equals(status);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // --- Setters ---
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
     *
     * @param other The other payment (same receipt number)
     * @return true if all financial fields are identical
     */
    public boolean isExactDuplicateOf(Payment other) {
        if (other == null) return false;
        return receiptNumber == other.receiptNumber
            && equalsNullable(program, other.program)
            && equalsNullable(intelFee, other.intelFee)
            && equalsNullable(tshirtSizing, other.tshirtSizing)
            && equalsNullable(penalties, other.penalties)
            && equalsNullable(citNight, other.citNight)
            && equalsNullable(receivedBy, other.receivedBy)
            && equalsNullable(remarks, other.remarks)
            && equalsNullable(remittanceDate, other.remittanceDate);
    }

    private static boolean equalsNullable(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    @Override
    public String toString() {
        String voidTag = isVoid() ? " [VOID]" : "";
        return "Receipt #" + receiptNumber + " - " + program + " - Total: ₱" + String.format("%,.2f", getTotalAmount()) + voidTag;
    }
}