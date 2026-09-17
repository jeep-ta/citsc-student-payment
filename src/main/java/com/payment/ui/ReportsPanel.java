package com.payment.ui;

import com.payment.ChargeAcademicTerm;
import com.payment.NameNormalizer;
import com.payment.Payment;
import com.payment.Student;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reports Panel - Generate and export reports with itemized fee-term splitting,
 * real-time auto-generation, and futuristic cyber-dark styling.
 */
public class ReportsPanel extends JPanel {

    private final DatabaseManager db;

    private JComboBox<String> reportTypeCombo;
    private JLabel categoryLabel;
    private JComboBox<String> categoryFilter;
    private JTextField dateFromField;
    private JTextField dateToField;
    private JComboBox<String> programFilter;
    private JComboBox<String> yearFilter;
    private JComboBox<String> termFilter;
    private JTable reportTable;
    private ReportTableModel reportTableModel;
    private JLabel statusLabel;
    private JLabel summaryLabel;
    private JLabel recordCountLabel; // Prominent record count in header

    // Category Full Payment Target Controls
    private JLabel targetFeeLabel;
    private JSpinner targetFeeSpinner;
    private JButton saveTargetButton;
    private JButton configAllTargetsButton;
    private boolean isUpdatingTargetSpinner = false;
    private volatile List<Payment> cachedAllPayments = null;

    public ReportsPanel() {
        this.db = DatabaseManager.getInstance();
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(ThemeUtils.BG_DEEPEST);

        // Header container (Title + Futuristic Controls)
        JPanel headerPanel = new JPanel(new BorderLayout(0, 10));
        headerPanel.setOpaque(false);

        // Title with futuristic gradient banner
        JPanel titleBanner = ThemeUtils.createGradientPanel(new Color(15, 23, 42), new Color(10, 14, 26));
        titleBanner.setLayout(new BorderLayout());
        titleBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, ThemeUtils.NEON_CYAN),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLabel = new JLabel("⚡ Financial & Academic Reports");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        titleLabel.setForeground(ThemeUtils.NEON_CYAN);
        titleBanner.add(titleLabel, BorderLayout.WEST);

        JLabel subtitleLabel = new JLabel("Multi-Term Financial Separation & Analytics");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        subtitleLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        titleBanner.add(subtitleLabel, BorderLayout.EAST);

        headerPanel.add(titleBanner, BorderLayout.NORTH);

        // Futuristic Controls Card
        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setBackground(ThemeUtils.BG_CARD);
        controlsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        // Row 1: Report Type Selection
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        row1.setOpaque(false);
        JLabel typeLabel = new JLabel("Report Mode:");
        typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD, 12f));
        typeLabel.setForeground(ThemeUtils.NEON_PURPLE);
        row1.add(typeLabel);

        reportTypeCombo = new JComboBox<>(new String[]{
            "Student Payment Report (Separated per Term)",
            "Single Category Payment Report",
            "Academic Term Report (Fee Attribution)",
            "Collection Summary",
            "Remittance Report",
            "Receiver Report",
            "Import Batch Report"
        });
        reportTypeCombo.setPreferredSize(new Dimension(300, 30));
        reportTypeCombo.addActionListener(e -> {
            updateCategoryFilterState();
            generateReport();
        });
        row1.add(reportTypeCombo);

        categoryLabel = new JLabel("Category:");
        categoryLabel.setFont(categoryLabel.getFont().deriveFont(Font.BOLD, 12f));
        categoryLabel.setForeground(ThemeUtils.NEON_AMBER);
        categoryLabel.setEnabled(false);
        row1.add(categoryLabel);

        categoryFilter = new JComboBox<>(new String[]{
            "T-Shirt Sizing",
            "Intel Fee",
            "Penalties",
            "CIT Night"
        });
        categoryFilter.setPreferredSize(new Dimension(140, 30));
        categoryFilter.setEnabled(false);
        categoryFilter.addActionListener(e -> {
            if (isSingleCategoryMode()) {
                updateCategoryTargetFromDb();
                generateReport();
            }
        });
        row1.add(categoryFilter);

        targetFeeLabel = new JLabel("Full Fee: ₱");
        targetFeeLabel.setFont(targetFeeLabel.getFont().deriveFont(Font.BOLD, 12f));
        targetFeeLabel.setForeground(ThemeUtils.NEON_GREEN);
        targetFeeLabel.setEnabled(false);
        row1.add(targetFeeLabel);

        targetFeeSpinner = new JSpinner(new SpinnerNumberModel(500.0, 0.0, 100000.0, 10.0));
        JSpinner.NumberEditor targetEditor = new JSpinner.NumberEditor(targetFeeSpinner, "#,##0.00");
        targetFeeSpinner.setEditor(targetEditor);
        targetEditor.getTextField().addActionListener(e -> saveCurrentCategoryTarget());
        targetFeeSpinner.setPreferredSize(new Dimension(95, 30));
        targetFeeSpinner.setEnabled(false);
        targetFeeSpinner.setToolTipText("Full payment target for the selected category. Press Enter or click Set to apply.");
        row1.add(targetFeeSpinner);

        saveTargetButton = new JButton("💾 Set");
        ThemeUtils.styleButton(saveTargetButton, ThemeUtils.NEON_GREEN);
        saveTargetButton.setPreferredSize(new Dimension(75, 30));
        saveTargetButton.setToolTipText("Save full target amount for this category");
        saveTargetButton.setEnabled(false);
        saveTargetButton.addActionListener(e -> saveCurrentCategoryTarget());
        row1.add(saveTargetButton);

        configAllTargetsButton = new JButton("⚙ All Targets...");
        ThemeUtils.styleButton(configAllTargetsButton, ThemeUtils.NEON_PURPLE);
        configAllTargetsButton.setPreferredSize(new Dimension(120, 30));
        configAllTargetsButton.setToolTipText("Define full targets for all fee categories");
        configAllTargetsButton.setEnabled(false);
        configAllTargetsButton.addActionListener(e -> openConfigureCategoryTargetsDialog());
        row1.add(configAllTargetsButton);

        controlsPanel.add(row1);

        // Row 2: Real-time Filters
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        row2.setOpaque(false);

        row2.add(createFilterLabel("Date From:"));
        dateFromField = new JTextField(8);
        dateFromField.setToolTipText("yyyy-MM-dd");
        dateFromField.addActionListener(e -> generateReport());
        row2.add(dateFromField);

        row2.add(createFilterLabel("Date To:"));
        dateToField = new JTextField(8);
        dateToField.setToolTipText("yyyy-MM-dd");
        dateToField.addActionListener(e -> generateReport());
        row2.add(dateToField);

        row2.add(createFilterLabel("Program:"));
        programFilter = new JComboBox<>(new String[]{"All Programs"});
        programFilter.setPreferredSize(new Dimension(130, 28));
        programFilter.addActionListener(e -> generateReport());
        row2.add(programFilter);

        row2.add(createFilterLabel("AY:"));
        yearFilter = new JComboBox<>(new String[]{"All AY"});
        yearFilter.setPreferredSize(new Dimension(110, 28));
        yearFilter.addActionListener(e -> generateReport());
        row2.add(yearFilter);

        row2.add(createFilterLabel("Term:"));
        termFilter = new JComboBox<>(new String[]{
            "All Terms",
            ChargeAcademicTerm.FIRST_SEM.getLabel(),
            ChargeAcademicTerm.SECOND_SEM.getLabel(),
            ChargeAcademicTerm.SUMMER.getLabel(),
            ChargeAcademicTerm.CURRENT.getLabel(),
            ChargeAcademicTerm.PREVIOUS.getLabel(),
            ChargeAcademicTerm.UNASSIGNED.getLabel()
        });
        termFilter.setPreferredSize(new Dimension(130, 28));
        termFilter.addActionListener(e -> generateReport());
        row2.add(termFilter);

        controlsPanel.add(row2);

        // Row 3: Actions
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        row3.setOpaque(false);

        // Prominent record count badge with solid background (prevents Swing alpha text artifacts)
        recordCountLabel = new JLabel("Records: 0");
        recordCountLabel.setFont(recordCountLabel.getFont().deriveFont(Font.BOLD, 13f));
        recordCountLabel.setForeground(ThemeUtils.NEON_CYAN);
        recordCountLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeUtils.NEON_CYAN, 1),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));
        recordCountLabel.setBackground(new Color(15, 23, 42));
        recordCountLabel.setOpaque(true);
        row3.add(recordCountLabel);
        row3.add(Box.createHorizontalStrut(12));

        JButton generateButton = new JButton("🔄 Refresh Report");
        ThemeUtils.styleButton(generateButton, ThemeUtils.NEON_CYAN);
        generateButton.addActionListener(e -> {
            cachedAllPayments = null;
            generateReport();
        });
        row3.add(generateButton);

        JButton exportCsvButton = new JButton("📊 Export CSV");
        ThemeUtils.styleButton(exportCsvButton, ThemeUtils.NEON_GREEN);
        exportCsvButton.addActionListener(e -> exportCSV());
        row3.add(exportCsvButton);

        JButton exportExcelButton = new JButton("📈 Export Excel (.xlsx)");
        ThemeUtils.styleButton(exportExcelButton, ThemeUtils.NEON_AMBER);
        exportExcelButton.addActionListener(e -> exportExcel());
        row3.add(exportExcelButton);

        controlsPanel.add(row3);

        headerPanel.add(controlsPanel, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // Report table
        reportTableModel = new ReportTableModel();
        reportTable = new JTable(reportTableModel);
        ThemeUtils.applyTableTheme(reportTable);
        reportTable.setAutoCreateRowSorter(true);
        reportTable.setFillsViewportHeight(true);

        JScrollPane scrollPane = new JScrollPane(reportTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(ThemeUtils.BG_SURFACE);
        add(scrollPane, BorderLayout.CENTER);

        // Status bar with cyber summary
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setOpaque(false);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));

        statusLabel = new JLabel("Loading financial data...");
        statusLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        statusPanel.add(statusLabel, BorderLayout.WEST);

        summaryLabel = new JLabel("Total: ₱0.00");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD, 14f));
        summaryLabel.setForeground(ThemeUtils.NEON_GREEN);
        statusPanel.add(summaryLabel, BorderLayout.EAST);

        add(statusPanel, BorderLayout.SOUTH);

        refreshData();
    }

    private JLabel createFilterLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 11f));
        l.setForeground(ThemeUtils.TEXT_SECONDARY);
        return l;
    }

    private boolean isSingleCategoryMode() {
        String rt = (String) reportTypeCombo.getSelectedItem();
        return rt != null && rt.contains("Single Category");
    }

    private void updateCategoryFilterState() {
        boolean active = isSingleCategoryMode();
        if (categoryLabel != null) categoryLabel.setEnabled(active);
        if (categoryFilter != null) categoryFilter.setEnabled(active);
        if (targetFeeLabel != null) targetFeeLabel.setEnabled(active);
        if (targetFeeSpinner != null) targetFeeSpinner.setEnabled(active);
        if (saveTargetButton != null) saveTargetButton.setEnabled(active);
        if (configAllTargetsButton != null) configAllTargetsButton.setEnabled(active);
        if (active) {
            updateCategoryTargetFromDb();
        }
    }

    private void updateCategoryTargetFromDb() {
        if (categoryFilter == null || targetFeeSpinner == null) return;
        String cat = (String) categoryFilter.getSelectedItem();
        if (cat == null) cat = "T-Shirt Sizing";
        double target = db.getCategoryFullTarget(cat);
        isUpdatingTargetSpinner = true;
        try {
            targetFeeSpinner.setValue(target);
        } finally {
            isUpdatingTargetSpinner = false;
        }
    }

    private void onTargetFeeSpinnerChanged() {
        if (isUpdatingTargetSpinner) return;
        saveCurrentCategoryTarget();
    }

    private void saveCurrentCategoryTarget() {
        if (categoryFilter == null || targetFeeSpinner == null) return;
        String cat = (String) categoryFilter.getSelectedItem();
        if (cat == null) cat = "T-Shirt Sizing";
        Object val = targetFeeSpinner.getValue();
        double amount = (val instanceof Number) ? ((Number) val).doubleValue() : 0.0;
        try {
            db.setCategoryFullTarget(cat, amount);
            if (statusLabel != null) {
                statusLabel.setText(String.format("Updated %s full target to ₱%,.2f", cat, amount));
            }
            if (isSingleCategoryMode()) {
                generateReport();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save category target: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openConfigureCategoryTargetsDialog() {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Configure Category Full Targets", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.getContentPane().setBackground(ThemeUtils.BG_CARD);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JLabel titleLbl = new JLabel("💵 Define Full Payment Targets Per Category");
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 15f));
        titleLbl.setForeground(ThemeUtils.NEON_CYAN);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(titleLbl);

        JLabel subLbl = new JLabel("Students reaching these thresholds are marked 'Fully Paid' in Single Category Reports.");
        subLbl.setFont(subLbl.getFont().deriveFont(Font.PLAIN, 11f));
        subLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        subLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(subLbl);
        mainPanel.add(Box.createVerticalStrut(14));

        Map<String, Double> currentTargets = db.getAllCategoryFullTargets();
        Map<String, JSpinner> spinners = new LinkedHashMap<>();

        JPanel gridPanel = new JPanel(new GridLayout(4, 2, 12, 10));
        gridPanel.setOpaque(false);
        gridPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] categories = new String[]{"Intel Fee", "T-Shirt Sizing", "CIT Night", "Penalties"};
        for (String cat : categories) {
            JLabel lbl = new JLabel(cat + " (₱):");
            lbl.setForeground(ThemeUtils.TEXT_PRIMARY);
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
            gridPanel.add(lbl);

            double cur = currentTargets.getOrDefault(cat, DatabaseManager.getDefaultCategoryTarget(cat));
            JSpinner sp = new JSpinner(new SpinnerNumberModel(cur, 0.0, 100000.0, 10.0));
            sp.setEditor(new JSpinner.NumberEditor(sp, "#,##0.00"));
            sp.setPreferredSize(new Dimension(110, 28));
            spinners.put(cat, sp);
            gridPanel.add(sp);
        }
        mainPanel.add(gridPanel);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnPanel.setOpaque(false);

        JButton saveBtn = new JButton("💾 Save Targets");
        ThemeUtils.styleButton(saveBtn, ThemeUtils.NEON_GREEN);
        saveBtn.addActionListener(e -> {
            try {
                for (Map.Entry<String, JSpinner> entry : spinners.entrySet()) {
                    Object val = entry.getValue().getValue();
                    double d = (val instanceof Number) ? ((Number) val).doubleValue() : 0.0;
                    db.setCategoryFullTarget(entry.getKey(), d);
                }
                updateCategoryTargetFromDb();
                if (isSingleCategoryMode()) {
                    generateReport();
                }
                dialog.dispose();
                JOptionPane.showMessageDialog(this, "Category full payment targets saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error saving targets: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        ThemeUtils.styleButton(cancelBtn, ThemeUtils.TEXT_SECONDARY);
        cancelBtn.addActionListener(e -> dialog.dispose());

        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);

        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public void refreshData() {
        cachedAllPayments = null;
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private List<String> programs = List.of();
            private List<String> years = List.of();

            @Override
            protected Void doInBackground() throws Exception {
                programs = db.getDistinctPrograms();
                years = db.getDistinctAcademicYears();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    String selectedProg = (String) programFilter.getSelectedItem();
                    programFilter.removeAllItems();
                    programFilter.addItem("All Programs");
                    for (String p : programs) {
                        programFilter.addItem(p);
                    }
                    if (selectedProg != null) {
                        programFilter.setSelectedItem(selectedProg);
                    }

                    String selectedYear = (String) yearFilter.getSelectedItem();
                    yearFilter.removeAllItems();
                    yearFilter.addItem("All AY");
                    for (String y : years) {
                        yearFilter.addItem(y);
                    }
                    String[] defaultYears = {"2026-2027", "2025-2026", "2024-2025"};
                    for (String dy : defaultYears) {
                        boolean exists = false;
                        for (int i = 0; i < yearFilter.getItemCount(); i++) {
                            if (dy.equals(yearFilter.getItemAt(i))) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) yearFilter.addItem(dy);
                    }
                    if (selectedYear != null) {
                        yearFilter.setSelectedItem(selectedYear);
                    }

                    // Auto-generate report immediately
                    generateReport();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private List<Payment> getFilteredPayments() throws Exception {
        if (cachedAllPayments == null) {
            cachedAllPayments = db.getAllPayments();
        }
        List<Payment> payments = cachedAllPayments;
        String dateFromStr = dateFromField.getText().trim();
        String dateToStr = dateToField.getText().trim();
        String program = (String) programFilter.getSelectedItem();

        LocalDate fromDate = null;
        LocalDate toDate = null;
        if (!dateFromStr.isEmpty()) {
            try { fromDate = LocalDate.parse(dateFromStr); } catch (Exception ignored) {}
        }
        if (!dateToStr.isEmpty()) {
            try { toDate = LocalDate.parse(dateToStr); } catch (Exception ignored) {}
        }

        final LocalDate fFrom = fromDate;
        final LocalDate fTo = toDate;
        final String fProg = (program != null && !"All Programs".equals(program)) ? program : null;

        return payments.stream()
            .filter(p -> {
                if (fProg != null && !fProg.equalsIgnoreCase(p.getProgram())) return false;
                if (fFrom != null && (p.getRemittanceDate() == null || p.getRemittanceDate().isBefore(fFrom))) return false;
                if (fTo != null && (p.getRemittanceDate() == null || p.getRemittanceDate().isAfter(fTo))) return false;
                return true;
            })
            .collect(Collectors.toList());
    }

    public void generateReport() {
        String reportType = (String) reportTypeCombo.getSelectedItem();
        if (reportType == null) return;

        statusLabel.setText("Computing report...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private List<Map<String, Object>> data = List.of();
            private String[] columns = new String[0];
            private double grandTotal = 0;

            @Override
            protected Void doInBackground() throws Exception {
                if (reportType.contains("Student Payment Report")) {
                    data = generateStudentPaymentReportSeparatedByTerm();
                    columns = new String[]{"Academic Year", "Term", "Student Code", "Name", "Program", "Receipt #", "Receipt AY", "Receipt Term", "Date", "Intel Fee", "T-Shirt", "Penalties", "CIT Night", "Received By", "Term Total"};
                } else if (reportType.contains("Single Category")) {
                    String cat = (String) categoryFilter.getSelectedItem();
                    if (cat == null) cat = "T-Shirt Sizing";
                    SingleCategoryResult res = generateSingleCategoryReport(cat);
                    data = res.data;
                    columns = res.columns;
                } else if (reportType.contains("Academic Term Report")) {
                    data = generateAcademicTermReport();
                    columns = new String[]{"Academic Year", "Term", "Intel Fee", "T-Shirt", "Penalties", "CIT Night", "Total Amount"};
                } else if (reportType.contains("Collection Summary")) {
                    data = generateCollectionSummary();
                    columns = new String[]{"Category", "Amount"};
                } else if (reportType.contains("Remittance Report")) {
                    data = generateRemittanceReport();
                    columns = new String[]{"Remittance Date", "Receipt Count", "Total Amount"};
                } else if (reportType.contains("Receiver Report")) {
                    data = generateReceiverReport();
                    columns = new String[]{"Received By", "Receipt Count", "Total Amount"};
                } else if (reportType.contains("Import Batch Report")) {
                    data = generateImportBatchReport();
                    columns = new String[]{"Batch Code", "Source", "Files", "Receipt Period", "Imported At", "Remittance Date", "Records", "New", "Duplicates", "Conflicts", "Errors", "Status"};
                }

                // Compute grand total for active financial columns
                for (Map<String, Object> row : data) {
                    Object tot = row.get("Total Amount");
                    if (tot == null) tot = row.get("Term Total");
                    if (tot == null) tot = row.get("Total Paid");
                    if (tot == null && row.containsKey("Amount") && !"TOTAL".equals(row.get("Category"))) tot = row.get("Amount");
                    if (tot instanceof Number n) {
                        grandTotal += n.doubleValue();
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    reportTableModel.setData(data, columns);
                    setupColumnRenderers();
                    statusLabel.setText(String.format("Report generated: %d records found", data.size()));
                    summaryLabel.setText(String.format("Total: ₱%,.2f", grandTotal));
                    if (recordCountLabel != null) {
                        recordCountLabel.setText(String.format("Records: %d", data.size()));
                    }
                } catch (Exception e) {
                    statusLabel.setText("Error generating report: " + e.getMessage());
                    if (recordCountLabel != null) {
                        recordCountLabel.setText("Records: 0");
                    }
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void setupColumnRenderers() {
        CyberCellRenderer defaultRenderer = new CyberCellRenderer(SwingConstants.LEFT, ThemeUtils.TEXT_PRIMARY);
        CyberCellRenderer centerCyanRenderer = new CyberCellRenderer(SwingConstants.CENTER, ThemeUtils.NEON_CYAN);
        CyberCellRenderer rightRenderer = new CyberCellRenderer(SwingConstants.RIGHT, ThemeUtils.TEXT_PRIMARY);
        CyberCurrencyRenderer currencyRenderer = new CyberCurrencyRenderer();
        CyberStatusCellRenderer statusRenderer = new CyberStatusCellRenderer();

        // Enable horizontal scrolling when table has many columns (e.g. 3+ payment installments)
        if (reportTable.getColumnCount() > 7) {
            reportTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        } else {
            reportTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        }

        for (int i = 0; i < reportTable.getColumnCount(); i++) {
            String name = reportTable.getColumnName(i);
            TableColumn col = reportTable.getColumnModel().getColumn(i);

            // Assign cell renderers
            if (name.contains("Amount") || name.contains("Total") || name.contains("Payment") || name.equals("Intel Fee") || name.equals("T-Shirt") || name.equals("Penalties") || name.equals("CIT Night")) {
                col.setCellRenderer(currencyRenderer);
            } else if (name.contains("Count") || name.contains("Records") || name.equals("New") || name.equals("Duplicates") || name.equals("Conflicts") || name.equals("Errors")) {
                col.setCellRenderer(rightRenderer);
            } else if (name.equals("Status")) {
                col.setCellRenderer(statusRenderer);
            } else if (name.contains("Date") || name.equals("Academic Year") || name.equals("Term") || name.contains("Receipt") || name.equals("Student Code") || name.equals("Batch Code") || name.equals("#")) {
                col.setCellRenderer(centerCyanRenderer);
            } else {
                col.setCellRenderer(defaultRenderer);
            }

            // Assign clean, proportional column widths
            if (name.equals("#")) {
                col.setPreferredWidth(45);
                col.setMaxWidth(60);
            } else if (name.contains("Receipt #")) {
                col.setPreferredWidth(100);
            } else if (name.equals("Student Name") || name.equals("Name")) {
                col.setPreferredWidth(190);
            } else if (name.equals("Program")) {
                col.setPreferredWidth(75);
            } else if (name.contains("Payment")) {
                col.setPreferredWidth(105);
            } else if (name.contains("Total") || name.contains("Amount")) {
                col.setPreferredWidth(110);
            } else if (name.equals("Status")) {
                col.setPreferredWidth(120);
            } else if (name.equals("Remarks")) {
                col.setPreferredWidth(180);
            } else if (name.contains("Date")) {
                col.setPreferredWidth(130);
            } else if (name.equals("Academic Year") || name.equals("Receipt AY")) {
                col.setPreferredWidth(105);
            } else if (name.equals("Term") || name.equals("Receipt Term")) {
                col.setPreferredWidth(115);
            } else if (name.equals("Received By")) {
                col.setPreferredWidth(120);
            } else {
                col.setPreferredWidth(110);
            }
        }
    }

    /**
     * Separates payments per term into individual, distinct records.
     * Even if a single receipt has charges from multiple terms, each term gets its own clean row and subtotal!
     */
    private List<Map<String, Object>> generateStudentPaymentReportSeparatedByTerm() throws Exception {
        List<Payment> payments = getFilteredPayments();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String selectedYear = (String) yearFilter.getSelectedItem();
        String selectedTerm = (String) termFilter.getSelectedItem();
        final String fYear = (selectedYear != null && !"All AY".equals(selectedYear)) ? selectedYear : null;
        final String fTerm = (selectedTerm != null && !"All Terms".equals(selectedTerm)) ? selectedTerm : null;

        List<Map<String, Object>> records = new ArrayList<>();
        Set<String> emittedReceiptKeys = new HashSet<>();

        for (Payment p : payments) {
            if (!"ACTIVE".equals(p.getStatus())) continue;

            // Group fee charges for this payment by (AY + "|||" + Term)
            class FeeSplit {
                double intelFee = 0;
                double tshirt = 0;
                double penalties = 0;
                double citNight = 0;
            }

            Map<String, FeeSplit> splits = new LinkedHashMap<>();

            // 1. Intel Fee
            if (p.getIntelFee() != null && p.getIntelFee() > 0) {
                String ay = p.getEffectiveAyForCategory("Intel Fee");
                String term = p.getEffectiveTermForCategory("Intel Fee").getLabel();
                String key = (ay != null ? ay : "-") + "|||" + term;
                splits.computeIfAbsent(key, k -> new FeeSplit()).intelFee = p.getIntelFee();
            }

            // 2. T-Shirt
            if (p.getTshirtSizing() != null && p.getTshirtSizing() > 0) {
                String ay = p.getEffectiveAyForCategory("T-Shirt");
                String term = p.getEffectiveTermForCategory("T-Shirt").getLabel();
                String key = (ay != null ? ay : "-") + "|||" + term;
                splits.computeIfAbsent(key, k -> new FeeSplit()).tshirt = p.getTshirtSizing();
            }

            // 3. Penalties
            if (p.getPenalties() != null && p.getPenalties() > 0) {
                String ay = p.getEffectiveAyForCategory("Penalties");
                String term = p.getEffectiveTermForCategory("Penalties").getLabel();
                String key = (ay != null ? ay : "-") + "|||" + term;
                splits.computeIfAbsent(key, k -> new FeeSplit()).penalties = p.getPenalties();
            }

            // 4. CIT Night
            if (p.getCitNight() != null && p.getCitNight() > 0) {
                String ay = p.getEffectiveAyForCategory("CIT Night");
                String term = p.getEffectiveTermForCategory("CIT Night").getLabel();
                String key = (ay != null ? ay : "-") + "|||" + term;
                splits.computeIfAbsent(key, k -> new FeeSplit()).citNight = p.getCitNight();
            }

            // If payment has 0 fees or unassigned empty row
            if (splits.isEmpty()) {
                String ay = p.getAcademicYear() != null ? p.getAcademicYear() : "-";
                String term = p.getChargeAcademicTerm() != null ? p.getChargeAcademicTerm().getLabel() : "Unassigned";
                String key = ay + "|||" + term;
                splits.put(key, new FeeSplit());
            }

            // Create a separate record for each term split
            for (Map.Entry<String, FeeSplit> entry : splits.entrySet()) {
                String[] parts = entry.getKey().split("\\|\\|\\|");
                String termAy = parts[0];
                String termName = parts[1];

                // Check filter criteria
                if (fYear != null && !fYear.equalsIgnoreCase(termAy)) continue;
                if (fTerm != null && !fTerm.equalsIgnoreCase(termName)) continue;

                FeeSplit fs = entry.getValue();
                double termSubtotal = fs.intelFee + fs.tshirt + fs.penalties + fs.citNight;

                // Keep each term split as its own payment line.  Amounts from
                // other terms are deliberately null (rendered blank), never
                // zero-valued, so they cannot be mistaken for collections in
                // the requested term.  Receipt metadata is shown once when a
                // receipt produces multiple term lines.
                String receiptKey = p.getReceiptKey().toString();
                boolean firstReceiptLine = emittedReceiptKeys.add(receiptKey);

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Academic Year", termAy);
                row.put("Term", termName);
                row.put("Student Code", p.getStudentId() != null ? p.getStudentId() : "-");
                row.put("Name", p.getName());
                row.put("Program", p.getProgram() != null ? p.getProgram() : "-");
                row.put("Receipt #", firstReceiptLine ? p.getReceiptNumber() : "");
                row.put("Receipt AY", firstReceiptLine ? (p.getReceiptAcademicYear() != null ? p.getReceiptAcademicYear() : "-") : "");
                row.put("Receipt Term", firstReceiptLine ? p.getReceiptTerm().getLabel() : "");
                row.put("Date", p.getRemittanceDate() != null ? p.getRemittanceDate().format(fmt) : "-");
                row.put("Intel Fee", fs.intelFee > 0 ? fs.intelFee : null);
                row.put("T-Shirt", fs.tshirt > 0 ? fs.tshirt : null);
                row.put("Penalties", fs.penalties > 0 ? fs.penalties : null);
                row.put("CIT Night", fs.citNight > 0 ? fs.citNight : null);
                row.put("Received By", p.getReceivedBy() != null ? p.getReceivedBy() : "-");
                row.put("Term Total", termSubtotal);
                records.add(row);
            }
        }

        return records;
    }

    static class SingleCategoryResult {
        final List<Map<String, Object>> data;
        final String[] columns;

        SingleCategoryResult(List<Map<String, Object>> data, String[] columns) {
            this.data = data;
            this.columns = columns;
        }
    }

    static String getOrdinalSuffix(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) return "th";
        switch (n % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }

    private double getCategoryAmount(Payment p, String category) {
        if (category == null) return 0.0;
        String c = category.trim().toLowerCase();
        if (c.contains("intel")) {
            return p.getIntelFee() != null ? p.getIntelFee() : 0.0;
        } else if (c.contains("shirt") || c.contains("tshirt")) {
            return p.getTshirtSizing() != null ? p.getTshirtSizing() : 0.0;
        } else if (c.contains("penalt")) {
            return p.getPenalties() != null ? p.getPenalties() : 0.0;
        } else if (c.contains("night")) {
            return p.getCitNight() != null ? p.getCitNight() : 0.0;
        }
        return 0.0;
    }

    /**
     * Generates an alphabetical roster for a single category of payment.
     * Multiple payments for the same category (e.g. downpayment + balance, or 3+ installments) are shown
     * together on the same record line alongside the first payment.
     */
    SingleCategoryResult generateSingleCategoryReport(String category) throws Exception {
        List<Payment> payments = getFilteredPayments();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String selectedYear = (String) yearFilter.getSelectedItem();
        String selectedTerm = (String) termFilter.getSelectedItem();
        final String fYear = (selectedYear != null && !"All AY".equals(selectedYear)) ? selectedYear : null;
        final String fTerm = (selectedTerm != null && !"All Terms".equals(selectedTerm)) ? selectedTerm : null;

        class StudentGroup {
            String studentId;
            String name;
            String program;
            final List<Payment> payments = new ArrayList<>();
        }

        // Pre-build mapping of normalized name to studentId to ensure all payments for the same student group together
        Map<String, String> normNameToStudentId = new HashMap<>();
        for (Payment p : payments) {
            String sId = p.getStudentId() != null ? p.getStudentId().trim() : "";
            String norm = p.getName() != null ? NameNormalizer.normalize(p.getName()) : "";
            if (!sId.isEmpty() && !norm.isEmpty()) {
                normNameToStudentId.putIfAbsent(norm, sId);
            }
        }

        Map<String, StudentGroup> groupMap = new LinkedHashMap<>();

        for (Payment p : payments) {
            if (!p.isActive()) continue;
            double amt = getCategoryAmount(p, category);
            if (amt <= 0) continue;

            if (fYear != null) {
                String ay = p.getEffectiveAyForCategory(category);
                if (ay == null || !ay.equalsIgnoreCase(fYear)) continue;
            }
            if (fTerm != null) {
                ChargeAcademicTerm catTerm = p.getEffectiveTermForCategory(category);
                String termLabel = catTerm != null ? catTerm.getLabel() : "Unassigned";
                if (!termLabel.equalsIgnoreCase(fTerm)) continue;
            }

            String sId = p.getStudentId() != null ? p.getStudentId().trim() : "";
            String norm = p.getName() != null ? NameNormalizer.normalize(p.getName()) : "";
            if (sId.isEmpty() && !norm.isEmpty() && normNameToStudentId.containsKey(norm)) {
                sId = normNameToStudentId.get(norm);
            }

            String key = !sId.isEmpty()
                ? "id:" + sId.toUpperCase()
                : (!norm.isEmpty() ? "name:" + norm : "rec:" + p.getId());

            StudentGroup group = groupMap.computeIfAbsent(key, k -> {
                StudentGroup sg = new StudentGroup();
                sg.studentId = p.getStudentId();
                sg.name = p.getName() != null ? p.getName().trim() : "Unknown";
                sg.program = p.getProgram() != null ? p.getProgram().trim() : "-";
                return sg;
            });

            if ((group.studentId == null || group.studentId.isBlank()) && !sId.isEmpty()) {
                group.studentId = sId;
            }
            if ((group.name == null || group.name.equalsIgnoreCase("Unknown")) && p.getName() != null) {
                group.name = p.getName().trim();
            }
            if ((group.program == null || "-".equals(group.program)) && p.getProgram() != null && !p.getProgram().isBlank()) {
                group.program = p.getProgram().trim();
            }

            group.payments.add(p);
        }

        // Sort groups alphabetically by student name (A-Z)
        List<StudentGroup> sortedGroups = new ArrayList<>(groupMap.values());
        sortedGroups.sort(Comparator.comparing(
            g -> g.name != null ? g.name.trim() : "",
            String.CASE_INSENSITIVE_ORDER
        ));

        // Determine max payment count among all students in this report
        int maxPayments = 1;
        for (StudentGroup g : sortedGroups) {
            if (g.payments.size() > maxPayments) {
                maxPayments = g.payments.size();
            }
        }
        int paymentSlots = Math.max(2, maxPayments);
        double categoryTarget = db.getCategoryFullTarget(category);

        // Build columns dynamically
        List<String> colList = new ArrayList<>();
        colList.add("#");
        colList.add("Receipt #");
        colList.add("Student Name");
        colList.add("Program");
        colList.add("1st Payment");
        for (int i = 2; i <= paymentSlots; i++) {
            String suffix = getOrdinalSuffix(i);
            colList.add(i + suffix + " Receipt #");
            colList.add(i + suffix + " Payment");
        }
        colList.add("Total Paid");
        colList.add("Status");
        colList.add("Remarks");
        colList.add("Date");

        String[] columns = colList.toArray(new String[0]);
        List<Map<String, Object>> records = new ArrayList<>();

        int rowNum = 0;
        for (StudentGroup g : sortedGroups) {
            rowNum++;
            // Sort payments chronologically, then by receipt number
            g.payments.sort(Comparator
                .comparing(Payment::getRemittanceDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(Payment::getReceiptNumber)
                .thenComparingInt(Payment::getId)
            );

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("#", rowNum);

            Payment p1 = g.payments.get(0);
            row.put("Receipt #", p1.getReceiptNumber() > 0 ? p1.getReceiptNumber() : null);
            row.put("Student Name", g.name);
            row.put("Program", g.program != null && !g.program.isBlank() ? g.program : "-");
            row.put("1st Payment", getCategoryAmount(p1, category));

            for (int i = 2; i <= paymentSlots; i++) {
                String suffix = getOrdinalSuffix(i);
                String recCol = i + suffix + " Receipt #";
                String amtCol = i + suffix + " Payment";
                if (g.payments.size() >= i) {
                    Payment pi = g.payments.get(i - 1);
                    row.put(recCol, pi.getReceiptNumber() > 0 ? pi.getReceiptNumber() : null);
                    row.put(amtCol, getCategoryAmount(pi, category));
                } else {
                    row.put(recCol, null);
                    row.put(amtCol, null);
                }
            }

            double totalPaid = g.payments.stream()
                .mapToDouble(p -> getCategoryAmount(p, category))
                .sum();
            row.put("Total Paid", totalPaid);

            String status;
            if (categoryTarget <= 0) {
                status = "Paid";
            } else if (Math.abs(totalPaid - categoryTarget) < 0.01) {
                status = "Fully Paid";
            } else if (totalPaid > categoryTarget + 0.01) {
                status = "Overpaid";
            } else if (totalPaid > 0) {
                status = "Partially Paid";
            } else {
                status = "Unpaid";
            }
            row.put("Status", status);

            // Deduplicated remarks
            List<String> rems = new ArrayList<>();
            for (Payment p : g.payments) {
                if (p.getRemarks() != null && !p.getRemarks().trim().isEmpty()) {
                    String r = p.getRemarks().trim();
                    if (!rems.contains(r)) rems.add(r);
                }
            }
            row.put("Remarks", String.join("; ", rems));

            // Deduplicated dates
            List<String> dates = new ArrayList<>();
            for (Payment p : g.payments) {
                if (p.getRemittanceDate() != null) {
                    String dStr = p.getRemittanceDate().format(fmt);
                    if (!dates.contains(dStr)) dates.add(dStr);
                }
            }
            row.put("Date", dates.isEmpty() ? "-" : String.join(", ", dates));

            records.add(row);
        }

        return new SingleCategoryResult(records, columns);
    }

    /**
     * Itemized Academic Term Report:
     * Calculates fee collections strictly separated per (AY, Term).
     */
    private List<Map<String, Object>> generateAcademicTermReport() throws Exception {
        List<Payment> payments = getFilteredPayments();

        String selectedYear = (String) yearFilter.getSelectedItem();
        String selectedTerm = (String) termFilter.getSelectedItem();
        final String fYear = (selectedYear != null && !"All AY".equals(selectedYear)) ? selectedYear : null;
        final String fTerm = (selectedTerm != null && !"All Terms".equals(selectedTerm)) ? selectedTerm : null;

        class TermBucket {
            double intelFee = 0;
            double tshirt = 0;
            double penalties = 0;
            double citNight = 0;
        }

        Map<String, TermBucket> buckets = new HashMap<>();

        for (Payment p : payments) {
            if (!"ACTIVE".equals(p.getStatus())) continue;

            if (p.getIntelFee() != null && p.getIntelFee() > 0) {
                String ay = p.getEffectiveAyForCategory("Intel Fee");
                String term = p.getEffectiveTermForCategory("Intel Fee").getLabel();
                String key = (ay != null ? ay : "-") + "|||" + term;
                buckets.computeIfAbsent(key, k -> new TermBucket()).intelFee += p.getIntelFee();
            }

            if (p.getTshirtSizing() != null && p.getTshirtSizing() > 0) {
                String ay = p.getEffectiveAyForCategory("T-Shirt");
                String term = p.getEffectiveTermForCategory("T-Shirt").getLabel();
                String key = (ay != null ? ay : "-") + "|||" + term;
                buckets.computeIfAbsent(key, k -> new TermBucket()).tshirt += p.getTshirtSizing();
            }

            if (p.getPenalties() != null && p.getPenalties() > 0) {
                String ay = p.getEffectiveAyForCategory("Penalties");
                String term = p.getEffectiveTermForCategory("Penalties").getLabel();
                String key = (ay != null ? ay : "-") + "|||" + term;
                buckets.computeIfAbsent(key, k -> new TermBucket()).penalties += p.getPenalties();
            }

            if (p.getCitNight() != null && p.getCitNight() > 0) {
                String ay = p.getEffectiveAyForCategory("CIT Night");
                String term = p.getEffectiveTermForCategory("CIT Night").getLabel();
                String key = (ay != null ? ay : "-") + "|||" + term;
                buckets.computeIfAbsent(key, k -> new TermBucket()).citNight += p.getCitNight();
            }
        }

        // If no active fees were found, add an empty/unassigned bucket
        if (buckets.isEmpty()) {
            buckets.put("-|||Unassigned", new TermBucket());
        }

        List<Map<String, Object>> result = new ArrayList<>();

        List<String> sortedKeys = buckets.keySet().stream()
            .sorted((k1, k2) -> {
                String[] p1 = k1.split("\\|\\|\\|");
                String[] p2 = k2.split("\\|\\|\\|");
                int cmpYear = p2[0].compareToIgnoreCase(p1[0]);
                if (cmpYear != 0) return cmpYear;
                return p1[1].compareToIgnoreCase(p2[1]);
            })
            .collect(Collectors.toList());

        for (String key : sortedKeys) {
            String[] parts = key.split("\\|\\|\\|");
            String ay = parts[0];
            String term = parts[1];

            if (fYear != null && !fYear.equalsIgnoreCase(ay)) continue;
            if (fTerm != null && !fTerm.equalsIgnoreCase(term)) continue;

            TermBucket b = buckets.get(key);
            double total = b.intelFee + b.tshirt + b.penalties + b.citNight;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Academic Year", ay);
            row.put("Term", term);
            row.put("Intel Fee", b.intelFee);
            row.put("T-Shirt", b.tshirt);
            row.put("Penalties", b.penalties);
            row.put("CIT Night", b.citNight);
            row.put("Total Amount", total);
            result.add(row);
        }

        return result;
    }

    private List<Map<String, Object>> generateCollectionSummary() throws Exception {
        List<Payment> payments = getFilteredPayments();

        double intelTotal = payments.stream()
            .filter(p -> p.isActive() || p.isRefunded())
            .mapToDouble(p -> (p.getIntelFee() != null ? p.getIntelFee() : 0) * (p.isRefunded() ? -1.0 : 1.0)).sum();
        double tshirtTotal = payments.stream()
            .filter(p -> p.isActive() || p.isRefunded())
            .mapToDouble(p -> (p.getTshirtSizing() != null ? p.getTshirtSizing() : 0) * (p.isRefunded() ? -1.0 : 1.0)).sum();
        double penaltiesTotal = payments.stream()
            .filter(p -> p.isActive() || p.isRefunded())
            .mapToDouble(p -> (p.getPenalties() != null ? p.getPenalties() : 0) * (p.isRefunded() ? -1.0 : 1.0)).sum();
        double citTotal = payments.stream()
            .filter(p -> p.isActive() || p.isRefunded())
            .mapToDouble(p -> (p.getCitNight() != null ? p.getCitNight() : 0) * (p.isRefunded() ? -1.0 : 1.0)).sum();
        double grandTotal = payments.stream()
            .filter(p -> p.isActive() || p.isRefunded())
            .mapToDouble(Payment::getTotalAmount).sum();

        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("Category", "Intel Fee");
        row1.put("Amount", intelTotal);

        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("Category", "T-Shirt Sizing");
        row2.put("Amount", tshirtTotal);

        Map<String, Object> row3 = new LinkedHashMap<>();
        row3.put("Category", "Penalties");
        row3.put("Amount", penaltiesTotal);

        Map<String, Object> row4 = new LinkedHashMap<>();
        row4.put("Category", "CIT Night");
        row4.put("Amount", citTotal);

        Map<String, Object> row5 = new LinkedHashMap<>();
        row5.put("Category", "TOTAL");
        row5.put("Amount", grandTotal);

        return List.of(row1, row2, row3, row4, row5);
    }

    private List<Map<String, Object>> generateRemittanceReport() throws Exception {
        List<Payment> payments = getFilteredPayments();

        Map<LocalDate, List<Payment>> byDate = payments.stream()
            .filter(p -> p.getRemittanceDate() != null && (p.isActive() || p.isRefunded()))
            .collect(Collectors.groupingBy(Payment::getRemittanceDate));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return byDate.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Remittance Date", e.getKey().format(fmt));
                row.put("Receipt Count", e.getValue().size());
                double total = e.getValue().stream().mapToDouble(Payment::getTotalAmount).sum();
                row.put("Total Amount", total);
                return row;
            })
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> generateReceiverReport() throws Exception {
        List<Payment> payments = getFilteredPayments();

        Map<String, List<Payment>> byReceiver = payments.stream()
            .filter(p -> p.isActive() || p.isRefunded())
            .collect(Collectors.groupingBy(p -> p.getReceivedBy() != null ? p.getReceivedBy() : "Unknown"));

        return byReceiver.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Received By", e.getKey());
                row.put("Receipt Count", e.getValue().size());
                double total = e.getValue().stream().mapToDouble(Payment::getTotalAmount).sum();
                row.put("Total Amount", total);
                return row;
            })
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> generateImportBatchReport() throws Exception {
        List<com.payment.ImportBatch> batches = db.getAllImportBatches();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        DateTimeFormatter remFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String dateFromStr = dateFromField.getText().trim();
        String dateToStr = dateToField.getText().trim();
        LocalDate fromDate = null;
        LocalDate toDate = null;
        if (!dateFromStr.isEmpty()) {
            try { fromDate = LocalDate.parse(dateFromStr); } catch (Exception ignored) {}
        }
        if (!dateToStr.isEmpty()) {
            try { toDate = LocalDate.parse(dateToStr); } catch (Exception ignored) {}
        }

        final LocalDate fFrom = fromDate;
        final LocalDate fTo = toDate;

        return batches.stream()
            .filter(b -> {
                if (fFrom != null && b.getRemittanceDate() != null && b.getRemittanceDate().isBefore(fFrom)) return false;
                if (fTo != null && b.getRemittanceDate() != null && b.getRemittanceDate().isAfter(fTo)) return false;
                return true;
            })
            .map(b -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Batch Code", b.getBatchCode());
                row.put("Source", b.getFileName());
                row.put("Files", b.getFileCount());
                row.put("Receipt Period", b.getReceiptPeriodDisplay());
                row.put("Imported At", b.getImportedAt() != null ? b.getImportedAt().format(fmt) : "-");
                row.put("Remittance Date", b.getRemittanceDateDisplay());
                row.put("Records", b.getTotalRows());
                row.put("New", b.getNewRecords());
                row.put("Duplicates", b.getDuplicateRecords());
                row.put("Conflicts", b.getConflictRecords());
                row.put("Errors", b.getErrorRecords());
                row.put("Status", b.getStatus());
                return row;
            }).collect(Collectors.toList());
    }

    private void exportCSV() {
        if (reportTableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No report data to export. Generate a report first.", "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV Files", "csv"));
        String reportType = (String) reportTypeCombo.getSelectedItem();
        String safeName = reportType != null ? reportType.replaceAll("[^a-zA-Z0-9]", "_") : "Report";
        if (isSingleCategoryMode() && categoryFilter != null && categoryFilter.getSelectedItem() != null) {
            safeName += "_" + categoryFilter.getSelectedItem().toString().replaceAll("[^a-zA-Z0-9]", "_");
        }
        fileChooser.setSelectedFile(new File(safeName + "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(fileChooser.getSelectedFile()))) {
                for (int i = 0; i < reportTableModel.getColumnCount(); i++) {
                    if (i > 0) writer.print(",");
                    writer.print("\"" + reportTableModel.getColumnName(i) + "\"");
                }
                writer.println();

                // Export in the current sorted order shown in the panel
                for (int viewRow = 0; viewRow < reportTable.getRowCount(); viewRow++) {
                    int modelRow = reportTable.convertRowIndexToModel(viewRow);
                    for (int col = 0; col < reportTableModel.getColumnCount(); col++) {
                        if (col > 0) writer.print(",");
                        Object val = reportTableModel.getValueAt(modelRow, col);
                        String str = val != null ? val.toString() : "";
                        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
                            str = "\"" + str.replace("\"", "\"\"") + "\"";
                        }
                        writer.print(str);
                    }
                    writer.println();
                }
                statusLabel.setText("Exported successfully to " + fileChooser.getSelectedFile().getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportExcel() {
        if (reportTableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No report data to export. Generate a report first.", "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel Files (*.xlsx)", "xlsx"));
        String reportType = (String) reportTypeCombo.getSelectedItem();
        String safeName = reportType != null ? reportType.replaceAll("[^a-zA-Z0-9]", "_") : "Report";
        if (isSingleCategoryMode() && categoryFilter != null && categoryFilter.getSelectedItem() != null) {
            safeName += "_" + categoryFilter.getSelectedItem().toString().replaceAll("[^a-zA-Z0-9]", "_");
        }
        fileChooser.setSelectedFile(new File(safeName + "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try (var fos = new java.io.FileOutputStream(fileChooser.getSelectedFile());
                 var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {

                var sheet = workbook.createSheet("Report");

                var headerFont = workbook.createFont();
                headerFont.setBold(true);
                var headerStyle = workbook.createCellStyle();
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

                var headerRow = sheet.createRow(0);
                for (int i = 0; i < reportTableModel.getColumnCount(); i++) {
                    var cell = headerRow.createCell(i);
                    cell.setCellValue(reportTableModel.getColumnName(i));
                    cell.setCellStyle(headerStyle);
                }

                // Export in the current sorted order shown in the panel
                for (int viewRow = 0; viewRow < reportTable.getRowCount(); viewRow++) {
                    int modelRow = reportTable.convertRowIndexToModel(viewRow);
                    var dataRow = sheet.createRow(viewRow + 1);
                    for (int col = 0; col < reportTableModel.getColumnCount(); col++) {
                        Object val = reportTableModel.getValueAt(modelRow, col);
                        var cell = dataRow.createCell(col);
                        if (val instanceof Number) {
                            cell.setCellValue(((Number) val).doubleValue());
                        } else {
                            cell.setCellValue(val != null ? val.toString() : "");
                        }
                    }
                }

                for (int i = 0; i < reportTableModel.getColumnCount(); i++) {
                    sheet.autoSizeColumn(i);
                }

                workbook.write(fos);
                statusLabel.setText("Exported successfully to " + fileChooser.getSelectedFile().getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }

    // --- Table Model & Cell Renderers ---

    private static class ReportTableModel extends AbstractTableModel {
        private List<Map<String, Object>> data = List.of();
        private String[] columns = new String[0];

        public void setData(List<Map<String, Object>> data, String[] columns) {
            this.data = data != null ? data : List.of();
            this.columns = columns != null ? columns : new String[0];
            fireTableStructureChanged();
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return column < columns.length ? columns[column] : "Column " + column;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex >= data.size() || columnIndex >= columns.length) return null;
            Map<String, Object> row = data.get(rowIndex);
            return row.get(columns[columnIndex]);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (data.isEmpty()) return String.class;
            for (Map<String, Object> row : data) {
                Object val = row.get(columns[columnIndex]);
                if (val != null) {
                    if (val instanceof Integer) return Integer.class;
                    if (val instanceof Number) return Double.class;
                    return String.class;
                }
            }
            return String.class;
        }
    }

    private static class CyberCellRenderer extends DefaultTableCellRenderer {
        private final Color customFg;

        public CyberCellRenderer(int alignment, Color fg) {
            setHorizontalAlignment(alignment);
            this.customFg = fg;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (isSelected) {
                setBackground(new Color(30, 58, 100));
                setForeground(ThemeUtils.NEON_CYAN);
            } else {
                setBackground(row % 2 == 0 ? ThemeUtils.BG_SURFACE : new Color(17, 24, 37));
                setForeground(customFg != null ? customFg : ThemeUtils.TEXT_PRIMARY);
            }
            setFont(getFont().deriveFont(Font.PLAIN, 12f));
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            return this;
        }
    }

    private static class CyberCurrencyRenderer extends DefaultTableCellRenderer {
        private final java.text.DecimalFormat format = new java.text.DecimalFormat("₱#,##0.00");

        public CyberCurrencyRenderer() {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof Number num) {
                setText(format.format(num.doubleValue()));
            } else if (value == null) {
                setText("");
            }

            if (isSelected) {
                setBackground(new Color(30, 58, 100));
                setForeground(ThemeUtils.NEON_CYAN);
            } else {
                setBackground(row % 2 == 0 ? ThemeUtils.BG_SURFACE : new Color(17, 24, 37));
                setForeground(ThemeUtils.NEON_GREEN);
            }
            setFont(getFont().deriveFont(Font.BOLD, 12f));
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            return this;
        }
    }

    private static class CyberStatusCellRenderer extends DefaultTableCellRenderer {
        public CyberStatusCellRenderer() {
            setHorizontalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (isSelected) {
                setBackground(new Color(30, 58, 100));
            } else {
                setBackground(row % 2 == 0 ? ThemeUtils.BG_SURFACE : new Color(17, 24, 37));
            }

            if (value != null) {
                String s = value.toString().trim();
                setText(s);
                if ("Fully Paid".equalsIgnoreCase(s) || "ACTIVE".equalsIgnoreCase(s) || "COMPLETED".equalsIgnoreCase(s)) {
                    setForeground(ThemeUtils.NEON_GREEN);
                } else if ("Partially Paid".equalsIgnoreCase(s) || "PENDING".equalsIgnoreCase(s)) {
                    setForeground(ThemeUtils.NEON_AMBER);
                } else if ("Overpaid".equalsIgnoreCase(s)) {
                    setForeground(new Color(236, 72, 153)); // Neon Pink
                } else if ("VOID".equalsIgnoreCase(s) || "CONFLICT".equalsIgnoreCase(s) || "ERROR".equalsIgnoreCase(s) || "Unpaid".equalsIgnoreCase(s)) {
                    setForeground(ThemeUtils.NEON_ROSE);
                } else if ("REFUNDED".equalsIgnoreCase(s)) {
                    setForeground(ThemeUtils.NEON_PURPLE);
                } else {
                    setForeground(ThemeUtils.NEON_CYAN);
                }
            } else {
                setText("-");
                setForeground(ThemeUtils.TEXT_MUTED);
            }
            setFont(getFont().deriveFont(Font.BOLD, 12f));
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            return this;
        }
    }
}
