package com.payment.database;

import com.payment.ImportBatch;
import com.payment.Payment;
import com.payment.Student;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SQLite database manager for the Student Payment Database.
 * Handles schema creation, connections, and basic CRUD operations.
 */
public class DatabaseManager {
    private static final String DB_FILE = "student_payment.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_FILE;

    // Singleton instance
    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        initialize();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Initialize database connection and create schema.
     */
    private void initialize() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Create connection
            connection = DriverManager.getConnection(JDBC_URL);

            // Enable foreign keys
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
            }

            // Create tables
            createTables();

            System.out.println("Database initialized: " + new File(DB_FILE).getAbsolutePath());

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Create database tables if they don't exist.
     */
    private void createTables() throws SQLException {
        String[] ddl = {
            // students table
            "CREATE TABLE IF NOT EXISTS students (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    student_code TEXT NOT NULL UNIQUE," +
            "    name TEXT NOT NULL," +
            "    normalized_name TEXT NOT NULL," +
            "    program TEXT," +
            "    year_level INTEGER," +
            "    created_at TEXT NOT NULL," +
            "    updated_at TEXT NOT NULL" +
            ")",

            // Index on normalized_name for fast matching
            "CREATE INDEX IF NOT EXISTS idx_students_normalized_name ON students(normalized_name)",
            "CREATE INDEX IF NOT EXISTS idx_students_student_code ON students(student_code)",

            // payments table
            "CREATE TABLE IF NOT EXISTS payments (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    receipt_number INTEGER NOT NULL," +
            "    student_id TEXT NOT NULL," +
            "    name TEXT NOT NULL," +
            "    program TEXT," +
            "    intel_fee REAL," +
            "    tshirt_sizing REAL," +
            "    penalties REAL," +
            "    cit_night REAL," +
            "    received_by TEXT," +
            "    remarks TEXT," +
            "    remittance_date TEXT," +
            "    status TEXT NOT NULL DEFAULT 'ACTIVE'," +
            "    created_at TEXT NOT NULL," +
            "    updated_at TEXT NOT NULL," +
            "    FOREIGN KEY (student_id) REFERENCES students(student_code)" +
            ")",

            // Index on receipt_number for duplicate detection
            "CREATE INDEX IF NOT EXISTS idx_payments_receipt_number ON payments(receipt_number)",
            "CREATE INDEX IF NOT EXISTS idx_payments_student_id ON payments(student_id)",
            "CREATE INDEX IF NOT EXISTS idx_payments_remittance_date ON payments(remittance_date)",

            // import_batches table
            "CREATE TABLE IF NOT EXISTS import_batches (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    batch_code TEXT NOT NULL UNIQUE," +
            "    file_name TEXT NOT NULL," +
            "    remittance_date TEXT," +
            "    imported_at TEXT NOT NULL," +
            "    imported_by TEXT," +
            "    total_rows INTEGER NOT NULL DEFAULT 0," +
            "    new_records INTEGER NOT NULL DEFAULT 0," +
            "    duplicate_records INTEGER NOT NULL DEFAULT 0," +
            "    conflict_records INTEGER NOT NULL DEFAULT 0," +
            "    error_records INTEGER NOT NULL DEFAULT 0," +
            "    status TEXT NOT NULL DEFAULT 'PENDING'," +
            "    created_at TEXT NOT NULL," +
            "    updated_at TEXT NOT NULL" +
            ")",

            // Index on batch_code
            "CREATE INDEX IF NOT EXISTS idx_import_batches_batch_code ON import_batches(batch_code)",

            // audit_logs table
            "CREATE TABLE IF NOT EXISTS audit_logs (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    timestamp TEXT NOT NULL," +
            "    action TEXT NOT NULL," +
            "    entity_type TEXT NOT NULL," +
            "    entity_id TEXT NOT NULL," +
            "    old_value TEXT," +
            "    new_value TEXT," +
            "    reason TEXT," +
            "    user TEXT," +
            "    created_at TEXT NOT NULL DEFAULT (datetime('now'))" +
            ")",

            "CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id)",
            "CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp)"
        };

        try (Statement stmt = connection.createStatement()) {
            for (String sql : ddl) {
                stmt.execute(sql);
            }
        }
    }

    /**
     * Get a database connection.
     * Caller must close the connection.
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(JDBC_URL);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
        }
        return connection;
    }

    /**
     * Close the database connection.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("Error closing database: " + e.getMessage());
            }
        }
    }

    // ==================== Student Operations ====================

    /**
     * Insert a new student.
     * @return The generated database ID
     */
    public int insertStudent(Student student) throws SQLException {
        String sql = "INSERT INTO students (student_code, name, normalized_name, program, year_level, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, student.getStudentCode());
            stmt.setString(2, student.getName());
            stmt.setString(3, student.getNormalizedName());
            stmt.setString(4, student.getProgram());
            if (student.getYearLevel() != null) {
                stmt.setInt(5, student.getYearLevel());
            } else {
                stmt.setNull(5, Types.INTEGER);
            }
            stmt.setString(6, student.getCreatedAt().toString());
            stmt.setString(7, student.getUpdatedAt().toString());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    /**
     * Update an existing student.
     */
    public boolean updateStudent(Student student) throws SQLException {
        String sql = "UPDATE students SET name = ?, normalized_name = ?, program = ?, year_level = ?, updated_at = ? " +
                     "WHERE student_code = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, student.getName());
            stmt.setString(2, student.getNormalizedName());
            stmt.setString(3, student.getProgram());
            if (student.getYearLevel() != null) {
                stmt.setInt(4, student.getYearLevel());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.setString(5, student.getUpdatedAt().toString());
            stmt.setString(6, student.getStudentCode());

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Find student by student_code.
     */
    public Optional<Student> findStudentByCode(String studentCode) throws SQLException {
        String sql = "SELECT * FROM students WHERE student_code = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, studentCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapStudent(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Find student by normalized name.
     * Returns empty if multiple matches (ambiguous).
     */
    public Optional<Student> findStudentByNormalizedName(String normalizedName) throws SQLException {
        String sql = "SELECT * FROM students WHERE normalized_name = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, normalizedName);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Student> matches = new ArrayList<>();
                while (rs.next()) {
                    matches.add(mapStudent(rs));
                }
                if (matches.size() == 1) {
                    return Optional.of(matches.get(0));
                }
                // Multiple matches = ambiguous
                return Optional.empty();
            }
        }
    }

    /**
     * Find all students matching a normalized name (for ambiguous detection).
     */
    public List<Student> findAllStudentsByNormalizedName(String normalizedName) throws SQLException {
        String sql = "SELECT * FROM students WHERE normalized_name = ?";
        List<Student> matches = new ArrayList<>();

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, normalizedName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    matches.add(mapStudent(rs));
                }
            }
        }
        return matches;
    }

    /**
     * Get all students.
     */
    public List<Student> getAllStudents() throws SQLException {
        String sql = "SELECT * FROM students ORDER BY name COLLATE NOCASE";
        List<Student> students = new ArrayList<>();

        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                students.add(mapStudent(rs));
            }
        }
        return students;
    }

    /**
     * Search students by name (partial match).
     */
    public List<Student> searchStudentsByName(String searchText) throws SQLException {
        String sql = "SELECT * FROM students WHERE name LIKE ? OR normalized_name LIKE ? ORDER BY name COLLATE NOCASE";
        List<Student> students = new ArrayList<>();

        String pattern = "%" + searchText.toLowerCase() + "%";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    students.add(mapStudent(rs));
                }
            }
        }
        return students;
    }

    private Student mapStudent(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setStudentCode(rs.getString("student_code"));
        s.setName(rs.getString("name"));
        s.setNormalizedName(rs.getString("normalized_name"));
        s.setProgram(rs.getString("program"));
        int yearLevel = rs.getInt("year_level");
        if (!rs.wasNull()) s.setYearLevel(yearLevel);
        s.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
        s.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at")));
        return s;
    }

    // ==================== Payment Operations ====================

    /**
     * Insert a new payment.
     */
    public int insertPayment(Payment payment) throws SQLException {
        String sql = "INSERT INTO payments (receipt_number, student_id, name, program, intel_fee, tshirt_sizing, " +
                     "penalties, cit_night, received_by, remarks, remittance_date, status, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, payment.getReceiptNumber());
            stmt.setString(2, payment.getStudentId());
            stmt.setString(3, payment.getName());
            stmt.setString(4, payment.getProgram());
            setNullableDouble(stmt, 5, payment.getIntelFee());
            setNullableDouble(stmt, 6, payment.getTshirtSizing());
            setNullableDouble(stmt, 7, payment.getPenalties());
            setNullableDouble(stmt, 8, payment.getCitNight());
            stmt.setString(9, payment.getReceivedBy());
            stmt.setString(10, payment.getRemarks());
            if (payment.getRemittanceDate() != null) {
                stmt.setString(11, payment.getRemittanceDate().toString());
            } else {
                stmt.setNull(11, Types.VARCHAR);
            }
            stmt.setString(12, payment.getStatus());
            stmt.setString(13, payment.getCreatedAt().toString());
            stmt.setString(14, payment.getUpdatedAt().toString());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    /**
     * Update an existing payment.
     */
    public boolean updatePayment(Payment payment) throws SQLException {
        String sql = "UPDATE payments SET name = ?, program = ?, intel_fee = ?, tshirt_sizing = ?, penalties = ?, " +
                     "cit_night = ?, received_by = ?, remarks = ?, remittance_date = ?, status = ?, updated_at = ? " +
                     "WHERE receipt_number = ? AND student_id = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, payment.getName());
            stmt.setString(2, payment.getProgram());
            setNullableDouble(stmt, 3, payment.getIntelFee());
            setNullableDouble(stmt, 4, payment.getTshirtSizing());
            setNullableDouble(stmt, 5, payment.getPenalties());
            setNullableDouble(stmt, 6, payment.getCitNight());
            stmt.setString(7, payment.getReceivedBy());
            stmt.setString(8, payment.getRemarks());
            if (payment.getRemittanceDate() != null) {
                stmt.setString(9, payment.getRemittanceDate().toString());
            } else {
                stmt.setNull(9, Types.VARCHAR);
            }
            stmt.setString(10, payment.getStatus());
            stmt.setString(11, payment.getUpdatedAt().toString());
            stmt.setInt(12, payment.getReceiptNumber());
            stmt.setString(13, payment.getStudentId());

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Find payment by receipt number and student_id.
     */
    public Optional<Payment> findPayment(int receiptNumber, String studentId) throws SQLException {
        String sql = "SELECT * FROM payments WHERE receipt_number = ? AND student_id = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, receiptNumber);
            stmt.setString(2, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPayment(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Find payment by receipt number (global, for duplicate detection).
     */
    public Optional<Payment> findPaymentByReceiptNumber(int receiptNumber) throws SQLException {
        String sql = "SELECT * FROM payments WHERE receipt_number = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, receiptNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPayment(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Get all payments for a student.
     */
    public List<Payment> getPaymentsByStudent(String studentId) throws SQLException {
        String sql = "SELECT * FROM payments WHERE student_id = ? ORDER BY receipt_number";
        List<Payment> payments = new ArrayList<>();

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    payments.add(mapPayment(rs));
                }
            }
        }
        return payments;
    }

    /**
     * Get all payments.
     */
    public List<Payment> getAllPayments() throws SQLException {
        String sql = "SELECT * FROM payments ORDER BY remittance_date DESC, receipt_number";
        List<Payment> payments = new ArrayList<>();

        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                payments.add(mapPayment(rs));
            }
        }
        return payments;
    }

    private Payment mapPayment(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setReceiptNumber(rs.getInt("receipt_number"));
        p.setStudentId(rs.getString("student_id"));
        p.setName(rs.getString("name"));
        p.setProgram(rs.getString("program"));
        double intelFee = rs.getDouble("intel_fee");
        if (!rs.wasNull()) p.setIntelFee(intelFee);
        double tshirt = rs.getDouble("tshirt_sizing");
        if (!rs.wasNull()) p.setTshirtSizing(tshirt);
        double penalties = rs.getDouble("penalties");
        if (!rs.wasNull()) p.setPenalties(penalties);
        double citNight = rs.getDouble("cit_night");
        if (!rs.wasNull()) p.setCitNight(citNight);
        p.setReceivedBy(rs.getString("received_by"));
        p.setRemarks(rs.getString("remarks"));
        String remDate = rs.getString("remittance_date");
        if (remDate != null && !remDate.isEmpty()) {
            p.setRemittanceDate(LocalDate.parse(remDate));
        }
        p.setStatus(rs.getString("status"));
        p.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
        p.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at")));
        return p;
    }

    private void setNullableDouble(PreparedStatement stmt, int index, Double value) throws SQLException {
        if (value != null) {
            stmt.setDouble(index, value);
        } else {
            stmt.setNull(index, Types.REAL);
        }
    }

    // ==================== ImportBatch Operations ====================

    public int insertImportBatch(ImportBatch batch) throws SQLException {
        String sql = "INSERT INTO import_batches (batch_code, file_name, remittance_date, imported_at, imported_by, " +
                     "total_rows, new_records, duplicate_records, conflict_records, error_records, status, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, batch.getBatchCode());
            stmt.setString(2, batch.getFileName());
            if (batch.getRemittanceDate() != null) {
                stmt.setString(3, batch.getRemittanceDate().toString());
            } else {
                stmt.setNull(3, Types.VARCHAR);
            }
            stmt.setString(4, batch.getImportedAt().toString());
            stmt.setString(5, batch.getImportedBy());
            stmt.setInt(6, batch.getTotalRows());
            stmt.setInt(7, batch.getNewRecords());
            stmt.setInt(8, batch.getDuplicateRecords());
            stmt.setInt(9, batch.getConflictRecords());
            stmt.setInt(10, batch.getErrorRecords());
            stmt.setString(11, batch.getStatus());
            stmt.setString(12, batch.getCreatedAt().toString());
            stmt.setString(13, batch.getUpdatedAt().toString());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public boolean updateImportBatch(ImportBatch batch) throws SQLException {
        String sql = "UPDATE import_batches SET file_name = ?, remittance_date = ?, imported_at = ?, imported_by = ?, " +
                     "total_rows = ?, new_records = ?, duplicate_records = ?, conflict_records = ?, error_records = ?, " +
                     "status = ?, updated_at = ? WHERE batch_code = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, batch.getFileName());
            if (batch.getRemittanceDate() != null) {
                stmt.setString(2, batch.getRemittanceDate().toString());
            } else {
                stmt.setNull(2, Types.VARCHAR);
            }
            stmt.setString(3, batch.getImportedAt().toString());
            stmt.setString(4, batch.getImportedBy());
            stmt.setInt(5, batch.getTotalRows());
            stmt.setInt(6, batch.getNewRecords());
            stmt.setInt(7, batch.getDuplicateRecords());
            stmt.setInt(8, batch.getConflictRecords());
            stmt.setInt(9, batch.getErrorRecords());
            stmt.setString(10, batch.getStatus());
            stmt.setString(11, batch.getUpdatedAt().toString());
            stmt.setString(12, batch.getBatchCode());

            return stmt.executeUpdate() > 0;
        }
    }

    public Optional<ImportBatch> findImportBatchByCode(String batchCode) throws SQLException {
        String sql = "SELECT * FROM import_batches WHERE batch_code = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, batchCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapImportBatch(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<ImportBatch> getAllImportBatches() throws SQLException {
        String sql = "SELECT * FROM import_batches ORDER BY imported_at DESC";
        List<ImportBatch> batches = new ArrayList<>();

        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                batches.add(mapImportBatch(rs));
            }
        }
        return batches;
    }

    private ImportBatch mapImportBatch(ResultSet rs) throws SQLException {
        ImportBatch b = new ImportBatch();
        b.setId(rs.getInt("id"));
        b.setBatchCode(rs.getString("batch_code"));
        b.setFileName(rs.getString("file_name"));
        String remDate = rs.getString("remittance_date");
        if (remDate != null && !remDate.isEmpty()) {
            b.setRemittanceDate(LocalDate.parse(remDate));
        }
        b.setImportedAt(LocalDateTime.parse(rs.getString("imported_at")));
        b.setImportedBy(rs.getString("imported_by"));
        b.setTotalRows(rs.getInt("total_rows"));
        b.setNewRecords(rs.getInt("new_records"));
        b.setDuplicateRecords(rs.getInt("duplicate_records"));
        b.setConflictRecords(rs.getInt("conflict_records"));
        b.setErrorRecords(rs.getInt("error_records"));
        b.setStatus(rs.getString("status"));
        b.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
        b.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at")));
        return b;
    }

    // ==================== Audit Log Operations ====================

    public void logAudit(String action, String entityType, String entityId,
                         String oldValue, String newValue, String reason, String user) throws SQLException {
        String sql = "INSERT INTO audit_logs (timestamp, action, entity_type, entity_id, old_value, new_value, reason, user) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, LocalDateTime.now().toString());
            stmt.setString(2, action);
            stmt.setString(3, entityType);
            stmt.setString(4, entityId);
            stmt.setString(5, oldValue);
            stmt.setString(6, newValue);
            stmt.setString(7, reason);
            stmt.setString(8, user);
            stmt.executeUpdate();
        }
    }

    public List<Map<String, Object>> getAuditLogs(int limit) throws SQLException {
        String sql = "SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT ?";
        List<Map<String, Object>> logs = new ArrayList<>();

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    logs.add(row);
                }
            }
        }
        return logs;
    }

    /**
     * Get audit logs for a specific entity.
     */
    public List<Map<String, Object>> getAuditLogsForEntity(String entityType, String entityId) throws SQLException {
        String sql = "SELECT * FROM audit_logs WHERE entity_type = ? AND entity_id = ? ORDER BY timestamp DESC";
        List<Map<String, Object>> logs = new ArrayList<>();

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, entityType);
            stmt.setString(2, entityId);
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    logs.add(row);
                }
            }
        }
        return logs;
    }

    // ==================== Transaction Support ====================

    public void beginTransaction() throws SQLException {
        getConnection().setAutoCommit(false);
    }

    public void commitTransaction() throws SQLException {
        getConnection().commit();
        getConnection().setAutoCommit(true);
    }

    public void rollbackTransaction() throws SQLException {
        getConnection().rollback();
        getConnection().setAutoCommit(true);
    }

    // ==================== Migration Support ====================

    /**
     * Check if database is empty (no students).
     */
    public boolean isEmpty() throws SQLException {
        String sql = "SELECT COUNT(*) FROM students";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        }
        return true;
    }

    /**
     * Get count of students.
     */
    public int getStudentCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM students";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    /**
     * Get count of payments.
     */
    public int getPaymentCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM payments";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }
}