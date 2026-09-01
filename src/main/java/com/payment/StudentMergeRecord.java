package com.payment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Model representing a student merge operation with full undo capabilities.
 */
public class StudentMergeRecord {
    private int id;
    private String mergeCode;
    private String sourceStudentCode;
    private String sourceName;
    private String sourceNormalizedName;
    private String sourceProgram;
    private Integer sourceYearLevel;
    private LocalDateTime sourceCreatedAt;
    private LocalDateTime sourceUpdatedAt;
    private String targetStudentCode;
    private String targetName;
    private String paymentReceipts; // comma-separated receipt numbers
    private LocalDateTime mergedAt;
    private String mergedBy;
    private String reason;
    private String status; // "ACTIVE" or "REVERTED"

    public StudentMergeRecord() {
        this.status = "ACTIVE";
        this.mergedAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMergeCode() { return mergeCode; }
    public void setMergeCode(String mergeCode) { this.mergeCode = mergeCode; }

    public String getSourceStudentCode() { return sourceStudentCode; }
    public void setSourceStudentCode(String sourceStudentCode) { this.sourceStudentCode = sourceStudentCode; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getSourceNormalizedName() { return sourceNormalizedName; }
    public void setSourceNormalizedName(String sourceNormalizedName) { this.sourceNormalizedName = sourceNormalizedName; }

    public String getSourceProgram() { return sourceProgram; }
    public void setSourceProgram(String sourceProgram) { this.sourceProgram = sourceProgram; }

    public Integer getSourceYearLevel() { return sourceYearLevel; }
    public void setSourceYearLevel(Integer sourceYearLevel) { this.sourceYearLevel = sourceYearLevel; }

    public LocalDateTime getSourceCreatedAt() { return sourceCreatedAt; }
    public void setSourceCreatedAt(LocalDateTime sourceCreatedAt) { this.sourceCreatedAt = sourceCreatedAt; }

    public LocalDateTime getSourceUpdatedAt() { return sourceUpdatedAt; }
    public void setSourceUpdatedAt(LocalDateTime sourceUpdatedAt) { this.sourceUpdatedAt = sourceUpdatedAt; }

    public String getTargetStudentCode() { return targetStudentCode; }
    public void setTargetStudentCode(String targetStudentCode) { this.targetStudentCode = targetStudentCode; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public String getPaymentReceipts() { return paymentReceipts; }
    public void setPaymentReceipts(String paymentReceipts) { this.paymentReceipts = paymentReceipts; }

    public List<Integer> getReceiptNumbersList() {
        if (paymentReceipts == null || paymentReceipts.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(paymentReceipts.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Integer::parseInt)
            .collect(Collectors.toList());
    }

    public void setReceiptNumbersList(List<Integer> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            this.paymentReceipts = "";
        } else {
            this.paymentReceipts = receipts.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        }
    }

    public LocalDateTime getMergedAt() { return mergedAt; }
    public void setMergedAt(LocalDateTime mergedAt) { this.mergedAt = mergedAt; }

    public String getMergedBy() { return mergedBy; }
    public void setMergedBy(String mergedBy) { this.mergedBy = mergedBy; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isActive() { return "ACTIVE".equalsIgnoreCase(status); }
    public boolean isReverted() { return "REVERTED".equalsIgnoreCase(status); }
}
