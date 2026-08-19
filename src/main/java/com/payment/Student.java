package com.payment;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.time.LocalDateTime;

public class Student {
    private String studentCode;       // Internal record number: STU-000001
    private String name;              // Original name from Excel (preserved)
    private String normalizedName;    // Normalized for matching (trim, lowercase, collapse whitespace)
    private String program;           // Primary program
    private Integer yearLevel;        // Year level (optional)
    private List<Payment> payments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Student(String name) {
        this.name = name != null ? name.trim() : "";
        this.normalizedName = NameNormalizer.normalize(name);
        this.payments = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // No-arg constructor for Gson/JSON deserialization
    public Student() {
        this.payments = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // --- Getters ---
    public String getStudentCode() {
        return studentCode;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        if (normalizedName == null && name != null) {
            normalizedName = NameNormalizer.normalize(name);
        }
        return normalizedName;
    }

    public String getProgram() {
        return program;
    }

    public Integer getYearLevel() {
        return yearLevel;
    }

    public List<Payment> getPayments() {
        return payments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // --- Setters ---
    public void setStudentCode(String studentCode) {
        this.studentCode = studentCode;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
        this.normalizedName = NameNormalizer.normalize(name);
        this.updatedAt = LocalDateTime.now();
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public void setProgram(String program) {
        this.program = program;
    }

    public void setYearLevel(Integer yearLevel) {
        this.yearLevel = yearLevel;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // --- Payment management ---
    public void addPayment(Payment payment) {
        this.payments.add(payment);
        this.updatedAt = LocalDateTime.now();
    }

    public void sortPaymentsByReceiptNumber() {
        this.payments.sort(Comparator.comparing(Payment::getReceiptNumber));
    }

    // --- Calculated fields ---
    public double getTotalAmount() {
        return payments.stream()
            .mapToDouble(Payment::getTotalAmount)
            .sum();
    }

    public int getPaymentCount() {
        return payments.size();
    }

    // --- Equality based on studentCode (if set) or normalizedName ---
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Student student = (Student) obj;
        if (studentCode != null && student.studentCode != null) {
            return studentCode.equals(student.studentCode);
        }
        return normalizedName != null ? normalizedName.equals(student.normalizedName) : student.normalizedName == null;
    }

    @Override
    public int hashCode() {
        if (studentCode != null) return studentCode.hashCode();
        return normalizedName != null ? normalizedName.hashCode() : 0;
    }

    @Override
    public String toString() {
        String code = studentCode != null ? studentCode + " " : "";
        return code + name + " (" + getPaymentCount() + " payment(s), Total: ₱" + String.format("%,.2f", getTotalAmount()) + ")";
    }
}