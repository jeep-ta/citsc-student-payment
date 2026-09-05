package com.payment;

import com.payment.database.DatabaseManager;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeeTermRuleTest {
    private DatabaseManager db;
    private File workbook;

    @BeforeEach
    void setup() throws Exception {
        DatabaseManager.setCustomDatabaseUrl("jdbc:sqlite:test_fee_rules.db");
        DatabaseManager.resetInstance();
        db = DatabaseManager.getInstance();
        try (var stmt = db.getConnection().createStatement()) {
            stmt.execute("DELETE FROM fee_term_rules"); stmt.execute("DELETE FROM payments"); stmt.execute("DELETE FROM students");
        }
        workbook = new File("Payment Import August 31, 2025.xlsx");
        try (Workbook wb = new XSSFWorkbook(); FileOutputStream out = new FileOutputStream(workbook)) {
            Sheet sheet = wb.createSheet();
            String[] headers = {"#", "Receipt #", "Name", "Program", "Intel Fee", "Tshirt Sizing", "Penalties", "CIT Night", "Received by", "Remarks"};
            Row h = sheet.createRow(0); for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            Row r = sheet.createRow(1);
            Object[] values = {1, 77, "Doe, Jane", "CS", 100, 0, 50, 0, "Cashier", "split-term receipt"};
            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof Number n) r.createCell(i).setCellValue(n.doubleValue()); else r.createCell(i).setCellValue(values[i].toString());
            }
            wb.write(out);
        }
    }

    @AfterEach
    void cleanup() {
        if (workbook != null) workbook.delete();
        DatabaseManager.resetInstance();
        new File("test_fee_rules.db").delete(); new File("test_fee_rules.db-wal").delete(); new File("test_fee_rules.db-shm").delete();
        DatabaseManager.setCustomDatabaseUrl(null);
    }

    @Test
    void rulesAssignDifferentCategoriesOnOneReceiptAndPersist() throws Exception {
        db.insertFeeTermRule(new FeeTermRule(FeeTermRule.INTEL_FEE, LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 31), "2025-2026", ChargeAcademicTerm.FIRST_SEM));
        db.insertFeeTermRule(new FeeTermRule(FeeTermRule.PENALTIES, LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 31), "2024-2025", ChargeAcademicTerm.SECOND_SEM));

        ImportService service = new ImportService();
        ImportPreviewResult preview = service.generateBatchPreview(
            List.of(new ImportFileSelection(workbook.getAbsolutePath(), "2025-2026", ChargeAcademicTerm.FIRST_SEM)),
            LocalDate.of(2025, 1, 1), "test");
        ImportPreviewItem item = preview.getItems().get(0);
        assertEquals(ChargeAcademicTerm.FIRST_SEM, item.getIntelFeeTerm());
        assertEquals("2025-2026", item.getIntelFeeAy());
        assertEquals(ChargeAcademicTerm.SECOND_SEM, item.getPenaltiesTerm());
        assertEquals("2024-2025", item.getPenaltiesAy());
        assertEquals(LocalDate.of(2025, 8, 31), item.getRemittanceDate());

        service.commitImport(preview);
        Payment saved = db.getAllPayments().get(0);
        assertEquals(ChargeAcademicTerm.FIRST_SEM, saved.getEffectiveTermForCategory(FeeTermRule.INTEL_FEE));
        assertEquals(ChargeAcademicTerm.SECOND_SEM, saved.getEffectiveTermForCategory(FeeTermRule.PENALTIES));
        assertEquals("2024-2025", saved.getEffectiveAyForCategory(FeeTermRule.PENALTIES));

        FeeTermRule intelRule = db.getFeeTermRules().stream().filter(r -> FeeTermRule.INTEL_FEE.equals(r.getCategory())).findFirst().orElseThrow();
        intelRule.setTerm(ChargeAcademicTerm.SUMMER);
        db.updateFeeTermRule(intelRule);
        assertEquals(1, db.refreshFeeTermAssignments());
        assertEquals(ChargeAcademicTerm.SUMMER, db.getAllPayments().get(0).getEffectiveTermForCategory(FeeTermRule.INTEL_FEE));
    }

    @Test
    void invalidRulesAreRejected() {
        FeeTermRule rule = new FeeTermRule(FeeTermRule.PENALTIES, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 8, 1), "2025-2026", ChargeAcademicTerm.FIRST_SEM);
        assertThrows(IllegalArgumentException.class, () -> db.insertFeeTermRule(rule));
    }
}
