package com.payment;

import java.util.Locale;

/**
 * Rule engine for detecting comments in receipt remarks indicating
 * refunds or reimbursements.
 */
public final class RefundReceiptRule {

    private RefundReceiptRule() {}

    /**
     * Checks whether a remark string indicates that the receipt is a refund / reimbursement.
     *
     * @param remarks The text from the remarks column/field
     * @return true if comments indicate a refund, false otherwise
     */
    public static boolean isRefundDueToRemarks(String remarks) {
        return VoidReceiptRule.containsRefundStatement(remarks);
    }

    /**
     * Generates an audit trail reason string for a refunded remark.
     */
    public static String getRefundReason(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            return "Refunded payment";
        }
        return "Auto-refunded based on remarks: " + remarks.trim();
    }
}

