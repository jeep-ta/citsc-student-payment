package com.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class RefundReceiptRuleTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "refund",
        "Refund",
        "REFUND",
        "refunded",
        "refunded to student",
        "refunding",
        "refunds",
        "reimbursement",
        "reimbursed",
        "reimbursements",
        "return of payment",
        "returned payment",
        "Student requested refund due to overpayment",
        "Processed refund for double charge",
        "Special reimbursement approved"
    })
    void testRefundKeywordsDetected(String remarks) {
        assertTrue(RefundReceiptRule.isRefundDueToRemarks(remarks),
            "Expected refund keyword to be recognized: " + remarks);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "damaged receipt",
        "duplicate receipt",
        "clerical error",
        "Castaneda",
        "Montilla",
        "Regular payment",
        "Paid in full",
        "T-shirt payment"
    })
    void testNonRefundKeywordsNotDetected(String remarks) {
        assertFalse(RefundReceiptRule.isRefundDueToRemarks(remarks),
            "Expected non-refund text not to be flagged: " + remarks);
    }

    @Test
    void testNullRemarks() {
        assertFalse(RefundReceiptRule.isRefundDueToRemarks(null));
        assertEquals("Refunded payment", RefundReceiptRule.getRefundReason(null));
    }

    @Test
    void testGetRefundReason() {
        assertEquals("Auto-refunded based on remarks: Refunded to student",
            RefundReceiptRule.getRefundReason("Refunded to student"));
    }
}

