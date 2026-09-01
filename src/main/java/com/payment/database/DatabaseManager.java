package com.payment.database;

import com.payment.ChargeAcademicTerm;
import com.payment.ImportBatch;
import com.payment.ImportBatchFile;
import com.payment.Payment;
import com.payment.ReceiptKey;
import com.payment.Student;
import com.payment.StudentMergeRecord;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SQLite database manager for the Student Payment Database.
 * Handles schema creation, connections, and basic CRUD operations.
 */
public class DatabaseManager {
    private static final String DB_FILE = "student_payment.db";
    private static final String DEFAULT_JDBC_URL = "jdbc:sqlite:" + DB_FILE;
    private static final int SQLITE_BIND_BATCH_SIZE = 900;
    private static String customJdbcUrl = null;

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
     * Configure a custom database URL (e.g. for testing with an isolated DB).
     */
    public static synchronized void setCustomDatabaseUrl(String url) {
        customJdbcUrl = url;
    }

    /**
     * Reset the singleton instance, closing any active connection.
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    private String getEffectiveJdbcUrl() {
        return customJdbcUrl != null ? customJdbcUrl : DEFAULT_JDBC_URL;
    }

    /**
     * Initialize database connection and create schema.
     */
    private void initialize() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Create connection
            connection = DriverManager.getConnection(getEffectiveJdbcUrl());

            // Enable foreign keys
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
            }

            // Create tables
            createTables();

            // Run schema migrations for existing databases
            runMigrations();

            String dbLocation = customJdbcUrl != null ? customJdbcUrl : new File(DB_FILE).getAbsolutePath();
            System.out.println("Database initialized: " + dbLocation);

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
            "    charge_academic_term TEXT NOT NULL DEFAULT 'UNASSIGNED'," +
            "    academic_year TEXT," +
            "    status TEXT NOT NULL DEFAULT 'ACTIVE'," +
            "    receipt_academic_year TEXT," +
            "    receipt_term TEXT," +
            "    import_batch_code TEXT," +
            "    import_source_file TEXT," +
            "    import_source_row INTEGER," +
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
            "    file_count INTEGER NOT NULL DEFAULT 1," +
            "    receipt_academic_year TEXT," +
            "    receipt_term TEXT," +
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
            "CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp)",

            // app_settings table
            "CREATE TABLE IF NOT EXISTS app_settings (" +
            "    key TEXT PRIMARY KEY," +
            "    value TEXT NOT NULL," +
            "    updated_at TEXT NOT NULL DEFAULT (datetime('now'))" +
            ")",

            // student_merges table
            "CREATE TABLE IF NOT EXISTS student_merges (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    merge_code TEXT NOT NULL UNIQUE," +
            "    source_student_code TEXT NOT NULL," +
            "    source_name TEXT NOT NULL," +
            "    source_normalized_name TEXT NOT NULL," +
            "    source_program TEXT," +
            "    source_year_level INTEGER," +
            "    source_created_at TEXT," +
            "    source_updated_at TEXT," +
            "    target_student_code TEXT NOT NULL," +
            "    target_name TEXT NOT NULL," +
            "    payment_receipts TEXT NOT NULL," +
            "    payment_ids TEXT," +
            "    merged_at TEXT NOT NULL," +
            "    merged_by TEXT," +
            "    reason TEXT," +
            "    status TEXT NOT NULL DEFAULT 'ACTIVE'" +
            ")",

            "CREATE INDEX IF NOT EXISTS idx_student_merges_code ON student_merges(merge_code)",
            "CREATE INDEX IF NOT EXISTS idx_student_merges_status ON student_merges(status)"
        };

        try (Statement stmt = connection.createStatement()) {
            for (String sql : ddl) {
                stmt.execute(sql);
            }
        }
    }

    /**
     * Run schema migrations for existing databases.
     * Adds new columns that don't exist yet.
     */
    private void runMigrations() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Check if charge_academic_term column exists in payments table
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, "payments", "charge_academic_term")) {
                if (!rs.next()) {
                    // Column doesn't exist, add it
                    stmt.execute("ALTER TABLE payments ADD COLUMN charge_academic_term TEXT NOT NULL DEFAULT 'UNASSIGNED'");
                    System.out.println("Migration: Added charge_academic_term column to payments table");
                }
            }

            // Check if academic_year column exists in payments table
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, "payments", "academic_year")) {
                if (!rs.next()) {
                    // Column doesn't exist, add it
                    stmt.execute("ALTER TABLE payments ADD COLUMN academic_year TEXT");
                    System.out.println("Migration: Added academic_year column to payments table");
                }
            }

            // Per-category term and AY columns
            String[] itemCols = {
                "intel_fee_term", "intel_fee_ay",
                "tshirt_term", "tshirt_ay",
                "penalties_term", "penalties_ay",
                "cit_night_term", "cit_night_ay"
            };
            for (String col : itemCols) {
                try (ResultSet rs = connection.getMetaData().getColumns(null, null, "payments", col)) {
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE payments ADD COLUMN " + col + " TEXT");
                        System.out.println("Migration: Added " + col + " column to payments table");
                    }
                }
            }

            String[][] paymentColumns = {
                {"receipt_academic_year", "TEXT"},
                {"receipt_term", "TEXT"},
                {"import_batch_code", "TEXT"},
                {"import_source_file", "TEXT"},
                {"import_source_row", "INTEGER"}
            };
            for (String[] paymentColumn : paymentColumns) {
                try (ResultSet rs = connection.getMetaData().getColumns(null, null, "payments", paymentColumn[0])) {
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE payments ADD COLUMN " + paymentColumn[0] + " " + paymentColumn[1]);
                        System.out.println("Migration: Added " + paymentColumn[0] + " column to payments table");
                    }
                }
            }

            String[][] batchColumns = {
                {"file_count", "INTEGER NOT NULL DEFAULT 1"},
                {"receipt_academic_year", "TEXT"},
                {"receipt_term", "TEXT"}
            };
            for (String[] batchColumn : batchColumns) {
                try (ResultSet rs = connection.getMetaData().getColumns(null, null, "import_batches", batchColumn[0])) {
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE import_batches ADD COLUMN " + batchColumn[0] + " " + batchColumn[1]);
                        System.out.println("Migration: Added " + batchColumn[0] + " column to import_batches table");
                    }
                }
            }

            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_receipt_scope " +
                "ON payments(receipt_number, receipt_academic_year, receipt_term) " +
                "WHERE receipt_academic_year IS NOT NULL " +
                "AND TRIM(receipt_academic_year) <> '' " +
                "AND receipt_term IN ('1ST_SEM', '2ND_SEM', 'SUMMER')");

            stmt.execute("CREATE TABLE IF NOT EXISTS import_batch_files (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    batch_code TEXT NOT NULL," +
                "    file_name TEXT NOT NULL," +
                "    file_hash TEXT NOT NULL," +
                "    receipt_academic_year TEXT," +
                "    receipt_term TEXT," +
                "    total_rows INTEGER NOT NULL DEFAULT 0," +
                "    new_records INTEGER NOT NULL DEFAULT 0," +
                "    duplicate_records INTEGER NOT NULL DEFAULT 0," +
                "    conflict_records INTEGER NOT NULL DEFAULT 0," +
                "    error_records INTEGER NOT NULL DEFAULT 0," +
                "    status TEXT NOT NULL DEFAULT 'PENDING'," +
                "    created_at TEXT NOT NULL," +
                "    UNIQUE(batch_code, file_name, file_hash)," +
                "    FOREIGN KEY (batch_code) REFERENCES import_batches(batch_code)" +
                ")");

            String[][] batchFileColumns = {
                {"receipt_academic_year", "TEXT"},
                {"receipt_term", "TEXT"}
            };
            for (String[] batchFileColumn : batchFileColumns) {
                try (ResultSet rs = connection.getMetaData().getColumns(null, null, "import_batch_files", batchFileColumn[0])) {
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE import_batch_files ADD COLUMN " +
                            batchFileColumn[0] + " " + batchFileColumn[1]);
                        System.out.println("Migration: Added " + batchFileColumn[0] +
                            " column to import_batch_files table");
                    }
                }
            }
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_import_batch_files_batch ON import_batch_files(batch_code)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_import_batch_files_hash ON import_batch_files(file_hash)");

            // Create app_settings table if not exists
            stmt.execute("CREATE TABLE IF NOT EXISTS app_settings (" +
                "    key TEXT PRIMARY KEY," +
                "    value TEXT NOT NULL," +
                "    updated_at TEXT NOT NULL DEFAULT (datetime('now'))" +
                ")");

            // Create student_merges table if not exists
            stmt.execute("CREATE TABLE IF NOT EXISTS student_merges (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    merge_code TEXT NOT NULL UNIQUE," +
                "    source_student_code TEXT NOT NULL," +
                "    source_name TEXT NOT NULL," +
                "    source_normalized_name TEXT NOT NULL," +
                "    source_program TEXT," +
                "    source_year_level INTEGER," +
                "    source_created_at TEXT," +
                "    source_updated_at TEXT," +
                "    target_student_code TEXT NOT NULL," +
                "    target_name TEXT NOT NULL," +
                "    payment_receipts TEXT NOT NULL," +
                "    payment_ids TEXT," +
                "    merged_at TEXT NOT NULL," +
                "    merged_by TEXT," +
                "    reason TEXT," +
                "    status TEXT NOT NULL DEFAULT 'ACTIVE'" +
                ")");

            try (ResultSet rs = connection.getMetaData().getColumns(null, null, "student_merges", "payment_ids")) {
                if (!rs.next()) {
                    stmt.execute("ALTER TABLE student_merges ADD COLUMN payment_ids TEXT");
                    System.out.println("Migration: Added payment_ids column to student_merges table");
                }
            }
        }
    }

    /**
     * Get a database connection.
     * Caller must close the connection.
     */
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(getEffectiveJdbcUrl());
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
     * Find student by student_code with payments.
     */
    public Optional<Student> findStudentByCode(String studentCode) throws SQLException {
        String sql = "SELECT * FROM students WHERE student_code = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, studentCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Student s = mapStudent(rs);
                    List<Payment> payments = getPaymentsByStudent(s.getStudentCode());
                    for (Payment p : payments) {
                        if ("ACTIVE".equals(p.getStatus())) {
                            s.addPayment(p);
                        }
                    }
                    return Optional.of(s);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Alias for findStudentByCode.
     */
    public Optional<Student> getStudentByCode(String studentCode) throws SQLException {
        return findStudentByCode(studentCode);
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
                    attachPaymentsToStudents(matches);
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
        attachPaymentsToStudents(matches);
        return matches;
    }

    /**
     * Find student metadata for several normalized names without loading payments.
     * Intended for bulk matching workflows such as import preview generation.
     */
    public List<Student> findStudentSummariesByNormalizedNames(Collection<String> normalizedNames) throws SQLException {
        return findStudentSummariesByColumn("normalized_name", normalizedNames);
    }

    /**
     * Find student metadata for several student codes without loading payments.
     */
    public List<Student> findStudentSummariesByCodes(Collection<String> studentCodes) throws SQLException {
        return findStudentSummariesByColumn("student_code", studentCodes);
    }

    private List<Student> findStudentSummariesByColumn(String column, Collection<String> values) throws SQLException {
        if (!"normalized_name".equals(column) && !"student_code".equals(column)) {
            throw new IllegalArgumentException("Unsupported student lookup column: " + column);
        }
        if (values == null || values.isEmpty()) return new ArrayList<>();

        List<String> distinctValues = values.stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        List<Student> students = new ArrayList<>();

        for (int start = 0; start < distinctValues.size(); start += SQLITE_BIND_BATCH_SIZE) {
            int end = Math.min(start + SQLITE_BIND_BATCH_SIZE, distinctValues.size());
            List<String> batch = distinctValues.subList(start, end);
            String sql = "SELECT * FROM students WHERE " + column + " IN (" + placeholders(batch.size()) + ") ORDER BY id";

            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                for (int i = 0; i < batch.size(); i++) {
                    stmt.setString(i + 1, batch.get(i));
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        students.add(mapStudent(rs));
                    }
                }
            }

        }
        return students;
    }

    /**
     * Read the largest generated STU sequence directly in SQLite.
     */
    public int getMaxStudentSequence() throws SQLException {
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTR(student_code, 5) AS INTEGER)), 0) " +
                     "FROM students WHERE student_code GLOB 'STU-[0-9][0-9][0-9][0-9][0-9][0-9]'";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Get all students with payments populated.
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
        attachPaymentsToStudents(students);
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
        attachPaymentsToStudents(students);
        return students;
    }

    /**
     * Helper to attach active payments to a list of students.
     */
    private void attachPaymentsToStudents(List<Student> students) throws SQLException {
        if (students == null || students.isEmpty()) return;
        Map<String, Student> studentMap = new HashMap<>();
        for (Student s : students) {
            if (s.getStudentCode() != null) {
                studentMap.put(s.getStudentCode(), s);
            }
        }
        List<String> studentCodes = new ArrayList<>(studentMap.keySet());
        for (int start = 0; start < studentCodes.size(); start += SQLITE_BIND_BATCH_SIZE) {
            int end = Math.min(start + SQLITE_BIND_BATCH_SIZE, studentCodes.size());
            List<String> batch = studentCodes.subList(start, end);
            String sql = "SELECT * FROM payments WHERE status = 'ACTIVE' AND student_id IN (" +
                         placeholders(batch.size()) + ") ORDER BY receipt_number";

            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                for (int i = 0; i < batch.size(); i++) {
                    stmt.setString(i + 1, batch.get(i));
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Payment p = mapPayment(rs);
                        Student s = studentMap.get(p.getStudentId());
                        if (s != null) {
                            s.addPayment(p);
                        }
                    }
                }
            }
        }
    }

    /**
     * Legacy unscoped bulk lookup. Rejects receipt numbers that occur in more
     * than one period instead of returning an arbitrary record.
     */
    @Deprecated
    public Map<Integer, Payment> findPaymentsByReceiptNumbers(Collection<Integer> receiptNumbers) throws SQLException {
        Map<Integer, Payment> payments = new LinkedHashMap<>();
        if (receiptNumbers == null || receiptNumbers.isEmpty()) return payments;

        List<Integer> distinctReceipts = receiptNumbers.stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

        for (int start = 0; start < distinctReceipts.size(); start += SQLITE_BIND_BATCH_SIZE) {
            int end = Math.min(start + SQLITE_BIND_BATCH_SIZE, distinctReceipts.size());
            List<Integer> batch = distinctReceipts.subList(start, end);
            String sql = "SELECT * FROM payments WHERE receipt_number IN (" + placeholders(batch.size()) + ") ORDER BY id";

            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                for (int i = 0; i < batch.size(); i++) {
                    stmt.setInt(i + 1, batch.get(i));
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Payment payment = mapPayment(rs);
                        Payment prior = payments.putIfAbsent(payment.getReceiptNumber(), payment);
                        if (prior != null) {
                            throw new SQLException("Receipt #" + payment.getReceiptNumber() +
                                " exists in multiple periods; use a period-scoped lookup");
                        }
                    }
                }
            }
        }
        return payments;
    }

    /**
     * Find payments by receipt number within one issuance period. Receipt
     * numbers may legitimately repeat in a different academic period.
     */
    public Map<Integer, Payment> findPaymentsByReceiptNumbers(Collection<Integer> receiptNumbers,
                                                               String receiptAcademicYear,
                                                               ChargeAcademicTerm receiptTerm) throws SQLException {
        ReceiptKey scopeProbe = new ReceiptKey(1, receiptAcademicYear, receiptTerm);
        if (!scopeProbe.hasDefinedScope()) {
            throw new IllegalArgumentException("Receipt academic year and a concrete semester are required");
        }

        Map<Integer, Payment> payments = new LinkedHashMap<>();
        if (receiptNumbers == null || receiptNumbers.isEmpty()) return payments;

        List<Integer> distinctReceipts = receiptNumbers.stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

        for (int start = 0; start < distinctReceipts.size(); start += SQLITE_BIND_BATCH_SIZE) {
            int end = Math.min(start + SQLITE_BIND_BATCH_SIZE, distinctReceipts.size());
            List<Integer> batch = distinctReceipts.subList(start, end);
            String sql = "SELECT * FROM payments WHERE receipt_academic_year = ? AND receipt_term = ? " +
                         "AND receipt_number IN (" + placeholders(batch.size()) + ") ORDER BY id";

            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, scopeProbe.academicYear());
                stmt.setString(2, scopeProbe.term().getCode());
                for (int i = 0; i < batch.size(); i++) {
                    stmt.setInt(i + 3, batch.get(i));
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Payment payment = mapPayment(rs);
                        payments.putIfAbsent(payment.getReceiptNumber(), payment);
                    }
                }
            }
        }
        return payments;
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
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
                     "penalties, cit_night, received_by, remarks, remittance_date, charge_academic_term, academic_year, status, " +
                     "receipt_academic_year, receipt_term, import_batch_code, import_source_file, import_source_row, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
            stmt.setString(12, payment.getChargeAcademicTermCode());
            stmt.setString(13, payment.getAcademicYear());
            stmt.setString(14, payment.getStatus());
            stmt.setString(15, payment.getReceiptAcademicYear());
            stmt.setString(16, payment.getReceiptTermCode());
            stmt.setString(17, payment.getImportBatchCode());
            stmt.setString(18, payment.getImportSourceFile());
            if (payment.getImportSourceRow() != null) {
                stmt.setInt(19, payment.getImportSourceRow());
            } else {
                stmt.setNull(19, Types.INTEGER);
            }
            stmt.setString(20, payment.getCreatedAt().toString());
            stmt.setString(21, payment.getUpdatedAt().toString());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int generatedId = rs.getInt(1);
                    payment.setId(generatedId);
                    return generatedId;
                }
            }
        }
        return -1;
    }

    /**
     * Update an existing payment.
     */
    public boolean updatePayment(Payment payment) throws SQLException {
        if (payment == null || payment.getId() <= 0) {
            throw new SQLException("A persisted payment ID is required for updates");
        }
        String sql = "UPDATE payments SET name = ?, program = ?, intel_fee = ?, tshirt_sizing = ?, penalties = ?, " +
                     "cit_night = ?, received_by = ?, remarks = ?, remittance_date = ?, charge_academic_term = ?, academic_year = ?, " +
                     "status = ?, receipt_academic_year = ?, receipt_term = ?, updated_at = ? " +
                     "WHERE id = ?";

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
            stmt.setString(10, payment.getChargeAcademicTermCode());
            stmt.setString(11, payment.getAcademicYear());
            stmt.setString(12, payment.getStatus());
            stmt.setString(13, payment.getReceiptAcademicYear());
            stmt.setString(14, payment.getReceiptTermCode());
            stmt.setString(15, payment.getUpdatedAt().toString());
            stmt.setInt(16, payment.getId());

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Find payment by primary key ID.
     */
    public Optional<Payment> findPaymentById(int id) throws SQLException {
        String sql = "SELECT * FROM payments WHERE id = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPayment(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Find a payment by receipt number and student ID. This legacy lookup
     * rejects ambiguous results now that receipt numbers can repeat by period.
     */
    public Optional<Payment> findPayment(int receiptNumber, String studentId) throws SQLException {
        String sql = "SELECT * FROM payments WHERE receipt_number = ? AND student_id = ? ORDER BY id LIMIT 2";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, receiptNumber);
            stmt.setString(2, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Payment payment = mapPayment(rs);
                    if (rs.next()) {
                        throw new SQLException("Receipt #" + receiptNumber +
                            " exists in multiple periods for student " + studentId +
                            "; select a specific payment record");
                    }
                    return Optional.of(payment);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Find a payment by receipt number. This legacy lookup rejects ambiguous
     * results; period-aware workflows must use a scoped lookup instead.
     */
    public Optional<Payment> findPaymentByReceiptNumber(int receiptNumber) throws SQLException {
        String sql = "SELECT * FROM payments WHERE receipt_number = ? ORDER BY id LIMIT 2";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, receiptNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Payment payment = mapPayment(rs);
                    if (rs.next()) {
                        throw new SQLException("Receipt #" + receiptNumber +
                            " exists in multiple periods; provide the receipt period or payment ID");
                    }
                    return Optional.of(payment);
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
        try {
            p.setId(rs.getInt("id"));
        } catch (SQLException ignored) {}
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
        try {
            String chargeTerm = rs.getString("charge_academic_term");
            if (chargeTerm != null && !chargeTerm.isEmpty()) {
                p.setChargeAcademicTermCode(chargeTerm);
            }
        } catch (SQLException ignored) {}
        try {
            p.setAcademicYear(rs.getString("academic_year"));
        } catch (SQLException ignored) {}

        try {
            p.setReceiptAcademicYear(rs.getString("receipt_academic_year"));
            p.setReceiptTermCode(rs.getString("receipt_term"));
            p.setImportBatchCode(rs.getString("import_batch_code"));
            p.setImportSourceFile(rs.getString("import_source_file"));
            int sourceRow = rs.getInt("import_source_row");
            if (!rs.wasNull()) p.setImportSourceRow(sourceRow);
        } catch (SQLException ignored) {}

        // Item-level terms and AY
        try {
            String itTerm = rs.getString("intel_fee_term");
            if (itTerm != null && !itTerm.isEmpty()) p.setIntelFeeTerm(com.payment.ChargeAcademicTerm.fromCode(itTerm));
            p.setIntelFeeAy(rs.getString("intel_fee_ay"));

            String tshTerm = rs.getString("tshirt_term");
            if (tshTerm != null && !tshTerm.isEmpty()) p.setTshirtTerm(com.payment.ChargeAcademicTerm.fromCode(tshTerm));
            p.setTshirtAy(rs.getString("tshirt_ay"));

            String penTerm = rs.getString("penalties_term");
            if (penTerm != null && !penTerm.isEmpty()) p.setPenaltiesTerm(com.payment.ChargeAcademicTerm.fromCode(penTerm));
            p.setPenaltiesAy(rs.getString("penalties_ay"));

            String citTerm = rs.getString("cit_night_term");
            if (citTerm != null && !citTerm.isEmpty()) p.setCitNightTerm(com.payment.ChargeAcademicTerm.fromCode(citTerm));
            p.setCitNightAy(rs.getString("cit_night_ay"));
        } catch (SQLException ignored) {}

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
        String sql = "INSERT INTO import_batches (batch_code, file_name, file_count, receipt_academic_year, receipt_term, remittance_date, imported_at, imported_by, " +
                     "total_rows, new_records, duplicate_records, conflict_records, error_records, status, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, batch.getBatchCode());
            stmt.setString(2, batch.getFileName());
            stmt.setInt(3, batch.getFileCount());
            stmt.setString(4, batch.getReceiptAcademicYear());
            stmt.setString(5, batch.getReceiptTerm().getCode());
            if (batch.getRemittanceDate() != null) {
                stmt.setString(6, batch.getRemittanceDate().toString());
            } else {
                stmt.setNull(6, Types.VARCHAR);
            }
            stmt.setString(7, batch.getImportedAt().toString());
            stmt.setString(8, batch.getImportedBy());
            stmt.setInt(9, batch.getTotalRows());
            stmt.setInt(10, batch.getNewRecords());
            stmt.setInt(11, batch.getDuplicateRecords());
            stmt.setInt(12, batch.getConflictRecords());
            stmt.setInt(13, batch.getErrorRecords());
            stmt.setString(14, batch.getStatus());
            stmt.setString(15, batch.getCreatedAt().toString());
            stmt.setString(16, batch.getUpdatedAt().toString());

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
        String sql = "UPDATE import_batches SET file_name = ?, file_count = ?, receipt_academic_year = ?, receipt_term = ?, remittance_date = ?, imported_at = ?, imported_by = ?, " +
                     "total_rows = ?, new_records = ?, duplicate_records = ?, conflict_records = ?, error_records = ?, " +
                     "status = ?, updated_at = ? WHERE batch_code = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, batch.getFileName());
            stmt.setInt(2, batch.getFileCount());
            stmt.setString(3, batch.getReceiptAcademicYear());
            stmt.setString(4, batch.getReceiptTerm().getCode());
            if (batch.getRemittanceDate() != null) {
                stmt.setString(5, batch.getRemittanceDate().toString());
            } else {
                stmt.setNull(5, Types.VARCHAR);
            }
            stmt.setString(6, batch.getImportedAt().toString());
            stmt.setString(7, batch.getImportedBy());
            stmt.setInt(8, batch.getTotalRows());
            stmt.setInt(9, batch.getNewRecords());
            stmt.setInt(10, batch.getDuplicateRecords());
            stmt.setInt(11, batch.getConflictRecords());
            stmt.setInt(12, batch.getErrorRecords());
            stmt.setString(13, batch.getStatus());
            stmt.setString(14, batch.getUpdatedAt().toString());
            stmt.setString(15, batch.getBatchCode());

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

    /**
     * Return the largest numeric suffix used by an import batch for a year.
     */
    public int getMaxImportBatchSequence(int year) throws SQLException {
        String prefix = "IMP-" + year + "-";
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTR(batch_code, ?) AS INTEGER)), 0) " +
                     "FROM import_batches WHERE batch_code GLOB ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, prefix.length() + 1);
            stmt.setString(2, prefix + "[0-9]*");
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private ImportBatch mapImportBatch(ResultSet rs) throws SQLException {
        ImportBatch b = new ImportBatch();
        b.setId(rs.getInt("id"));
        b.setBatchCode(rs.getString("batch_code"));
        b.setFileName(rs.getString("file_name"));
        b.setFileCount(rs.getInt("file_count"));
        b.setReceiptAcademicYear(rs.getString("receipt_academic_year"));
        b.setReceiptTerm(ChargeAcademicTerm.fromCode(rs.getString("receipt_term")));
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

    public int insertImportBatchFile(ImportBatchFile file) throws SQLException {
        String sql = "INSERT INTO import_batch_files (batch_code, file_name, file_hash, receipt_academic_year, receipt_term, " +
                     "total_rows, new_records, duplicate_records, conflict_records, error_records, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, file.getBatchCode());
            stmt.setString(2, file.getFileName());
            stmt.setString(3, file.getFileHash());
            stmt.setString(4, file.getReceiptAcademicYear());
            stmt.setString(5, file.getReceiptTerm().getCode());
            stmt.setInt(6, file.getTotalRows());
            stmt.setInt(7, file.getNewRecords());
            stmt.setInt(8, file.getDuplicateRecords());
            stmt.setInt(9, file.getConflictRecords());
            stmt.setInt(10, file.getErrorRecords());
            stmt.setString(11, file.getStatus());
            stmt.setString(12, file.getCreatedAt().toString());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    file.setId(rs.getInt(1));
                    return file.getId();
                }
            }
        }
        return -1;
    }

    public List<ImportBatchFile> getImportBatchFiles(String batchCode) throws SQLException {
        String sql = "SELECT * FROM import_batch_files WHERE batch_code = ? ORDER BY id";
        List<ImportBatchFile> files = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, batchCode);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ImportBatchFile file = new ImportBatchFile();
                    file.setId(rs.getInt("id"));
                    file.setBatchCode(rs.getString("batch_code"));
                    file.setFileName(rs.getString("file_name"));
                    file.setFileHash(rs.getString("file_hash"));
                    file.setReceiptAcademicYear(rs.getString("receipt_academic_year"));
                    file.setReceiptTerm(ChargeAcademicTerm.fromCode(rs.getString("receipt_term")));
                    file.setTotalRows(rs.getInt("total_rows"));
                    file.setNewRecords(rs.getInt("new_records"));
                    file.setDuplicateRecords(rs.getInt("duplicate_records"));
                    file.setConflictRecords(rs.getInt("conflict_records"));
                    file.setErrorRecords(rs.getInt("error_records"));
                    file.setStatus(rs.getString("status"));
                    file.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
                    files.add(file);
                }
            }
        }
        return files;
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

    /**
     * Get a distinct list of all program names from students and payments.
     */
    public List<String> getDistinctPrograms() throws SQLException {
        String sql = "SELECT DISTINCT program FROM students WHERE program IS NOT NULL AND TRIM(program) != '' " +
                     "UNION " +
                     "SELECT DISTINCT program FROM payments WHERE program IS NOT NULL AND TRIM(program) != '' " +
                     "ORDER BY 1 COLLATE NOCASE";
        List<String> programs = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String prog = rs.getString(1);
                if (prog != null && !prog.trim().isEmpty()) {
                    programs.add(prog.trim());
                }
            }
        }
        return programs;
    }

    /**
     * Set payment status to VOID with an audit trail entry.
     */
    public boolean voidPayment(int receiptNumber, String reason, String user) throws SQLException {
        int paymentId = findUniquePaymentIdByReceipt(receiptNumber);
        return paymentId >= 0 && voidPaymentById(paymentId, reason, user);
    }

    public boolean voidPaymentById(int paymentId, String reason, String user) throws SQLException {
        Optional<Payment> existing = findPaymentById(paymentId);
        if (existing.isEmpty()) return false;
        String sql = "UPDATE payments SET status = 'VOID', updated_at = ? WHERE id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, LocalDateTime.now().toString());
            stmt.setInt(2, paymentId);
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                Payment payment = existing.get();
                logAudit("VOID", "PAYMENT", String.valueOf(payment.getReceiptNumber()),
                    "ACTIVE", "VOID", reason,
                    user != null ? user : "user");
                return true;
            }
        }
        return false;
    }

    /**
     * Reactivate a VOID payment back to ACTIVE with an audit trail entry.
     */
    public boolean unvoidPayment(int receiptNumber, String reason, String user) throws SQLException {
        int paymentId = findUniquePaymentIdByReceipt(receiptNumber);
        return paymentId >= 0 && unvoidPaymentById(paymentId, reason, user);
    }

    public boolean unvoidPaymentById(int paymentId, String reason, String user) throws SQLException {
        Optional<Payment> existing = findPaymentById(paymentId);
        if (existing.isEmpty()) return false;
        String sql = "UPDATE payments SET status = 'ACTIVE', updated_at = ? WHERE id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, LocalDateTime.now().toString());
            stmt.setInt(2, paymentId);
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                Payment payment = existing.get();
                logAudit("UPDATE", "PAYMENT", String.valueOf(payment.getReceiptNumber()),
                    "VOID", "ACTIVE", reason,
                    user != null ? user : "user");
                return true;
            }
        }
        return false;
    }

    private int findUniquePaymentIdByReceipt(int receiptNumber) throws SQLException {
        String sql = "SELECT id FROM payments WHERE receipt_number = ? ORDER BY id";
        Integer paymentId = null;
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, receiptNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (paymentId != null) {
                        throw new SQLException("Receipt #" + receiptNumber +
                            " exists in multiple periods; select a specific payment record");
                    }
                    paymentId = rs.getInt(1);
                }
            }
        }
        return paymentId != null ? paymentId : -1;
    }

    /**
     * Update the charge academic term for a payment with an audit trail entry (by receipt number).
     */
    public boolean updatePaymentChargeTerm(int receiptNumber, com.payment.ChargeAcademicTerm newTerm, String reason, String user) throws SQLException {
        int paymentId = findUniquePaymentIdByReceipt(receiptNumber);
        return paymentId >= 0 && updatePaymentChargeTermById(paymentId, newTerm, reason, user);
    }

    public boolean updatePaymentChargeTermById(int paymentId, ChargeAcademicTerm newTerm,
                                                String reason, String user) throws SQLException {
        Optional<Payment> existing = findPaymentById(paymentId);
        if (existing.isEmpty()) return false;
        String oldTerm = existing.get().getChargeAcademicTermCode();
        String termCode = newTerm != null ? newTerm.getCode() : ChargeAcademicTerm.DB_UNASSIGNED;

        String sql = "UPDATE payments SET charge_academic_term = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, termCode);
            stmt.setString(2, LocalDateTime.now().toString());
            stmt.setInt(3, paymentId);
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logAudit("UPDATE", "PAYMENT", "id:" + paymentId + " (rcpt:" + existing.get().getReceiptNumber() + ")",
                    "term:" + oldTerm, "term:" + termCode, reason != null ? reason : "Assigned academic term", user != null ? user : "user");
                return true;
            }
        }
        return false;
    }

    public boolean updatePaymentReceiptScope(int paymentId, String academicYear,
                                              ChargeAcademicTerm receiptTerm,
                                              String reason, String user) throws SQLException {
        ReceiptKey newKey = new ReceiptKey(1, academicYear, receiptTerm);
        if (receiptTerm == ChargeAcademicTerm.CURRENT || receiptTerm == ChargeAcademicTerm.PREVIOUS) {
            throw new IllegalArgumentException("Receipt semester must be 1st Semester, 2nd Semester, or Summer");
        }
        Optional<Payment> existing = findPaymentById(paymentId);
        if (existing.isEmpty()) return false;

        Payment oldPayment = existing.get();
        String sql = "UPDATE payments SET receipt_academic_year = ?, receipt_term = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, newKey.academicYear());
            stmt.setString(2, newKey.term().getCode());
            stmt.setString(3, LocalDateTime.now().toString());
            stmt.setInt(4, paymentId);
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logAudit("UPDATE", "PAYMENT", "id:" + paymentId + " (rcpt:" + oldPayment.getReceiptNumber() + ")",
                    "receipt-scope:" + oldPayment.getReceiptKey().displayScope(),
                    "receipt-scope:" + newKey.displayScope(),
                    reason != null ? reason : "Assigned receipt period", user != null ? user : "user");
                return true;
            }
        }
        return false;
    }

    /**
     * Update the charge academic term and academic year for a specific payment by payment ID.
     */
    public boolean updatePaymentTermAndYear(int paymentId, com.payment.ChargeAcademicTerm newTerm, String academicYear, String reason, String user) throws SQLException {
        Optional<Payment> existing = findPaymentById(paymentId);
        if (existing.isEmpty()) return false;
        String oldTerm = existing.get().getChargeAcademicTermCode();
        String oldYear = existing.get().getAcademicYear() != null ? existing.get().getAcademicYear() : "None";
        String termCode = newTerm != null ? newTerm.getCode() : com.payment.ChargeAcademicTerm.DB_UNASSIGNED;
        String yearVal = academicYear != null && !academicYear.trim().isEmpty() ? academicYear.trim() : null;

        String sql = "UPDATE payments SET charge_academic_term = ?, academic_year = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, termCode);
            stmt.setString(2, yearVal);
            stmt.setString(3, LocalDateTime.now().toString());
            stmt.setInt(4, paymentId);
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logAudit("UPDATE", "PAYMENT", "id:" + paymentId + " (rcpt:" + existing.get().getReceiptNumber() + ")",
                    "term:" + oldTerm + ", AY:" + oldYear, "term:" + termCode + ", AY:" + (yearVal != null ? yearVal : "None"),
                    reason != null ? reason : "Assigned term & academic year", user != null ? user : "user");
                return true;
            }
        }
        return false;
    }

    /**
     * Update individual fee category terms and academic years for a specific payment receipt.
     */
    public boolean updatePaymentItemTerms(int paymentId,
                                          com.payment.ChargeAcademicTerm defaultTerm, String defaultAy,
                                          com.payment.ChargeAcademicTerm intelTerm, String intelAy,
                                          com.payment.ChargeAcademicTerm tshirtTerm, String tshirtAy,
                                          com.payment.ChargeAcademicTerm penTerm, String penAy,
                                          com.payment.ChargeAcademicTerm citTerm, String citAy,
                                          String reason, String user) throws SQLException {
        Optional<Payment> existing = findPaymentById(paymentId);
        if (existing.isEmpty()) return false;

        String sql = "UPDATE payments SET " +
                     "charge_academic_term = ?, academic_year = ?, " +
                     "intel_fee_term = ?, intel_fee_ay = ?, " +
                     "tshirt_term = ?, tshirt_ay = ?, " +
                     "penalties_term = ?, penalties_ay = ?, " +
                     "cit_night_term = ?, cit_night_ay = ?, " +
                     "updated_at = ? WHERE id = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, defaultTerm != null ? defaultTerm.getCode() : com.payment.ChargeAcademicTerm.DB_UNASSIGNED);
            stmt.setString(2, defaultAy != null && !defaultAy.trim().isEmpty() ? defaultAy.trim() : null);

            stmt.setString(3, intelTerm != null ? intelTerm.getCode() : null);
            stmt.setString(4, intelAy != null && !intelAy.trim().isEmpty() ? intelAy.trim() : null);

            stmt.setString(5, tshirtTerm != null ? tshirtTerm.getCode() : null);
            stmt.setString(6, tshirtAy != null && !tshirtAy.trim().isEmpty() ? tshirtAy.trim() : null);

            stmt.setString(7, penTerm != null ? penTerm.getCode() : null);
            stmt.setString(8, penAy != null && !penAy.trim().isEmpty() ? penAy.trim() : null);

            stmt.setString(9, citTerm != null ? citTerm.getCode() : null);
            stmt.setString(10, citAy != null && !citAy.trim().isEmpty() ? citAy.trim() : null);

            stmt.setString(11, LocalDateTime.now().toString());
            stmt.setInt(12, paymentId);

            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logAudit("UPDATE", "PAYMENT_ITEMS", "id:" + paymentId + " (rcpt:" + existing.get().getReceiptNumber() + ")",
                    "previous_item_terms", "updated_item_terms",
                    reason != null ? reason : "Assigned itemized payment terms", user != null ? user : "user");
                return true;
            }
        }
        return false;
    }

    /**
     * Get distinct academic years present in payments.
     */
    public List<String> getDistinctAcademicYears() throws SQLException {
        String sql = "SELECT DISTINCT academic_year FROM payments WHERE academic_year IS NOT NULL AND TRIM(academic_year) != '' ORDER BY 1 DESC";
        List<String> list = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String y = rs.getString(1);
                if (y != null && !y.trim().isEmpty()) {
                    list.add(y.trim());
                }
            }
        }
        return list;
    }

    /**
     * Get distinct terms present in payments.
     */
    public List<String> getDistinctTerms() throws SQLException {
        String sql = "SELECT DISTINCT charge_academic_term FROM payments WHERE charge_academic_term IS NOT NULL AND TRIM(charge_academic_term) != '' ORDER BY 1";
        List<String> list = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String t = rs.getString(1);
                if (t != null && !t.trim().isEmpty()) {
                    list.add(t.trim());
                }
            }
        }
        return list;
    }

    // ==========================================
    // App Settings & Academic Period Declaration
    // ==========================================

    public String getAppSetting(String key, String defaultValue) throws SQLException {
        String sql = "SELECT value FROM app_settings WHERE key = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        }
        return defaultValue;
    }

    public void setAppSetting(String key, String value) throws SQLException {
        String sql = "INSERT INTO app_settings (key, value, updated_at) VALUES (?, ?, datetime('now')) " +
                     "ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        }
    }

    public String getCurrentAcademicYear() {
        try {
            return getAppSetting("current_academic_year", "2026-2027");
        } catch (SQLException e) {
            return "2026-2027";
        }
    }

    public void setCurrentAcademicYear(String ay) throws SQLException {
        setAppSetting("current_academic_year", ay != null ? ay.trim() : "2026-2027");
    }

    public ChargeAcademicTerm getCurrentAcademicTerm() {
        try {
            String val = getAppSetting("current_academic_term", ChargeAcademicTerm.FIRST_SEM.getLabel());
            return ChargeAcademicTerm.fromCode(val);
        } catch (Exception e) {
            return ChargeAcademicTerm.FIRST_SEM;
        }
    }

    public void setCurrentAcademicTerm(ChargeAcademicTerm term) throws SQLException {
        setAppSetting("current_academic_term", term != null ? term.getLabel() : ChargeAcademicTerm.FIRST_SEM.getLabel());
    }

    public boolean isAutoAssignCurrentTerm() {
        try {
            String val = getAppSetting("auto_assign_current_term", "true");
            return Boolean.parseBoolean(val);
        } catch (Exception e) {
            return true;
        }
    }

    public void setAutoAssignCurrentTerm(boolean auto) throws SQLException {
        setAppSetting("auto_assign_current_term", String.valueOf(auto));
    }

    // ==========================================
    // Student Merge & Undo Operations
    // ==========================================

    /**
     * Merge a source student (with typo or duplicate) into a target student.
     * All payments of the source student are transferred to the target student.
     * The source student is archived into the student_merges table and removed from students.
     * This action is completely undoable via undoStudentMerge().
     */
    public synchronized StudentMergeRecord mergeStudents(String sourceCode, String targetCode, String reason, String user) throws SQLException {
        if (sourceCode == null || targetCode == null || sourceCode.equalsIgnoreCase(targetCode)) {
            throw new IllegalArgumentException("Source and target student codes must be distinct and non-null.");
        }

        Optional<Student> optSource = findStudentByCode(sourceCode);
        Optional<Student> optTarget = findStudentByCode(targetCode);

        if (optSource.isEmpty()) {
            throw new IllegalArgumentException("Source student not found: " + sourceCode);
        }
        if (optTarget.isEmpty()) {
            throw new IllegalArgumentException("Target student not found: " + targetCode);
        }

        Student source = optSource.get();
        Student target = optTarget.get();

        List<Payment> sourcePayments = getPaymentsByStudent(sourceCode);
        List<Integer> receipts = sourcePayments.stream().map(Payment::getReceiptNumber).collect(Collectors.toList());
        List<Integer> paymentIds = sourcePayments.stream().map(Payment::getId).collect(Collectors.toList());

        String mergeCode = "MRG-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 900 + 100);
        LocalDateTime now = LocalDateTime.now();

        Connection conn = getConnection();
        boolean origAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            // 1. Reassign all payments from source to target
            String updatePaymentsSql = "UPDATE payments SET student_id = ?, name = ?, program = ?, updated_at = ? WHERE student_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updatePaymentsSql)) {
                stmt.setString(1, target.getStudentCode());
                stmt.setString(2, target.getName());
                stmt.setString(3, target.getProgram());
                stmt.setString(4, now.toString());
                stmt.setString(5, source.getStudentCode());
                stmt.executeUpdate();
            }

            // 2. Delete source student from students table
            String deleteStudentSql = "DELETE FROM students WHERE student_code = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteStudentSql)) {
                stmt.setString(1, source.getStudentCode());
                stmt.executeUpdate();
            }

            // 3. Record merge into student_merges table
            String insertMergeSql = "INSERT INTO student_merges (" +
                "merge_code, source_student_code, source_name, source_normalized_name, " +
                "source_program, source_year_level, source_created_at, source_updated_at, " +
                "target_student_code, target_name, payment_receipts, payment_ids, merged_at, merged_by, reason, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')";
            try (PreparedStatement stmt = conn.prepareStatement(insertMergeSql)) {
                stmt.setString(1, mergeCode);
                stmt.setString(2, source.getStudentCode());
                stmt.setString(3, source.getName());
                stmt.setString(4, source.getNormalizedName());
                stmt.setString(5, source.getProgram());
                if (source.getYearLevel() != null) stmt.setInt(6, source.getYearLevel());
                else stmt.setNull(6, Types.INTEGER);
                stmt.setString(7, source.getCreatedAt() != null ? source.getCreatedAt().toString() : now.toString());
                stmt.setString(8, source.getUpdatedAt() != null ? source.getUpdatedAt().toString() : now.toString());
                stmt.setString(9, target.getStudentCode());
                stmt.setString(10, target.getName());
                stmt.setString(11, receipts.stream().map(String::valueOf).collect(Collectors.joining(",")));
                stmt.setString(12, paymentIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
                stmt.setString(13, now.toString());
                stmt.setString(14, user != null ? user : "user");
                stmt.setString(15, reason != null ? reason : "Merged typo/duplicate student");
                stmt.executeUpdate();
            }

            conn.commit();

            // Log in audit trail
            logAudit("MERGE", "STUDENT", source.getStudentCode(),
                source.getName() + " (" + receipts.size() + " payments)",
                "Merged into " + target.getStudentCode() + " (" + target.getName() + ")",
                reason != null ? reason : "Merged typo/duplicate student records",
                user != null ? user : "user");

            StudentMergeRecord record = new StudentMergeRecord();
            record.setMergeCode(mergeCode);
            record.setSourceStudentCode(source.getStudentCode());
            record.setSourceName(source.getName());
            record.setSourceNormalizedName(source.getNormalizedName());
            record.setSourceProgram(source.getProgram());
            record.setSourceYearLevel(source.getYearLevel());
            record.setSourceCreatedAt(source.getCreatedAt());
            record.setSourceUpdatedAt(source.getUpdatedAt());
            record.setTargetStudentCode(target.getStudentCode());
            record.setTargetName(target.getName());
            record.setReceiptNumbersList(receipts);
            record.setPaymentIdsList(paymentIds);
            record.setMergedAt(now);
            record.setMergedBy(user);
            record.setReason(reason);
            record.setStatus("ACTIVE");

            return record;
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(origAutoCommit);
        }
    }

    /**
     * Undo a previous student merge operation.
     * Restores the source student into the students table and reverts all reassigned payments back to the source.
     */
    public synchronized boolean undoStudentMerge(String mergeCode, String user) throws SQLException {
        String sql = "SELECT * FROM student_merges WHERE merge_code = ? AND status = 'ACTIVE'";
        StudentMergeRecord record = null;
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, mergeCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    record = mapStudentMerge(rs);
                }
            }
        }

        if (record == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        Connection conn = getConnection();
        boolean origAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            // 1. Re-insert the source student
            String insertStudentSql = "INSERT OR REPLACE INTO students (" +
                "student_code, name, normalized_name, program, year_level, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertStudentSql)) {
                stmt.setString(1, record.getSourceStudentCode());
                stmt.setString(2, record.getSourceName());
                stmt.setString(3, record.getSourceNormalizedName());
                stmt.setString(4, record.getSourceProgram());
                if (record.getSourceYearLevel() != null) stmt.setInt(5, record.getSourceYearLevel());
                else stmt.setNull(5, Types.INTEGER);
                stmt.setString(6, record.getSourceCreatedAt() != null ? record.getSourceCreatedAt().toString() : now.toString());
                stmt.setString(7, now.toString());
                stmt.executeUpdate();
            }

            // 2. Revert transferred payments back to source student
            List<Integer> paymentIds = record.getPaymentIdsList();
            List<Integer> receipts = record.getReceiptNumbersList();
            List<Integer> identifiers = !paymentIds.isEmpty() ? paymentIds : receipts;
            if (!identifiers.isEmpty()) {
                String inClause = identifiers.stream().map(r -> "?").collect(Collectors.joining(","));
                boolean usesStableIds = !paymentIds.isEmpty();
                String identityColumn = usesStableIds ? "id" : "receipt_number";
                String revertPaymentsSql = "UPDATE payments SET student_id = ?, name = ?, program = ?, updated_at = ? WHERE " +
                    identityColumn + " IN (" + inClause + ") AND student_id = ?" +
                    (usesStableIds ? "" : " AND updated_at = ?");
                try (PreparedStatement stmt = conn.prepareStatement(revertPaymentsSql)) {
                    stmt.setString(1, record.getSourceStudentCode());
                    stmt.setString(2, record.getSourceName());
                    stmt.setString(3, record.getSourceProgram());
                    stmt.setString(4, now.toString());
                    int idx = 5;
                    for (Integer identifier : identifiers) {
                        stmt.setInt(idx++, identifier);
                    }
                    stmt.setString(idx++, record.getTargetStudentCode());
                    if (!usesStableIds) {
                        // Legacy merge rows did not retain payment IDs. The merge
                        // timestamp distinguishes transferred rows from a target
                        // student's own reused receipt number.
                        stmt.setString(idx, record.getMergedAt().toString());
                    }
                    stmt.executeUpdate();
                }
            }

            // 3. Mark merge record as REVERTED
            String updateMergeSql = "UPDATE student_merges SET status = 'REVERTED' WHERE merge_code = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateMergeSql)) {
                stmt.setString(1, mergeCode);
                stmt.executeUpdate();
            }

            conn.commit();

            // Log in audit trail
            logAudit("UNDO_MERGE", "STUDENT", record.getSourceStudentCode(),
                "Merged into " + record.getTargetStudentCode(),
                "Restored " + record.getSourceStudentCode() + " (" + record.getSourceName() + ") with " + identifiers.size() + " payments",
                "Reverted student merge " + mergeCode,
                user != null ? user : "user");

            return true;
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(origAutoCommit);
        }
    }

    /**
     * Get all student merge records ordered by date descending.
     */
    public List<StudentMergeRecord> getAllStudentMerges() throws SQLException {
        String sql = "SELECT * FROM student_merges ORDER BY merged_at DESC";
        List<StudentMergeRecord> list = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapStudentMerge(rs));
            }
        }
        return list;
    }

    /**
     * Get only active (non-reverted) student merge records.
     */
    public List<StudentMergeRecord> getActiveStudentMerges() throws SQLException {
        String sql = "SELECT * FROM student_merges WHERE status = 'ACTIVE' ORDER BY merged_at DESC";
        List<StudentMergeRecord> list = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapStudentMerge(rs));
            }
        }
        return list;
    }

    private StudentMergeRecord mapStudentMerge(ResultSet rs) throws SQLException {
        StudentMergeRecord r = new StudentMergeRecord();
        r.setId(rs.getInt("id"));
        r.setMergeCode(rs.getString("merge_code"));
        r.setSourceStudentCode(rs.getString("source_student_code"));
        r.setSourceName(rs.getString("source_name"));
        r.setSourceNormalizedName(rs.getString("source_normalized_name"));
        r.setSourceProgram(rs.getString("source_program"));
        int yl = rs.getInt("source_year_level");
        if (!rs.wasNull()) r.setSourceYearLevel(yl);
        String sCreated = rs.getString("source_created_at");
        if (sCreated != null) r.setSourceCreatedAt(LocalDateTime.parse(sCreated));
        String sUpdated = rs.getString("source_updated_at");
        if (sUpdated != null) r.setSourceUpdatedAt(LocalDateTime.parse(sUpdated));
        r.setTargetStudentCode(rs.getString("target_student_code"));
        r.setTargetName(rs.getString("target_name"));
        r.setPaymentReceipts(rs.getString("payment_receipts"));
        try {
            r.setPaymentIds(rs.getString("payment_ids"));
        } catch (SQLException ignored) {}
        r.setMergedAt(LocalDateTime.parse(rs.getString("merged_at")));
        r.setMergedBy(rs.getString("merged_by"));
        r.setReason(rs.getString("reason"));
        r.setStatus(rs.getString("status"));
        return r;
    }
}
