package com.payment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ImportBatchTest {

    @Test
    void testDefaultValues() {
        ImportBatch b = new ImportBatch("test.xlsx", LocalDate.of(2026, 8, 19), "Admin");
        assertEquals("test.xlsx", b.getFileName());
        assertEquals(LocalDate.of(2026, 8, 19), b.getRemittanceDate());
        assertEquals("Admin", b.getImportedBy());
        assertEquals(ImportBatch.STATUS_PENDING, b.getStatus());
        assertEquals(0, b.getTotalRows());
        assertEquals(0, b.getNewRecords());
        assertEquals(0, b.getDuplicateRecords());
        assertEquals(0, b.getConflictRecords());
        assertEquals(0, b.getErrorRecords());
    }

    @Test
    void testCounters() {
        ImportBatch b = new ImportBatch("test.xlsx", LocalDate.now(), "Admin");
        b.incrementTotalRows();
        b.incrementTotalRows();
        b.incrementNewRecords();
        b.incrementDuplicateRecords();
        b.incrementConflictRecords();
        b.incrementErrorRecords();

        assertEquals(2, b.getTotalRows());
        assertEquals(1, b.getNewRecords());
        assertEquals(1, b.getDuplicateRecords());
        assertEquals(1, b.getConflictRecords());
        assertEquals(1, b.getErrorRecords());
    }

    @Test
    void testStatusTransitions() {
        ImportBatch b = new ImportBatch("test.xlsx", LocalDate.now(), "Admin");
        assertEquals(ImportBatch.STATUS_PENDING, b.getStatus());

        b.setStatus(ImportBatch.STATUS_PROCESSING);
        assertEquals(ImportBatch.STATUS_PROCESSING, b.getStatus());

        b.setStatus(ImportBatch.STATUS_COMPLETED);
        assertEquals(ImportBatch.STATUS_COMPLETED, b.getStatus());
    }

    @Test
    void testBatchCodeAssignment() {
        ImportBatch b = new ImportBatch("test.xlsx", LocalDate.now(), "Admin");
        assertNull(b.getBatchCode());
        b.setBatchCode("IMP-2026-0001");
        assertEquals("IMP-2026-0001", b.getBatchCode());
    }

    @Test
    void testTimestampUpdates() {
        ImportBatch b = new ImportBatch("test.xlsx", LocalDate.now(), "Admin");
        LocalDateTime before = b.getUpdatedAt();
        b.setStatus(ImportBatch.STATUS_PROCESSING);
        assertTrue(b.getUpdatedAt().compareTo(before) >= 0 || b.getUpdatedAt().equals(before));
    }
}