package com.payment;

import com.payment.ui.ThemeUtils;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Import Dialog - handles the full import workflow:
 * File selection → Remittance date → Preview → Confirmation → Import → Result
 */
public class ImportDialog extends JDialog {

    private final ImportService importService;
    private final DatabaseManager db;
    private ImportPreviewResult previewResult;
    private final SelectedFilesTableModel selectedFilesTableModel = new SelectedFilesTableModel();
    private LocalDate remittanceDate;

    // UI Components
    private JFileChooser fileChooser;
    private JSpinner dateSpinner;
    private JComboBox<String> receiptYearCombo;
    private JComboBox<ChargeAcademicTerm> receiptTermCombo;
    private JTable selectedFilesTable;
    private JTextField filePathField;
    private JButton browseButton;
    private JButton applyReceiptPeriodButton;
    private JButton previewButton;
    private JButton importButton;
    private JButton cancelButton;

    // Preview panel
    private JTable previewTable;
    private PreviewTableModel previewTableModel;
    private JLabel summaryLabel;
    private JScrollPane previewScrollPane;

    // Import progress
    private JProgressBar progressBar;
    private JLabel statusLabel;

    public ImportDialog(JFrame parent, ImportService importService) {
        super(parent, "Import Payments from Excel", true);
        this.importService = importService;
        this.db = DatabaseManager.getInstance();
        this.remittanceDate = LocalDate.now();

        initializeUI();
        pack();
        setLocationRelativeTo(parent);
        setMinimumSize(new Dimension(900, 600));
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(ThemeUtils.BG_DEEPEST);

        // Top panel: File selection and remittance date
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);

        // Center panel: Preview table
        JPanel centerPanel = createCenterPanel();
        add(centerPanel, BorderLayout.CENTER);

        selectedFilesTableModel.addTableModelListener(event -> {
            if (previewResult != null) clearPreview();
        });

        // Bottom panel: Buttons and progress
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ThemeUtils.BG_CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
            ThemeUtils.styleTitledBorder("Import Settings", ThemeUtils.NEON_CYAN),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // File selection
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel fileLbl = new JLabel("Excel Files:");
        fileLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(fileLbl, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        filePathField = new JTextField();
        filePathField.setEditable(false);
        panel.add(filePathField, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        browseButton = new JButton("📂 Browse...");
        ThemeUtils.styleButton(browseButton, ThemeUtils.NEON_CYAN);
        browseButton.addActionListener(e -> browseFile());
        panel.add(browseButton, gbc);

        // Remittance date
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel dateLbl = new JLabel("Fallback Date:");
        dateLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(dateLbl, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        SpinnerDateModel dateModel = new SpinnerDateModel();
        dateSpinner = new JSpinner(dateModel);
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
        dateSpinner.setEditor(dateEditor);
        dateSpinner.setValue(java.sql.Date.valueOf(remittanceDate));
        panel.add(dateSpinner, gbc);

        // Receipt issuance period (separate from fee attribution)
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        JLabel periodLbl = new JLabel("Default Receipt Period:");
        periodLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(periodLbl, gbc);

        JPanel periodPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        periodPanel.setOpaque(false);
        receiptYearCombo = new JComboBox<>(new String[]{
            "2024-2025", "2025-2026", "2026-2027", "2027-2028"
        });
        receiptYearCombo.setEditable(true);
        receiptYearCombo.setSelectedItem(db.getCurrentAcademicYear());
        receiptTermCombo = new JComboBox<>(new ChargeAcademicTerm[]{
            ChargeAcademicTerm.FIRST_SEM,
            ChargeAcademicTerm.SECOND_SEM,
            ChargeAcademicTerm.SUMMER
        });
        ChargeAcademicTerm currentTerm = db.getCurrentAcademicTerm();
        receiptTermCombo.setSelectedItem(ReceiptKey.isConcreteTerm(currentTerm)
            ? currentTerm : ChargeAcademicTerm.FIRST_SEM);
        periodPanel.add(receiptYearCombo);
        periodPanel.add(receiptTermCombo);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(periodPanel, gbc);

        applyReceiptPeriodButton = new JButton("Apply to All Files");
        ThemeUtils.styleButton(applyReceiptPeriodButton, ThemeUtils.NEON_CYAN);
        applyReceiptPeriodButton.setEnabled(false);
        applyReceiptPeriodButton.addActionListener(e -> selectedFilesTableModel.applyPeriod(
            selectedReceiptYear(), (ChargeAcademicTerm) receiptTermCombo.getSelectedItem()));
        gbc.gridx = 2; gbc.gridy = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(applyReceiptPeriodButton, gbc);

        selectedFilesTable = new JTable(selectedFilesTableModel);
        selectedFilesTable.setRowHeight(22);
        selectedFilesTable.getTableHeader().setReorderingAllowed(false);
        ThemeUtils.applyTableTheme(selectedFilesTable);
        selectedFilesTable.getColumnModel().getColumn(0).setPreferredWidth(360);
        selectedFilesTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        selectedFilesTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        JComboBox<ChargeAcademicTerm> fileTermEditor = new JComboBox<>(new ChargeAcademicTerm[]{
            ChargeAcademicTerm.FIRST_SEM,
            ChargeAcademicTerm.SECOND_SEM,
            ChargeAcademicTerm.SUMMER
        });
        selectedFilesTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(fileTermEditor));
        JComboBox<String> fileYearEditor = new JComboBox<>(new String[]{
            "2024-2025", "2025-2026", "2026-2027", "2027-2028"
        });
        fileYearEditor.setEditable(true);
        selectedFilesTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(fileYearEditor));
        JScrollPane selectedFilesScroll = new JScrollPane(selectedFilesTable);
        selectedFilesScroll.setPreferredSize(new Dimension(620, 82));
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(selectedFilesScroll, gbc);

        // Preview button
        gbc.gridx = 2; gbc.gridy = 4; gbc.gridwidth = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        previewButton = new JButton("⚡ Generate Preview");
        ThemeUtils.styleButton(previewButton, ThemeUtils.NEON_GREEN);
        previewButton.setEnabled(false);
        previewButton.addActionListener(e -> generatePreview());
        panel.add(previewButton, gbc);

        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(ThemeUtils.BG_SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            ThemeUtils.styleTitledBorder("Import Preview", ThemeUtils.NEON_PURPLE),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        // Header panel with summary and batch term actions
        JPanel topPreviewPanel = new JPanel(new BorderLayout());
        topPreviewPanel.setBackground(ThemeUtils.BG_SURFACE);
        summaryLabel = new JLabel("Select spreadsheets and click 'Generate Preview'");
        summaryLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        topPreviewPanel.add(summaryLabel, BorderLayout.WEST);

        JPanel batchTermPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        batchTermPanel.setBackground(ThemeUtils.BG_SURFACE);
        JButton setAllCurrentBtn = new JButton("Set All Eligible to Current Term");
        JButton setAllPrevBtn = new JButton("Set All Eligible to Previous Term");
        setAllCurrentBtn.setFont(setAllCurrentBtn.getFont().deriveFont(11f));
        setAllPrevBtn.setFont(setAllPrevBtn.getFont().deriveFont(11f));
        ThemeUtils.styleButton(setAllCurrentBtn, ThemeUtils.NEON_CYAN);
        ThemeUtils.styleButton(setAllPrevBtn, ThemeUtils.NEON_AMBER);

        setAllCurrentBtn.addActionListener(e -> setAllEligibleTerms(ChargeAcademicTerm.CURRENT));
        setAllPrevBtn.addActionListener(e -> setAllEligibleTerms(ChargeAcademicTerm.PREVIOUS));

        JLabel quickLabel = new JLabel("Quick Term Assign:");
        quickLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        batchTermPanel.add(quickLabel);
        batchTermPanel.add(setAllCurrentBtn);
        batchTermPanel.add(setAllPrevBtn);
        topPreviewPanel.add(batchTermPanel, BorderLayout.EAST);

        panel.add(topPreviewPanel, BorderLayout.NORTH);

        // Preview table
        previewTableModel = new PreviewTableModel();
        previewTable = new JTable(previewTableModel);
        previewTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        previewTable.setAutoCreateRowSorter(true);
        previewTable.getTableHeader().setReorderingAllowed(false);
        ThemeUtils.applyTableTheme(previewTable);

        // Set column widths
        previewTable.getColumnModel().getColumn(0).setPreferredWidth(50);   // Row
        previewTable.getColumnModel().getColumn(1).setPreferredWidth(100);  // Receipt
        previewTable.getColumnModel().getColumn(2).setPreferredWidth(180);  // Student Name
        previewTable.getColumnModel().getColumn(3).setPreferredWidth(100);  // Program
        previewTable.getColumnModel().getColumn(4).setPreferredWidth(100);  // Amount
        previewTable.getColumnModel().getColumn(5).setPreferredWidth(100);  // Status
        previewTable.getColumnModel().getColumn(6).setPreferredWidth(130);  // Charge Term
        previewTable.getColumnModel().getColumn(7).setPreferredWidth(120);  // Matched Student
        previewTable.getColumnModel().getColumn(8).setPreferredWidth(200);  // Details
        previewTable.getColumnModel().getColumn(9).setPreferredWidth(160);  // Source file
        previewTable.getColumnModel().getColumn(10).setPreferredWidth(170); // Receipt period
        previewTable.getColumnModel().getColumn(11).setPreferredWidth(130); // Remittance date

        // Custom renderer for status column
        previewTable.getColumnModel().getColumn(5).setCellRenderer(new StatusCellRenderer());

        // Combo box editor for Charge Term column
        JComboBox<String> termComboBox = new JComboBox<>();
        termComboBox.addItem(ChargeAcademicTerm.CURRENT.getLabel());
        termComboBox.addItem(ChargeAcademicTerm.PREVIOUS.getLabel());
        termComboBox.addItem(ChargeAcademicTerm.UNASSIGNED.getLabel());
        previewTable.getColumnModel().getColumn(6).setCellEditor(new DefaultCellEditor(termComboBox));

        // Context menu on preview table
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem setCurrItem = new JMenuItem("Set to Current Term");
        JMenuItem setPrevItem = new JMenuItem("Set to Previous Term");
        JMenuItem setUnassignedItem = new JMenuItem("Set to Unassigned");

        setCurrItem.addActionListener(e -> setChargeTermForSelectedPreview(ChargeAcademicTerm.CURRENT));
        setPrevItem.addActionListener(e -> setChargeTermForSelectedPreview(ChargeAcademicTerm.PREVIOUS));
        setUnassignedItem.addActionListener(e -> setChargeTermForSelectedPreview(ChargeAcademicTerm.UNASSIGNED));

        popupMenu.add(setCurrItem);
        popupMenu.add(setPrevItem);
        popupMenu.add(setUnassignedItem);
        previewTable.setComponentPopupMenu(popupMenu);

        previewScrollPane = new JScrollPane(previewTable);
        previewScrollPane.setBorder(BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1));
        previewScrollPane.getViewport().setBackground(ThemeUtils.BG_SURFACE);
        panel.add(previewScrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void setAllEligibleTerms(ChargeAcademicTerm term) {
        if (previewResult == null || previewResult.getItems() == null) return;
        int count = 0;
        for (ImportPreviewItem item : previewResult.getItems()) {
            if (ChargeAcademicTerm.isTermEligibleCategory(item.getCitNight(), item.getPenalties())) {
                item.setChargeAcademicTerm(term);
                count++;
            }
        }
        previewTableModel.setItems(previewResult.getItems());
        JOptionPane.showMessageDialog(this,
            String.format("Assigned %s to %d eligible record(s).", term.getLabel(), count),
            "Term Assigned", JOptionPane.INFORMATION_MESSAGE);
    }

    private void setChargeTermForSelectedPreview(ChargeAcademicTerm term) {
        int selectedRow = previewTable.getSelectedRow();
        if (selectedRow < 0) return;
        int modelRow = previewTable.convertRowIndexToModel(selectedRow);
        if (previewResult != null && previewResult.getItems() != null && modelRow < previewResult.getItems().size()) {
            ImportPreviewItem item = previewResult.getItems().get(modelRow);
            item.setChargeAcademicTerm(term);
            previewTableModel.fireTableRowsUpdated(modelRow, modelRow);
        }
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ThemeUtils.BG_CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeUtils.BORDER_COLOR),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // Progress bar and status
        JPanel progressPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        progressPanel.setBackground(ThemeUtils.BG_CARD);
        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(300, 20));
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        progressPanel.add(statusLabel);
        progressPanel.add(progressBar);
        panel.add(progressPanel, BorderLayout.WEST);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(ThemeUtils.BG_CARD);

        importButton = new JButton("📥 Import");
        importButton.setEnabled(false);
        ThemeUtils.styleButton(importButton, ThemeUtils.NEON_GREEN);
        importButton.addActionListener(e -> performImport());
        buttonPanel.add(importButton);

        cancelButton = new JButton("Close");
        ThemeUtils.styleButton(cancelButton, ThemeUtils.TEXT_SECONDARY);
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    private void browseFile() {
        if (fileChooser == null) {
            fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Excel Files (*.xlsx)", "xlsx"));
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
            fileChooser.setMultiSelectionEnabled(true);
        }

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            List<File> selectedFiles = new ArrayList<>();
            File[] files = fileChooser.getSelectedFiles();
            if (files != null && files.length > 0) {
                selectedFiles.addAll(List.of(files));
            } else if (fileChooser.getSelectedFile() != null) {
                selectedFiles.add(fileChooser.getSelectedFile());
            }
            selectedFilesTableModel.setFiles(selectedFiles, selectedReceiptYear(),
                (ChargeAcademicTerm) receiptTermCombo.getSelectedItem());
            filePathField.setText(selectedFiles.size() == 1
                ? selectedFiles.get(0).getAbsolutePath()
                : selectedFiles.size() + " spreadsheets selected");
            filePathField.setToolTipText(selectedFiles.stream()
                .map(File::getName)
                .collect(java.util.stream.Collectors.joining(", ")));
            previewButton.setEnabled(!selectedFiles.isEmpty());
            applyReceiptPeriodButton.setEnabled(!selectedFiles.isEmpty());
            clearPreview();
        }
    }

    private void generatePreview() {
        if (selectedFilesTableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Please select one or more Excel files first.", "No Files", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selectedFilesTable.isEditing()) {
            selectedFilesTable.getCellEditor().stopCellEditing();
        }

        // Get remittance date from spinner
        java.util.Date spinnerDate = (java.util.Date) dateSpinner.getValue();
        remittanceDate = new java.sql.Date(spinnerDate.getTime()).toLocalDate();
        List<ImportFileSelection> selections = selectedFilesTableModel.getSelections();

        // Disable UI during preview generation
        setPreviewUIState(false);
        statusLabel.setText("Generating preview...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        // Run in background thread
        SwingWorker<ImportPreviewResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ImportPreviewResult doInBackground() throws Exception {
                return importService.generateBatchPreview(selections, remittanceDate, "user");
            }

            @Override
            protected void done() {
                try {
                    previewResult = get();
                    displayPreview(previewResult);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ImportDialog.this,
                        "Error generating preview: " + e.getMessage(),
                        "Preview Error", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                } finally {
                    setPreviewUIState(true);
                    progressBar.setVisible(false);
                    progressBar.setIndeterminate(false);
                    statusLabel.setText("Preview generated");
                }
            }
        };
        worker.execute();
    }

    private void displayPreview(ImportPreviewResult result) {
        previewTableModel.setItems(result.getItems());
        updateSummary(result);
        importButton.setEnabled(true);
    }

    private void clearPreview() {
        previewResult = null;
        previewTableModel.setItems(null);
        summaryLabel.setText("Select spreadsheets and click 'Generate Preview'");
        importButton.setEnabled(false);
    }

    private void updateSummary(ImportPreviewResult result) {
        String summary = String.format(
            "Files: %d | Total: %d | New: %d | Duplicates: %d | Conflicts: %d | Ambiguous: %d | Errors: %d",
            result.getFileCount(),
            result.getTotalItems(),
            result.getNewCount(),
            result.getDuplicateCount(),
            result.getConflictCount(),
            result.getAmbiguousCount(),
            result.getErrorCount()
        );
        summaryLabel.setText(summary);
    }

    private void setPreviewUIState(boolean enabled) {
        browseButton.setEnabled(enabled);
        previewButton.setEnabled(enabled && selectedFilesTableModel.getRowCount() > 0);
        dateSpinner.setEnabled(enabled);
        receiptYearCombo.setEnabled(enabled);
        receiptTermCombo.setEnabled(enabled);
        selectedFilesTable.setEnabled(enabled);
        applyReceiptPeriodButton.setEnabled(enabled && selectedFilesTableModel.getRowCount() > 0);
        filePathField.setEnabled(enabled);
    }

    private void performImport() {
        if (previewResult == null) {
            JOptionPane.showMessageDialog(this, "No preview generated.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Check for items requiring review
        int reviewCount = previewResult.getRequiresReviewCount();
        if (reviewCount > 0) {
            int choice = JOptionPane.showConfirmDialog(this,
                String.format("There are %d items requiring review (conflicts, ambiguous, errors).\n" +
                    "These items will be skipped during import.\n\n" +
                    "Do you want to continue?", reviewCount),
                "Items Require Review", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // Final confirmation
        int confirm = JOptionPane.showConfirmDialog(this,
            String.format("Import %d new records?\n\n" +
                "Batch: %s\n" +
                "Files: %d\n" +
                "Receipt Period: %s\n" +
                "Remittance Date: %s",
                previewResult.getNewCount(),
                previewResult.getBatch().getBatchCode(),
                previewResult.getFileCount(),
                previewResult.getBatch().getReceiptPeriodDisplay(),
                previewResult.getBatch().getRemittanceDate()),
            "Confirm Import", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // Disable UI during import
        setImportUIState(false);
        statusLabel.setText("Importing...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        SwingWorker<ImportResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ImportResult doInBackground() throws Exception {
                return importService.commitImport(previewResult);
            }

            @Override
            protected void done() {
                try {
                    ImportResult result = get();
                    showImportResult(result);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ImportDialog.this,
                        "Import failed: " + e.getMessage(),
                        "Import Error", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                } finally {
                    setImportUIState(true);
                    progressBar.setVisible(false);
                    progressBar.setIndeterminate(false);
                    statusLabel.setText("Import completed");
                }
            }
        };
        worker.execute();
    }

    private void showImportResult(ImportResult result) {
        String message = String.format(
            "Import completed successfully!\n\n" +
            "Batch: %s\n" +
            "New Records: %d\n" +
            "Duplicates Skipped: %d\n" +
            "Conflicts Skipped: %d\n" +
            "Errors: %d",
            result.getBatch().getBatchCode(),
            result.getNewRecords(),
            result.getDuplicateRecords(),
            result.getConflictRecords(),
            result.getErrorRecords()
        );

        JOptionPane.showMessageDialog(this, message, "Import Complete", JOptionPane.INFORMATION_MESSAGE);

        // Clear preview and reset
        clearPreview();
        filePathField.setText("");
        filePathField.setToolTipText(null);
        selectedFilesTableModel.clear();
        previewButton.setEnabled(false);
        applyReceiptPeriodButton.setEnabled(false);
    }

    private void setImportUIState(boolean enabled) {
        importButton.setEnabled(enabled && previewResult != null);
        cancelButton.setEnabled(enabled);
        browseButton.setEnabled(enabled);
        previewButton.setEnabled(enabled && selectedFilesTableModel.getRowCount() > 0);
        dateSpinner.setEnabled(enabled);
        receiptYearCombo.setEnabled(enabled);
        receiptTermCombo.setEnabled(enabled);
        selectedFilesTable.setEnabled(enabled);
        applyReceiptPeriodButton.setEnabled(enabled && selectedFilesTableModel.getRowCount() > 0);
    }

    private String selectedReceiptYear() {
        Object selected = receiptYearCombo.getSelectedItem();
        return selected != null ? selected.toString().trim() : "";
    }

    // --- Table Model ---

    private static class SelectedFilesTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Spreadsheet", "Receipt AY", "Receipt Semester"};
        private final List<ImportFileSelection> selections = new ArrayList<>();

        public void setFiles(List<File> files, String academicYear, ChargeAcademicTerm term) {
            selections.clear();
            if (files != null) {
                for (File file : files) {
                    if (file != null) {
                        selections.add(new ImportFileSelection(file.getAbsolutePath(), academicYear, term));
                    }
                }
            }
            fireTableDataChanged();
        }

        public void applyPeriod(String academicYear, ChargeAcademicTerm term) {
            for (int i = 0; i < selections.size(); i++) {
                ImportFileSelection current = selections.get(i);
                selections.set(i, new ImportFileSelection(current.filePath(), academicYear, term));
            }
            if (selections.isEmpty()) fireTableDataChanged();
            else fireTableRowsUpdated(0, selections.size() - 1);
        }

        public List<ImportFileSelection> getSelections() {
            return List.copyOf(selections);
        }

        public void clear() {
            selections.clear();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() { return selections.size(); }

        @Override
        public int getColumnCount() { return COLUMNS.length; }

        @Override
        public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ImportFileSelection selection = selections.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> new File(selection.filePath()).getName();
                case 1 -> selection.receiptAcademicYear();
                case 2 -> selection.receiptTerm();
                default -> null;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 1 || columnIndex == 2;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            ImportFileSelection current = selections.get(rowIndex);
            String year = current.receiptAcademicYear();
            ChargeAcademicTerm term = current.receiptTerm();
            if (columnIndex == 1) {
                year = value != null ? value.toString().trim() : null;
            } else if (columnIndex == 2) {
                term = value instanceof ChargeAcademicTerm selectedTerm
                    ? selectedTerm : ChargeAcademicTerm.fromCode(String.valueOf(value));
            } else {
                return;
            }
            selections.set(rowIndex, new ImportFileSelection(current.filePath(), year, term));
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 2 ? ChargeAcademicTerm.class : String.class;
        }
    }

    private static class PreviewTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {
            "Row", "Receipt #", "Student Name", "Program", "Amount", "Status",
            "Charge Term", "Matched Student", "Details", "Source File", "Receipt Period", "Remittance Date"
        };

        private List<ImportPreviewItem> items;

        public void setItems(List<ImportPreviewItem> items) {
            this.items = items;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return items != null ? items.size() : 0;
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            // Charge Term column is editable for rows with CIT Night or Penalty
            if (columnIndex == 6 && items != null && rowIndex < items.size()) {
                ImportPreviewItem item = items.get(rowIndex);
                return ChargeAcademicTerm.isTermEligibleCategory(item.getCitNight(), item.getPenalties());
            }
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (items == null || rowIndex >= items.size()) return null;
            ImportPreviewItem item = items.get(rowIndex);

            switch (columnIndex) {
                case 0: return item.getRowNumber();
                case 1: return item.getReceiptDisplay();
                case 2: return item.getStudentName();
                case 3: return item.getProgram() != null ? item.getProgram() : "";
                case 4: return String.format("₱%,.2f", item.getTotalAmount());
                case 5: return item.getStatus();
                case 6: return item.getChargeAcademicTerm().getLabel();
                case 7: return item.getMatchedStudentName() != null ?
                    item.getMatchedStudentCode() + " - " + item.getMatchedStudentName() :
                    (item.getProposedStudentCode() != null ? item.getProposedStudentCode() + " (new)" : "");
                case 8: return getDetails(item);
                case 9: return item.getSourceFileName() != null ? item.getSourceFileName() : "";
                case 10: return item.getReceiptNumber() > 0
                    ? item.getReceiptKey().displayScope() : "Not applicable";
                case 11: return item.getRemittanceDate() != null ? item.getRemittanceDate().toString() : "";
                default: return null;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (items == null || rowIndex >= items.size()) return;
            ImportPreviewItem item = items.get(rowIndex);
            if (columnIndex == 6) {
                if (aValue instanceof ChargeAcademicTerm) {
                    item.setChargeAcademicTerm((ChargeAcademicTerm) aValue);
                } else if (aValue instanceof String) {
                    item.setChargeAcademicTerm(ChargeAcademicTerm.fromCode((String) aValue));
                }
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }

        private String getDetails(ImportPreviewItem item) {
            if (item.isError()) {
                return "ERROR: " + item.getErrorMessage();
            }
            if (item.isConflict() && item.getConflictingPayment() != null) {
                Payment existing = item.getConflictingPayment();
                return String.format("CONFLICT: Existing receipt %d has different amounts. Existing: ₱%,.2f, New: ₱%,.2f",
                    item.getReceiptNumber(), existing.getTotalAmount(), item.getTotalAmount());
            }
            if (item.isAmbiguous()) {
                return "AMBIGUOUS: " + item.getAmbiguousMatches().size() + " students match this name";
            }
            if (item.isDuplicate()) {
                return "EXACT DUPLICATE - will be skipped";
            }
            java.util.List<String> attributions = new java.util.ArrayList<>();
            if (item.getIntelFee() != null && item.getIntelFee() > 0 && item.getIntelFeeTerm() != null)
                attributions.add("Intel: " + item.getIntelFeeAy() + " / " + item.getIntelFeeTerm().getLabel());
            if (item.getTshirtSizing() != null && item.getTshirtSizing() > 0 && item.getTshirtTerm() != null)
                attributions.add("T-Shirt: " + item.getTshirtAy() + " / " + item.getTshirtTerm().getLabel());
            if (item.getPenalties() != null && item.getPenalties() > 0 && item.getPenaltiesTerm() != null)
                attributions.add("Penalties: " + item.getPenaltiesAy() + " / " + item.getPenaltiesTerm().getLabel());
            if (item.getCitNight() != null && item.getCitNight() > 0 && item.getCitNightTerm() != null)
                attributions.add("CIT: " + item.getCitNightAy() + " / " + item.getCitNightTerm().getLabel());
            return String.join(" | ", attributions);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Integer.class;
            if (columnIndex == 6) return String.class;
            return String.class;
        }
    }

    // --- Status Cell Renderer ---

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final java.util.Map<String, Color> STATUS_COLORS = new java.util.HashMap<>();
        static {
            STATUS_COLORS.put(ImportPreviewItem.STATUS_NEW, ThemeUtils.NEON_GREEN);
            STATUS_COLORS.put(ImportPreviewItem.STATUS_DUPLICATE, ThemeUtils.NEON_AMBER);
            STATUS_COLORS.put(ImportPreviewItem.STATUS_CONFLICT, new Color(255, 140, 0));
            STATUS_COLORS.put(ImportPreviewItem.STATUS_AMBIGUOUS, ThemeUtils.NEON_ROSE);
            STATUS_COLORS.put(ImportPreviewItem.STATUS_ERROR, ThemeUtils.NEON_ROSE);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value != null) {
                String status = value.toString();
                Color color = STATUS_COLORS.getOrDefault(status, ThemeUtils.TEXT_SECONDARY);
                if (!isSelected) {
                    c.setForeground(color);
                }
                setText(status);
                setHorizontalAlignment(CENTER);
                setFont(getFont().deriveFont(Font.BOLD, 11f));
            }
            return c;
        }
    }
}
