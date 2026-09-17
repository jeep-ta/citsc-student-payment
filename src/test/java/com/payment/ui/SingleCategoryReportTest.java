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
            stmt.execute("DELETE FROM app_settings");
        }
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            try (var stmt = db.getConnection().createStatement()) {
                stmt.execute("DELETE FROM payments");
                stmt.execute("DELETE FROM students");
                stmt.execute("DELETE FROM app_settings");
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
            new String[]{"#", "Receipt #", "Student Name", "Program", "1st Payment", "2nd Receipt #", "2nd Payment", "Total Paid", "Status", "Remarks", "Date"},
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
        assertEquals("Fully Paid", row1.get("Status"));
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
        assertEquals("Fully Paid", row2.get("Status"));
        assertEquals("Size M", row2.get("Remarks"));
        assertEquals("2026-08-01", row2.get("Date"));
    }

    @Test
    void testIntelFeeCategoryReport() throws Exception {
        db.setCategoryFullTarget("Intel Fee", 50.0);

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
        assertEquals("Fully Paid", result.data.get(0).get("Status"));

        assertEquals("Cruz, Clara", result.data.get(1).get("Student Name"));
        assertEquals("Fully Paid", result.data.get(1).get("Status"));
    }

    @Test
    void testCategoryFullPaymentStatusThresholds() throws Exception {
        // Define full payment target for T-Shirt Sizing as 500.00
        db.setCategoryFullTarget("T-Shirt Sizing", 500.0);

        // Student 1: Partially Paid (paid 300 out of 500)
        Student s1 = new Student("Partial, Pete");
        s1.setStudentCode("STU-000021");
        db.insertStudent(s1);

        Payment p1 = new Payment(7001, "Partial, Pete", "BSIT", 0.0, 300.0, 0.0, 0.0, "Alice", "Downpayment");
        p1.setStudentId("STU-000021");
        p1.setRemittanceDate(LocalDate.of(2026, 8, 1));
        db.insertPayment(p1);

        // Student 2: Fully Paid (paid exactly 500)
        Student s2 = new Student("Exact, Emma");
        s2.setStudentCode("STU-000022");
        db.insertStudent(s2);

        Payment p2 = new Payment(7002, "Exact, Emma", "BSIS", 0.0, 500.0, 0.0, 0.0, "Alice", "Full");
        p2.setStudentId("STU-000022");
        p2.setRemittanceDate(LocalDate.of(2026, 8, 2));
        db.insertPayment(p2);

        // Student 3: Overpaid (paid 650 out of 500)
        Student s3 = new Student("Over, Oliver");
        s3.setStudentCode("STU-000023");
        db.insertStudent(s3);

        Payment p3 = new Payment(7003, "Over, Oliver", "BSCS", 0.0, 650.0, 0.0, 0.0, "Alice", "Overpayment");
        p3.setStudentId("STU-000023");
        p3.setRemittanceDate(LocalDate.of(2026, 8, 3));
        db.insertPayment(p3);

        ReportsPanel panel = new ReportsPanel();
        ReportsPanel.SingleCategoryResult result = panel.generateSingleCategoryReport("T-Shirt Sizing");

        assertEquals(3, result.data.size());

        // Alphabetical: Exact, Emma (1st), Over, Oliver (2nd), Partial, Pete (3rd)
        Map<String, Object> rowExact = result.data.get(0);
        assertEquals("Exact, Emma", rowExact.get("Student Name"));
        assertEquals(500.0, (Double) rowExact.get("Total Paid"), 0.001);
        assertEquals("Fully Paid", rowExact.get("Status"));

        Map<String, Object> rowOver = result.data.get(1);
        assertEquals("Over, Oliver", rowOver.get("Student Name"));
        assertEquals(650.0, (Double) rowOver.get("Total Paid"), 0.001);
        assertEquals("Overpaid", rowOver.get("Status"));

        Map<String, Object> rowPartial = result.data.get(2);
        assertEquals("Partial, Pete", rowPartial.get("Student Name"));
        assertEquals(300.0, (Double) rowPartial.get("Total Paid"), 0.001);
        assertEquals("Partially Paid", rowPartial.get("Status"));

        // Now dynamically re-define the target to 300.00
        db.setCategoryFullTarget("T-Shirt Sizing", 300.0);
        ReportsPanel.SingleCategoryResult updatedResult = panel.generateSingleCategoryReport("T-Shirt Sizing");

        // Pete (paid 300) should now be "Fully Paid"!
        Map<String, Object> updatedPete = updatedResult.data.get(2);
        assertEquals("Partial, Pete", updatedPete.get("Student Name"));
        assertEquals("Fully Paid", updatedPete.get("Status"));

        // Emma (paid 500) should now be "Overpaid"!
        Map<String, Object> updatedEmma = updatedResult.data.get(0);
        assertEquals("Exact, Emma", updatedEmma.get("Student Name"));
        assertEquals("Overpaid", updatedEmma.get("Status"));
    }

    @Test
    void testMultiplePaymentsMoreThanTwoInstallments() throws Exception {
        db.setCategoryFullTarget("T-Shirt Sizing", 500.0);

        // Student 1: 3 installments (100 + 150 + 250 = 500, Fully Paid)
        Student s3 = new Student("Castro, Carlos");
        s3.setStudentCode("STU-000031");
        s3.setProgram("BSCS");
        db.insertStudent(s3);

        Payment p3_1 = new Payment(8001, "Castro, Carlos", "BSCS", 0.0, 100.0, 0.0, 0.0, "Alice", "1st Inst");
        p3_1.setStudentId("STU-000031");
        p3_1.setRemittanceDate(LocalDate.of(2026, 8, 1));
        db.insertPayment(p3_1);

        Payment p3_2 = new Payment(8002, "Castro, Carlos", "BSCS", 0.0, 150.0, 0.0, 0.0, "Alice", "2nd Inst");
        p3_2.setStudentId("STU-000031");
        p3_2.setRemittanceDate(LocalDate.of(2026, 8, 10));
        db.insertPayment(p3_2);

        Payment p3_3 = new Payment(8003, "Castro, Carlos", "BSCS", 0.0, 250.0, 0.0, 0.0, "Bob", "Final Bal");
        p3_3.setStudentId("STU-000031");
        p3_3.setRemittanceDate(LocalDate.of(2026, 8, 20));
        db.insertPayment(p3_3);

        // Student 2: 4 installments (100 + 100 + 100 + 100 = 400, Partially Paid)
        Student s4 = new Student("Del Rosario, Dave");
        s4.setStudentCode("STU-000032");
        s4.setProgram("BSIT");
        db.insertStudent(s4);

        for (int i = 1; i <= 4; i++) {
            Payment pi = new Payment(8010 + i, "Del Rosario, Dave", "BSIT", 0.0, 100.0, 0.0, 0.0, "Alice", "Part " + i);
            pi.setStudentId("STU-000032");
            pi.setRemittanceDate(LocalDate.of(2026, 8, i * 5));
            db.insertPayment(pi);
        }

        // Student 3: 5 installments (150 + 150 + 150 + 100 + 50 = 600, Overpaid)
        Student s5 = new Student("Alvarez, Ana");
        s5.setStudentCode("STU-000033");
        s5.setProgram("BSIT");
        db.insertStudent(s5);

        double[] amounts = {150.0, 150.0, 150.0, 100.0, 50.0};
        for (int i = 0; i < amounts.length; i++) {
            Payment pi = new Payment(8020 + i, "Alvarez, Ana", "BSIT", 0.0, amounts[i], 0.0, 0.0, "Alice", "Inst " + (i + 1));
            pi.setStudentId("STU-000033");
            pi.setRemittanceDate(LocalDate.of(2026, 8, 2 + (i * 4)));
            db.insertPayment(pi);
        }

        // Student 4: 1 single payment (500, Fully Paid)
        Student s1 = new Student("Bernardo, Ben");
        s1.setStudentCode("STU-000034");
        s1.setProgram("BSIS");
        db.insertStudent(s1);

        Payment p1 = new Payment(8030, "Bernardo, Ben", "BSIS", 0.0, 500.0, 0.0, 0.0, "Bob", "Spot cash");
        p1.setStudentId("STU-000034");
        p1.setRemittanceDate(LocalDate.of(2026, 8, 12));
        db.insertPayment(p1);

        ReportsPanel panel = new ReportsPanel();
        ReportsPanel.SingleCategoryResult result = panel.generateSingleCategoryReport("T-Shirt Sizing");

        assertNotNull(result);
        assertEquals(4, result.data.size());

        // Dynamic columns must expand to 5 payment slots (1st, 2nd, 3rd, 4th, 5th)
        String[] expectedColumns = new String[]{
            "#", "Receipt #", "Student Name", "Program", "1st Payment",
            "2nd Receipt #", "2nd Payment",
            "3rd Receipt #", "3rd Payment",
            "4th Receipt #", "4th Payment",
            "5th Receipt #", "5th Payment",
            "Total Paid", "Status", "Remarks", "Date"
        };
        assertArrayEquals(expectedColumns, result.columns);

        // Alphabetical: Alvarez (1st), Bernardo (2nd), Castro (3rd), Del Rosario (4th)
        // 1. Alvarez, Ana (5 payments)
        Map<String, Object> rowAlvarez = result.data.get(0);
        assertEquals("Alvarez, Ana", rowAlvarez.get("Student Name"));
        assertEquals(8020, rowAlvarez.get("Receipt #"));
        assertEquals(150.0, (Double) rowAlvarez.get("1st Payment"), 0.001);
        assertEquals(8021, rowAlvarez.get("2nd Receipt #"));
        assertEquals(150.0, (Double) rowAlvarez.get("2nd Payment"), 0.001);
        assertEquals(8022, rowAlvarez.get("3rd Receipt #"));
        assertEquals(150.0, (Double) rowAlvarez.get("3rd Payment"), 0.001);
        assertEquals(8023, rowAlvarez.get("4th Receipt #"));
        assertEquals(100.0, (Double) rowAlvarez.get("4th Payment"), 0.001);
        assertEquals(8024, rowAlvarez.get("5th Receipt #"));
        assertEquals(50.0, (Double) rowAlvarez.get("5th Payment"), 0.001);
        assertEquals(600.0, (Double) rowAlvarez.get("Total Paid"), 0.001);
        assertEquals("Overpaid", rowAlvarez.get("Status"));

        // 2. Bernardo, Ben (1 payment)
        Map<String, Object> rowBernardo = result.data.get(1);
        assertEquals("Bernardo, Ben", rowBernardo.get("Student Name"));
        assertEquals(8030, rowBernardo.get("Receipt #"));
        assertEquals(500.0, (Double) rowBernardo.get("1st Payment"), 0.001);
        assertNull(rowBernardo.get("2nd Receipt #"));
        assertNull(rowBernardo.get("2nd Payment"));
        assertNull(rowBernardo.get("3rd Receipt #"));
        assertNull(rowBernardo.get("3rd Payment"));
        assertNull(rowBernardo.get("4th Receipt #"));
        assertNull(rowBernardo.get("4th Payment"));
        assertNull(rowBernardo.get("5th Receipt #"));
        assertNull(rowBernardo.get("5th Payment"));
        assertEquals(500.0, (Double) rowBernardo.get("Total Paid"), 0.001);
        assertEquals("Fully Paid", rowBernardo.get("Status"));

        // 3. Castro, Carlos (3 payments)
        Map<String, Object> rowCastro = result.data.get(2);
        assertEquals("Castro, Carlos", rowCastro.get("Student Name"));
        assertEquals(8001, rowCastro.get("Receipt #"));
        assertEquals(100.0, (Double) rowCastro.get("1st Payment"), 0.001);
        assertEquals(8002, rowCastro.get("2nd Receipt #"));
        assertEquals(150.0, (Double) rowCastro.get("2nd Payment"), 0.001);
        assertEquals(8003, rowCastro.get("3rd Receipt #"));
        assertEquals(250.0, (Double) rowCastro.get("3rd Payment"), 0.001);
        assertNull(rowCastro.get("4th Receipt #"));
        assertNull(rowCastro.get("4th Payment"));
        assertNull(rowCastro.get("5th Receipt #"));
        assertNull(rowCastro.get("5th Payment"));
        assertEquals(500.0, (Double) rowCastro.get("Total Paid"), 0.001);
        assertEquals("Fully Paid", rowCastro.get("Status"));

        // 4. Del Rosario, Dave (4 payments)
        Map<String, Object> rowDelRosario = result.data.get(3);
        assertEquals("Del Rosario, Dave", rowDelRosario.get("Student Name"));
        assertEquals(8011, rowDelRosario.get("Receipt #"));
        assertEquals(100.0, (Double) rowDelRosario.get("1st Payment"), 0.001);
        assertEquals(8012, rowDelRosario.get("2nd Receipt #"));
        assertEquals(100.0, (Double) rowDelRosario.get("2nd Payment"), 0.001);
        assertEquals(8013, rowDelRosario.get("3rd Receipt #"));
        assertEquals(100.0, (Double) rowDelRosario.get("3rd Payment"), 0.001);
        assertEquals(8014, rowDelRosario.get("4th Receipt #"));
        assertEquals(100.0, (Double) rowDelRosario.get("4th Payment"), 0.001);
        assertNull(rowDelRosario.get("5th Receipt #"));
        assertNull(rowDelRosario.get("5th Payment"));
        assertEquals(400.0, (Double) rowDelRosario.get("Total Paid"), 0.001);
        assertEquals("Partially Paid", rowDelRosario.get("Status"));
    }

    @Test
    void testOrdinalSuffix() {
        assertEquals("st", ReportsPanel.getOrdinalSuffix(1));
        assertEquals("nd", ReportsPanel.getOrdinalSuffix(2));
        assertEquals("rd", ReportsPanel.getOrdinalSuffix(3));
        assertEquals("th", ReportsPanel.getOrdinalSuffix(4));
        assertEquals("th", ReportsPanel.getOrdinalSuffix(5));
        assertEquals("th", ReportsPanel.getOrdinalSuffix(10));
        assertEquals("th", ReportsPanel.getOrdinalSuffix(11));
        assertEquals("th", ReportsPanel.getOrdinalSuffix(12));
        assertEquals("th", ReportsPanel.getOrdinalSuffix(13));
        assertEquals("th", ReportsPanel.getOrdinalSuffix(14));
        assertEquals("st", ReportsPanel.getOrdinalSuffix(21));
        assertEquals("nd", ReportsPanel.getOrdinalSuffix(22));
        assertEquals("rd", ReportsPanel.getOrdinalSuffix(23));
        assertEquals("th", ReportsPanel.getOrdinalSuffix(24));
        assertEquals("th", ReportsPanel.getOrdinalSuffix(111));
        assertEquals("th", ReportsPanel.getOrdinalSuffix(112));
        assertEquals("th", ReportsPanel.getOrdinalSuffix(113));
        assertEquals("st", ReportsPanel.getOrdinalSuffix(121));
    }
}
