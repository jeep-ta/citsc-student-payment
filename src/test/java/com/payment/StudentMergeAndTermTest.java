package com.payment;

import com.payment.database.DatabaseManager;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class StudentMergeAndTermTest {

    private static DatabaseManager db;
    private static final String TEST_DB_FILE = "test_merge_terms.db";

    @BeforeAll
    static void setup() {
        DatabaseManager.setCustomDatabaseUrl("jdbc:sqlite:" + TEST_DB_FILE);
        DatabaseManager.resetInstance();
        db = DatabaseManager.getInstance();
    }

    @AfterAll
    static void cleanup() {
        DatabaseManager.resetInstance();
        new java.io.File(TEST_DB_FILE).delete();
        new java.io.File(TEST_DB_FILE + "-wal").delete();
        new java.io.File(TEST_DB_FILE + "-shm").delete();
        DatabaseManager.setCustomDatabaseUrl(null);
    }

    @BeforeEach
    void clearDb() throws SQLException {
        try (var conn = db.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM payments");
            stmt.execute("DELETE FROM students");
            stmt.execute("DELETE FROM student_merges");
            stmt.execute("DELETE FROM app_settings");
            stmt.execute("DELETE FROM audit_logs");
        }
    }

    @Test
    void testAcademicPeriodSettings() throws SQLException {
        // Test defaults
        assertEquals("2026-2027", db.getCurrentAcademicYear());
        assertEquals(ChargeAcademicTerm.FIRST_SEM, db.getCurrentAcademicTerm());
        assertTrue(db.isAutoAssignCurrentTerm());

        // Update settings
        db.setCurrentAcademicYear("2025-2026");
        db.setCurrentAcademicTerm(ChargeAcademicTerm.SECOND_SEM);
        db.setAutoAssignCurrentTerm(false);

        assertEquals("2025-2026", db.getCurrentAcademicYear());
        assertEquals(ChargeAcademicTerm.SECOND_SEM, db.getCurrentAcademicTerm());
        assertFalse(db.isAutoAssignCurrentTerm());
    }

    @Test
    void testStudentMergeAndUndo() throws SQLException {
        // 1. Create two students (Target & Source with Typo)
        Student target = new Student("DELA CRUZ, JUAN");
        target.setStudentCode("STU-000001");
        target.setProgram("BSIT");
        db.insertStudent(target);

        Student source = new Student("DELA CRUS, JUAN");
        source.setStudentCode("STU-000002");
        source.setProgram("BSIT");
        db.insertStudent(source);

        // 2. Add payment to source
        Payment p1 = new Payment(1001, "DELA CRUS, JUAN", "BSIT", 50.0, 0.0, 0.0, 0.0, "Treasurer", "Payment 1");
        p1.setStudentId(source.getStudentCode());
        p1.setRemittanceDate(LocalDate.now());
        p1.setStatus("ACTIVE");
        p1.setReceiptAcademicYear("2026-2027");
        p1.setReceiptTerm(ChargeAcademicTerm.FIRST_SEM);
        db.insertPayment(p1);

        Payment p2 = new Payment(1002, "DELA CRUS, JUAN", "BSIT", 0.0, 200.0, 0.0, 0.0, "Treasurer", "Payment 2");
        p2.setStudentId(source.getStudentCode());
        p2.setRemittanceDate(LocalDate.now());
        p2.setStatus("ACTIVE");
        p2.setReceiptAcademicYear("2026-2027");
        p2.setReceiptTerm(ChargeAcademicTerm.FIRST_SEM);
        db.insertPayment(p2);

        // Target also has a payment whose receipt number was reused next semester.
        Payment p3 = new Payment(1001, "DELA CRUZ, JUAN", "BSIT", 50.0, 0.0, 0.0, 0.0, "Treasurer", "Payment 3");
        p3.setStudentId(target.getStudentCode());
        p3.setRemittanceDate(LocalDate.now());
        p3.setStatus("ACTIVE");
        p3.setReceiptAcademicYear("2026-2027");
        p3.setReceiptTerm(ChargeAcademicTerm.SECOND_SEM);
        db.insertPayment(p3);

        assertEquals(2, db.getPaymentsByStudent(source.getStudentCode()).size());
        assertEquals(1, db.getPaymentsByStudent(target.getStudentCode()).size());

        // 3. Execute Merge: Source -> Target
        StudentMergeRecord mergeRecord = db.mergeStudents(
            source.getStudentCode(), target.getStudentCode(), "Typo in surname CRUS -> CRUZ", "test_user");

        assertNotNull(mergeRecord);
        assertEquals("ACTIVE", mergeRecord.getStatus());
        assertEquals(2, mergeRecord.getReceiptNumbersList().size());
        assertEquals(2, mergeRecord.getPaymentIdsList().size());

        // Source student should be deleted from active table
        Optional<Student> optSource = db.getStudentByCode(source.getStudentCode());
        assertTrue(optSource.isEmpty());

        // Target student should now have all 3 payments
        List<Payment> targetPayments = db.getPaymentsByStudent(target.getStudentCode());
        assertEquals(3, targetPayments.size());

        // 4. Undo the Merge
        boolean undone = db.undoStudentMerge(mergeRecord.getMergeCode(), "test_user");
        assertTrue(undone);

        // Source student is restored!
        Optional<Student> restoredSource = db.getStudentByCode(source.getStudentCode());
        assertTrue(restoredSource.isPresent());
        assertEquals("DELA CRUS, JUAN", restoredSource.get().getName());

        // Payments are back to their original owners
        assertEquals(2, db.getPaymentsByStudent(source.getStudentCode()).size());
        assertEquals(1, db.getPaymentsByStudent(target.getStudentCode()).size());
        assertEquals(ChargeAcademicTerm.SECOND_SEM,
            db.getPaymentsByStudent(target.getStudentCode()).get(0).getReceiptTerm());

        // Merge record status is now REVERTED
        List<StudentMergeRecord> allMerges = db.getAllStudentMerges();
        assertEquals(1, allMerges.size());
        assertEquals("REVERTED", allMerges.get(0).getStatus());
        assertTrue(allMerges.get(0).isReverted());
    }
}
