package com.payment.ui;

import com.payment.ImportBatch;
import com.payment.ImportBatchFile;
import com.payment.ImportService;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Imports Panel - View import history and initiate new imports.
 */
public class ImportPanel extends JPanel {

    private final DatabaseManager db;
    private final ImportService importService;

    private JTable batchTable;
    private BatchTableModel batchTableModel;
    private JLabel statusLabel;

    public ImportPanel() {
        this.db = DatabaseManager.getInstance();
        this.importService = new ImportService();
        initializeUI();
        refreshData();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(ThemeUtils.BG_DEEPEST);

        // Header with gradient banner
        JPanel headerPanel = new JPanel(new BorderLayout(0, 10));
        headerPanel.setOpaque(false);

        JPanel titleBanner = ThemeUtils.createGradientPanel(new Color(15, 23, 42), new Color(10, 14, 26));
        titleBanner.setLayout(new BorderLayout());
        titleBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, ThemeUtils.NEON_CYAN),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLabel = new JLabel("📥 Import Manager");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        titleLabel.setForeground(ThemeUtils.NEON_CYAN);
        titleBanner.add(titleLabel, BorderLayout.WEST);

        JButton importButton = new JButton("⚡ New Import");
        importButton.setFont(importButton.getFont().deriveFont(Font.BOLD, 12f));
        ThemeUtils.styleButton(importButton, ThemeUtils.NEON_GREEN);
        importButton.addActionListener(e -> openImportDialog());
        titleBanner.add(importButton, BorderLayout.EAST);

        headerPanel.add(titleBanner, BorderLayout.NORTH);
        add(headerPanel, BorderLayout.NORTH);

        // Batch table
        batchTableModel = new BatchTableModel();
        batchTable = new JTable(batchTableModel);
        batchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ThemeUtils.applyTableTheme(batchTable);

        // Column widths
        batchTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        batchTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        batchTable.getColumnModel().getColumn(2).setPreferredWidth(50);
        batchTable.getColumnModel().getColumn(3).setPreferredWidth(160);
        batchTable.getColumnModel().getColumn(4).setPreferredWidth(130);
        batchTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        batchTable.getColumnModel().getColumn(6).setPreferredWidth(70);
        batchTable.getColumnModel().getColumn(7).setPreferredWidth(70);
        batchTable.getColumnModel().getColumn(8).setPreferredWidth(70);
        batchTable.getColumnModel().getColumn(9).setPreferredWidth(70);
        batchTable.getColumnModel().getColumn(10).setPreferredWidth(70);
        batchTable.getColumnModel().getColumn(11).setPreferredWidth(100);

        // Custom renderer for status
        batchTable.getColumnModel().getColumn(11).setCellRenderer(new StatusCellRenderer());

        JScrollPane scrollPane = new JScrollPane(batchTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(ThemeUtils.BG_SURFACE);
        add(scrollPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Loading import batches...");
        statusLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(statusLabel, BorderLayout.SOUTH);

        // Double-click to view details
        batchTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = batchTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        int modelRow = batchTable.convertRowIndexToModel(row);
                        showBatchDetails(modelRow);
                    }
                }
            }
        });
    }

    private void openImportDialog() {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        com.payment.ImportDialog importDialog = new com.payment.ImportDialog(frame, importService);
        importDialog.setVisible(true);
        refreshData();
    }

    public void refreshData() {
        SwingWorker<List<ImportBatch>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<ImportBatch> doInBackground() throws Exception {
                return db.getAllImportBatches();
            }

            @Override
            protected void done() {
                try {
                    List<ImportBatch> batches = get();
                    batchTableModel.setBatches(batches);
                    statusLabel.setText(String.format("Showing %d import batches", batches.size()));
                } catch (Exception e) {
                    statusLabel.setText("Error loading imports: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void showBatchDetails(int modelRow) {
        ImportBatch batch = batchTableModel.getBatch(modelRow);

        StringBuilder fileDetails = new StringBuilder();
        try {
            for (ImportBatchFile file : db.getImportBatchFiles(batch.getBatchCode())) {
                fileDetails.append(String.format("\n  • %s [%s] — %d rows, %d new, %d duplicate, %d conflict, %d error",
                    file.getFileName(), file.getReceiptPeriodDisplay(), file.getTotalRows(), file.getNewRecords(),
                    file.getDuplicateRecords(), file.getConflictRecords(), file.getErrorRecords()));
            }
        } catch (Exception ignored) {
            fileDetails.append("\n  File details unavailable");
        }

        String details = String.format(
            "Batch Code: %s\n" +
            "Source: %s\n" +
            "Files: %d%s\n" +
            "Receipt Period: %s\n" +
            "Imported By: %s\n" +
            "Imported At: %s\n" +
            "Remittance Date: %s\n" +
            "Total Rows: %d\n" +
            "New Records: %d\n" +
            "Duplicates: %d\n" +
            "Conflicts: %d\n" +
            "Errors: %d\n" +
            "Status: %s",
            batch.getBatchCode(),
            batch.getFileName(),
            batch.getFileCount(),
            fileDetails,
            batch.getReceiptPeriodDisplay(),
            batch.getImportedBy(),
            batch.getImportedAt() != null ? batch.getImportedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")) : "-",
            batch.getRemittanceDate() != null ? batch.getRemittanceDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) : "-",
            batch.getTotalRows(),
            batch.getNewRecords(),
            batch.getDuplicateRecords(),
            batch.getConflictRecords(),
            batch.getErrorRecords(),
            batch.getStatus()
        );

        JOptionPane.showMessageDialog(this, details, "Import Batch Details", JOptionPane.INFORMATION_MESSAGE);
    }

    // --- Table Model ---

    private static class BatchTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
            "Batch Code", "Source", "Files", "Receipt Period", "Imported At", "Remittance Date",
            "Records", "New", "Duplicates", "Conflicts", "Errors", "Status"
        };
        private List<ImportBatch> batches = List.of();
        private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        private final DateTimeFormatter remittanceFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        public void setBatches(List<ImportBatch> batches) {
            this.batches = batches;
            fireTableDataChanged();
        }

        public ImportBatch getBatch(int rowIndex) {
            return batches.get(rowIndex);
        }

        @Override public int getRowCount() { return batches.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ImportBatch b = batches.get(rowIndex);
            switch (columnIndex) {
                case 0: return b.getBatchCode();
                case 1: return b.getFileName();
                case 2: return b.getFileCount();
                case 3: return b.getReceiptPeriodDisplay();
                case 4: return b.getImportedAt() != null ? b.getImportedAt().format(dateFormat) : "-";
                case 5: return b.getRemittanceDate() != null ? b.getRemittanceDate().format(remittanceFormat) : "-";
                case 6: return b.getTotalRows();
                case 7: return b.getNewRecords();
                case 8: return b.getDuplicateRecords();
                case 9: return b.getConflictRecords();
                case 10: return b.getErrorRecords();
                case 11: return b.getStatus();
                default: return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 2 || (columnIndex >= 6 && columnIndex <= 10)) return Integer.class;
            return String.class;
        }
    }

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final java.util.Map<String, Color> STATUS_COLORS = new java.util.HashMap<>();
        static {
            STATUS_COLORS.put("COMPLETED", ThemeUtils.NEON_GREEN);
            STATUS_COLORS.put("PENDING", ThemeUtils.NEON_AMBER);
            STATUS_COLORS.put("FAILED", ThemeUtils.NEON_ROSE);
            STATUS_COLORS.put("CANCELLED", ThemeUtils.TEXT_MUTED);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value != null) {
                String status = value.toString();
                Color color = STATUS_COLORS.getOrDefault(status, ThemeUtils.TEXT_SECONDARY);
                if (!isSelected) c.setForeground(color);
                setText(status);
                setHorizontalAlignment(CENTER);
                setFont(getFont().deriveFont(Font.BOLD, 11f));
            }
            return c;
        }
    }
}
