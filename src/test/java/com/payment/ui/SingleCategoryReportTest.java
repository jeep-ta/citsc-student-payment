package com.payment.ui;

import com.payment.Payment;
import com.payment.Student;
import com.payment.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SingleCategoryReportTest {

    private DatabaseManager db;
    private static final String TEST_DB = "test_single_cat_report.db";

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("java.awt.headless", "true");
        DatabaseManager.setCustomDatabaseUrl("jdbc:sqlite:" + TEST_DB);
        DatabaseManager.resetInstance();
        db = DatabaseManager.getInstance();

        try (var stmt = db.getConnection().createStatement()) {
            stmt.execute("DELETE FROM payments");
            stmt.execute("DELETE FROM students");
            stmt.execute("DELETE FROM fee_term_rules");
        }
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            try (var stmt = db.getConnection().createStatement()) {
                stmt.execute("DELETE FROM payments");
                stmt.execute("DELETE FROM students");
            } catch (Exception ignored) {}
        }
        File f = new File(TEST_DB);
        if (f.exists()) f.delete();
    }

    @Test
    void testAlphabeticalOrderingAndMultiPaymentGrouping() throws Exception {
        // Student Z: 1 payment of 500 for T-Shirt
        Student sZ = new Student("Zulueta, Maria");
        sZ.setStudentCode("STU-000003");
        sZ.setProgram("BSIT");
        db.insertStudent(sZ);

        Payment pZ = new Payment(5001, "Zulueta, Maria", "BSIT", 0.0, 500.0, 0.0, 0.0, "Alice", "Size M");
        pZ.setStudentId("STU-000003");
        pZ.setRemittanceDate(LocalDate.of(2026, 8, 1));
        db.insertPayment(pZ);

        // Student A: 2 payments for T-Shirt (200 downpayment, then 300 balance)
        Student sA = new Student("Abad, Bryan");
        sA.setStudentCode("STU-000001");
        sA.setProgram("BSIT");
        db.insertStudent(sA);

        Payment pA1 = new Payment(4001, "Abad, Bryan", "BSIT", 0.0, 200.0, 0.0, 0.0, "Alice", "Downpayment");
        pA1.setStudentId("STU-000001");
        pA1.setRemittanceDate(LocalDate.of(2026, 8, 5));
        db.insertPayment(pA1);

        Payment pA2 = new Payment(4050, "Abad, Bryan", "BSIT", 0.0, 300.0, 0.0, 0.0, "Bob", "Size L, fully paid");
        pA2.setStudentId("STU-000001");
        pA2.setRemittanceDate(LocalDate.of(2026, 8, 20));
        db.insertPayment(pA2);

        // Student M: 1 payment for Intel Fee only (NO T-Shirt payment)
        Student sM = new Student("Mercado, Juan");
        sM.setStudentCode("STU-000002");
        sM.setProgram("BSCS");
        db.insertStudent(sM);

        Payment pM = new Payment(6001, "Mercado, Juan", "BSCS", 50.0, 0.0, 0.0, 0.0, "Alice", "Intel only");
        pM.setStudentId("STU-000002");
        pM.setRemittanceDate(LocalDate.of(2026, 8, 10));
        db.insertPayment(pM);

        ReportsPanel panel = new ReportsPanel();
        ReportsPanel.SingleCategoryResult result = panel.generateSingleCategoryReport("T-Shirt Sizing");

        assertNotNull(result);
        List<Map<String, Object>> data = result.data;
        String[] columns = result.columns;

        // Verify column headers
        assertArrayEquals(
            new String[]{"#", "Receipt #", "Student Name", "Program", "1st Payment", "2nd Receipt #", "2nd Payment", "Total Paid", "Remarks", "Date"},
            columns
        );

        // Student M must NOT be in T-Shirt roster (since amount was 0)
        assertEquals(2, data.size(), "Only students with T-Shirt payments should be included");

        // Verify alphabetical order: Abad first, Zulueta second
        Map<String, Object> row1 = data.get(0);
        Map<String, Object> row2 = data.get(1);

        assertEquals("Abad, Bryan", row1.get("Student Name"));
        assertEquals(1, row1.get("#"));
        assertEquals(4001, row1.get("Receipt #"));
        assertEquals(200.0, (Double) row1.get("1st Payment"), 0.001);
        assertEquals(4050, row1.get("2nd Receipt #"));
        assertEquals(300.0, (Double) row1.get("2nd Payment"), 0.001);
        assertEquals(500.0, (Double) row1.get("Total Paid"), 0.001);
        assertTrue(((String) row1.get("Remarks")).contains("Downpayment"));
        assertTrue(((String) row1.get("Remarks")).contains("Size L, fully paid"));
        assertTrue(((String) row1.get("Date")).contains("2026-08-05"));
        assertTrue(((String) row1.get("Date")).contains("2026-08-20"));

        assertEquals("Zulueta, Maria", row2.get("Student Name"));
        assertEquals(2, row2.get("#"));
        assertEquals(5001, row2.get("Receipt #"));
        assertEquals(500.0, (Double) row2.get("1st Payment"), 0.001);
        assertNull(row2.get("2nd Receipt #"), "Single payment should have null 2nd Receipt #");
        assertNull(row2.get("2nd Payment"), "Single payment should have null 2nd Payment");
        assertEquals(500.0, (Double) row2.get("Total Paid"), 0.001);
        assertEquals("Size M", row2.get("Remarks"));
        assertEquals("2026-08-01", row2.get("Date"));
    }

    @Test
    void testIntelFeeCategoryReport() throws Exception {
        Student s1 = new Student("Cruz, Clara");
        s1.setStudentCode("STU-000010");
        db.insertStudent(s1);

        Payment p1 = new Payment(1001, "Cruz, Clara", "BSIT", 50.0, 0.0, 0.0, 0.0, "Alice", "");
        p1.setStudentId("STU-000010");
        db.insertPayment(p1);

        Student s2 = new Student("Alvarez, Bea");
        s2.setStudentCode("STU-000011");
        db.insertStudent(s2);

        Payment p2 = new Payment(1002, "Alvarez, Bea", "BSCS", 50.0, 0.0, 0.0, 0.0, "Alice", "");
        p2.setStudentId("STU-000011");
        db.insertPayment(p2);

        ReportsPanel panel = new ReportsPanel();
        ReportsPanel.SingleCategoryResult result = panel.generateSingleCategoryReport("Intel Fee");

        assertEquals(2, result.data.size());
        // Alvarez should be first alphabetically
        assertEquals("Alvarez, Bea", result.data.get(0).get("Student Name"));
        assertEquals(50.0, (Double) result.data.get(0).get("1st Payment"), 0.001);
        assertEquals(50.0, (Double) result.data.get(0).get("Total Paid"), 0.001);

        assertEquals("Cruz, Clara", result.data.get(1).get("Student Name"));
    }
}
