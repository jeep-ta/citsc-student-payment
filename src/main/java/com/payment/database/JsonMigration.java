package com.payment.database;

import com.payment.DataManager;
import com.payment.ImportBatch;
import com.payment.NameNormalizer;
import com.payment.Payment;
import com.payment.Student;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Migration utility to move data from JSON to SQLite.
 * Run once to populate the database from existing JSON file.
 */
public class JsonMigration {

    public static void migrate() {
        System.out.println("Starting JSON to SQLite migration...");

        DatabaseManager db = DatabaseManager.getInstance();

        try {
            // Check if database already has data
            if (!db.isEmpty()) {
                System.out.println("Database already contains data. Skipping migration.");
                System.out.println("Students in DB: " + db.getStudentCount());
                System.out.println("Payments in DB: " + db.getPaymentCount());
                return;
            }

            // Load JSON data
            DataManager.SavedData savedData = DataManager.loadData();
            if (savedData == null || savedData.getStudents() == null || savedData.getStudents().isEmpty()) {
                System.out.println("No JSON data found to migrate.");
                return;
            }

            List<Student> students = savedData.getStudents();
            System.out.println("Found " + students.size() + " students in JSON");

            // Assign student codes if missing
            int assigned = DataManager.assignStudentCodes(students);
            System.out.println("Assigned " + assigned + " student codes");

            // Link payments to student codes
            for (Student s : students) {
                for (Payment p : s.getPayments()) {
                    if (p.getStudentId() == null || p.getStudentId().isEmpty()) {
                        p.setStudentId(s.getStudentCode());
                    }
                    // Ensure status is set
                    if (p.getStatus() == null) {
                        p.setStatus(Payment.STATUS_ACTIVE);
                    }
                }
            }

            // Begin transaction for migration
            db.beginTransaction();

            try {
                // Insert students
                int studentCount = 0;
                for (Student s : students) {
                    // Ensure normalizedName is computed (lazy initialization may not have run for JSON-loaded objects)
                    String studentName = s.getName();
                    if (studentName == null || studentName.isEmpty()) {
                        System.err.println("ERROR: Student at index " + studentCount + " has null/empty name!");
                        continue;
                    }
                    String nn = s.getNormalizedName();
                    if (nn == null || nn.isEmpty()) {
                        // Force compute it
                        s.setNormalizedName(NameNormalizer.normalize(studentName));
                    }
                    db.insertStudent(s);
                    studentCount++;
                }
                System.out.println("Migrated " + studentCount + " students");

                // Insert payments
                int paymentCount = 0;
                for (Student s : students) {
                    for (Payment p : s.getPayments()) {
                        db.insertPayment(p);
                        paymentCount++;
                    }
                }
                System.out.println("Migrated " + paymentCount + " payments");

                // Create initial import batch record
                ImportBatch batch = new ImportBatch(
                    savedData.getLastImportFile() != null ? savedData.getLastImportFile() : "migrated_from_json",
                    LocalDate.now(),
                    "system"
                );
                batch.setBatchCode("IMP-MIGRATION-" + LocalDateTime.now().getYear());
                batch.setImportedAt(savedData.getLastImportTime() != null ? savedData.getLastImportTime() : LocalDateTime.now());
                batch.setTotalRows(paymentCount);
                batch.setNewRecords(studentCount);
                batch.setDuplicateRecords(0);
                batch.setConflictRecords(0);
                batch.setErrorRecords(0);
                batch.setStatus(ImportBatch.STATUS_COMPLETED);
                db.insertImportBatch(batch);
                System.out.println("Created migration import batch record");

                // Log audit entry for migration
                db.logAudit(
                    "MIGRATE_FROM_JSON",
                    "DATABASE",
                    "ALL",
                    null,
                    "Migrated " + studentCount + " students and " + paymentCount + " payments from JSON",
                    "Initial database migration",
                    "system"
                );

                db.commitTransaction();
                System.out.println("Migration completed successfully!");

            } catch (Exception e) {
                db.rollbackTransaction();
                throw e;
            }

        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Migration failed", e);
        }
    }

    public static void main(String[] args) {
        migrate();
        DatabaseManager.getInstance().close();
    }
}