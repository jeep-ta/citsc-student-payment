package com.payment;

import com.payment.database.DatabaseManager;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Import Service handles the full import workflow:
 * Parse → Validate → Normalize → Match → Detect conflicts → Preview → Commit
 */
public class ImportService {

    private final DatabaseManager db;
    private final AuditService auditService;

    public ImportService() {
        this.db = DatabaseManager.getInstance();
        this.auditService = new AuditService();
    }

    /**
     * Process an Excel file and generate a preview of the import.
     * Does NOT commit to database - only analyzes and matches.
     *
     * @param filePath Path to Excel file
     * @param remittanceDate Remittance date for the import
     * @param importedBy User performing the import
     * @return ImportPreviewResult containing preview items and batch info
     * @throws IOException If file cannot be read
     */
    public ImportPreviewResult generatePreview(String filePath, LocalDate remittanceDate, String importedBy) throws IOException {
        // 1. Parse Excel
        List<Student> parsedStudents = ExcelImporter.importFromExcel(filePath, remittanceDate);

        // 2. Convert to flat list of preview items
        List<ImportPreviewItem> previewItems = new ArrayList<>();
        int rowNumber = 1; // Excel row (1-based, after header)

        for (Student parsedStudent : parsedStudents) {
            for (Payment payment : parsedStudent.getPayments()) {
                ImportPreviewItem item = new ImportPreviewItem(
                    rowNumber++,
                    payment.getReceiptNumber(),
                    parsedStudent.getName(),
                    parsedStudent.getProgram(),
                    payment.getIntelFee(),
                    payment.getTshirtSizing(),
                    payment.getPenalties(),
                    payment.getCitNight(),
                    payment.getReceivedBy(),
                    payment.getRemarks(),
                    payment.getRemittanceDate()
                );
                previewItems.add(item);
            }
        }

        // 3. Validate and match each item
        validateAndMatch(previewItems);

        // 4. Create batch record (not committed yet)
        ImportBatch batch = new ImportBatch(
            new java.io.File(filePath).getName(),
            remittanceDate,
            importedBy
        );
        batch.setBatchCode(generateBatchCode());
        batch.setImportedAt(LocalDateTime.now());
        batch.setTotalRows(previewItems.size());
        batch.setNewRecords((int) previewItems.stream().filter(ImportPreviewItem::isNew).count());
        batch.setDuplicateRecords((int) previewItems.stream().filter(ImportPreviewItem::isDuplicate).count());
        batch.setConflictRecords((int) previewItems.stream().filter(ImportPreviewItem::isConflict).count());
        batch.setErrorRecords((int) previewItems.stream().filter(ImportPreviewItem::isError).count());
        batch.setStatus(ImportBatch.STATUS_PENDING);

        return new ImportPreviewResult(previewItems, batch);
    }

    /**
     * Validate and match each preview item against existing database.
     */
    private void validateAndMatch(List<ImportPreviewItem> items) {
        for (ImportPreviewItem item : items) {
            // Validate receipt number
            if (item.getReceiptNumber() <= 0) {
                item.setStatus(ImportPreviewItem.STATUS_ERROR);
                item.setErrorMessage("Invalid receipt number: " + item.getReceiptNumber());
                continue;
            }

            // Validate student name
            if (item.getStudentName() == null || item.getStudentName().trim().isEmpty()) {
                item.setStatus(ImportPreviewItem.STATUS_ERROR);
                item.setErrorMessage("Empty student name");
                continue;
            }

            try {
                // Check for existing payment with same receipt number (global)
                Optional<Payment> existingPayment = db.findPaymentByReceiptNumber(item.getReceiptNumber());

                if (existingPayment.isPresent()) {
                    Payment existing = existingPayment.get();
                    // Check if exact duplicate
                    if (isExactDuplicate(item, existing)) {
                        item.setStatus(ImportPreviewItem.STATUS_DUPLICATE);
                        item.setMatchedStudentCode(existing.getStudentId());
                        // Find student name
                        db.findStudentByCode(existing.getStudentId())
                            .ifPresent(s -> item.setMatchedStudentName(s.getName()));
                    } else {
                        // Conflict - same receipt, different data
                        item.setStatus(ImportPreviewItem.STATUS_CONFLICT);
                        item.setConflictingPayment(existing);
                        item.setMatchedStudentCode(existing.getStudentId());
                        db.findStudentByCode(existing.getStudentId())
                            .ifPresent(s -> item.setMatchedStudentName(s.getName()));
                    }
                    continue;
                }

                // No existing payment with this receipt - match by student name
                String normalizedName = NameNormalizer.normalize(item.getStudentName());
                List<Student> matches = db.findAllStudentsByNormalizedName(normalizedName);

                if (matches.isEmpty()) {
                    // New student
                    item.setStatus(ImportPreviewItem.STATUS_NEW);
                    // Propose student code (will be assigned on commit)
                    item.setProposedStudentCode(generateProposedStudentCode());
                } else if (matches.size() == 1) {
                    // Exact match
                    Student matched = matches.get(0);
                    item.setStatus(ImportPreviewItem.STATUS_NEW); // New payment for existing student
                    item.setMatchedStudentCode(matched.getStudentCode());
                    item.setMatchedStudentName(matched.getName());
                } else {
                    // Ambiguous - multiple students with same normalized name
                    item.setStatus(ImportPreviewItem.STATUS_AMBIGUOUS);
                    item.setAmbiguousMatches(matches);
                }

            } catch (Exception e) {
                item.setStatus(ImportPreviewItem.STATUS_ERROR);
                item.setErrorMessage("Database error: " + e.getMessage());
            }
        }
    }

    /**
     * Check if preview item is an exact duplicate of existing payment.
     */
    private boolean isExactDuplicate(ImportPreviewItem item, Payment existing) {
        // Create temporary payment from item for comparison
        Payment itemPayment = new Payment(
            item.getReceiptNumber(),
            item.getStudentName(),
            item.getProgram(),
            item.getIntelFee(),
            item.getTshirtSizing(),
            item.getPenalties(),
            item.getCitNight(),
            item.getReceivedBy(),
            item.getRemarks()
        );
        itemPayment.setRemittanceDate(item.getRemittanceDate());

        return itemPayment.isExactDuplicateOf(existing);
    }

    /**
     * Generate a batch code like IMP-2026-0001
     */
    private String generateBatchCode() {
        try {
            List<ImportBatch> batches = db.getAllImportBatches();
            int year = LocalDateTime.now().getYear();
            int maxNum = 0;
            for (ImportBatch b : batches) {
                if (b.getBatchCode() != null && b.getBatchCode().startsWith("IMP-" + year + "-")) {
                    try {
                        int num = Integer.parseInt(b.getBatchCode().substring(("IMP-" + year + "-").length()));
                        if (num > maxNum) maxNum = num;
                    } catch (NumberFormatException ignored) {}
                }
            }
            return String.format("IMP-%d-%04d", year, maxNum + 1);
        } catch (Exception e) {
            // Fallback
            return "IMP-" + LocalDateTime.now().getYear() + "-" + System.currentTimeMillis() % 10000;
        }
    }

    /**
     * Generate a proposed student code for new students
     */
    private String generateProposedStudentCode() {
        try {
            List<Student> students = db.getAllStudents();
            int maxSeq = 0;
            for (Student s : students) {
                int seq = StudentCodeGenerator.extractSequence(s.getStudentCode());
                if (seq > maxSeq) maxSeq = seq;
            }
            return StudentCodeGenerator.generate(maxSeq + 1);
        } catch (Exception e) {
            return StudentCodeGenerator.generate(1);
        }
    }

    /**
     * Get the maximum student sequence number from database.
     * Called once at start of transaction to avoid conflicts.
     */
    private int getMaxStudentSequence() throws Exception {
        List<Student> students = db.getAllStudents();
        int maxSeq = 0;
        for (Student s : students) {
            int seq = StudentCodeGenerator.extractSequence(s.getStudentCode());
            if (seq > maxSeq) maxSeq = seq;
        }
        return maxSeq;
    }

    /**
     * Commit the import based on preview result.
     * Should only be called after user confirms the preview.
     *
     * @param previewResult The preview result (may have been modified by user)
     * @return ImportResult with counts and batch
     * @throws Exception If commit fails
     */
    public ImportResult commitImport(ImportPreviewResult previewResult) throws Exception {
        ImportBatch batch = previewResult.getBatch();
        List<ImportPreviewItem> items = previewResult.getItems();

        db.beginTransaction();
        try {
            int newRecords = 0;
            int duplicateRecords = 0;
            int conflictRecords = 0;
            int errorRecords = 0;

            // Track student codes assigned in this import to avoid duplicates
            Map<String, String> newStudentCodes = new HashMap<>(); // normalizedName -> studentCode

            // Get max existing sequence once at start of transaction
            int nextStudentSeq = getMaxStudentSequence();

            for (ImportPreviewItem item : items) {
                if (item.isError()) {
                    errorRecords++;
                    continue;
                }

                // Handle ambiguous items - user must have resolved them
                String studentCode = null;
                String studentName = item.getStudentName();

                if (item.isAmbiguous()) {
                    // User should have selected one or created new
                    // For now, skip ambiguous items (they need UI resolution)
                    errorRecords++;
                    item.setErrorMessage("Ambiguous student - requires manual resolution");
                    continue;
                } else if (item.getMatchedStudentCode() != null) {
                    // Matched existing student
                    studentCode = item.getMatchedStudentCode();
                } else if (item.isNew()) {
                    // New student - check if we already created one for this name in this import
                    String normalizedName = NameNormalizer.normalize(studentName);
                    if (newStudentCodes.containsKey(normalizedName)) {
                        studentCode = newStudentCodes.get(normalizedName);
                    } else {
                        // Create new student with next sequence
                        nextStudentSeq++;
                        studentCode = StudentCodeGenerator.generate(nextStudentSeq);
                        Student newStudent = new Student(studentName);
                        newStudent.setProgram(item.getProgram());
                        newStudent.setStudentCode(studentCode);
                        db.insertStudent(newStudent);

                        // Log student creation
                        auditService.logStudentCreated(newStudent, batch.getImportedBy());

                        newStudentCodes.put(normalizedName, studentCode);
                        newRecords++; // Count new student
                    }
                }

                // Insert payment if not duplicate
                if (item.isDuplicate()) {
                    duplicateRecords++;
                    continue;
                }

                if (item.isConflict()) {
                    // Conflicts must be resolved by user before commit
                    // For now, skip - they need UI resolution
                    conflictRecords++;
                    continue;
                }

                // Insert new payment
                if (studentCode != null) {
                    Payment payment = new Payment(
                        item.getReceiptNumber(),
                        item.getStudentName(),
                        item.getProgram(),
                        item.getIntelFee(),
                        item.getTshirtSizing(),
                        item.getPenalties(),
                        item.getCitNight(),
                        item.getReceivedBy(),
                        item.getRemarks()
                    );
                    payment.setStudentId(studentCode);
                    payment.setRemittanceDate(item.getRemittanceDate());
                    payment.setStatus(Payment.STATUS_ACTIVE);
                    db.insertPayment(payment);

                    // Log payment creation
                    auditService.logPaymentCreated(payment, batch.getImportedBy());

                    // Count as new payment (student already counted if new)
                    if (item.isNew() && newStudentCodes.get(NameNormalizer.normalize(studentName)) == studentCode) {
                        // Student was already counted above
                    } else {
                        // Payment for existing student
                        // Don't double-count
                    }
                }
            }

            // Update batch with final counts
            batch.setNewRecords(newRecords);
            batch.setDuplicateRecords(duplicateRecords);
            batch.setConflictRecords(conflictRecords);
            batch.setErrorRecords(errorRecords);
            batch.setStatus(ImportBatch.STATUS_COMPLETED);
            batch.setUpdatedAt(LocalDateTime.now());
            db.insertImportBatch(batch);

            // Log audit via AuditService
            auditService.logImportBatch(batch, batch.getImportedBy());

            db.commitTransaction();

            return new ImportResult(batch, newRecords, duplicateRecords, conflictRecords, errorRecords);

        } catch (Exception e) {
            db.rollbackTransaction();
            throw e;
        }
    }
}