package com.payment;

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
        panel.setBorder(BorderFactory.createTitledBorder("Import Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // File selection
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Excel File:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        filePathField = new JTextField();
        filePathField.setEditable(false);
        panel.add(filePathField, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseFile());
        panel.add(browseButton, gbc);

        // Remittance date
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Remittance Date:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        SpinnerDateModel dateModel = new SpinnerDateModel();
        dateSpinner = new JSpinner(dateModel);
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
        dateSpinner.setEditor(dateEditor);
        dateSpinner.setValue(java.sql.Date.valueOf(remittanceDate));
        panel.add(dateSpinner, gbc);

        // Preview button
        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        previewButton = new JButton("Generate Preview");
        previewButton.setEnabled(false);
        previewButton.addActionListener(e -> generatePreview());
        panel.add(previewButton, gbc);

        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Import Preview"));

        // Summary label
        summaryLabel = new JLabel("Select a file and click 'Generate Preview'");
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(summaryLabel, BorderLayout.NORTH);

        // Preview table
        previewTableModel = new PreviewTableModel();
        previewTable = new JTable(previewTableModel);
        previewTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        previewTable.setAutoCreateRowSorter(true);
        previewTable.getTableHeader().setReorderingAllowed(false);

        // Set column widths
        previewTable.getColumnModel().getColumn(0).setPreferredWidth(50);   // Row
        previewTable.getColumnModel().getColumn(1).setPreferredWidth(100);  // Receipt
        previewTable.getColumnModel().getColumn(2).setPreferredWidth(180);  // Student Name
        previewTable.getColumnModel().getColumn(3).setPreferredWidth(100);  // Program
        previewTable.getColumnModel().getColumn(4).setPreferredWidth(100);  // Amount
        previewTable.getColumnModel().getColumn(5).setPreferredWidth(100);  // Status
        previewTable.getColumnModel().getColumn(6).setPreferredWidth(120);  // Matched Student
        previewTable.getColumnModel().getColumn(7).setPreferredWidth(200);  // Details

        // Custom renderer for status column
        previewTable.getColumnModel().getColumn(5).setCellRenderer(new StatusCellRenderer());

        previewScrollPane = new JScrollPane(previewTable);
        panel.add(previewScrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Progress bar and status
        JPanel progressPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(300, 20));
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        statusLabel = new JLabel("Ready");
        progressPanel.add(statusLabel);
        progressPanel.add(progressBar);
        panel.add(progressPanel, BorderLayout.WEST);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        importButton = new JButton("Import");
        importButton.setEnabled(false);
        importButton.addActionListener(e -> performImport());
        buttonPanel.add(importButton);

        cancelButton = new JButton("Close");
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
            "Matched Student", "Details"
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
                case 6: return item.getMatchedStudentName() != null ?
                    item.getMatchedStudentCode() + " - " + item.getMatchedStudentName() :
                    (item.getProposedStudentCode() != null ? item.getProposedStudentCode() + " (new)" : "");
                case 7: return getDetails(item);
                default: return null;
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
            return String.class;
        }
    }

    // --- Status Cell Renderer ---

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final java.util.Map<String, Color> STATUS_COLORS = new java.util.HashMap<>();
        static {
            STATUS_COLORS.put(ImportPreviewItem.STATUS_NEW, new Color(0, 150, 0));
            STATUS_COLORS.put(ImportPreviewItem.STATUS_DUPLICATE, new Color(150, 150, 0));
            STATUS_COLORS.put(ImportPreviewItem.STATUS_CONFLICT, new Color(200, 100, 0));
            STATUS_COLORS.put(ImportPreviewItem.STATUS_AMBIGUOUS, new Color(200, 0, 0));
            STATUS_COLORS.put(ImportPreviewItem.STATUS_ERROR, new Color(150, 0, 0));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value != null) {
                String status = value.toString();
                Color color = STATUS_COLORS.getOrDefault(status, Color.BLACK);
                if (!isSelected) {
                    c.setForeground(color);
                }
                setText(status);
            }
            return c;
        }
    }
}