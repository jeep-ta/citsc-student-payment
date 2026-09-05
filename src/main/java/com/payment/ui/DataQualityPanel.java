package com.payment.ui;

import com.payment.Payment;
import com.payment.Student;
import com.payment.ChargeAcademicTerm;
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
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(ThemeUtils.BG_DEEPEST);

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(0, 10));
        headerPanel.setOpaque(false);

        // Title banner
        JPanel titleBanner = ThemeUtils.createGradientPanel(new Color(15, 23, 42), new Color(10, 14, 26));
        titleBanner.setLayout(new BorderLayout());
        titleBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, ThemeUtils.NEON_AMBER),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLabel = new JLabel("🔍 Data Quality Inspector");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        titleLabel.setForeground(ThemeUtils.NEON_AMBER);
        titleBanner.add(titleLabel, BorderLayout.WEST);

        JLabel subtitleLabel = new JLabel("Integrity Checks & Issue Detection");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        subtitleLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        titleBanner.add(subtitleLabel, BorderLayout.EAST);

        headerPanel.add(titleBanner, BorderLayout.NORTH);

        // Toolbar
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        toolBar.setBackground(ThemeUtils.BG_CARD);
        toolBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        JButton scanButton = new JButton("⚡ Scan for Issues");
        ThemeUtils.styleButton(scanButton, ThemeUtils.NEON_AMBER);
        scanButton.addActionListener(e -> scanForIssues());
        toolBar.add(scanButton);

        JButton exportButton = new JButton("📊 Export Issues");
        ThemeUtils.styleButton(exportButton, ThemeUtils.NEON_GREEN);
        exportButton.addActionListener(e -> exportIssues());
        toolBar.add(exportButton);

        JButton mergeButton = new JButton("🔀 Merge / Deduplicate...");
        ThemeUtils.styleButton(mergeButton, ThemeUtils.NEON_CYAN);
        mergeButton.addActionListener(e -> openMergeDialog());
        toolBar.add(mergeButton);

        headerPanel.add(toolBar, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // Issues table
        issuesTableModel = new IssuesTableModel();
        issuesTable = new JTable(issuesTableModel);
        issuesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ThemeUtils.applyTableTheme(issuesTable);
        issuesTable.setAutoCreateRowSorter(true);

        // Column widths
        issuesTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        issuesTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        issuesTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        issuesTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        issuesTable.getColumnModel().getColumn(4).setPreferredWidth(400);
        issuesTable.getColumnModel().getColumn(5).setPreferredWidth(100);

        // Custom renderers
        issuesTable.getColumnModel().getColumn(0).setCellRenderer(new SeverityCellRenderer());
        issuesTable.getColumnModel().getColumn(5).setCellRenderer(new StatusCellRenderer());

        setupContextMenu();

        JScrollPane scrollPane = new JScrollPane(issuesTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(ThemeUtils.BG_SURFACE);
        add(scrollPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Click 'Scan for Issues' to start");
        statusLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void setupContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem setCurrItem = new JMenuItem("Assign Current Term");
        JMenuItem setPrevItem = new JMenuItem("Assign Previous Term");
        JMenuItem viewItem = new JMenuItem("View Details in Panel");
        JMenuItem copyIdItem = new JMenuItem("Copy Entity ID");

        setCurrItem.addActionListener(e -> fixSelectedChargeTerm(ChargeAcademicTerm.CURRENT));
        setPrevItem.addActionListener(e -> fixSelectedChargeTerm(ChargeAcademicTerm.PREVIOUS));
        viewItem.addActionListener(e -> navigateToIssueEntity());
        copyIdItem.addActionListener(e -> {
            int row = issuesTable.getSelectedRow();
            if (row >= 0) {
                Object val = issuesTable.getValueAt(row, 3);
                if (val != null) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(val.toString()), null);
                }
            }
        });

        JMenuItem mergeItem = new JMenuItem("🔀 Merge Student Records...");
        mergeItem.addActionListener(e -> {
            int row = issuesTable.getSelectedRow();
            String preselected = null;
            if (row >= 0) {
                int modelRow = issuesTable.convertRowIndexToModel(row);
                QualityIssue issue = issuesTableModel.getIssue(modelRow);
                if (issue != null && "STUDENT".equalsIgnoreCase(issue.entity)) {
                    preselected = issue.entityId;
                }
            }
            openMergeDialog(preselected);
        });

        popupMenu.add(setCurrItem);
        popupMenu.add(setPrevItem);
        popupMenu.add(mergeItem);
        popupMenu.addSeparator();
        popupMenu.add(viewItem);
        popupMenu.add(copyIdItem);

        issuesTable.setComponentPopupMenu(popupMenu);
        issuesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToIssueEntity();
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                int row = issuesTable.rowAtPoint(e.getPoint());
                if (row >= 0 && !issuesTable.isRowSelected(row)) {
                    issuesTable.setRowSelectionInterval(row, row);
                }
                if (row >= 0) {
                    int modelRow = issuesTable.convertRowIndexToModel(row);
                    QualityIssue issue = issuesTableModel.getIssue(modelRow);
                    boolean isTermIssue = issue != null && "Unassigned Charge Term".equals(issue.issueType);
                    setCurrItem.setEnabled(isTermIssue);
                    setPrevItem.setEnabled(isTermIssue);
                }
            }
        });
    }

    private void fixSelectedChargeTerm(ChargeAcademicTerm term) {
        int selectedRow = issuesTable.getSelectedRow();
        if (selectedRow < 0) return;
        int modelRow = issuesTable.convertRowIndexToModel(selectedRow);
        QualityIssue issue = issuesTableModel.getIssue(modelRow);
        if (issue == null || !"PAYMENT".equals(issue.entity)) return;

        try {
            int paymentId = Integer.parseInt(issue.entityId);
            Payment payment = db.findPaymentById(paymentId).orElseThrow();
            db.updatePaymentChargeTermById(paymentId, term, "Fixed via Data Quality Panel", "user");
            JOptionPane.showMessageDialog(this, "Receipt #" + payment.getReceiptNumber() + " assigned to " + term.getLabel(), "Term Assigned", JOptionPane.INFORMATION_MESSAGE);
            scanForIssues();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error assigning term: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void navigateToIssueEntity() {
        int selectedRow = issuesTable.getSelectedRow();
        if (selectedRow < 0) return;
        int modelRow = issuesTable.convertRowIndexToModel(selectedRow);
        QualityIssue issue = issuesTableModel.getIssue(modelRow);
        if (issue == null) return;

        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof MainFrame mainFrame) {
            if ("PAYMENT".equals(issue.entity)) {
                try {
                    int paymentId = Integer.parseInt(issue.entityId);
                    db.findPaymentById(paymentId)
                        .ifPresent(payment -> mainFrame.showPaymentsForStudent(payment.getStudentId()));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Could not open payment: " + ex.getMessage(),
                        "Navigation Error", JOptionPane.ERROR_MESSAGE);
                }
            } else if ("STUDENT".equals(issue.entity)) {
                mainFrame.navigateTo("Students");
            }
        }
    }

    private void openMergeDialog() {
        openMergeDialog(null);
    }

    private void openMergeDialog(String preselectedSource) {
        Window window = SwingUtilities.getWindowAncestor(this);
        MergeStudentsDialog dialog = new MergeStudentsDialog(window, preselectedSource, null, this::scanForIssues);
        dialog.setVisible(true);
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

                // 2. Check for receipt conflicts
                List<Payment> payments = db.getAllPayments();
                Map<com.payment.ReceiptKey, List<Payment>> byReceipt = payments.stream()
                    .filter(p -> p.getReceiptKey().hasDefinedScope())
                    .collect(Collectors.groupingBy(Payment::getReceiptKey));

                for (Map.Entry<com.payment.ReceiptKey, List<Payment>> entry : byReceipt.entrySet()) {
                    if (entry.getValue().size() > 1) {
                        com.payment.ReceiptKey receiptKey = entry.getKey();
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
                                String.valueOf(entry.getValue().get(0).getId()),
                                String.format("Receipt %d in %s has conflicting amounts: %s",
                                    receiptKey.receiptNumber(), receiptKey.displayScope(), details),
                                "OPEN"
                            ));
                        } else {
                            issues.add(new QualityIssue(
                                QualityIssue.Severity.INFO,
                                "Duplicate Receipt (Same Amount)",
                                "PAYMENT",
                                String.valueOf(entry.getValue().get(0).getId()),
                                String.format("Receipt %d in %s appears %d times with same amount",
                                    receiptKey.receiptNumber(), receiptKey.displayScope(), entry.getValue().size()),
                                "OPEN"
                            ));
                        }
                    }
                }

                // 3. Check for invalid payment records
                for (Payment p : payments) {
                    if (p.getReceiptNumber() > 0 && !p.getReceiptKey().hasDefinedScope()) {
                        issues.add(new QualityIssue(QualityIssue.Severity.WARNING, "Missing Receipt Period", "PAYMENT",
                            String.valueOf(p.getId()),
                            String.format("Receipt %d has no issuance academic year/semester", p.getReceiptNumber()), "OPEN"));
                    }
                    if (p.getTotalAmount() < 0) {
                        issues.add(new QualityIssue(QualityIssue.Severity.ERROR, "Negative Total Amount", "PAYMENT",
                            String.valueOf(p.getId()),
                            String.format("Receipt %d has negative total: ₱%,.2f", p.getReceiptNumber(), p.getTotalAmount()), "OPEN"));
                    }
                    if (p.getIntelFee() != null && p.getIntelFee() < 0) {
                        issues.add(new QualityIssue(QualityIssue.Severity.WARNING, "Negative Intel Fee", "PAYMENT",
                            String.valueOf(p.getId()),
                            String.format("Receipt %d has negative Intel Fee: ₱%,.2f", p.getReceiptNumber(), p.getIntelFee()), "OPEN"));
                    }
                    if (p.getTshirtSizing() != null && p.getTshirtSizing() < 0) {
                        issues.add(new QualityIssue(QualityIssue.Severity.WARNING, "Negative T-Shirt Fee", "PAYMENT",
                            String.valueOf(p.getId()),
                            String.format("Receipt %d has negative T-Shirt Fee: ₱%,.2f", p.getReceiptNumber(), p.getTshirtSizing()), "OPEN"));
                    }
                    if (p.getPenalties() != null && p.getPenalties() < 0) {
                        issues.add(new QualityIssue(QualityIssue.Severity.WARNING, "Negative Penalties", "PAYMENT",
                            String.valueOf(p.getId()),
                            String.format("Receipt %d has negative Penalties: ₱%,.2f", p.getReceiptNumber(), p.getPenalties()), "OPEN"));
                    }
                    if (p.getCitNight() != null && p.getCitNight() < 0) {
                        issues.add(new QualityIssue(QualityIssue.Severity.WARNING, "Negative CIT Night Fee", "PAYMENT",
                            String.valueOf(p.getId()),
                            String.format("Receipt %d has negative CIT Night Fee: ₱%,.2f", p.getReceiptNumber(), p.getCitNight()), "OPEN"));
                    }
                    if (p.getProgram() == null || p.getProgram().trim().isEmpty()) {
                        issues.add(new QualityIssue(QualityIssue.Severity.WARNING, "Missing Program", "PAYMENT",
                            String.valueOf(p.getId()),
                            String.format("Receipt %d has no program assigned", p.getReceiptNumber()), "OPEN"));
                    }
                    if (p.getReceivedBy() == null || p.getReceivedBy().trim().isEmpty()) {
                        issues.add(new QualityIssue(QualityIssue.Severity.INFO, "Missing Receiver", "PAYMENT",
                            String.valueOf(p.getId()),
                            String.format("Receipt %d has no receiver recorded", p.getReceiptNumber()), "OPEN"));
                    }
                    if (p.getRemittanceDate() == null) {
                        issues.add(new QualityIssue(QualityIssue.Severity.INFO, "Missing Remittance Date", "PAYMENT",
                            String.valueOf(p.getId()),
                            String.format("Receipt %d has no remittance date", p.getReceiptNumber()), "OPEN"));
                    }
                    String[] categories = {"Intel Fee", "T-Shirt", "Penalties", "CIT Night"};
                    Double[] amounts = {p.getIntelFee(), p.getTshirtSizing(), p.getPenalties(), p.getCitNight()};
                    for (int i = 0; i < categories.length; i++) {
                        if (amounts[i] == null || amounts[i] <= 0) continue;
                        if (p.getEffectiveTermForCategory(categories[i]) == ChargeAcademicTerm.UNASSIGNED
                            || p.getEffectiveAyForCategory(categories[i]) == null) {
                            issues.add(new QualityIssue(QualityIssue.Severity.WARNING, "Unassigned Fee Attribution", "PAYMENT",
                                String.valueOf(p.getId()),
                                String.format("Receipt %d has %s without a complete academic year/term tag", p.getReceiptNumber(), categories[i]), "OPEN"));
                        }
                    }
                }

                // 4. Students without payments
                for (Student s : students) {
                    if (s.getPaymentCount() == 0) {
                        issues.add(new QualityIssue(QualityIssue.Severity.INFO, "Student Without Payments", "STUDENT",
                            s.getStudentCode(),
                            String.format("Student %s (%s) has no payment records", s.getStudentCode(), s.getName()), "OPEN"));
                    }
                }

                // 5. Inconsistent program naming
                Map<String, Long> programCounts = students.stream()
                    .filter(s -> s.getProgram() != null && !s.getProgram().isEmpty())
                    .collect(Collectors.groupingBy(Student::getProgram, Collectors.counting()));

                for (String program : programCounts.keySet()) {
                    String lower = program.toLowerCase();
                    long similarCount = programCounts.keySet().stream()
                        .filter(p -> p.toLowerCase().equals(lower) && !p.equals(program))
                        .count();
                    if (similarCount > 0) {
                        issues.add(new QualityIssue(QualityIssue.Severity.WARNING, "Inconsistent Program Naming", "STUDENT",
                            program,
                            String.format("Program '%s' has %d similar variant(s) (case difference)", program, similarCount), "OPEN"));
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

        public QualityIssue getIssue(int rowIndex) {
            if (issues != null && rowIndex >= 0 && rowIndex < issues.size()) return issues.get(rowIndex);
            return null;
        }

        @Override public int getRowCount() { return issues.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

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

        @Override public Class<?> getColumnClass(int columnIndex) { return String.class; }
    }

    // --- Cell Renderers ---

    private static class SeverityCellRenderer extends DefaultTableCellRenderer {
        private static final java.util.Map<String, Color> SEVERITY_COLORS = new java.util.HashMap<>();
        static {
            SEVERITY_COLORS.put("ERROR", ThemeUtils.NEON_ROSE);
            SEVERITY_COLORS.put("WARNING", ThemeUtils.NEON_AMBER);
            SEVERITY_COLORS.put("INFO", ThemeUtils.NEON_CYAN);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value != null) {
                String severity = value.toString();
                Color color = SEVERITY_COLORS.getOrDefault(severity, ThemeUtils.TEXT_SECONDARY);
                if (!isSelected) c.setForeground(color);
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
                if (!isSelected) c.setForeground(ThemeUtils.NEON_CYAN);
            }
            return c;
        }
    }
}
