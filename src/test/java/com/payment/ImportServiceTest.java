package com.payment;

import com.payment.database.DatabaseManager;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ImportServiceTest {

    private static DatabaseManager db;
    private static ImportService importService;
    private static String testFile;

    private static final String TEST_DB_FILE = "test_import_payment.db";

    @BeforeAll
    static void setup() throws IOException {
        DatabaseManager.setCustomDatabaseUrl("jdbc:sqlite:" + TEST_DB_FILE);
        DatabaseManager.resetInstance();
        db = DatabaseManager.getInstance();
        importService = new ImportService();

        // Create a test Excel file
        testFile = createTestExcelFile();
    }

    @AfterAll
    static void cleanup() {
        // Clean up test file and database
        new File(testFile).delete();
        DatabaseManager.resetInstance();
        new File(TEST_DB_FILE).delete();
        new File(TEST_DB_FILE + "-wal").delete();
        new File(TEST_DB_FILE + "-shm").delete();
        DatabaseManager.setCustomDatabaseUrl(null);
    }

    @BeforeEach
    void clearDatabase() throws Exception {
        // Clear all tables for clean test
        try (var conn = db.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM payments");
            stmt.execute("DELETE FROM students");
            stmt.execute("DELETE FROM import_batch_files");
            stmt.execute("DELETE FROM import_batches");
            stmt.execute("DELETE FROM audit_logs");
        }
    }

    private static String createTestExcelFile() throws IOException {
        String fileName = "test_import.xlsx";
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(fileName)) {

            Sheet sheet = wb.createSheet("Payments");
            Row header = sheet.createRow(0);
            String[] headers = {"#", "Receipt #", "Name", "Program", "Intel Fee", "Tshirt Sizing", "Penalties", "CIT Night", "Received by", "Remarks"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Row 1: New student, new payment
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(1);
            r1.createCell(1).setCellValue(10001);
            r1.createCell(2).setCellValue("Test, Student One");
            r1.createCell(3).setCellValue("CS-1");
            r1.createCell(4).setCellValue(150.0);
            r1.createCell(5).setCellValue(200.0);
            r1.createCell(6).setCellValue(0);
            r1.createCell(7).setCellValue(0);
            r1.createCell(8).setCellValue("Admin");
            r1.createCell(9).setCellValue("Test payment 1");

            // Row 2: Same student, different payment
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(2);
            r2.createCell(1).setCellValue(10002);
            r2.createCell(2).setCellValue("Test, Student One");
            r2.createCell(3).setCellValue("CS-2");
            r2.createCell(4).setCellValue(100.0);
            r2.createCell(5).setCellValue(0);
            r2.createCell(6).setCellValue(50.0);
            r2.createCell(7).setCellValue(0);
            r2.createCell(8).setCellValue("Admin");
            r2.createCell(9).setCellValue("Test payment 2");

            // Row 3: New student
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue(3);
            r3.createCell(1).setCellValue(10003);
            r3.createCell(2).setCellValue("Another, Student");
            r3.createCell(3).setCellValue("IT-1");
            r3.createCell(4).setCellValue(0);
            r3.createCell(5).setCellValue(0);
            r3.createCell(6).setCellValue(0);
            r3.createCell(7).setCellValue(500.0);
            r3.createCell(8).setCellValue("Admin");
            r3.createCell(9).setCellValue("Test payment 3");

            wb.write(fos);
        }
        return fileName;
    }

    @Test
    @Order(1)
    void testGeneratePreview_NewStudents() throws Exception {
        ImportPreviewResult result = importService.generatePreview(testFile, LocalDate.now(), "testuser");

        assertNotNull(result);
        assertEquals(3, result.getTotalItems());

        // All should be NEW (no existing data in DB)
        assertEquals(3, result.getNewCount());
        assertEquals(0, result.getDuplicateCount());
        assertEquals(0, result.getConflictCount());
        assertEquals(0, result.getAmbiguousCount());
        assertEquals(0, result.getErrorCount());

        // Check batch
        assertNotNull(result.getBatch());
        assertEquals("test_import.xlsx", result.getBatch().getFileName());
        assertEquals(3, result.getBatch().getTotalRows());
        assertEquals(3, result.getBatch().getNewRecords());
    }

    @Test
    @Order(2)
    void testPaymentImportFilenameSuppliesRemittanceDate() {
        assertEquals(LocalDate.of(2025, 8, 31),
            ImportService.parseRemittanceDateFromFilename(
                "Payment Import August 31, 2025.xlsx").orElseThrow());
        assertTrue(ImportService.parseRemittanceDateFromFilename("other_file.xlsx").isEmpty());
    }

    @Test
    @Order(3)
    void testCommitImport_NewStudents() throws Exception {
        ImportPreviewResult preview = importService.generatePreview(testFile, LocalDate.now(), "testuser");
        ImportResult result = importService.commitImport(preview);

        assertNotNull(result);
        assertEquals(2, result.getNewRecords()); // 2 new students (not 3 payments)
        assertEquals(0, result.getDuplicateRecords());
        assertEquals(0, result.getConflictRecords());
        assertEquals(0, result.getErrorRecords());

        // Verify data in database
        List<Student> students = db.getAllStudents();
        assertEquals(2, students.size()); // 2 unique students

        List<Payment> payments = db.getAllPayments();
        assertEquals(3, payments.size()); // 3 payments

        // Check batch created
        List<ImportBatch> batches = db.getAllImportBatches();
        assertEquals(1, batches.size());
        assertEquals(ImportBatch.STATUS_COMPLETED, batches.get(0).getStatus());

        // Check audit log
        List<java.util.Map<String, Object>> auditLogs = db.getAuditLogs(10);
        assertTrue(auditLogs.size() >= 1);
    }

    @Test
    @Order(4)
    void testImportDuplicateReceipt() throws Exception {
        // First import
        ImportPreviewResult preview1 = importService.generatePreview(testFile, LocalDate.now(), "testuser");
        importService.commitImport(preview1);

        // Second import of same file
        ImportPreviewResult preview2 = importService.generatePreview(testFile, LocalDate.now(), "testuser");

        // Should detect all as duplicates
        assertEquals(3, preview2.getDuplicateCount());
        assertEquals(0, preview2.getNewCount());

        // Commit second import
        ImportResult result2 = importService.commitImport(preview2);
        assertEquals(0, result2.getNewRecords());
        assertEquals(3, result2.getDuplicateRecords());

        // Database should still have only 3 payments
        List<Payment> payments = db.getAllPayments();
        assertEquals(3, payments.size());
    }

    @Test
    @Order(5)
    void testImportConflictReceipt() throws Exception {
        // First import
        ImportPreviewResult preview1 = importService.generatePreview(testFile, LocalDate.now(), "testuser");
        importService.commitImport(preview1);

        // Create modified file with same receipt but different amount
        String conflictFile = createConflictExcelFile();

        // Import conflict file
        ImportPreviewResult preview2 = importService.generatePreview(conflictFile, LocalDate.now(), "testuser");

        // Should detect conflicts
        assertEquals(1, preview2.getConflictCount()); // First row has same receipt 10001 but different amount
        assertEquals(2, preview2.getDuplicateCount()); // Other two are exact duplicates

        // Cleanup
        new File(conflictFile).delete();
    }

    private String createConflictExcelFile() throws IOException {
        String fileName = "test_import_conflict.xlsx";
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(fileName)) {

            Sheet sheet = wb.createSheet("Payments");
            Row header = sheet.createRow(0);
            String[] headers = {"#", "Receipt #", "Name", "Program", "Intel Fee", "Tshirt Sizing", "Penalties", "CIT Night", "Received by", "Remarks"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Row 1: Same receipt 10001 but different amount (CONFLICT)
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(1);
            r1.createCell(1).setCellValue(10001); // Same receipt!
            r1.createCell(2).setCellValue("Test, Student One");
            r1.createCell(3).setCellValue("CS-1");
            r1.createCell(4).setCellValue(999.0); // Different amount!
            r1.createCell(5).setCellValue(200.0);
            r1.createCell(6).setCellValue(0);
            r1.createCell(7).setCellValue(0);
            r1.createCell(8).setCellValue("Admin");
            r1.createCell(9).setCellValue("Conflict test");

            // Row 2: Same as before (DUPLICATE)
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(2);
            r2.createCell(1).setCellValue(10002);
            r2.createCell(2).setCellValue("Test, Student One");
            r2.createCell(3).setCellValue("CS-2");
            r2.createCell(4).setCellValue(100.0);
            r2.createCell(5).setCellValue(0);
            r2.createCell(6).setCellValue(50.0);
            r2.createCell(7).setCellValue(0);
            r2.createCell(8).setCellValue("Admin");
            r2.createCell(9).setCellValue("Test payment 2");

            // Row 3: Same as before (DUPLICATE)
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue(3);
            r3.createCell(1).setCellValue(10003);
            r3.createCell(2).setCellValue("Another, Student");
            r3.createCell(3).setCellValue("IT-1");
            r3.createCell(4).setCellValue(0);
            r3.createCell(5).setCellValue(0);
            r3.createCell(6).setCellValue(0);
            r3.createCell(7).setCellValue(500.0);
            r3.createCell(8).setCellValue("Admin");
            r3.createCell(9).setCellValue("Test payment 3");

            wb.write(fos);
        }
        return fileName;
    }

    @Test
    @Order(6)
    void testImportAmbiguousStudents() throws Exception {
        // First, add two students with same normalized name
        Student s1 = new Student("Smith, John");
        s1.setStudentCode("STU-000001");
        s1.setProgram("CS-1");
        db.insertStudent(s1);

        Student s2 = new Student("Smith, John");
        s2.setStudentCode("STU-000002");
        s2.setProgram("IT-1");
        db.insertStudent(s2);

        // Create file with ambiguous name
        String ambiguousFile = createAmbiguousExcelFile();

        // Import
        ImportPreviewResult preview = importService.generatePreview(ambiguousFile, LocalDate.now(), "testuser");

        // Should detect ambiguous
        assertEquals(1, preview.getAmbiguousCount());
        ImportPreviewItem item = preview.getItems().get(0);
        assertTrue(item.isAmbiguous());
        assertNotNull(item.getAmbiguousMatches());
        assertEquals(2, item.getAmbiguousMatches().size());

        // Cleanup
        new File(ambiguousFile).delete();
    }

    @Test
    @Order(7)
    void testPreviewAllocatesStableUniqueCodesAfterCurrentMaximum() throws Exception {
        Student existing = new Student("Existing, Student");
        existing.setStudentCode("STU-000125");
        existing.setProgram("CS-4");
        db.insertStudent(existing);

        ImportPreviewResult preview = importService.generatePreview(testFile, LocalDate.now(), "testuser");

        Set<String> proposedCodes = preview.getItems().stream()
            .map(ImportPreviewItem::getProposedStudentCode)
            .collect(Collectors.toSet());
        assertEquals(Set.of("STU-000126", "STU-000127"), proposedCodes);

        List<ImportPreviewItem> repeatedStudentRows = preview.getItems().stream()
            .filter(item -> "Test, Student One".equals(item.getStudentName()))
            .collect(Collectors.toList());
        assertEquals(2, repeatedStudentRows.size());
        assertEquals(repeatedStudentRows.get(0).getProposedStudentCode(),
            repeatedStudentRows.get(1).getProposedStudentCode());
    }

    @Test
    @Order(8)
    void testReceiptNumbersCanRepeatAcrossSemestersButNotWithinSemester() throws Exception {
        ImportPreviewResult firstSemester = importService.generatePreview(
            List.of(testFile), LocalDate.now(), "testuser", "2026-2027", ChargeAcademicTerm.FIRST_SEM);
        importService.commitImport(firstSemester);

        Payment scopedDuplicate = new Payment(
            10001, "Test, Student One", "CS-1", 150.0, 200.0, null, null, "Admin", "duplicate");
        scopedDuplicate.setStudentId("STU-000001");
        scopedDuplicate.setReceiptAcademicYear("2026-2027");
        scopedDuplicate.setReceiptTerm(ChargeAcademicTerm.FIRST_SEM);
        assertThrows(java.sql.SQLException.class, () -> db.insertPayment(scopedDuplicate));

        ImportPreviewResult secondSemester = importService.generatePreview(
            List.of(testFile), LocalDate.now(), "testuser", "2026-2027", ChargeAcademicTerm.SECOND_SEM);
        assertEquals(3, secondSemester.getNewCount());
        assertEquals(0, secondSemester.getDuplicateCount());
        assertEquals(0, secondSemester.getConflictCount());
        importService.commitImport(secondSemester);

        List<Payment> stored = db.getAllPayments();
        assertEquals(6, stored.size());
        assertEquals(2, stored.stream().filter(p -> p.getReceiptNumber() == 10001).count());
        assertEquals(Set.of(ChargeAcademicTerm.FIRST_SEM, ChargeAcademicTerm.SECOND_SEM),
            stored.stream()
                .filter(p -> p.getReceiptNumber() == 10001)
                .map(Payment::getReceiptTerm)
                .collect(Collectors.toSet()));

        ImportPreviewResult repeatedSecondSemester = importService.generatePreview(
            List.of(testFile), LocalDate.now(), "testuser", "2026-2027", ChargeAcademicTerm.SECOND_SEM);
        assertEquals(3, repeatedSecondSemester.getDuplicateCount());
    }

    @Test
    @Order(9)
    void testMultipleSpreadsheetsCommitAsOneBatchWithFileProvenance() throws Exception {
        String secondFile = createSingleRowExcelFile(
            "test_import_second.xlsx", 10001, "Batch, Student Two", 275.0);
        try {
            ImportPreviewResult preview = importService.generateBatchPreview(
                List.of(
                    new ImportFileSelection(testFile, "2026-2027", ChargeAcademicTerm.FIRST_SEM),
                    new ImportFileSelection(secondFile, "2026-2027", ChargeAcademicTerm.SECOND_SEM)
                ),
                LocalDate.now(),
                "testuser"
            );

            assertEquals(2, preview.getFileCount());
            assertEquals(4, preview.getTotalItems());
            assertEquals("Multiple periods", preview.getBatch().getReceiptPeriodDisplay());
            assertEquals(0, preview.getConflictCount());
            assertEquals(0, preview.getDuplicateCount());
            assertEquals(Set.of("test_import.xlsx", "test_import_second.xlsx"),
                preview.getItems().stream()
                    .map(ImportPreviewItem::getSourceFileName)
                    .collect(Collectors.toSet()));

            ImportResult result = importService.commitImport(preview);
            assertEquals(ImportBatch.STATUS_COMPLETED, result.getBatch().getStatus());
            assertEquals(2, result.getBatch().getFileCount());
            assertEquals(2, db.getImportBatchFiles(result.getBatch().getBatchCode()).size());

            List<Payment> stored = db.getAllPayments();
            assertEquals(4, stored.size());
            assertTrue(stored.stream().allMatch(p -> result.getBatch().getBatchCode().equals(p.getImportBatchCode())));
            assertTrue(stored.stream().allMatch(p -> p.getImportSourceFile() != null));
            assertTrue(stored.stream().allMatch(p -> p.getImportSourceRow() != null && p.getImportSourceRow() >= 2));
            assertEquals(2, stored.stream().filter(p -> p.getReceiptNumber() == 10001).count());
            assertEquals(Set.of(ChargeAcademicTerm.FIRST_SEM, ChargeAcademicTerm.SECOND_SEM),
                stored.stream().map(Payment::getReceiptTerm).collect(Collectors.toSet()));
            assertEquals(Set.of(ChargeAcademicTerm.FIRST_SEM, ChargeAcademicTerm.SECOND_SEM),
                db.getImportBatchFiles(result.getBatch().getBatchCode()).stream()
                    .map(ImportBatchFile::getReceiptTerm)
                    .collect(Collectors.toSet()));
        } finally {
            new File(secondFile).delete();
        }
    }

    @Test
    @Order(10)
    void testMultipleSpreadsheetCommitRollsBackAsOneTransaction() throws Exception {
        String secondFile = createSingleRowExcelFile(
            "test_import_atomic.xlsx", 30001, "Atomic, Student", 325.0);
        try {
            ImportPreviewResult preview = importService.generatePreview(
                List.of(testFile, secondFile), LocalDate.now(), "testuser",
                "2026-2027", ChargeAcademicTerm.FIRST_SEM);

            // Simulate another writer claiming a receipt after preview but
            // before commit. The database uniqueness rule must abort the whole batch.
            Student blocker = new Student("Concurrent, Student");
            blocker.setStudentCode("STU-000900");
            blocker.setProgram("IT-1");
            db.insertStudent(blocker);
            Payment claimedReceipt = new Payment(
                30001, blocker.getName(), blocker.getProgram(), 325.0, null, null, null,
                "Admin", "Concurrent claim");
            claimedReceipt.setStudentId(blocker.getStudentCode());
            claimedReceipt.setReceiptAcademicYear("2026-2027");
            claimedReceipt.setReceiptTerm(ChargeAcademicTerm.FIRST_SEM);
            db.insertPayment(claimedReceipt);

            assertThrows(Exception.class, () -> importService.commitImport(preview));

            assertEquals(1, db.getAllPayments().size());
            assertEquals(1, db.getAllStudents().size());
            assertTrue(db.getAllImportBatches().isEmpty());
            assertTrue(db.getImportBatchFiles(preview.getBatch().getBatchCode()).isEmpty());
            assertTrue(db.getAuditLogs(100).isEmpty());
        } finally {
            new File(secondFile).delete();
        }
    }

    @Test
    @Order(11)
    void testReceiptlessRemittancesAreImportedWithoutReceiptIntegrityChecks() throws Exception {
        String receiptlessFile = createReceiptlessExcelFile();
        try {
            ImportPreviewResult preview = importService.generateBatchPreview(
                List.of(new ImportFileSelection(receiptlessFile, null, ChargeAcademicTerm.UNASSIGNED)),
                LocalDate.now(), "testuser");
            assertEquals(2, preview.getNewCount());
            assertTrue(preview.getItems().stream().allMatch(item -> item.getReceiptNumber() == 0));

            importService.commitImport(preview);

            assertEquals(2, db.getAllPayments().size());
            assertTrue(db.getAllPayments().stream().allMatch(payment -> payment.getReceiptNumber() == 0));
        } finally {
            new File(receiptlessFile).delete();
        }
    }

    private String createSingleRowExcelFile(String fileName, int receiptNumber,
                                            String studentName, double intelFee) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(fileName)) {
            Sheet sheet = wb.createSheet("Payments");
            Row header = sheet.createRow(0);
            String[] headers = {"#", "Receipt #", "Name", "Program", "Intel Fee", "Tshirt Sizing", "Penalties", "CIT Night", "Received by", "Remarks"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue(receiptNumber);
            row.createCell(2).setCellValue(studentName);
            row.createCell(3).setCellValue("CS-1");
            row.createCell(4).setCellValue(intelFee);
            row.createCell(5).setCellValue(0);
            row.createCell(6).setCellValue(0);
            row.createCell(7).setCellValue(0);
            row.createCell(8).setCellValue("Admin");
            row.createCell(9).setCellValue("Multi-file batch test");
            wb.write(fos);
        }
        return fileName;
    }

    private String createReceiptlessExcelFile() throws IOException {
        String fileName = "receiptless_import.xlsx";
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(fileName)) {
            Sheet sheet = wb.createSheet("Payments");
            Row header = sheet.createRow(0);
            String[] headers = {"#", "Receipt #", "Name", "Program", "Intel Fee", "Tshirt Sizing", "Penalties", "CIT Night", "Received by", "Remarks"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            for (int rowIndex = 1; rowIndex <= 2; rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue(rowIndex);
                row.createCell(2).setCellValue("Accounting Recovery " + rowIndex);
                row.createCell(3).setCellValue("CS-1");
                row.createCell(4).setCellValue(100.0 * rowIndex);
                row.createCell(8).setCellValue("University Accounting");
                row.createCell(9).setCellValue("Remitted without receipt");
            }
            wb.write(fos);
        }
        return fileName;
    }

    private String createAmbiguousExcelFile() throws IOException {
        String fileName = "test_import_ambiguous.xlsx";
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(fileName)) {

            Sheet sheet = wb.createSheet("Payments");
            Row header = sheet.createRow(0);
            String[] headers = {"#", "Receipt #", "Name", "Program", "Intel Fee", "Tshirt Sizing", "Penalties", "CIT Night", "Received by", "Remarks"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Row with ambiguous name
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(1);
            r1.createCell(1).setCellValue(20001);
            r1.createCell(2).setCellValue("Smith, John"); // Same normalized name as existing
            r1.createCell(3).setCellValue("CS-2");
            r1.createCell(4).setCellValue(150.0);
            r1.createCell(5).setCellValue(200.0);
            r1.createCell(6).setCellValue(0);
            r1.createCell(7).setCellValue(0);
            r1.createCell(8).setCellValue("Admin");
            r1.createCell(9).setCellValue("Ambiguous test");

            wb.write(fos);
        }
        return fileName;
    }
}
