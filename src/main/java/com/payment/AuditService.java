package com.payment;

import com.payment.database.DatabaseManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Audit Service for logging important financial mutations.
 * All significant changes to students, payments, and imports are logged.
 */
public class AuditService {

    private final DatabaseManager db;

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_VOID = "VOID";
    public static final String ACTION_IMPORT = "IMPORT";
    public static final String ACTION_MERGE = "MERGE";
    public static final String ACTION_DELETE = "DELETE"; // For audit trail only - never actually delete

    public static final String ENTITY_STUDENT = "STUDENT";
    public static final String ENTITY_PAYMENT = "PAYMENT";
    public static final String ENTITY_IMPORT_BATCH = "IMPORT_BATCH";
    public static final String ENTITY_DATABASE = "DATABASE";

    public AuditService() {
        this.db = DatabaseManager.getInstance();
    }

    /**
     * Log student creation
     */
    public void logStudentCreated(Student student, String user) {
        logAudit(ACTION_CREATE, ENTITY_STUDENT, student.getStudentCode(),
            null, student.toString(), "Student record created", user);
    }

    /**
     * Log student update
     */
    public void logStudentUpdated(Student student, String oldName, String oldProgram,
                                   String user, String reason) {
        logAudit(ACTION_UPDATE, ENTITY_STUDENT, student.getStudentCode(),
            "Name: " + oldName + ", Program: " + oldProgram,
            "Name: " + student.getName() + ", Program: " + student.getProgram(),
            reason, user);
    }

    /**
     * Log payment creation
     */
    public void logPaymentCreated(Payment payment, String user) {
        logAudit(ACTION_CREATE, ENTITY_PAYMENT, String.valueOf(payment.getReceiptNumber()),
            null, payment.toString(), "Payment record created", user);
    }

    /**
     * Log payment update
     */
    public void logPaymentUpdated(Payment payment, Payment oldPayment, String user, String reason) {
        String oldVal = formatPaymentForAudit(oldPayment);
        String newVal = formatPaymentForAudit(payment);
        logAudit(ACTION_UPDATE, ENTITY_PAYMENT, String.valueOf(payment.getReceiptNumber()),
            oldVal, newVal, reason, user);
    }

    /**
     * Log payment voided
     */
    public void logPaymentVoided(Payment payment, String user, String reason) {
        logAudit(ACTION_VOID, ENTITY_PAYMENT, String.valueOf(payment.getReceiptNumber()),
            payment.toString(), payment.toString() + " [VOIDED]",
            "Payment voided: " + reason, user);
    }

    /**
     * Log import batch creation/completion
     */
    public void logImportBatch(ImportBatch batch, String user) {
        logAudit(ACTION_IMPORT, ENTITY_IMPORT_BATCH, batch.getBatchCode(),
            null, batch.toString(),
            "Import batch completed: " + batch.getFileName(), user);
    }

    /**
     * Log import conflicts detected
     */
    public void logImportConflicts(ImportBatch batch, int conflictCount, String user) {
        logAudit(ACTION_IMPORT, ENTITY_IMPORT_BATCH, batch.getBatchCode(),
            null, "Conflicts: " + conflictCount,
            conflictCount + " payment conflicts detected during import", user);
    }

    /**
     * Log import cancellation
     */
    public void logImportCancelled(ImportBatch batch, String user, String reason) {
        logAudit(ACTION_IMPORT, ENTITY_IMPORT_BATCH, batch.getBatchCode(),
            null, "CANCELLED: " + reason,
            "Import cancelled: " + reason, user);
    }

    /**
     * Log student merge (when payments from import are merged into existing student)
     */
    public void logStudentMerge(String existingStudentCode, String newStudentName,
                                 int paymentsMerged, String user) {
        logAudit(ACTION_MERGE, ENTITY_STUDENT, existingStudentCode,
            null, "Merged " + paymentsMerged + " payments from " + newStudentName,
            "Imported payments merged into existing student", user);
    }

    /**
     * Generic audit logging
     */
    public void logAudit(String action, String entityType, String entityId,
                         String oldValue, String newValue, String reason, String user) {
        try {
            db.logAudit(action, entityType, entityId, oldValue, newValue, reason, user);
        } catch (Exception e) {
            System.err.println("Failed to log audit: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get recent audit logs
     */
    public List<Map<String, Object>> getRecentAuditLogs(int limit) {
        try {
            return db.getAuditLogs(limit);
        } catch (Exception e) {
            System.err.println("Failed to get audit logs: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get audit logs for a specific entity
     */
    public List<Map<String, Object>> getAuditLogsForEntity(String entityType, String entityId) {
        try {
            return db.getAuditLogsForEntity(entityType, entityId);
        } catch (Exception e) {
            System.err.println("Failed to get audit logs for entity: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Format payment for audit log
     */
    private String formatPaymentForAudit(Payment p) {
        return String.format("Receipt: %d, Program: %s, Intel: %s, T-Shirt: %s, Penalties: %s, CIT Night: %s, Received: %s, Remarks: %s, Date: %s, Total: ₱%,.2f",
            p.getReceiptNumber(),
            p.getProgram() != null ? p.getProgram() : "-",
            p.getIntelFee() != null ? p.getIntelFee() : "-",
            p.getTshirtSizing() != null ? p.getTshirtSizing() : "-",
            p.getPenalties() != null ? p.getPenalties() : "-",
            p.getCitNight() != null ? p.getCitNight() : "-",
            p.getReceivedBy() != null ? p.getReceivedBy() : "-",
            p.getRemarks() != null ? p.getRemarks() : "-",
            p.getRemittanceDate() != null ? p.getRemittanceDate().toString() : "-",
            p.getTotalAmount()
        );
    }
}