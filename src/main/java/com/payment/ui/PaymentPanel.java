package com.payment.ui;

import com.payment.ChargeAcademicTerm;
import com.payment.Payment;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Payments Panel - View and manage payment records with support for
 * itemized per-fee category term & academic year assignments within each receipt.
 */
public class PaymentPanel extends JPanel {

    private final DatabaseManager db;

    private JTable paymentTable;
    private PaymentTableModel paymentTableModel;
    private JTextField searchField;
    private JComboBox<String> programFilter;
    private JComboBox<String> yearFilter;
    private JComboBox<String> termFilter;
    private JComboBox<String> statusFilter;
    private JTextField receiptField;
    private JLabel statusLabel;
    private JLabel summaryLabel;

    public PaymentPanel() {
        this.db = DatabaseManager.getInstance();
        initializeUI();
        refreshData();
    }

    private JLabel createToolLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 11f));
        l.setForeground(ThemeUtils.TEXT_SECONDARY);
        return l;
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(ThemeUtils.BG_DEEPEST);

        // Header container (Title + Controls)
        JPanel headerPanel = new JPanel(new BorderLayout(0, 10));
        headerPanel.setOpaque(false);

        // Title with futuristic gradient banner
        JPanel titleBanner = ThemeUtils.createGradientPanel(new Color(15, 23, 42), new Color(10, 14, 26));
        titleBanner.setLayout(new BorderLayout());
        titleBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, ThemeUtils.NEON_CYAN),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLabel = new JLabel("💳 Payment Ledger & Fee Breakdown");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        titleLabel.setForeground(ThemeUtils.NEON_CYAN);
        titleBanner.add(titleLabel, BorderLayout.WEST);

        JLabel subtitleLabel = new JLabel("Double-click any record to assign individual fee terms");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        subtitleLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        titleBanner.add(subtitleLabel, BorderLayout.EAST);

        headerPanel.add(titleBanner, BorderLayout.NORTH);

        // Toolbar Card
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolBar.setBackground(ThemeUtils.BG_CARD);
        toolBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        toolBar.add(createToolLabel("Search:"));
        searchField = new JTextField(14);
        searchField.setToolTipText("Search by student name or code...");
        searchField.addActionListener(e -> filterPayments());
        toolBar.add(searchField);

        toolBar.add(createToolLabel("Receipt #:"));
        receiptField = new JTextField(7);
        receiptField.addActionListener(e -> filterPayments());
        toolBar.add(receiptField);

        toolBar.add(createToolLabel("Program:"));
        programFilter = new JComboBox<>(new String[]{"All Programs"});
        programFilter.addActionListener(e -> filterPayments());
        toolBar.add(programFilter);

        toolBar.add(createToolLabel("AY:"));
        yearFilter = new JComboBox<>(new String[]{"All AY"});
        yearFilter.addActionListener(e -> filterPayments());
        toolBar.add(yearFilter);

        toolBar.add(createToolLabel("Term:"));
        termFilter = new JComboBox<>(new String[]{
            "All Terms",
            ChargeAcademicTerm.FIRST_SEM.getLabel(),
            ChargeAcademicTerm.SECOND_SEM.getLabel(),
            ChargeAcademicTerm.SUMMER.getLabel(),
            ChargeAcademicTerm.CURRENT.getLabel(),
            ChargeAcademicTerm.PREVIOUS.getLabel(),
            ChargeAcademicTerm.UNASSIGNED.getLabel()
        });
        termFilter.addActionListener(e -> filterPayments());
        toolBar.add(termFilter);

        toolBar.add(createToolLabel("Status:"));
        statusFilter = new JComboBox<>(new String[]{"All", "ACTIVE", "VOID"});
        statusFilter.addActionListener(e -> filterPayments());
        toolBar.add(statusFilter);

        JButton assignTermButton = new JButton("⚡ Assign Fee Terms...");
        assignTermButton.setToolTipText("Assign Academic Year & Term to receipt or individual fees");
        assignTermButton.addActionListener(e -> openAssignTermDialogForSelected());
        toolBar.add(assignTermButton);

        JButton refreshButton = new JButton("🔄 Refresh");
        refreshButton.addActionListener(e -> refreshData());
        toolBar.add(refreshButton);

        headerPanel.add(toolBar, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // Payment table
        paymentTableModel = new PaymentTableModel();
        paymentTable = new JTable(paymentTableModel);
        paymentTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        ThemeUtils.applyTableTheme(paymentTable);
        paymentTable.getTableHeader().setFont(paymentTable.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        paymentTable.setFont(paymentTable.getFont().deriveFont(Font.PLAIN, 12f));

        // Column widths
        paymentTable.getColumnModel().getColumn(0).setPreferredWidth(45);   // #
        paymentTable.getColumnModel().getColumn(1).setPreferredWidth(80);   // Receipt
        paymentTable.getColumnModel().getColumn(2).setPreferredWidth(100);  // Student Code
        paymentTable.getColumnModel().getColumn(3).setPreferredWidth(160);  // Name
        paymentTable.getColumnModel().getColumn(4).setPreferredWidth(75);   // Program
        paymentTable.getColumnModel().getColumn(5).setPreferredWidth(90);   // Date
        paymentTable.getColumnModel().getColumn(6).setPreferredWidth(75);   // Intel Fee
        paymentTable.getColumnModel().getColumn(7).setPreferredWidth(75);   // T-Shirt
        paymentTable.getColumnModel().getColumn(8).setPreferredWidth(75);   // Penalties
        paymentTable.getColumnModel().getColumn(9).setPreferredWidth(75);   // CIT Night
        paymentTable.getColumnModel().getColumn(10).setPreferredWidth(85);  // Received By
        paymentTable.getColumnModel().getColumn(11).setPreferredWidth(100); // AY
        paymentTable.getColumnModel().getColumn(12).setPreferredWidth(115); // Charge Term
        paymentTable.getColumnModel().getColumn(13).setPreferredWidth(70);  // Status
        paymentTable.getColumnModel().getColumn(14).setPreferredWidth(90);  // Total

        // Custom renderers
        CurrencyCellRenderer currencyRenderer = new CurrencyCellRenderer();
        paymentTable.getColumnModel().getColumn(6).setCellRenderer(currencyRenderer);
        paymentTable.getColumnModel().getColumn(7).setCellRenderer(currencyRenderer);
        paymentTable.getColumnModel().getColumn(8).setCellRenderer(currencyRenderer);
        paymentTable.getColumnModel().getColumn(9).setCellRenderer(currencyRenderer);
        paymentTable.getColumnModel().getColumn(14).setCellRenderer(currencyRenderer);
        paymentTable.getColumnModel().getColumn(13).setCellRenderer(new StatusCellRenderer());

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        paymentTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        paymentTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        paymentTable.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);
        paymentTable.getColumnModel().getColumn(11).setCellRenderer(centerRenderer);
        paymentTable.getColumnModel().getColumn(12).setCellRenderer(centerRenderer);

        // Editor for Academic Year column
        JComboBox<String> yearCombo = new JComboBox<>(new String[]{
            "2023-2024", "2024-2025", "2025-2026", "2026-2027", "2027-2028"
        });
        yearCombo.setEditable(true);
        paymentTable.getColumnModel().getColumn(11).setCellEditor(new DefaultCellEditor(yearCombo));

        // Editor for Charge Term column
        JComboBox<String> termComboBox = new JComboBox<>(new String[]{
            ChargeAcademicTerm.FIRST_SEM.getLabel(),
            ChargeAcademicTerm.SECOND_SEM.getLabel(),
            ChargeAcademicTerm.SUMMER.getLabel(),
            ChargeAcademicTerm.CURRENT.getLabel(),
            ChargeAcademicTerm.PREVIOUS.getLabel(),
            ChargeAcademicTerm.UNASSIGNED.getLabel()
        });
        paymentTable.getColumnModel().getColumn(12).setCellEditor(new DefaultCellEditor(termComboBox));

        // Real-time filtering listeners with debounce
        javax.swing.Timer filterDebounceTimer = new javax.swing.Timer(150, e -> filterPayments());
        filterDebounceTimer.setRepeats(false);

        javax.swing.event.DocumentListener docListener = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterDebounceTimer.restart(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterDebounceTimer.restart(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterDebounceTimer.restart(); }
        };
        searchField.getDocument().addDocumentListener(docListener);
        receiptField.getDocument().addDocumentListener(docListener);

        // Double-click row listener to open itemized term dialog
        paymentTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && paymentTable.getSelectedRow() >= 0) {
                    openAssignTermDialogForSelected();
                }
            }
        });

        // Right-click context menu
        setupContextMenu();

        // Row sorter
        TableRowSorter<PaymentTableModel> sorter = new TableRowSorter<>(paymentTableModel);
        paymentTable.setRowSorter(sorter);

        JScrollPane scrollPane = new JScrollPane(paymentTable);
        add(scrollPane, BorderLayout.CENTER);

        // Status bar with summary
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        statusLabel = new JLabel("Loading...");
        statusPanel.add(statusLabel, BorderLayout.WEST);

        summaryLabel = new JLabel("Total: ₱0.00");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD, 14f));
        summaryLabel.setForeground(new Color(166, 227, 161)); // Bright green in dark theme
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        statusPanel.add(summaryLabel, BorderLayout.EAST);

        add(statusPanel, BorderLayout.SOUTH);
    }

    private void setupContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem assignItem = new JMenuItem("Assign Terms / AY (Receipt & Fee Items)...");
        JMenuItem voidItem = new JMenuItem("Void Payment");
        JMenuItem unvoidItem = new JMenuItem("Reactivate Payment (Unvoid)");

        JMenu termMenu = new JMenu("Quick Assign Receipt Term");
        for (ChargeAcademicTerm term : ChargeAcademicTerm.values()) {
            JMenuItem item = new JMenuItem(term.getLabel());
            item.addActionListener(e -> setChargeTermForSelected(term));
            termMenu.add(item);
        }

        assignItem.addActionListener(e -> openAssignTermDialogForSelected());
        voidItem.addActionListener(e -> voidSelectedPayment());
        unvoidItem.addActionListener(e -> unvoidSelectedPayment());

        JMenuItem copyReceiptItem = new JMenuItem("Copy Receipt Number");
        JMenuItem copyStudentCodeItem = new JMenuItem("Copy Student Code");
        copyReceiptItem.addActionListener(e -> copySelectedCellValue(1));
        copyStudentCodeItem.addActionListener(e -> copySelectedCellValue(2));

        popupMenu.add(assignItem);
        popupMenu.add(termMenu);
        popupMenu.addSeparator();
        popupMenu.add(voidItem);
        popupMenu.add(unvoidItem);
        popupMenu.addSeparator();
        popupMenu.add(copyReceiptItem);
        popupMenu.add(copyStudentCodeItem);

        paymentTable.setComponentPopupMenu(popupMenu);
        paymentTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = paymentTable.rowAtPoint(e.getPoint());
                if (row >= 0 && !paymentTable.isRowSelected(row)) {
                    paymentTable.setRowSelectionInterval(row, row);
                }
                if (row >= 0) {
                    int modelRow = paymentTable.convertRowIndexToModel(row);
                    Payment p = paymentTableModel.getPayment(modelRow);
                    boolean isVoid = p != null && p.isVoid();
                    voidItem.setEnabled(!isVoid);
                    unvoidItem.setEnabled(isVoid);
                }
            }
        });
    }

    /**
     * Opens a dialog allowing individual term and academic year assignment
     * for EACH fee category (Intel Fee, T-Shirt, Penalties, CIT Night) in the receipt.
     */
    private void openAssignTermDialogForSelected() {
        int[] selectedRows = paymentTable.getSelectedRows();
        if (selectedRows == null || selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select a payment record to assign terms.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int firstModelRow = paymentTable.convertRowIndexToModel(selectedRows[0]);
        Payment p = paymentTableModel.getPayment(firstModelRow);
        if (p == null) return;

        // If single row, show full itemized breakdown
        if (selectedRows.length == 1) {
            openItemizedTermDialogForSinglePayment(p, firstModelRow);
        } else {
            // Bulk assignment dialog for multiple rows
            openBulkTermDialog(selectedRows);
        }
    }

    private void openItemizedTermDialogForSinglePayment(Payment p, int modelRow) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Receipt #" + p.getReceiptNumber() + " - Term & AY Assignment", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(620, 480);
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Header info
        JPanel infoPanel = new JPanel(new GridLayout(2, 2, 8, 4));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Receipt Information"));
        infoPanel.add(new JLabel("Student: " + p.getName()));
        infoPanel.add(new JLabel("Student Code: " + (p.getStudentId() != null ? p.getStudentId() : "-")));
        infoPanel.add(new JLabel("Receipt #: " + p.getReceiptNumber()));
        infoPanel.add(new JLabel(String.format("Total: ₱%,.2f", p.getTotalAmount())));
        content.add(infoPanel);
        content.add(Box.createVerticalStrut(10));

        // Default / Master Term Panel
        JPanel masterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        masterPanel.setBorder(BorderFactory.createTitledBorder("Default Receipt Term & AY"));

        JComboBox<String> masterYearCombo = createYearComboBox(p.getAcademicYear() != null ? p.getAcademicYear() : "2025-2026");
        JComboBox<ChargeAcademicTerm> masterTermCombo = new JComboBox<>(ChargeAcademicTerm.values());
        masterTermCombo.setSelectedItem(p.getChargeAcademicTerm() != null ? p.getChargeAcademicTerm() : ChargeAcademicTerm.FIRST_SEM);

        masterPanel.add(new JLabel("Default AY:"));
        masterPanel.add(masterYearCombo);
        masterPanel.add(new JLabel("Default Term:"));
        masterPanel.add(masterTermCombo);

        content.add(masterPanel);
        content.add(Box.createVerticalStrut(10));

        // Fee Categories Breakdown
        JPanel feePanel = new JPanel(new GridLayout(0, 3, 10, 8));
        feePanel.setBorder(BorderFactory.createTitledBorder("Itemized Fee Term Breakdown (Assign Different Terms per Fee)"));

        // Header
        feePanel.add(new JLabel("<html><b>Fee Component & Amount</b></html>"));
        feePanel.add(new JLabel("<html><b>Academic Year (AY)</b></html>"));
        feePanel.add(new JLabel("<html><b>Academic Term</b></html>"));

        // Intel Fee
        JLabel intelLabel = new JLabel(String.format("Intel Fee (₱%,.2f):", p.getIntelFee() != null ? p.getIntelFee() : 0.0));
        JComboBox<String> intelYearCombo = createYearComboBox(p.getEffectiveAyForCategory("Intel Fee"));
        JComboBox<ChargeAcademicTerm> intelTermCombo = new JComboBox<>(ChargeAcademicTerm.values());
        intelTermCombo.setSelectedItem(p.getEffectiveTermForCategory("Intel Fee"));
        boolean hasIntel = p.getIntelFee() != null && p.getIntelFee() > 0;
        intelLabel.setEnabled(hasIntel);
        intelYearCombo.setEnabled(hasIntel);
        intelTermCombo.setEnabled(hasIntel);
        feePanel.add(intelLabel);
        feePanel.add(intelYearCombo);
        feePanel.add(intelTermCombo);

        // T-Shirt
        JLabel tshirtLabel = new JLabel(String.format("T-Shirt (₱%,.2f):", p.getTshirtSizing() != null ? p.getTshirtSizing() : 0.0));
        JComboBox<String> tshirtYearCombo = createYearComboBox(p.getEffectiveAyForCategory("T-Shirt"));
        JComboBox<ChargeAcademicTerm> tshirtTermCombo = new JComboBox<>(ChargeAcademicTerm.values());
        tshirtTermCombo.setSelectedItem(p.getEffectiveTermForCategory("T-Shirt"));
        boolean hasTshirt = p.getTshirtSizing() != null && p.getTshirtSizing() > 0;
        tshirtLabel.setEnabled(hasTshirt);
        tshirtYearCombo.setEnabled(hasTshirt);
        tshirtTermCombo.setEnabled(hasTshirt);
        feePanel.add(tshirtLabel);
        feePanel.add(tshirtYearCombo);
        feePanel.add(tshirtTermCombo);

        // Penalties
        JLabel penLabel = new JLabel(String.format("Penalties (₱%,.2f):", p.getPenalties() != null ? p.getPenalties() : 0.0));
        JComboBox<String> penYearCombo = createYearComboBox(p.getEffectiveAyForCategory("Penalties"));
        JComboBox<ChargeAcademicTerm> penTermCombo = new JComboBox<>(ChargeAcademicTerm.values());
        penTermCombo.setSelectedItem(p.getEffectiveTermForCategory("Penalties"));
        boolean hasPen = p.getPenalties() != null && p.getPenalties() > 0;
        penLabel.setEnabled(hasPen);
        penYearCombo.setEnabled(hasPen);
        penTermCombo.setEnabled(hasPen);
        feePanel.add(penLabel);
        feePanel.add(penYearCombo);
        feePanel.add(penTermCombo);

        // CIT Night
        JLabel citLabel = new JLabel(String.format("CIT Night (₱%,.2f):", p.getCitNight() != null ? p.getCitNight() : 0.0));
        JComboBox<String> citYearCombo = createYearComboBox(p.getEffectiveAyForCategory("CIT Night"));
        JComboBox<ChargeAcademicTerm> citTermCombo = new JComboBox<>(ChargeAcademicTerm.values());
        citTermCombo.setSelectedItem(p.getEffectiveTermForCategory("CIT Night"));
        boolean hasCit = p.getCitNight() != null && p.getCitNight() > 0;
        citLabel.setEnabled(hasCit);
        citYearCombo.setEnabled(hasCit);
        citTermCombo.setEnabled(hasCit);
        feePanel.add(citLabel);
        feePanel.add(citYearCombo);
        feePanel.add(citTermCombo);

        content.add(feePanel);

        // Quick Apply Default Button
        JButton applyDefaultToAllBtn = new JButton("Apply Default AY/Term to All Active Fees Above");
        applyDefaultToAllBtn.addActionListener(e -> {
            String mYear = (String) masterYearCombo.getSelectedItem();
            ChargeAcademicTerm mTerm = (ChargeAcademicTerm) masterTermCombo.getSelectedItem();
            if (hasIntel) { intelYearCombo.setSelectedItem(mYear); intelTermCombo.setSelectedItem(mTerm); }
            if (hasTshirt) { tshirtYearCombo.setSelectedItem(mYear); tshirtTermCombo.setSelectedItem(mTerm); }
            if (hasPen) { penYearCombo.setSelectedItem(mYear); penTermCombo.setSelectedItem(mTerm); }
            if (hasCit) { citYearCombo.setSelectedItem(mYear); citTermCombo.setSelectedItem(mTerm); }
        });
        JPanel applyPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        applyPanel.add(applyDefaultToAllBtn);
        content.add(applyPanel);

        dialog.add(new JScrollPane(content), BorderLayout.CENTER);

        // Action Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton saveBtn = new JButton("Save Changes");
        JButton cancelBtn = new JButton("Cancel");

        saveBtn.addActionListener(e -> {
            try {
                String defYear = (String) masterYearCombo.getSelectedItem();
                ChargeAcademicTerm defTerm = (ChargeAcademicTerm) masterTermCombo.getSelectedItem();

                String inAy = (String) intelYearCombo.getSelectedItem();
                ChargeAcademicTerm inTerm = (ChargeAcademicTerm) intelTermCombo.getSelectedItem();

                String tshAy = (String) tshirtYearCombo.getSelectedItem();
                ChargeAcademicTerm tshTerm = (ChargeAcademicTerm) tshirtTermCombo.getSelectedItem();

                String penAy = (String) penYearCombo.getSelectedItem();
                ChargeAcademicTerm penTerm = (ChargeAcademicTerm) penTermCombo.getSelectedItem();

                String citAy = (String) citYearCombo.getSelectedItem();
                ChargeAcademicTerm citTerm = (ChargeAcademicTerm) citTermCombo.getSelectedItem();

                db.updatePaymentItemTerms(p.getId(), defTerm, defYear, inTerm, inAy, tshTerm, tshAy, penTerm, penAy, citTerm, citAy, "User edited itemized terms", "user");

                // Update in-memory model
                p.setAcademicYear(defYear);
                p.setChargeAcademicTerm(defTerm);
                p.setIntelFeeAy(inAy);
                p.setIntelFeeTerm(inTerm);
                p.setTshirtAy(tshAy);
                p.setTshirtTerm(tshTerm);
                p.setPenaltiesAy(penAy);
                p.setPenaltiesTerm(penTerm);
                p.setCitNightAy(citAy);
                p.setCitNightTerm(citTerm);

                paymentTableModel.fireTableRowsUpdated(modelRow, modelRow);
                dialog.dispose();
                JOptionPane.showMessageDialog(this, "Itemized payment terms successfully saved for Receipt #" + p.getReceiptNumber() + "!", "Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error saving itemized terms: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        buttonPanel.add(saveBtn);
        buttonPanel.add(cancelBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void openBulkTermDialog(int[] selectedRows) {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JComboBox<String> yearCombo = createYearComboBox("2025-2026");
        JComboBox<ChargeAcademicTerm> termCombo = new JComboBox<>(ChargeAcademicTerm.values());

        panel.add(new JLabel("Academic Year (AY):"));
        panel.add(yearCombo);
        panel.add(new JLabel("Academic Term:"));
        panel.add(termCombo);

        int result = JOptionPane.showConfirmDialog(this, panel, "Assign Term & AY for " + selectedRows.length + " Selected Receipts", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String selectedYear = (String) yearCombo.getSelectedItem();
            ChargeAcademicTerm selectedTerm = (ChargeAcademicTerm) termCombo.getSelectedItem();

            int updatedCount = 0;
            for (int row : selectedRows) {
                int modelRow = paymentTable.convertRowIndexToModel(row);
                Payment p = paymentTableModel.getPayment(modelRow);
                if (p != null) {
                    try {
                        db.updatePaymentItemTerms(p.getId(), selectedTerm, selectedYear, selectedTerm, selectedYear, selectedTerm, selectedYear, selectedTerm, selectedYear, selectedTerm, selectedYear, "Bulk Assigned via Dialog", "user");
                        p.setChargeAcademicTerm(selectedTerm);
                        p.setAcademicYear(selectedYear);
                        p.setIntelFeeTerm(selectedTerm);
                        p.setIntelFeeAy(selectedYear);
                        p.setTshirtTerm(selectedTerm);
                        p.setTshirtAy(selectedYear);
                        p.setPenaltiesTerm(selectedTerm);
                        p.setPenaltiesAy(selectedYear);
                        p.setCitNightTerm(selectedTerm);
                        p.setCitNightAy(selectedYear);
                        paymentTableModel.fireTableRowsUpdated(modelRow, modelRow);
                        updatedCount++;
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "Error updating payment (ID " + p.getId() + "): " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
            if (updatedCount > 0) {
                refreshData();
                JOptionPane.showMessageDialog(this, String.format("Successfully assigned %s (%s) to %d payment record(s).",
                    selectedTerm.getLabel(), selectedYear != null ? selectedYear : "No AY", updatedCount), "Updated", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private JComboBox<String> createYearComboBox(String initialValue) {
        JComboBox<String> yearCombo = new JComboBox<>(new String[]{
            "2023-2024", "2024-2025", "2025-2026", "2026-2027", "2027-2028"
        });
        yearCombo.setEditable(true);
        if (initialValue != null && !initialValue.trim().isEmpty()) {
            yearCombo.setSelectedItem(initialValue.trim());
        }
        return yearCombo;
    }

    private void setChargeTermForSelected(ChargeAcademicTerm term) {
        int[] selectedRows = paymentTable.getSelectedRows();
        if (selectedRows == null || selectedRows.length == 0) return;

        int updatedCount = 0;
        for (int row : selectedRows) {
            int modelRow = paymentTable.convertRowIndexToModel(row);
            Payment p = paymentTableModel.getPayment(modelRow);
            if (p != null) {
                try {
                    db.updatePaymentTermAndYear(p.getId(), term, p.getAcademicYear(), "Quick Assigned via Context Menu", "user");
                    p.setChargeAcademicTerm(term);
                    paymentTableModel.fireTableCellUpdated(modelRow, 12);
                    updatedCount++;
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error updating payment: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        if (updatedCount > 0) {
            JOptionPane.showMessageDialog(this, String.format("Updated term to %s for %d payment(s).", term.getLabel(), updatedCount), "Term Updated", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void voidSelectedPayment() {
        int selectedRow = paymentTable.getSelectedRow();
        if (selectedRow < 0) return;
        int modelRow = paymentTable.convertRowIndexToModel(selectedRow);
        Payment p = paymentTableModel.getPayment(modelRow);
        if (p == null) return;

        String reason = JOptionPane.showInputDialog(this,
            "Enter reason for voiding receipt #" + p.getReceiptNumber() + ":",
            "Void Payment",
            JOptionPane.WARNING_MESSAGE);

        if (reason != null && !reason.trim().isEmpty()) {
            try {
                db.voidPayment(p.getReceiptNumber(), reason.trim(), "user");
                refreshData();
                JOptionPane.showMessageDialog(this, "Receipt #" + p.getReceiptNumber() + " has been voided.", "Payment Voided", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error voiding payment: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void unvoidSelectedPayment() {
        int selectedRow = paymentTable.getSelectedRow();
        if (selectedRow < 0) return;
        int modelRow = paymentTable.convertRowIndexToModel(selectedRow);
        Payment p = paymentTableModel.getPayment(modelRow);
        if (p == null) return;

        String reason = JOptionPane.showInputDialog(this,
            "Enter reason for reactivating receipt #" + p.getReceiptNumber() + ":",
            "Reactivate Payment",
            JOptionPane.QUESTION_MESSAGE);

        if (reason != null && !reason.trim().isEmpty()) {
            try {
                db.unvoidPayment(p.getReceiptNumber(), reason.trim(), "user");
                refreshData();
                JOptionPane.showMessageDialog(this, "Receipt #" + p.getReceiptNumber() + " has been reactivated.", "Payment Reactivated", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error reactivating payment: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void copySelectedCellValue(int columnIndex) {
        int selectedRow = paymentTable.getSelectedRow();
        if (selectedRow >= 0) {
            Object val = paymentTable.getValueAt(selectedRow, columnIndex);
            if (val != null) {
                java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(val.toString());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            }
        }
    }

    /**
     * Filter payments by a specific student name or student code.
     */
    public void filterByStudent(String query) {
        searchField.setText(query != null ? query : "");
        receiptField.setText("");
        programFilter.setSelectedIndex(0);
        yearFilter.setSelectedIndex(0);
        termFilter.setSelectedIndex(0);
        statusFilter.setSelectedIndex(0);
        filterPayments();
    }

    public void refreshData() {
        SwingWorker<List<Payment>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Payment> doInBackground() throws Exception {
                return db.getAllPayments();
            }

            @Override
            protected void done() {
                try {
                    List<Payment> payments = get();
                    paymentTableModel.setPayments(payments);

                    // Update program filter
                    java.util.Set<String> programs = new java.util.TreeSet<>();
                    java.util.Set<String> years = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
                    for (Payment p : payments) {
                        if (p.getProgram() != null && !p.getProgram().isEmpty()) {
                            programs.add(p.getProgram());
                        }
                        if (p.getAcademicYear() != null && !p.getAcademicYear().isEmpty()) {
                            years.add(p.getAcademicYear());
                        }
                        if (p.getIntelFeeAy() != null) years.add(p.getIntelFeeAy());
                        if (p.getTshirtAy() != null) years.add(p.getTshirtAy());
                        if (p.getPenaltiesAy() != null) years.add(p.getPenaltiesAy());
                        if (p.getCitNightAy() != null) years.add(p.getCitNightAy());
                    }

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
                    if (selectedYear != null) {
                        yearFilter.setSelectedItem(selectedYear);
                    }

                    // Calculate totals
                    double total = 0;
                    int activeCount = 0;
                    for (Payment p : payments) {
                        if ("ACTIVE".equals(p.getStatus())) {
                            total += p.getTotalAmount();
                            activeCount++;
                        }
                    }

                    statusLabel.setText(String.format("Showing %d payments (%d active)", payments.size(), activeCount));
                    summaryLabel.setText(String.format("Active Total: ₱%,.2f", total));

                } catch (Exception e) {
                    statusLabel.setText("Error loading payments: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void filterPayments() {
        TableRowSorter<PaymentTableModel> sorter = (TableRowSorter<PaymentTableModel>) paymentTable.getRowSorter();
        if (sorter == null) return;

        List<RowFilter<Object, Object>> filters = new java.util.ArrayList<>();

        // Name or Student Code search
        String searchText = searchField.getText().trim();
        if (!searchText.isEmpty()) {
            List<RowFilter<Object, Object>> nameOrCode = new java.util.ArrayList<>();
            nameOrCode.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(searchText), 2)); // Student Code
            nameOrCode.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(searchText), 3)); // Name
            filters.add(RowFilter.orFilter(nameOrCode));
        }

        // Receipt number
        String receiptText = receiptField.getText().trim();
        if (!receiptText.isEmpty()) {
            try {
                int receipt = Integer.parseInt(receiptText);
                filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.EQUAL, receipt, 1));
            } catch (NumberFormatException ex) {
                // Invalid number, ignore
            }
        }

        // Program filter (Column 4)
        String program = (String) programFilter.getSelectedItem();
        if (program != null && !"All Programs".equals(program)) {
            filters.add(RowFilter.regexFilter("^" + java.util.regex.Pattern.quote(program) + "$", 4));
        }

        // Academic Year filter (Column 11)
        String year = (String) yearFilter.getSelectedItem();
        if (year != null && !"All AY".equals(year)) {
            filters.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(year), 11));
        }

        // Term filter (Column 12)
        String term = (String) termFilter.getSelectedItem();
        if (term != null && !"All Terms".equals(term)) {
            filters.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(term), 12));
        }

        // Status filter (Column 13)
        String status = (String) statusFilter.getSelectedItem();
        if (status != null && !"All".equals(status)) {
            filters.add(RowFilter.regexFilter("^" + status + "$", 13));
        }

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else if (filters.size() == 1) {
            sorter.setRowFilter(filters.get(0));
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }

        statusLabel.setText(String.format("Showing %d payments (filtered)", paymentTable.getRowCount()));
    }

    // --- Table Model ---

    private class PaymentTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
            "#", "Receipt #", "Student Code", "Student Name", "Program",
            "Remittance Date", "Intel Fee", "T-Shirt", "Penalties", "CIT Night",
            "Received By", "Academic Year", "Charge Term", "Status", "Total"
        };
        private List<Payment> payments = List.of();
        private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        public void setPayments(List<Payment> payments) {
            this.payments = payments;
            fireTableDataChanged();
        }

        public Payment getPayment(int rowIndex) {
            if (payments != null && rowIndex >= 0 && rowIndex < payments.size()) {
                return payments.get(rowIndex);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return payments.size();
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
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 11 || columnIndex == 12;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            Payment p = getPayment(rowIndex);
            if (p == null) return;

            if (columnIndex == 11) {
                String newYear = aValue != null ? aValue.toString().trim() : null;
                try {
                    db.updatePaymentTermAndYear(p.getId(), p.getChargeAcademicTerm(), newYear, "Edited in Payments Table", "user");
                    p.setAcademicYear(newYear);
                    fireTableCellUpdated(rowIndex, columnIndex);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(PaymentPanel.this, "Error updating academic year: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else if (columnIndex == 12) {
                ChargeAcademicTerm term = aValue instanceof ChargeAcademicTerm
                    ? (ChargeAcademicTerm) aValue
                    : ChargeAcademicTerm.fromCode(String.valueOf(aValue));
                try {
                    db.updatePaymentTermAndYear(p.getId(), term, p.getAcademicYear(), "Edited in Payments Table", "user");
                    p.setChargeAcademicTerm(term);
                    fireTableCellUpdated(rowIndex, columnIndex);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(PaymentPanel.this, "Error updating charge term: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Payment p = payments.get(rowIndex);
            switch (columnIndex) {
                case 0: return rowIndex + 1;
                case 1: return p.getReceiptNumber();
                case 2: return p.getStudentId() != null ? p.getStudentId() : "-";
                case 3: return p.getName();
                case 4: return p.getProgram() != null ? p.getProgram() : "-";
                case 5: return p.getRemittanceDate() != null ? p.getRemittanceDate().format(dateFormat) : "-";
                case 6: return p.getIntelFee() != null ? p.getIntelFee() : 0.0;
                case 7: return p.getTshirtSizing() != null ? p.getTshirtSizing() : 0.0;
                case 8: return p.getPenalties() != null ? p.getPenalties() : 0.0;
                case 9: return p.getCitNight() != null ? p.getCitNight() : 0.0;
                case 10: return p.getReceivedBy() != null ? p.getReceivedBy() : "-";
                case 11: {
                    // Check if multiple different AYs exist
                    String ay = p.getAcademicYear();
                    return ay != null ? ay : "-";
                }
                case 12: {
                    ChargeAcademicTerm term = p.getChargeAcademicTerm();
                    return term != null ? term.getLabel() : "Unassigned";
                }
                case 13: return p.getStatus() != null ? p.getStatus() : "ACTIVE";
                case 14: return p.getTotalAmount();
                default: return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0 || columnIndex == 1) return Integer.class;
            if (columnIndex >= 6 && columnIndex <= 9) return Double.class;
            if (columnIndex == 14) return Double.class;
            return String.class;
        }
    }

    // --- Cell Renderers ---

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final Color ACTIVE_COLOR = new Color(166, 227, 161);
        private static final Color VOID_COLOR = new Color(243, 139, 168);
        private Font boldFont = null;

        public StatusCellRenderer() {
            setHorizontalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (boldFont == null) {
                boldFont = getFont().deriveFont(Font.BOLD, 11f);
            }
            setFont(boldFont);

            if (value != null) {
                String status = value.toString();
                if (!isSelected) {
                    setForeground("ACTIVE".equals(status) ? ACTIVE_COLOR : VOID_COLOR);
                }
                setText(status);
            }
            return this;
        }
    }

    private static class CurrencyCellRenderer extends DefaultTableCellRenderer {
        private final java.text.DecimalFormat format = new java.text.DecimalFormat("₱#,##0.00");

        public CurrencyCellRenderer() {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof Number num) {
                setText(format.format(num.doubleValue()));
            }
            return this;
        }
    }
}