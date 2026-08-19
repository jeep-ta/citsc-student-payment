package com.payment.ui;

import com.payment.Payment;
import com.payment.Student;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Data Quality Panel - Detect and review data quality issues.
 */
public class DataQualityPanel extends JPanel {

    private final DatabaseManager db;

    private JTable issuesTable;
    private IssuesTableModel issuesTableModel;
    private JLabel statusLabel;

    public DataQualityPanel() {
        this.db = DatabaseManager.getInstance();
        initializeUI();
        scanForIssues();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(Color.WHITE);

        // Title
        JLabel titleLabel = new JLabel("Data Quality");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        add(titleLabel, BorderLayout.NORTH);

        // Toolbar
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        toolBar.setBackground(Color.WHITE);
        toolBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JButton scanButton = new JButton("Scan for Issues");
        scanButton.addActionListener(e -> scanForIssues());
        toolBar.add(scanButton);

        JButton exportButton = new JButton("Export Issues");
        exportButton.addActionListener(e -> exportIssues());
        toolBar.add(exportButton);

        add(toolBar, BorderLayout.NORTH);

        // Issues table
        issuesTableModel = new IssuesTableModel();
        issuesTable = new JTable(issuesTableModel);
        issuesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        issuesTable.setRowHeight(28);
        issuesTable.setShowGrid(false);
        issuesTable.setIntercellSpacing(new Dimension(0, 1));
        issuesTable.getTableHeader().setReorderingAllowed(false);
        issuesTable.getTableHeader().setBackground(new Color(245, 245, 245));
        issuesTable.getTableHeader().setFont(issuesTable.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        issuesTable.setFont(issuesTable.getFont().deriveFont(Font.PLAIN, 12f));
        issuesTable.setAutoCreateRowSorter(true);

        // Column widths
        issuesTable.getColumnModel().getColumn(0).setPreferredWidth(80);   // Severity
        issuesTable.getColumnModel().getColumn(1).setPreferredWidth(200);  // Issue Type
        issuesTable.getColumnModel().getColumn(2).setPreferredWidth(120);  // Entity
        issuesTable.getColumnModel().getColumn(3).setPreferredWidth(100);  // Entity ID
        issuesTable.getColumnModel().getColumn(4).setPreferredWidth(400);  // Description
        issuesTable.getColumnModel().getColumn(5).setPreferredWidth(100);  // Status

        // Custom renderers
        issuesTable.getColumnModel().getColumn(0).setCellRenderer(new SeverityCellRenderer());
        issuesTable.getColumnModel().getColumn(5).setCellRenderer(new StatusCellRenderer());

        JScrollPane scrollPane = new JScrollPane(issuesTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220), 1));
        scrollPane.getViewport().setBackground(Color.WHITE);
        add(scrollPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Click 'Scan for Issues' to start");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(statusLabel, BorderLayout.SOUTH);
    }

    public void scanForIssues() {
        statusLabel.setText("Scanning for data quality issues...");

        SwingWorker<List<QualityIssue>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<QualityIssue> doInBackground() throws Exception {
                List<QualityIssue> issues = new ArrayList<>();

                // 1. Check for duplicate/ambiguous student names
                List<Student> students = db.getAllStudents();
                Map<String, List<Student>> byNormalizedName = students.stream()
                    .collect(Collectors.groupingBy(Student::getNormalizedName));

                for (Map.Entry<String, List<Student>> entry : byNormalizedName.entrySet()) {
                    if (entry.getValue().size() > 1) {
                        String names = entry.getValue().stream()
                            .map(s -> s.getStudentCode() + " (" + s.getProgram() + ")")
                            .collect(Collectors.joining(", "));
                        issues.add(new QualityIssue(
                            QualityIssue.Severity.WARNING,
                            "Ambiguous Student Name",
                            "STUDENT",
                            entry.getValue().get(0).getStudentCode(),
                            String.format("Multiple students share normalized name '%s': %s", entry.getKey(), names),
                            "OPEN"
                        ));
                    }
                }

                // 2. Check for receipt conflicts (same receipt, different amounts)
                List<Payment> payments = db.getAllPayments();
                Map<Integer, List<Payment>> byReceipt = payments.stream()
                    .collect(Collectors.groupingBy(Payment::getReceiptNumber));

                for (Map.Entry<Integer, List<Payment>> entry : byReceipt.entrySet()) {
                    if (entry.getValue().size() > 1) {
                        // Check if they have different amounts
                        double firstAmount = entry.getValue().get(0).getTotalAmount();
                        boolean hasConflict = entry.getValue().stream()
                            .anyMatch(p -> Math.abs(p.getTotalAmount() - firstAmount) > 0.01);

                        if (hasConflict) {
                            String details = entry.getValue().stream()
                                .map(p -> "Student " + p.getStudentId() + ": ₱" + String.format("%,.2f", p.getTotalAmount()))
                                .collect(Collectors.joining("; "));
                            issues.add(new QualityIssue(
                                QualityIssue.Severity.ERROR,
                                "Receipt Conflict",
                                "PAYMENT",
                                String.valueOf(entry.getKey()),
                                String.format("Receipt %d has conflicting amounts: %s", entry.getKey(), details),
                                "OPEN"
                            ));
                        } else {
                            issues.add(new QualityIssue(
                                QualityIssue.Severity.INFO,
                                "Duplicate Receipt (Same Amount)",
                                "PAYMENT",
                                String.valueOf(entry.getKey()),
                                String.format("Receipt %d appears %d times with same amount", entry.getKey(), entry.getValue().size()),
                                "OPEN"
                            ));
                        }
                    }
                }

                // 3. Check for invalid payment records
                for (Payment p : payments) {
                    if (p.getTotalAmount() < 0) {
                        issues.add(new QualityIssue(
                            QualityIssue.Severity.ERROR,
                            "Negative Total Amount",
                            "PAYMENT",
                            String.valueOf(p.getReceiptNumber()),
                            String.format("Receipt %d has negative total: ₱%,.2f", p.getReceiptNumber(), p.getTotalAmount()),
                            "OPEN"
                        ));
                    }

                    if (p.getIntelFee() != null && p.getIntelFee() < 0) {
                        issues.add(new QualityIssue(
                            QualityIssue.Severity.WARNING,
                            "Negative Intel Fee",
                            "PAYMENT",
                            String.valueOf(p.getReceiptNumber()),
                            String.format("Receipt %d has negative Intel Fee: ₱%,.2f", p.getReceiptNumber(), p.getIntelFee()),
                            "OPEN"
                        ));
                    }

                    if (p.getTshirtSizing() != null && p.getTshirtSizing() < 0) {
                        issues.add(new QualityIssue(
                            QualityIssue.Severity.WARNING,
                            "Negative T-Shirt Fee",
                            "PAYMENT",
                            String.valueOf(p.getReceiptNumber()),
                            String.format("Receipt %d has negative T-Shirt Fee: ₱%,.2f", p.getReceiptNumber(), p.getTshirtSizing()),
                            "OPEN"
                        ));
                    }

                    if (p.getPenalties() != null && p.getPenalties() < 0) {
                        issues.add(new QualityIssue(
                            QualityIssue.Severity.WARNING,
                            "Negative Penalties",
                            "PAYMENT",
                            String.valueOf(p.getReceiptNumber()),
                            String.format("Receipt %d has negative Penalties: ₱%,.2f", p.getReceiptNumber(), p.getPenalties()),
                            "OPEN"
                        ));
                    }

                    if (p.getCitNight() != null && p.getCitNight() < 0) {
                        issues.add(new QualityIssue(
                            QualityIssue.Severity.WARNING,
                            "Negative CIT Night Fee",
                            "PAYMENT",
                            String.valueOf(p.getReceiptNumber()),
                            String.format("Receipt %d has negative CIT Night Fee: ₱%,.2f", p.getReceiptNumber(), p.getCitNight()),
                            "OPEN"
                        ));
                    }

                    // Missing program
                    if (p.getProgram() == null || p.getProgram().trim().isEmpty()) {
                        issues.add(new QualityIssue(
                            QualityIssue.Severity.WARNING,
                            "Missing Program",
                            "PAYMENT",
                            String.valueOf(p.getReceiptNumber()),
                            String.format("Receipt %d has no program assigned", p.getReceiptNumber()),
                            "OPEN"
                        ));
                    }

                    // Missing received by
                    if (p.getReceivedBy() == null || p.getReceivedBy().trim().isEmpty()) {
                        issues.add(new QualityIssue(
                            QualityIssue.Severity.INFO,
                            "Missing Receiver",
                            "PAYMENT",
                            String.valueOf(p.getReceiptNumber()),
                            String.format("Receipt %d has no receiver recorded", p.getReceiptNumber()),
                            "OPEN"
                        ));
                    }

                    // Missing remittance date
                    if (p.getRemittanceDate() == null) {
                        issues.add(new QualityIssue(
                            QualityIssue.Severity.INFO,
                            "Missing Remittance Date",
                            "PAYMENT",
                            String.valueOf(p.getReceiptNumber()),
                            String.format("Receipt %d has no remittance date", p.getReceiptNumber()),
                            "OPEN"
                        ));
                    }
                }

                // 4. Check for students without payments
                for (Student s : students) {
                    if (s.getPaymentCount() == 0) {
                        issues.add(new QualityIssue(
                            QualityIssue.Severity.INFO,
                            "Student Without Payments",
                            "STUDENT",
                            s.getStudentCode(),
                            String.format("Student %s (%s) has no payment records", s.getStudentCode(), s.getName()),
                            "OPEN"
                        ));
                    }
                }

                // 5. Check for inconsistent program naming
                Map<String, Long> programCounts = students.stream()
                    .filter(s -> s.getProgram() != null && !s.getProgram().isEmpty())
                    .collect(Collectors.groupingBy(Student::getProgram, Collectors.counting()));

                for (String program : programCounts.keySet()) {
                    // Check for similar programs (case insensitive)
                    String lower = program.toLowerCase();
                    long similarCount = programCounts.keySet().stream()
                        .filter(p -> p.toLowerCase().equals(lower) && !p.equals(program))
                        .count();
                    if (similarCount > 0) {
                        issues.add(new QualityIssue(
                            QualityIssue.Severity.WARNING,
                            "Inconsistent Program Naming",
                            "STUDENT",
                            program,
                            String.format("Program '%s' has %d similar variant(s) (case difference)", program, similarCount),
                            "OPEN"
                        ));
                    }
                }

                return issues;
            }

            @Override
            protected void done() {
                try {
                    List<QualityIssue> issues = get();
                    issuesTableModel.setIssues(issues);

                    int errors = (int) issues.stream().filter(i -> i.severity == QualityIssue.Severity.ERROR).count();
                    int warnings = (int) issues.stream().filter(i -> i.severity == QualityIssue.Severity.WARNING).count();
                    int info = (int) issues.stream().filter(i -> i.severity == QualityIssue.Severity.INFO).count();

                    statusLabel.setText(String.format("Scan complete: %d errors, %d warnings, %d info", errors, warnings, info));
                } catch (Exception e) {
                    statusLabel.setText("Error scanning: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void exportIssues() {
        if (issuesTableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No issues to export", "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV Files", "csv"));
        fileChooser.setSelectedFile(new java.io.File("data_quality_issues_" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(fileChooser.getSelectedFile())) {
                writer.println("Severity,Issue Type,Entity,Entity ID,Description,Status");
                for (int i = 0; i < issuesTableModel.getRowCount(); i++) {
                    StringBuilder row = new StringBuilder();
                    for (int j = 0; j < issuesTableModel.getColumnCount(); j++) {
                        if (j > 0) row.append(",");
                        Object val = issuesTableModel.getValueAt(i, j);
                        String str = val != null ? val.toString() : "";
                        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
                            str = "\"" + str.replace("\"", "\"\"") + "\"";
                        }
                        row.append(str);
                    }
                    writer.println(row);
                }
                statusLabel.setText("Exported to " + fileChooser.getSelectedFile().getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- Issue Model ---

    private static class QualityIssue {
        enum Severity { ERROR, WARNING, INFO }

        final Severity severity;
        final String issueType;
        final String entity;
        final String entityId;
        final String description;
        final String status;

        QualityIssue(Severity severity, String issueType, String entity, String entityId, String description, String status) {
            this.severity = severity;
            this.issueType = issueType;
            this.entity = entity;
            this.entityId = entityId;
            this.description = description;
            this.status = status;
        }
    }

    // --- Table Model ---

    private static class IssuesTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Severity", "Issue Type", "Entity", "Entity ID", "Description", "Status"};
        private List<QualityIssue> issues = List.of();

        public void setIssues(List<QualityIssue> issues) {
            this.issues = issues;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return issues.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            QualityIssue issue = issues.get(rowIndex);
            switch (columnIndex) {
                case 0: return issue.severity.name();
                case 1: return issue.issueType;
                case 2: return issue.entity;
                case 3: return issue.entityId;
                case 4: return issue.description;
                case 5: return issue.status;
                default: return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }
    }

    // --- Cell Renderers ---

    private static class SeverityCellRenderer extends DefaultTableCellRenderer {
        private static final java.util.Map<String, Color> SEVERITY_COLORS = new java.util.HashMap<>();
        static {
            SEVERITY_COLORS.put("ERROR", new Color(200, 0, 0));
            SEVERITY_COLORS.put("WARNING", new Color(200, 150, 0));
            SEVERITY_COLORS.put("INFO", new Color(0, 100, 200));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value != null) {
                String severity = value.toString();
                Color color = SEVERITY_COLORS.getOrDefault(severity, Color.BLACK);
                if (!isSelected) {
                    c.setForeground(color);
                }
                setText(severity);
                setHorizontalAlignment(CENTER);
                setFont(getFont().deriveFont(Font.BOLD, 11f));
            }
            return c;
        }
    }

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value != null) {
                setText(value.toString());
                setHorizontalAlignment(CENTER);
                if (!isSelected) {
                    c.setForeground(new Color(0, 120, 215));
                }
            }
            return c;
        }
    }
}