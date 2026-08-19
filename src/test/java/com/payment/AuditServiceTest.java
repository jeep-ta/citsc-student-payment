package com.payment;

import com.payment.database.DatabaseManager;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuditServiceTest {

    private static DatabaseManager db;
    private static AuditService auditService;
    private static ImportService importService;
    private static String testFile;

    @BeforeAll
    static void setup() throws Exception {
        db = DatabaseManager.getInstance();
        auditService = new AuditService();
        importService = new ImportService();

        // Create test Excel file
        testFile = createTestExcelFile();
    }

    @AfterAll
    static void cleanup() {
        new java.io.File(testFile).delete();
        db.close();
    }

    @BeforeEach
    void clearDatabase() throws Exception {
        try (var conn = db.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM payments");
            stmt.execute("DELETE FROM students");
            stmt.execute("DELETE FROM import_batches");
            stmt.execute("DELETE FROM audit_logs");
        }
    }

    @Test
    @Order(1)
    void testLogStudentCreated() {
        Student student = new Student("Test, Student One");
        student.setStudentCode("STU-001001");
        student.setProgram("CS-1");
        student.setCreatedAt(LocalDateTime.now());
        student.setUpdatedAt(LocalDateTime.now());

        auditService.logStudentCreated(student, "admin");

        List<Map<String, Object>> logs = auditService.getRecentAuditLogs(10);
        assertFalse(logs.isEmpty());

        Map<String, Object> log = logs.get(0);
        assertEquals("CREATE", log.get("action"));
        assertEquals("STUDENT", log.get("entity_type"));
        assertEquals("STU-001001", log.get("entity_id"));
        assertEquals("admin", log.get("user"));
    }

    @Test
    @Order(2)
    void testLogPaymentCreated() {
        Payment payment = new Payment(10001, "Test, Student", "CS-1", 150.0, 200.0, 0.0, 0.0, "Admin", "Test");
        payment.setStudentId("STU-001001");
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        payment.setStatus(Payment.STATUS_ACTIVE);

        auditService.logPaymentCreated(payment, "admin");

        List<Map<String, Object>> logs = auditService.getRecentAuditLogs(10);
        assertFalse(logs.isEmpty());

        Map<String, Object> log = logs.get(0);
        assertEquals("CREATE", log.get("action"));
        assertEquals("PAYMENT", log.get("entity_type"));
        assertEquals("10001", log.get("entity_id"));
    }

    @Test
    @Order(3)
    void testLogPaymentVoided() {
        Payment payment = new Payment(10001, "Test, Student", "CS-1", 150.0, 200.0, 0.0, 0.0, "Admin", "Test");
        payment.setStudentId("STU-001001");
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        payment.setStatus(Payment.STATUS_VOID);

        auditService.logPaymentVoided(payment, "admin", "Duplicate payment");

        List<Map<String, Object>> logs = auditService.getRecentAuditLogs(10);
        Map<String, Object> log = logs.get(0);
        assertEquals("VOID", log.get("action"));
        assertEquals("Payment voided: Duplicate payment", log.get("reason"));
    }

    @Test
    @Order(4)
    void testLogImportBatch() {
        ImportBatch batch = new ImportBatch("test.xlsx", LocalDate.now(), "admin");
        batch.setBatchCode("IMP-2026-0001");
        batch.setImportedAt(LocalDateTime.now());
        batch.setNewRecords(5);
        batch.setDuplicateRecords(2);
        batch.setConflictRecords(1);
        batch.setErrorRecords(0);
        batch.setStatus(ImportBatch.STATUS_COMPLETED);
        batch.setCreatedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());

        auditService.logImportBatch(batch, "admin");

        List<Map<String, Object>> logs = auditService.getRecentAuditLogs(10);
        Map<String, Object> log = logs.get(0);
        assertEquals("IMPORT", log.get("action"));
        assertEquals("IMPORT_BATCH", log.get("entity_type"));
        assertEquals("IMP-2026-0001", log.get("entity_id"));
    }

    @Test
    @Order(5)
    void testGetAuditLogsForEntity() {
        Student student = new Student("Test, Student One");
        student.setStudentCode("STU-001001");
        student.setProgram("CS-1");
        student.setCreatedAt(LocalDateTime.now());
        student.setUpdatedAt(LocalDateTime.now());

        auditService.logStudentCreated(student, "admin");

        // Add another unrelated log
        Payment payment = new Payment(10001, "Test, Student", "CS-1", 150.0, 200.0, 0.0, 0.0, "Admin", "Test");
        payment.setStudentId("STU-001001");
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        payment.setStatus(Payment.STATUS_ACTIVE);
        auditService.logPaymentCreated(payment, "admin");

        // Query by entity
        List<Map<String, Object>> studentLogs = auditService.getAuditLogsForEntity("STUDENT", "STU-001001");
        assertEquals(1, studentLogs.size());
        assertEquals("STUDENT", studentLogs.get(0).get("entity_type"));
        assertEquals("STU-001001", studentLogs.get(0).get("entity_id"));

        // Query by payment entity
        List<Map<String, Object>> paymentLogs = auditService.getAuditLogsForEntity("PAYMENT", "10001");
        assertEquals(1, paymentLogs.size());
        assertEquals("PAYMENT", paymentLogs.get(0).get("entity_type"));
    }

    @Test
    @Order(6)
    void testImportServiceLogsAudit() throws Exception {
        // Import test file
        ImportPreviewResult preview = importService.generatePreview(testFile, LocalDate.now(), "testuser");
        ImportResult result = importService.commitImport(preview);

        // Verify audit logs were created
        List<Map<String, Object>> logs = auditService.getRecentAuditLogs(20);
        assertFalse(logs.isEmpty());

        // Should have: 2 student creates + 3 payment creates + 1 import batch = 6 logs
        assertTrue(logs.size() >= 6);

        // Check import batch log exists
        boolean hasImportLog = logs.stream()
            .anyMatch(l -> "IMPORT".equals(l.get("action")) && "IMPORT_BATCH".equals(l.get("entity_type")));
        assertTrue(hasImportLog);
    }

    private static String createTestExcelFile() throws Exception {
        String fileName = "test_audit.xlsx";
        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.FileOutputStream fos = new java.io.FileOutputStream(fileName)) {

            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Payments");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            String[] headers = {"#", "Receipt #", "Name", "Program", "Intel Fee", "Tshirt Sizing", "Penalties", "CIT Night", "Received by", "Remarks"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Row 1
            org.apache.poi.ss.usermodel.Row r1 = sheet.createRow(1);
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

            // Row 2
            org.apache.poi.ss.usermodel.Row r2 = sheet.createRow(2);
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

            // Row 3
            org.apache.poi.ss.usermodel.Row r3 = sheet.createRow(3);
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
}