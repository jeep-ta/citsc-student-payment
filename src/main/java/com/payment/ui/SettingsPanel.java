package com.payment.ui;

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
 * Settings Panel - Application configuration and maintenance.
 */
public class SettingsPanel extends JPanel {

    private final DatabaseManager db;

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
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(Color.WHITE);

        // Title
        JLabel titleLabel = new JLabel("Settings");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        add(titleLabel, BorderLayout.NORTH);

        // Scrollable content
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(Color.WHITE);

        // General Settings
        contentPanel.add(createSection("General Settings", createGeneralSettings()));
        contentPanel.add(Box.createVerticalStrut(20));

        // Import Settings
        contentPanel.add(createSection("Import Settings", createImportSettings()));
        contentPanel.add(Box.createVerticalStrut(20));

        // Database Info
        contentPanel.add(createSection("Database Information", createDatabaseInfo()));
        contentPanel.add(Box.createVerticalStrut(20));

        // Maintenance
        contentPanel.add(createSection("Maintenance", createMaintenancePanel()));
        contentPanel.add(Box.createVerticalStrut(20));

        // About
        contentPanel.add(createSection("About", createAboutPanel()));

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(Color.WHITE);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createSection(String title, JComponent content) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(15));

        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(content);

        return panel;
    }

    private JPanel createGeneralSettings() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Theme
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("UI Theme:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        themeCombo = new JComboBox<>(new String[]{"System Default", "Light", "Dark"});
        themeCombo.setPreferredSize(new Dimension(200, 28));
        panel.add(themeCombo, gbc);

        return panel;
    }

    private JPanel createImportSettings() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Default file
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Default Excel File:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        defaultFilePathField = new JTextField(30);
        defaultFilePathField.setEditable(false);
        panel.add(defaultFilePathField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseDefaultFile());
        panel.add(browseButton, gbc);

        // Auto load
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Auto-load on Startup:"), gbc);
        gbc.gridx = 1;
        autoLoadDefaultFile = new JCheckBox("Load default file automatically");
        panel.add(autoLoadDefaultFile, gbc);

        // Max preview rows
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Max Preview Rows:"), gbc);
        gbc.gridx = 1;
        maxPreviewRowsSpinner = new JSpinner(new SpinnerNumberModel(1000, 100, 10000, 100));
        maxPreviewRowsSpinner.setPreferredSize(new Dimension(100, 28));
        panel.add(maxPreviewRowsSpinner, gbc);

        return panel;
    }

    private JPanel createDatabaseInfo() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // DB Path
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Database File:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        dbPathLabel = new JLabel("-");
        dbPathLabel.setFont(dbPathLabel.getFont().deriveFont(Font.PLAIN, 12f));
        dbPathLabel.setForeground(new Color(80, 80, 80));
        panel.add(dbPathLabel, gbc);

        // DB Size
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Database Size:"), gbc);
        gbc.gridx = 1;
        dbSizeLabel = new JLabel("-");
        panel.add(dbSizeLabel, gbc);

        // Student count
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Students:"), gbc);
        gbc.gridx = 1;
        studentCountLabel = new JLabel("-");
        panel.add(studentCountLabel, gbc);

        // Payment count
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Payments:"), gbc);
        gbc.gridx = 1;
        paymentCountLabel = new JLabel("-");
        panel.add(paymentCountLabel, gbc);

        // Last migration
        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(new JLabel("Last Migration:"), gbc);
        gbc.gridx = 1;
        lastMigrationLabel = new JLabel("-");
        panel.add(lastMigrationLabel, gbc);

        // Refresh button
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        JButton refreshButton = new JButton("Refresh Database Info");
        refreshButton.addActionListener(e -> refreshDatabaseInfo());
        panel.add(refreshButton, gbc);

        return panel;
    }

    private JPanel createMaintenancePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        panel.setBackground(Color.WHITE);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton vacuumButton = new JButton("Vacuum Database");
        vacuumButton.setToolTipText("Reclaim unused space and defragment database");
        vacuumButton.addActionListener(e -> vacuumDatabase());
        panel.add(vacuumButton);

        JButton exportButton = new JButton("Export All Data (CSV)");
        exportButton.setToolTipText("Export all students and payments to CSV files");
        exportButton.addActionListener(e -> exportAllData());
        panel.add(exportButton);

        JButton backupButton = new JButton("Create Backup");
        backupButton.setToolTipText("Create a backup copy of the database");
        backupButton.addActionListener(e -> createBackup());
        panel.add(backupButton);

        return panel;
    }

    private JPanel createAboutPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 0);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        JLabel appName = new JLabel("Student Payment Database");
        appName.setFont(appName.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(appName, gbc);

        gbc.gridy = 1;
        JLabel version = new JLabel("Version 2.0.0 (SQLite Edition)");
        version.setForeground(new Color(100, 100, 100));
        panel.add(version, gbc);

        gbc.gridy = 2;
        JLabel desc = new JLabel("<html>Java Swing application for managing student payment records.<br>Uses SQLite with audit logging and import validation.</html>");
        panel.add(desc, gbc);

        gbc.gridy = 3;
        JLabel tech = new JLabel("Java 17 • Maven • SQLite • Apache POI • Gson");
        tech.setForeground(new Color(120, 120, 120));
        tech.setFont(tech.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(tech, gbc);

        gbc.gridy = 4;
        JLabel build = new JLabel("Built: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        build.setForeground(new Color(150, 150, 150));
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
                    writer.println("Receipt Number,Student ID,Name,Program,Intel Fee,T-Shirt,Penalties,CIT Night,Received By,Remarks,Remittance Date,Status,Created At,Updated At");
                    List<Payment> payments = db.getAllPayments();
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    for (Payment p : payments) {
                        writer.printf("%d,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s,%s,%s,%s,%s,%s%n",
                            p.getReceiptNumber(),
                            escapeCsv(p.getStudentId()),
                            escapeCsv(p.getName()),
                            escapeCsv(p.getProgram()),
                            p.getIntelFee() != null ? p.getIntelFee() : 0,
                            p.getTshirtSizing() != null ? p.getTshirtSizing() : 0,
                            p.getPenalties() != null ? p.getPenalties() : 0,
                            p.getCitNight() != null ? p.getCitNight() : 0,
                            escapeCsv(p.getReceivedBy()),
                            escapeCsv(p.getRemarks()),
                            p.getRemittanceDate() != null ? p.getRemittanceDate().format(fmt) : "",
                            escapeCsv(p.getStatus()),
                            p.getCreatedAt(),
                            p.getUpdatedAt()
                        );
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