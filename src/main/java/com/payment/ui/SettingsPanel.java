package com.payment.ui;

import com.payment.ChargeAcademicTerm;
import com.payment.Payment;
import com.payment.Student;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Settings Panel - Application configuration, academic period declaration, and maintenance.
 * Styled with the cyber-dark theme for visual consistency.
 */
public class SettingsPanel extends JPanel {

    private final DatabaseManager db;

    // Academic Period Declaration
    private JComboBox<String> currentAyCombo;
    private JComboBox<String> currentTermCombo;
    private JCheckBox autoAssignTermCheck;
    private JLabel currentPeriodStatusLabel;

    // General & Import Settings
    private JCheckBox autoLoadDefaultFile;
    private JTextField defaultFilePathField;
    private JSpinner maxPreviewRowsSpinner;
    private JComboBox<String> themeCombo;
    private JLabel dbPathLabel;
    private JLabel dbSizeLabel;
    private JLabel lastMigrationLabel;
    private JLabel studentCountLabel;
    private JLabel paymentCountLabel;

    public SettingsPanel() {
        this.db = DatabaseManager.getInstance();
        initializeUI();
        loadSettings();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(ThemeUtils.BG_DEEPEST);

        // Header with futuristic gradient banner
        JPanel headerPanel = new JPanel(new BorderLayout(0, 10));
        headerPanel.setOpaque(false);

        JPanel titleBanner = ThemeUtils.createGradientPanel(new Color(15, 23, 42), new Color(10, 14, 26));
        titleBanner.setLayout(new BorderLayout());
        titleBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, ThemeUtils.NEON_CYAN),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLabel = new JLabel("⚙️ Application Settings & Academic Declaration");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        titleLabel.setForeground(ThemeUtils.NEON_CYAN);
        titleBanner.add(titleLabel, BorderLayout.WEST);

        JLabel subtitleLabel = new JLabel("Active Period, Term Configuration & Maintenance");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        subtitleLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        titleBanner.add(subtitleLabel, BorderLayout.EAST);

        headerPanel.add(titleBanner, BorderLayout.NORTH);
        add(headerPanel, BorderLayout.NORTH);

        // Scrollable content
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ThemeUtils.BG_DEEPEST);

        // 1. Academic Period & Current Term Declaration (Top Priority)
        contentPanel.add(createSection("🎓 Academic Period & Current Term Declaration", ThemeUtils.NEON_CYAN, createAcademicPeriodSettings()));
        contentPanel.add(Box.createVerticalStrut(16));

        // 2. General Settings
        contentPanel.add(createSection("General Settings", ThemeUtils.NEON_PURPLE, createGeneralSettings()));
        contentPanel.add(Box.createVerticalStrut(16));

        // 3. Import Settings
        contentPanel.add(createSection("Import Settings", ThemeUtils.NEON_GREEN, createImportSettings()));
        contentPanel.add(Box.createVerticalStrut(16));

        // 4. Database Info
        contentPanel.add(createSection("Database Information", ThemeUtils.NEON_AMBER, createDatabaseInfo()));
        contentPanel.add(Box.createVerticalStrut(16));

        // 5. Maintenance
        contentPanel.add(createSection("Maintenance", ThemeUtils.NEON_ROSE, createMaintenancePanel()));
        contentPanel.add(Box.createVerticalStrut(16));

        // 6. About
        contentPanel.add(createSection("About", ThemeUtils.NEON_BLUE, createAboutPanel()));

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(ThemeUtils.BG_DEEPEST);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createSection(String title, Color accentColor, JComponent content) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ThemeUtils.BG_CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(0, 0, 15, 0)
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // Section header with accent glow line
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setBackground(ThemeUtils.BG_CARD);
        headerBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(2, 0, 0, 0, accentColor),
            BorderFactory.createEmptyBorder(12, 15, 8, 15)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(accentColor);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerBar.add(titleLabel, BorderLayout.WEST);

        panel.add(headerBar);

        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.setBackground(ThemeUtils.BG_CARD);
        contentWrapper.setBorder(BorderFactory.createEmptyBorder(5, 15, 0, 15));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentWrapper.add(content, BorderLayout.CENTER);
        panel.add(contentWrapper);

        return panel;
    }

    private JPanel createAcademicPeriodSettings() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ThemeUtils.BG_CARD);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Current Academic Year
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel ayLabel = new JLabel("Current Academic Year:");
        ayLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(ayLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        currentAyCombo = new JComboBox<>(new String[]{"2026-2027", "2025-2026", "2024-2025", "2027-2028"});
        currentAyCombo.setEditable(true);
        currentAyCombo.setPreferredSize(new Dimension(200, 28));
        panel.add(currentAyCombo, gbc);

        // Current Active Term
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel termLabel = new JLabel("Active Academic Term:");
        termLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(termLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        currentTermCombo = new JComboBox<>(new String[]{
            ChargeAcademicTerm.FIRST_SEM.getLabel(),
            ChargeAcademicTerm.SECOND_SEM.getLabel(),
            ChargeAcademicTerm.SUMMER.getLabel(),
            ChargeAcademicTerm.CURRENT.getLabel(),
            ChargeAcademicTerm.PREVIOUS.getLabel()
        });
        currentTermCombo.setPreferredSize(new Dimension(200, 28));
        panel.add(currentTermCombo, gbc);

        // Auto-assign Checkbox
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        autoAssignTermCheck = new JCheckBox("Automatically assign new & imported payments to this academic term & year");
        autoAssignTermCheck.setBackground(ThemeUtils.BG_CARD);
        autoAssignTermCheck.setForeground(ThemeUtils.TEXT_PRIMARY);
        autoAssignTermCheck.setFont(autoAssignTermCheck.getFont().deriveFont(Font.BOLD, 12f));
        panel.add(autoAssignTermCheck, gbc);

        // Status / hint label
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        currentPeriodStatusLabel = new JLabel("Declared active period will be automatically populated on new receipts & imports.");
        currentPeriodStatusLabel.setFont(currentPeriodStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        currentPeriodStatusLabel.setForeground(ThemeUtils.TEXT_MUTED);
        panel.add(currentPeriodStatusLabel, gbc);

        // Save Button
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        JButton savePeriodBtn = new JButton("💾 Save Academic Declaration");
        ThemeUtils.styleButton(savePeriodBtn, ThemeUtils.NEON_CYAN);
        savePeriodBtn.addActionListener(e -> saveAcademicPeriodSettings());
        panel.add(savePeriodBtn, gbc);

        return panel;
    }

    private void saveAcademicPeriodSettings() {
        String ay = (String) currentAyCombo.getSelectedItem();
        String termStr = (String) currentTermCombo.getSelectedItem();
        boolean autoAssign = autoAssignTermCheck.isSelected();

        if (ay == null || ay.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please specify an academic year.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            db.setCurrentAcademicYear(ay.trim());
            db.setCurrentAcademicTerm(ChargeAcademicTerm.fromCode(termStr));
            db.setAutoAssignCurrentTerm(autoAssign);

            currentPeriodStatusLabel.setText(String.format("✅ Active: %s (%s) • Auto-assign %s",
                ay.trim(), termStr, autoAssign ? "ENABLED" : "DISABLED"));
            currentPeriodStatusLabel.setForeground(ThemeUtils.NEON_GREEN);

            JOptionPane.showMessageDialog(this,
                String.format("Academic Period Updated:\n\n• Academic Year: %s\n• Active Term: %s\n• Auto-Assign: %s\n\nNew payments & imports will use this period.",
                    ay.trim(), termStr, autoAssign ? "YES" : "NO"),
                "Academic Period Declared", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error saving academic period: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private JPanel createGeneralSettings() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ThemeUtils.BG_CARD);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Theme
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel themeLabel = new JLabel("UI Theme:");
        themeLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(themeLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        themeCombo = new JComboBox<>(new String[]{"System Default", "Light", "Dark"});
        themeCombo.setPreferredSize(new Dimension(200, 28));
        panel.add(themeCombo, gbc);

        return panel;
    }

    private JPanel createImportSettings() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ThemeUtils.BG_CARD);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Default file
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel fileLabel = new JLabel("Default Excel File:");
        fileLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(fileLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        defaultFilePathField = new JTextField(30);
        defaultFilePathField.setEditable(false);
        panel.add(defaultFilePathField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JButton browseButton = new JButton("Browse...");
        ThemeUtils.styleButton(browseButton, ThemeUtils.NEON_CYAN);
        browseButton.addActionListener(e -> browseDefaultFile());
        panel.add(browseButton, gbc);

        // Auto load
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel autoLoadLabel = new JLabel("Auto-load on Startup:");
        autoLoadLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(autoLoadLabel, gbc);
        gbc.gridx = 1;
        autoLoadDefaultFile = new JCheckBox("Load default file automatically");
        autoLoadDefaultFile.setBackground(ThemeUtils.BG_CARD);
        autoLoadDefaultFile.setForeground(ThemeUtils.TEXT_PRIMARY);
        panel.add(autoLoadDefaultFile, gbc);

        // Max preview rows
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel maxRowsLabel = new JLabel("Max Preview Rows:");
        maxRowsLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(maxRowsLabel, gbc);
        gbc.gridx = 1;
        maxPreviewRowsSpinner = new JSpinner(new SpinnerNumberModel(1000, 100, 10000, 100));
        maxPreviewRowsSpinner.setPreferredSize(new Dimension(100, 28));
        panel.add(maxPreviewRowsSpinner, gbc);

        return panel;
    }

    private JPanel createDatabaseInfo() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ThemeUtils.BG_CARD);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // DB Path
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel pathLabel = new JLabel("Database File:");
        pathLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(pathLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        dbPathLabel = new JLabel("-");
        dbPathLabel.setFont(dbPathLabel.getFont().deriveFont(Font.PLAIN, 12f));
        dbPathLabel.setForeground(ThemeUtils.TEXT_MUTED);
        panel.add(dbPathLabel, gbc);

        // DB Size
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel sizeLabel = new JLabel("Database Size:");
        sizeLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(sizeLabel, gbc);
        gbc.gridx = 1;
        dbSizeLabel = new JLabel("-");
        dbSizeLabel.setForeground(ThemeUtils.TEXT_PRIMARY);
        panel.add(dbSizeLabel, gbc);

        // Student count
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel studentsLabel = new JLabel("Students:");
        studentsLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(studentsLabel, gbc);
        gbc.gridx = 1;
        studentCountLabel = new JLabel("-");
        studentCountLabel.setForeground(ThemeUtils.NEON_CYAN);
        studentCountLabel.setFont(studentCountLabel.getFont().deriveFont(Font.BOLD));
        panel.add(studentCountLabel, gbc);

        // Payment count
        gbc.gridx = 0; gbc.gridy = 3;
        JLabel paymentsLabel = new JLabel("Payments:");
        paymentsLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(paymentsLabel, gbc);
        gbc.gridx = 1;
        paymentCountLabel = new JLabel("-");
        paymentCountLabel.setForeground(ThemeUtils.NEON_GREEN);
        paymentCountLabel.setFont(paymentCountLabel.getFont().deriveFont(Font.BOLD));
        panel.add(paymentCountLabel, gbc);

        // Last migration
        gbc.gridx = 0; gbc.gridy = 4;
        JLabel migLabel = new JLabel("Last Migration:");
        migLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(migLabel, gbc);
        gbc.gridx = 1;
        lastMigrationLabel = new JLabel("-");
        lastMigrationLabel.setForeground(ThemeUtils.TEXT_MUTED);
        panel.add(lastMigrationLabel, gbc);

        // Refresh button
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        JButton refreshButton = new JButton("🔄 Refresh Database Info");
        ThemeUtils.styleButton(refreshButton, ThemeUtils.NEON_CYAN);
        refreshButton.addActionListener(e -> refreshDatabaseInfo());
        panel.add(refreshButton, gbc);

        return panel;
    }

    private JPanel createMaintenancePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        panel.setBackground(ThemeUtils.BG_CARD);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton vacuumButton = new JButton("🗜️ Vacuum Database");
        vacuumButton.setToolTipText("Reclaim unused space and defragment database");
        ThemeUtils.styleButton(vacuumButton, ThemeUtils.NEON_AMBER);
        vacuumButton.addActionListener(e -> vacuumDatabase());
        panel.add(vacuumButton);

        JButton exportButton = new JButton("📊 Export All Data (CSV)");
        exportButton.setToolTipText("Export all students and payments to CSV files");
        ThemeUtils.styleButton(exportButton, ThemeUtils.NEON_GREEN);
        exportButton.addActionListener(e -> exportAllData());
        panel.add(exportButton);

        JButton backupButton = new JButton("💾 Create Backup");
        backupButton.setToolTipText("Create a backup copy of the database");
        ThemeUtils.styleButton(backupButton, ThemeUtils.NEON_PURPLE);
        backupButton.addActionListener(e -> createBackup());
        panel.add(backupButton);

        return panel;
    }

    private JPanel createAboutPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ThemeUtils.BG_CARD);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 0);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        JLabel appName = new JLabel("Student Payment Database");
        appName.setFont(appName.getFont().deriveFont(Font.BOLD, 16f));
        appName.setForeground(ThemeUtils.TEXT_PRIMARY);
        panel.add(appName, gbc);

        gbc.gridy = 1;
        JLabel version = new JLabel("Version 2.0.0 (SQLite Edition)");
        version.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(version, gbc);

        gbc.gridy = 2;
        JLabel desc = new JLabel("<html>Java Swing application for managing student payment records.<br>Uses SQLite with audit logging and import validation.</html>");
        desc.setForeground(ThemeUtils.TEXT_PRIMARY);
        panel.add(desc, gbc);

        gbc.gridy = 3;
        JLabel tech = new JLabel("Java 17 • Maven • SQLite • Apache POI • Gson");
        tech.setForeground(ThemeUtils.TEXT_MUTED);
        tech.setFont(tech.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(tech, gbc);

        gbc.gridy = 4;
        JLabel build = new JLabel("Built: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        build.setForeground(ThemeUtils.TEXT_MUTED);
        build.setFont(build.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(build, gbc);

        return panel;
    }

    private void browseDefaultFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel Files (*.xlsx)", "xlsx"));
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            defaultFilePathField.setText(file.getAbsolutePath());
        }
    }

    private void loadSettings() {
        // Load database info
        refreshDatabaseInfo();

        // Load academic period declaration
        String currentAy = db.getCurrentAcademicYear();
        ChargeAcademicTerm currentTerm = db.getCurrentAcademicTerm();
        boolean autoAssign = db.isAutoAssignCurrentTerm();

        if (currentAyCombo != null) {
            currentAyCombo.setSelectedItem(currentAy);
        }
        if (currentTermCombo != null) {
            currentTermCombo.setSelectedItem(currentTerm != null ? currentTerm.getLabel() : ChargeAcademicTerm.FIRST_SEM.getLabel());
        }
        if (autoAssignTermCheck != null) {
            autoAssignTermCheck.setSelected(autoAssign);
        }
        if (currentPeriodStatusLabel != null) {
            currentPeriodStatusLabel.setText(String.format("Active: %s (%s) • Auto-assign %s",
                currentAy, currentTerm != null ? currentTerm.getLabel() : "1st Sem", autoAssign ? "ENABLED" : "DISABLED"));
        }

        // Load default file path from DataManager
        String defaultFile = "Payment Import Jul 28, 2026.xlsx";
        File file = new File(defaultFile);
        if (file.exists()) {
            defaultFilePathField.setText(file.getAbsolutePath());
            autoLoadDefaultFile.setSelected(true);
        }
    }

    public void refreshDatabaseInfo() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private String dbPath;
            private long dbSize;
            private int students;
            private int payments;

            @Override
            protected Void doInBackground() throws Exception {
                File dbFile = new File("student_payment.db");
                dbPath = dbFile.getAbsolutePath();
                dbSize = dbFile.length();
                students = db.getStudentCount();
                payments = db.getPaymentCount();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    dbPathLabel.setText(dbPath);
                    dbSizeLabel.setText(formatFileSize(dbSize));
                    studentCountLabel.setText(String.valueOf(students));
                    paymentCountLabel.setText(String.valueOf(payments));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void vacuumDatabase() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "This will run VACUUM on the database to reclaim space and defragment.\n" +
            "The database will be temporarily locked.\n\n" +
            "Continue?",
            "Vacuum Database", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try (var conn = db.getConnection();
                     var stmt = conn.createStatement()) {
                    stmt.execute("VACUUM");
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(SettingsPanel.this, "Database vacuum completed successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    refreshDatabaseInfo();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SettingsPanel.this, "Vacuum failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void exportAllData() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Export Directory");

        int result = fileChooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File exportDir = fileChooser.getSelectedFile();

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Export students
                File studentFile = new File(exportDir, "students_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
                try (var writer = new java.io.PrintWriter(studentFile)) {
                    writer.println("Student Code,Name,Normalized Name,Program,Year Level,Created At,Updated At");
                    List<Student> students = db.getAllStudents();
                    for (Student s : students) {
                        writer.printf("%s,%s,%s,%s,%s,%s,%s%n",
                            s.getStudentCode(),
                            escapeCsv(s.getName()),
                            escapeCsv(s.getNormalizedName()),
                            escapeCsv(s.getProgram()),
                            s.getYearLevel() != null ? s.getYearLevel() : "",
                            s.getCreatedAt(),
                            s.getUpdatedAt()
                        );
                    }
                }

                // Export payments
                File paymentFile = new File(exportDir, "payments_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
                try (var writer = new java.io.PrintWriter(paymentFile)) {
                    writer.println("Receipt Number,Student ID,Name,Program,Intel Fee,T-Shirt,Penalties,CIT Night,Received By,Remarks,Remittance Date,Receipt Academic Year,Receipt Term,Charge Academic Year,Charge Term,Import Batch,Source File,Source Row,Status,Created At,Updated At");
                    List<Payment> payments = db.getAllPayments();
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    for (Payment p : payments) {
                        writer.println(String.join(",", new String[]{
                            String.valueOf(p.getReceiptNumber()),
                            escapeCsv(p.getStudentId()),
                            escapeCsv(p.getName()),
                            escapeCsv(p.getProgram()),
                            String.format(java.util.Locale.ROOT, "%.2f", p.getIntelFee() != null ? p.getIntelFee() : 0),
                            String.format(java.util.Locale.ROOT, "%.2f", p.getTshirtSizing() != null ? p.getTshirtSizing() : 0),
                            String.format(java.util.Locale.ROOT, "%.2f", p.getPenalties() != null ? p.getPenalties() : 0),
                            String.format(java.util.Locale.ROOT, "%.2f", p.getCitNight() != null ? p.getCitNight() : 0),
                            escapeCsv(p.getReceivedBy()),
                            escapeCsv(p.getRemarks()),
                            p.getRemittanceDate() != null ? p.getRemittanceDate().format(fmt) : "",
                            escapeCsv(p.getReceiptAcademicYear()),
                            escapeCsv(p.getReceiptTermCode()),
                            escapeCsv(p.getAcademicYear()),
                            escapeCsv(p.getChargeAcademicTermCode()),
                            escapeCsv(p.getImportBatchCode()),
                            escapeCsv(p.getImportSourceFile()),
                            p.getImportSourceRow() != null ? String.valueOf(p.getImportSourceRow()) : "",
                            escapeCsv(p.getStatus()),
                            String.valueOf(p.getCreatedAt()),
                            String.valueOf(p.getUpdatedAt())
                        }));
                    }
                }

                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                        "Data exported successfully to:\n" + exportDir.getAbsolutePath(),
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SettingsPanel.this, "Export failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void createBackup() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SQLite Database (*.db)", "db"));
        fileChooser.setSelectedFile(new File("student_payment_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".db"));

        int result = fileChooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File backupFile = fileChooser.getSelectedFile();

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                File sourceFile = new File("student_payment.db");
                java.nio.file.Files.copy(sourceFile.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                        "Backup created: " + backupFile.getName(),
                        "Backup Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SettingsPanel.this, "Backup failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }
}
