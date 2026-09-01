package com.payment;

import com.payment.database.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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
        ChargeAcademicTerm receiptTerm = concreteReceiptTerm(db.getCurrentAcademicTerm());
        return generatePreview(
            List.of(filePath),
            remittanceDate,
            importedBy,
            db.getCurrentAcademicYear(),
            receiptTerm
        );
    }

    /**
     * Generate one combined preview for several spreadsheets. All files are
     * committed as one transaction and share the same receipt issuance period.
     */
    public ImportPreviewResult generatePreview(List<String> filePaths,
                                               LocalDate remittanceDate,
                                               String importedBy,
                                               String receiptAcademicYear,
                                               ChargeAcademicTerm receiptTerm) throws IOException {
        if (filePaths == null || filePaths.isEmpty()) {
            throw new IllegalArgumentException("Select at least one spreadsheet");
        }
        List<ImportFileSelection> selections = filePaths.stream()
            .map(path -> new ImportFileSelection(path, receiptAcademicYear, receiptTerm))
            .toList();
        return generateBatchPreview(selections, remittanceDate, importedBy);
    }

    /**
     * Generate one combined preview where each spreadsheet can carry its own
     * receipt issuance period. This supports historical batches containing
     * reused receipt numbers from different semesters.
     */
    public ImportPreviewResult generateBatchPreview(List<ImportFileSelection> selections,
                                                    LocalDate remittanceDate,
                                                    String importedBy) throws IOException {
        if (selections == null || selections.isEmpty()) {
            throw new IllegalArgumentException("Select at least one spreadsheet");
        }

        for (ImportFileSelection selection : selections) {
            if (selection == null || selection.filePath() == null || selection.filePath().isBlank()) {
                throw new IllegalArgumentException("Every spreadsheet must have a valid file path");
            }
            if (!selection.getReceiptKey(1).hasDefinedScope()) {
                throw new IllegalArgumentException("Receipt academic year and semester are required for " +
                    new File(selection.filePath()).getName());
            }
        }

        List<ImportPreviewItem> previewItems = new ArrayList<>();
        List<ImportBatchFile> batchFiles = new ArrayList<>();

        ChargeAcademicTerm activeTerm = db.getCurrentAcademicTerm();
        boolean autoAssign = db.isAutoAssignCurrentTerm();

        for (ImportFileSelection selection : selections) {
            String filePath = selection.filePath();
            ReceiptKey fileScope = selection.getReceiptKey(1);
            File sourceFile = new File(filePath);
            ImportBatchFile batchFile = new ImportBatchFile(sourceFile.getName(), "UNAVAILABLE");
            batchFile.setReceiptAcademicYear(fileScope.academicYear());
            batchFile.setReceiptTerm(fileScope.term());
            batchFiles.add(batchFile);
            try {
                batchFile.setFileHash(sha256(sourceFile));

                List<Student> parsedStudents = ExcelImporter.importFromExcel(filePath, remittanceDate);
                for (Student parsedStudent : parsedStudents) {
                    for (Payment payment : parsedStudent.getPayments()) {
                        int sourceRow = payment.getImportSourceRow() != null
                            ? payment.getImportSourceRow() : 0;
                        ImportPreviewItem item = new ImportPreviewItem(
                            sourceRow,
                            payment.getReceiptNumber(),
                            parsedStudent.getName(),
                            payment.getProgram(),
                            payment.getIntelFee(),
                            payment.getTshirtSizing(),
                            payment.getPenalties(),
                            payment.getCitNight(),
                            payment.getReceivedBy(),
                            payment.getRemarks(),
                            payment.getRemittanceDate()
                        );
                        item.setSourceFileName(sourceFile.getName());
                        item.setReceiptAcademicYear(fileScope.academicYear());
                        item.setReceiptTerm(fileScope.term());

                        if (autoAssign && (payment.getChargeAcademicTerm() == null
                                || payment.getChargeAcademicTerm() == ChargeAcademicTerm.UNASSIGNED)) {
                            item.setChargeAcademicTerm(activeTerm != null
                                ? activeTerm : ChargeAcademicTerm.FIRST_SEM);
                        } else {
                            item.setChargeAcademicTerm(payment.getChargeAcademicTerm());
                        }
                        previewItems.add(item);
                    }
                }
            } catch (Exception e) {
                ImportPreviewItem errorItem = new ImportPreviewItem();
                errorItem.setSourceFileName(sourceFile.getName());
                errorItem.setReceiptAcademicYear(fileScope.academicYear());
                errorItem.setReceiptTerm(fileScope.term());
                errorItem.setStatus(ImportPreviewItem.STATUS_ERROR);
                errorItem.setErrorMessage("Could not read spreadsheet: " + e.getMessage());
                previewItems.add(errorItem);
            }
        }

        validateAndMatch(previewItems, autoAssign);

        String displayName = batchFiles.size() == 1
            ? batchFiles.get(0).getFileName()
            : batchFiles.size() + " spreadsheets";
        ImportBatch batch = new ImportBatch(
            displayName,
            remittanceDate,
            importedBy
        );
        batch.setBatchCode(generateBatchCode());
        batch.setFileCount(batchFiles.size());
        Set<ReceiptKey> receiptPeriods = selections.stream()
            .map(selection -> selection.getReceiptKey(1))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (receiptPeriods.size() == 1) {
            ReceiptKey batchScope = receiptPeriods.iterator().next();
            batch.setReceiptAcademicYear(batchScope.academicYear());
            batch.setReceiptTerm(batchScope.term());
        } else {
            batch.setReceiptAcademicYear(null);
            batch.setReceiptTerm(ChargeAcademicTerm.UNASSIGNED);
        }
        batch.setImportedAt(LocalDateTime.now());
        batch.setTotalRows(previewItems.size());
        batch.setNewRecords((int) previewItems.stream().filter(ImportPreviewItem::isNew).count());
        batch.setDuplicateRecords((int) previewItems.stream().filter(ImportPreviewItem::isDuplicate).count());
        batch.setConflictRecords((int) previewItems.stream().filter(ImportPreviewItem::isConflict).count());
        batch.setErrorRecords((int) previewItems.stream().filter(ImportPreviewItem::isError).count());
        batch.setStatus(ImportBatch.STATUS_PENDING);

        updateFileSummaries(previewItems, batchFiles);
        return new ImportPreviewResult(previewItems, batch, batchFiles);
    }

    private static ChargeAcademicTerm concreteReceiptTerm(ChargeAcademicTerm term) {
        return ReceiptKey.isConcreteTerm(term) ? term : ChargeAcademicTerm.FIRST_SEM;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Validate and match each preview item against existing database.
     */
    private void validateAndMatch(List<ImportPreviewItem> items, boolean autoAssign) {
        List<ImportPreviewItem> validItems = new ArrayList<>();
        for (ImportPreviewItem item : items) {
            if (item.isError()) continue;

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

            if (!item.getReceiptKey().hasDefinedScope()) {
                item.setStatus(ImportPreviewItem.STATUS_ERROR);
                item.setErrorMessage("Receipt academic year and semester are required");
                continue;
            }

            validItems.add(item);
        }

        if (validItems.isEmpty()) return;

        ImportLookupSnapshot snapshot;
        try {
            snapshot = loadLookupSnapshot(validItems, autoAssign);
        } catch (Exception e) {
            for (ImportPreviewItem item : validItems) {
                item.setStatus(ImportPreviewItem.STATUS_ERROR);
                item.setErrorMessage("Database error: " + e.getMessage());
            }
            return;
        }

        Map<String, String> proposedCodes = new HashMap<>();
        Map<ReceiptKey, ImportPreviewItem> firstItemsByReceipt = new LinkedHashMap<>();
        int nextStudentSequence = snapshot.maxStudentSequence();

        for (ImportPreviewItem item : validItems) {
            Payment existing = snapshot.paymentsByReceipt().get(item.getReceiptKey());

            if (existing != null) {
                if (isExactDuplicate(item, existing, snapshot.autoAssign(), snapshot.academicYear())) {
                    item.setStatus(ImportPreviewItem.STATUS_DUPLICATE);
                } else {
                    item.setStatus(ImportPreviewItem.STATUS_CONFLICT);
                    item.setConflictingPayment(existing);
                }

                item.setMatchedStudentCode(existing.getStudentId());
                Student matchedStudent = snapshot.studentsByCode().get(existing.getStudentId());
                item.setMatchedStudentName(matchedStudent != null ? matchedStudent.getName() : existing.getName());
                continue;
            }

            ImportPreviewItem firstItem = firstItemsByReceipt.get(item.getReceiptKey());
            if (firstItem != null) {
                Payment firstPayment = paymentFromItem(firstItem);
                Payment currentPayment = paymentFromItem(item);
                if (snapshot.autoAssign()) {
                    firstPayment.setAcademicYear(snapshot.academicYear());
                    currentPayment.setAcademicYear(snapshot.academicYear());
                }
                if (currentPayment.isExactDuplicateOf(firstPayment)) {
                    item.setStatus(ImportPreviewItem.STATUS_DUPLICATE);
                } else {
                    item.setStatus(ImportPreviewItem.STATUS_CONFLICT);
                    item.setConflictingPayment(firstPayment);
                }
                item.setMatchedStudentCode(firstItem.getMatchedStudentCode());
                item.setMatchedStudentName(firstItem.getMatchedStudentName());
                continue;
            }

            String normalizedName = NameNormalizer.normalize(item.getStudentName());
            List<Student> matches = snapshot.studentsByNormalizedName()
                .getOrDefault(normalizedName, List.of());

            if (matches.isEmpty()) {
                item.setStatus(ImportPreviewItem.STATUS_NEW);
                String proposedCode = proposedCodes.get(normalizedName);
                if (proposedCode == null) {
                    proposedCode = StudentCodeGenerator.generate(++nextStudentSequence);
                    proposedCodes.put(normalizedName, proposedCode);
                }
                item.setProposedStudentCode(proposedCode);
            } else if (matches.size() == 1) {
                Student matched = matches.get(0);
                item.setStatus(ImportPreviewItem.STATUS_NEW);
                item.setMatchedStudentCode(matched.getStudentCode());
                item.setMatchedStudentName(matched.getName());
            } else {
                item.setStatus(ImportPreviewItem.STATUS_AMBIGUOUS);
                item.setAmbiguousMatches(matches);
            }
            firstItemsByReceipt.put(item.getReceiptKey(), item);
        }
    }

    private ImportLookupSnapshot loadLookupSnapshot(List<ImportPreviewItem> items, boolean autoAssign) throws Exception {
        Set<String> normalizedNames = new LinkedHashSet<>();

        for (ImportPreviewItem item : items) {
            normalizedNames.add(NameNormalizer.normalize(item.getStudentName()));
        }

        Map<ReceiptKey, List<ImportPreviewItem>> itemsByPeriod = items.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                item -> new ReceiptKey(1, item.getReceiptAcademicYear(), item.getReceiptTerm()),
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));

        Map<ReceiptKey, Payment> paymentsByReceipt = new LinkedHashMap<>();
        for (Map.Entry<ReceiptKey, List<ImportPreviewItem>> periodEntry : itemsByPeriod.entrySet()) {
            ReceiptKey period = periodEntry.getKey();
            Set<Integer> receiptNumbers = periodEntry.getValue().stream()
                .map(ImportPreviewItem::getReceiptNumber)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            for (Payment payment : db.findPaymentsByReceiptNumbers(
                    receiptNumbers, period.academicYear(), period.term()).values()) {
                paymentsByReceipt.put(payment.getReceiptKey(), payment);
            }
        }
        List<Student> studentsByName = db.findStudentSummariesByNormalizedNames(normalizedNames);

        Map<String, List<Student>> studentsByNormalizedName = new HashMap<>();
        Map<String, Student> studentsByCode = new HashMap<>();
        for (Student student : studentsByName) {
            studentsByNormalizedName
                .computeIfAbsent(student.getNormalizedName(), ignored -> new ArrayList<>())
                .add(student);
            studentsByCode.put(student.getStudentCode(), student);
        }

        Set<String> matchedStudentCodes = paymentsByReceipt.values().stream()
            .map(Payment::getStudentId)
            .filter(Objects::nonNull)
            .filter(code -> !studentsByCode.containsKey(code))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        for (Student student : db.findStudentSummariesByCodes(matchedStudentCodes)) {
            studentsByCode.put(student.getStudentCode(), student);
        }

        String academicYear = autoAssign ? db.getCurrentAcademicYear() : null;
        return new ImportLookupSnapshot(
            paymentsByReceipt,
            studentsByNormalizedName,
            studentsByCode,
            db.getMaxStudentSequence(),
            autoAssign,
            academicYear
        );
    }

    private record ImportLookupSnapshot(
        Map<ReceiptKey, Payment> paymentsByReceipt,
        Map<String, List<Student>> studentsByNormalizedName,
        Map<String, Student> studentsByCode,
        int maxStudentSequence,
        boolean autoAssign,
        String academicYear
    ) {}

    /**
     * Check if preview item is an exact duplicate of existing payment.
     */
    private boolean isExactDuplicate(ImportPreviewItem item, Payment existing,
                                     boolean autoAssign, String academicYear) {
        Payment itemPayment = paymentFromItem(item);
        if (existing.getAcademicYear() != null && autoAssign) {
            itemPayment.setAcademicYear(academicYear);
        }

        return itemPayment.isExactDuplicateOf(existing);
    }

    private Payment paymentFromItem(ImportPreviewItem item) {
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
        payment.setRemittanceDate(item.getRemittanceDate());
        payment.setChargeAcademicTerm(item.getChargeAcademicTerm());
        payment.setReceiptAcademicYear(item.getReceiptAcademicYear());
        payment.setReceiptTerm(item.getReceiptTerm());
        return payment;
    }

    private void updateFileSummaries(List<ImportPreviewItem> items, List<ImportBatchFile> files) {
        for (ImportBatchFile file : files) {
            List<ImportPreviewItem> fileItems = items.stream()
                .filter(item -> Objects.equals(file.getFileName(), item.getSourceFileName()))
                .filter(item -> Objects.equals(file.getReceiptAcademicYear(), item.getReceiptAcademicYear()))
                .filter(item -> file.getReceiptTerm() == item.getReceiptTerm())
                .toList();
            file.setTotalRows(fileItems.size());
            file.setNewRecords((int) fileItems.stream().filter(ImportPreviewItem::isNew).count());
            file.setDuplicateRecords((int) fileItems.stream().filter(ImportPreviewItem::isDuplicate).count());
            file.setConflictRecords((int) fileItems.stream().filter(ImportPreviewItem::isConflict).count());
            file.setErrorRecords((int) fileItems.stream().filter(ImportPreviewItem::isError).count());
        }
    }

    /**
     * Generate a batch code like IMP-2026-0001
     */
    private String generateBatchCode() {
        try {
            int year = LocalDateTime.now().getYear();
            int maxNum = db.getMaxImportBatchSequence(year);
            return String.format("IMP-%d-%04d", year, maxNum + 1);
        } catch (Exception e) {
            // Fallback
            return "IMP-" + LocalDateTime.now().getYear() + "-" + System.currentTimeMillis() % 10000;
        }
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
            batch.setFileCount(previewResult.getFiles().size());
            batch.setStatus(ImportBatch.STATUS_PROCESSING);
            db.insertImportBatch(batch);

            int newRecords = 0;
            int duplicateRecords = 0;
            int conflictRecords = 0;
            int errorRecords = 0;

            // Track student codes assigned in this import to avoid duplicates
            Map<String, String> newStudentCodes = new HashMap<>(); // normalizedName -> studentCode

            // Read shared values once instead of querying for every imported row.
            int nextStudentSeq = db.getMaxStudentSequence();
            boolean autoAssign = db.isAutoAssignCurrentTerm();
            String currentAcademicYear = autoAssign ? db.getCurrentAcademicYear() : null;

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
                    // Set charge academic term from preview item
                    payment.setChargeAcademicTerm(item.getChargeAcademicTerm());
                    payment.setReceiptAcademicYear(item.getReceiptAcademicYear());
                    payment.setReceiptTerm(item.getReceiptTerm());
                    payment.setImportBatchCode(batch.getBatchCode());
                    payment.setImportSourceFile(item.getSourceFileName());
                    payment.setImportSourceRow(item.getRowNumber());
                    if (autoAssign) {
                        payment.setAcademicYear(currentAcademicYear);
                    }
                    db.insertPayment(payment);

                    // Log payment creation
                    auditService.logPaymentCreated(payment, batch.getImportedBy());
                }
            }

            // Update batch with final counts
            batch.setNewRecords(newRecords);
            batch.setDuplicateRecords(duplicateRecords);
            batch.setConflictRecords(conflictRecords);
            batch.setErrorRecords(errorRecords);
            batch.setStatus(ImportBatch.STATUS_COMPLETED);
            batch.setUpdatedAt(LocalDateTime.now());
            db.updateImportBatch(batch);

            updateFileSummaries(items, previewResult.getFiles());
            for (ImportBatchFile file : previewResult.getFiles()) {
                file.setBatchCode(batch.getBatchCode());
                file.setStatus(ImportBatchFile.STATUS_COMPLETED);
                db.insertImportBatchFile(file);
            }

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
