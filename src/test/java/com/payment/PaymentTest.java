package com.payment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

public class PaymentTest {

    @Test
    void testTotalAmount() {
        Payment p = new Payment(1, "Student", "BSIT", 100.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        assertEquals(185.0, p.getTotalAmount(), 0.001);
    }

    @Test
    void testTotalAmountWithNulls() {
        Payment p = new Payment(1, "Student", "BSIT", null, null, null, null, "Receiver", "Remarks");
        assertEquals(0.0, p.getTotalAmount(), 0.001);
    }

    @Test
    void testStatusActiveByDefault() {
        Payment p = new Payment(1, "Student", "BSIT", 100.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        assertEquals(Payment.STATUS_ACTIVE, p.getStatus());
        assertTrue(p.isActive());
        assertFalse(p.isVoid());
    }

    @Test
    void testStatusVoid() {
        Payment p = new Payment(1, "Student", "BSIT", 100.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        p.setStatus(Payment.STATUS_VOID);
        assertEquals(Payment.STATUS_VOID, p.getStatus());
        assertTrue(p.isVoid());
        assertFalse(p.isActive());
        assertEquals(0.0, p.getTotalAmount(), "Void payment must have total 0.0");
        assertEquals(185.0, p.getFaceAmount(), "Face amount should reflect original fee sum");
        assertFalse(p.hasPayment(), "Void payment should report hasPayment=false");
    }

    @Test
    void testExactDuplicate() {
        Payment p1 = new Payment(1001, "Student", "BSIT", 100.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        p1.setRemittanceDate(LocalDate.of(2026, 8, 19));

        Payment p2 = new Payment(1001, "Student", "BSIT", 100.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        p2.setRemittanceDate(LocalDate.of(2026, 8, 19));

        assertTrue(p1.isExactDuplicateOf(p2), "Identical payments should be exact duplicates");
    }

    @Test
    void testConflictDifferentFee() {
        Payment p1 = new Payment(1001, "Student", "BSIT", 100.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        p1.setRemittanceDate(LocalDate.of(2026, 8, 19));

        Payment p2 = new Payment(1001, "Student", "BSIT", 150.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        p2.setRemittanceDate(LocalDate.of(2026, 8, 19));

        assertFalse(p1.isExactDuplicateOf(p2), "Different intel fee should be conflict");
    }

    @Test
    void testConflictDifferentRemittance() {
        Payment p1 = new Payment(1001, "Student", "BSIT", 100.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        p1.setRemittanceDate(LocalDate.of(2026, 8, 19));

        Payment p2 = new Payment(1001, "Student", "BSIT", 100.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        p2.setRemittanceDate(LocalDate.of(2026, 8, 20));

        assertFalse(p1.isExactDuplicateOf(p2), "Different remittance date should be conflict");
    }

    @Test
    void testStudentIdLinking() {
        Payment p = new Payment(1, "Student", "BSIT", 100.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        assertNull(p.getStudentId());
        p.setStudentId("STU-000001");
        assertEquals("STU-000001", p.getStudentId());
    }

    @Test
    void testTimestamps() {
        Payment p = new Payment(1, "Student", "BSIT", 100.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        assertNotNull(p.getCreatedAt());
        assertNotNull(p.getUpdatedAt());

        java.time.LocalDateTime before = p.getUpdatedAt();
        p.setStatus(Payment.STATUS_VOID);
        assertTrue(p.getUpdatedAt().compareTo(before) >= 0 || p.getUpdatedAt().equals(before));
    }

    @Test
    void testStatusRefunded() {
        Payment p = new Payment(1, "Student", "BSIT", 100.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        p.setStatus(Payment.STATUS_REFUNDED);
        assertEquals(Payment.STATUS_REFUNDED, p.getStatus());
        assertTrue(p.isRefunded());
        assertFalse(p.isActive());
        assertFalse(p.isVoid());
        assertEquals(-185.0, p.getTotalAmount(), 0.001, "Refunded payment total must be negative face amount");
        assertEquals(185.0, p.getFaceAmount(), 0.001, "Face amount remains positive");
        assertTrue(p.hasPayment(), "Refunded payment still counts as having a financial record");
    }

    @Test
    void testStatusAliases() {
        Payment p = new Payment(1, "Student", "BSIT", 100.0, 50.0, 10.0, 25.0, "Receiver", "Remarks");
        p.setStatus("VOIDED");
        assertTrue(p.isVoid());
        assertEquals(0.0, p.getTotalAmount());

        p.setStatus("REFUND");
        assertTrue(p.isRefunded());
        assertEquals(-185.0, p.getTotalAmount());
    }

    @Test
    void testNormalizeStatus() {
        assertEquals(Payment.STATUS_ACTIVE, Payment.normalizeStatus("active"));
        assertEquals(Payment.STATUS_ACTIVE, Payment.normalizeStatus(null));
        assertEquals(Payment.STATUS_VOID, Payment.normalizeStatus("voided"));
        assertEquals(Payment.STATUS_VOID, Payment.normalizeStatus("VOID"));
        assertEquals(Payment.STATUS_REFUNDED, Payment.normalizeStatus("refund"));
        assertEquals(Payment.STATUS_REFUNDED, Payment.normalizeStatus("REFUNDED"));
    }
}