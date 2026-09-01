package com.payment;

import com.payment.ui.ThemeUtils;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Import Dialog - handles the full import workflow:
 * File selection → Remittance date → Preview → Confirmation → Import → Result
 */
public class ImportDialog extends JDialog {

    private final ImportService importService;
    private ImportPreviewResult previewResult;
    private File selectedFile;
    private LocalDate remittanceDate;

    // UI Components
    private JFileChooser fileChooser;
    private JSpinner dateSpinner;
    private JTextField filePathField;
    private JButton browseButton;
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
        JLabel fileLbl = new JLabel("Excel File:");
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
        JLabel dateLbl = new JLabel("Remittance Date:");
        dateLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        panel.add(dateLbl, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        SpinnerDateModel dateModel = new SpinnerDateModel();
        dateSpinner = new JSpinner(dateModel);
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
        dateSpinner.setEditor(dateEditor);
        dateSpinner.setValue(java.sql.Date.valueOf(remittanceDate));
        panel.add(dateSpinner, gbc);

        // Preview button
        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
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
        summaryLabel = new JLabel("Select a file and click 'Generate Preview'");
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
        }

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();
            filePathField.setText(selectedFile.getAbsolutePath());
            previewButton.setEnabled(true);
            clearPreview();
        }
    }

    private void generatePreview() {
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "Please select an Excel file first.", "No File", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Get remittance date from spinner
        java.util.Date spinnerDate = (java.util.Date) dateSpinner.getValue();
        remittanceDate = spinnerDate.toInstant()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate();

        // Disable UI during preview generation
        setPreviewUIState(false);
        statusLabel.setText("Generating preview...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        // Run in background thread
        SwingWorker<ImportPreviewResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ImportPreviewResult doInBackground() throws Exception {
                return importService.generatePreview(selectedFile.getAbsolutePath(), remittanceDate, "user");
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
        previewTableModel.setItems(null);
        summaryLabel.setText("Select a file and click 'Generate Preview'");
        importButton.setEnabled(false);
    }

    private void updateSummary(ImportPreviewResult result) {
        String summary = String.format(
            "Total: %d | New: %d | Duplicates: %d | Conflicts: %d | Ambiguous: %d | Errors: %d",
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
        previewButton.setEnabled(enabled && selectedFile != null);
        dateSpinner.setEnabled(enabled);
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
                "File: %s\n" +
                "Remittance Date: %s",
                previewResult.getNewCount(),
                previewResult.getBatch().getBatchCode(),
                previewResult.getBatch().getFileName(),
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
        selectedFile = null;
        previewButton.setEnabled(false);
    }

    private void setImportUIState(boolean enabled) {
        importButton.setEnabled(enabled && previewResult != null);
        cancelButton.setEnabled(enabled);
        browseButton.setEnabled(enabled);
        previewButton.setEnabled(enabled && selectedFile != null);
        dateSpinner.setEnabled(enabled);
    }

    // --- Table Model ---

    private static class PreviewTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {
            "Row", "Receipt #", "Student Name", "Program", "Amount", "Status",
            "Charge Term", "Matched Student", "Details"
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
                case 1: return item.getReceiptNumber();
                case 2: return item.getStudentName();
                case 3: return item.getProgram() != null ? item.getProgram() : "";
                case 4: return String.format("₱%,.2f", item.getTotalAmount());
                case 5: return item.getStatus();
                case 6: return item.getChargeAcademicTerm().getLabel();
                case 7: return item.getMatchedStudentName() != null ?
                    item.getMatchedStudentCode() + " - " + item.getMatchedStudentName() :
                    (item.getProposedStudentCode() != null ? item.getProposedStudentCode() + " (new)" : "");
                case 8: return getDetails(item);
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
            return "";
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0 || columnIndex == 1) return Integer.class;
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