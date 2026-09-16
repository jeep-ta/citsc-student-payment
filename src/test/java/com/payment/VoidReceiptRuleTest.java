package com.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class VoidReceiptRuleTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "damaged receipt",
        "Damaged Receipt",
        "DAMAGED RECEIPT",
        "damaged",
        "damage",
        "damages",
        "receipt damaged",
        "paper damaged",
        "damaged copy",
        "Voided due to paper damage",
        "Damaged receipt during printing"
    })
    void testDamagedReceiptKeywords(String remark) {
        assertTrue(VoidReceiptRule.isVoidDueToRemarks(remark), "Should recognize damage remark: " + remark);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "duplication",
        "duplicate",
        "duplicated",
        "duplicate receipt",
        "Duplicate Receipt",
        "DUPLICATE",
        "duplicate copy",
        "double entry",
        "double entered",
        "double issue",
        "double issued",
        "already entered",
        "already encoded",
        "already recorded"
    })
    void testDuplicationKeywords(String remark) {
        assertTrue(VoidReceiptRule.isVoidDueToRemarks(remark), "Should recognize duplication remark: " + remark);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "void",
        "VOID",
        "voided",
        "voiding",
        "void receipt",
        "receipt voided",
        "null and void",
        "cancelled",
        "canceled",
        "cancellation",
        "cancelled receipt",
        "receipt cancelled",
        "invalid receipt",
        "spoil",
        "spoiled",
        "spoilage",
        "spoiled receipt",
        "torn",
        "torn receipt",
        "mutilated",
        "defective",
        "misprint",
        "misprinted",
        "wrong entry",
        "error entry",
        "erroneous entry",
        "printed by mistake"
    })
    void testOtherVoidAndDefectKeywords(String remark) {
        assertTrue(VoidReceiptRule.isVoidDueToRemarks(remark), "Should recognize void/defect remark: " + remark);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "refund",
        "Refund",
        "REFUND",
        "refunded",
        "refunding",
        "refunds",
        "student refund",
        "refund of intel fee",
        "refund requested",
        "refunded to student",
        "reimburse",
        "reimbursed",
        "reimbursement",
        "return of payment",
        "returned payment",
        // Crucial edge case: remarks mentioning void or duplicate AND refund MUST be excluded
        "void - refund requested",
        "duplicate payment - refund",
        "damaged receipt - refund to student",
        "cancelled due to refund",
        "refund for duplicate payment"
    })
    void testStrictRefundExclusions(String remark) {
        assertFalse(VoidReceiptRule.isVoidDueToRemarks(remark),
            "Refund statements must NOT be treated as void under damage/duplication rule: " + remark);
        assertTrue(VoidReceiptRule.containsRefundStatement(remark),
            "Should detect refund statement in: " + remark);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Castaneda",
        "Montilla",
        "Reyes",
        "Caliston",
        "Osorio",
        "BSIT 1-A",
        "Paid in cash",
        "Official receipt",
        "Batch 1",
        "Regular remittance"
    })
    void testRegularRemarksDoNotVoid(String remark) {
        assertFalse(VoidReceiptRule.isVoidDueToRemarks(remark), "Normal remarks must not be voided: " + remark);
    }

    @Test
    void testNullAndEmptyRemarks() {
        assertFalse(VoidReceiptRule.isVoidDueToRemarks(null));
        assertFalse(VoidReceiptRule.isVoidDueToRemarks(""));
        assertFalse(VoidReceiptRule.isVoidDueToRemarks("   "));
        assertFalse(VoidReceiptRule.containsRefundStatement(null));
        assertFalse(VoidReceiptRule.containsRefundStatement(""));
    }

    @Test
    void testGetVoidReason() {
        assertEquals("Auto-voided based on remarks: damaged receipt",
            VoidReceiptRule.getVoidReason("damaged receipt"));
        assertEquals("Voided receipt",
            VoidReceiptRule.getVoidReason(""));
    }
}

