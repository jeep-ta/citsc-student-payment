package com.payment.ui;

import com.payment.ImportBatch;
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
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(Color.WHITE);

        // Title and import button
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel("Imports");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JButton importButton = new JButton("New Import");
        importButton.setFont(importButton.getFont().deriveFont(Font.BOLD, 12f));
        importButton.setBackground(new Color(0, 120, 215));
        importButton.setForeground(Color.WHITE);
        importButton.setFocusPainted(false);
        importButton.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        importButton.addActionListener(e -> openImportDialog());
        headerPanel.add(importButton, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Batch table
        batchTableModel = new BatchTableModel();
        batchTable = new JTable(batchTableModel);
        batchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        batchTable.setRowHeight(32);
        batchTable.setShowGrid(false);
        batchTable.setIntercellSpacing(new Dimension(0, 1));
        batchTable.getTableHeader().setReorderingAllowed(false);
        batchTable.getTableHeader().setBackground(new Color(245, 245, 245));
        batchTable.getTableHeader().setFont(batchTable.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        batchTable.setFont(batchTable.getFont().deriveFont(Font.PLAIN, 12f));

        // Column widths
        batchTable.getColumnModel().getColumn(0).setPreferredWidth(120); // Batch Code
        batchTable.getColumnModel().getColumn(1).setPreferredWidth(200); // File
        batchTable.getColumnModel().getColumn(2).setPreferredWidth(130); // Imported At
        batchTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Remittance Date
        batchTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Records
        batchTable.getColumnModel().getColumn(5).setPreferredWidth(80);  // New
        batchTable.getColumnModel().getColumn(6).setPreferredWidth(80);  // Duplicates
        batchTable.getColumnModel().getColumn(7).setPreferredWidth(80);  // Conflicts
        batchTable.getColumnModel().getColumn(8).setPreferredWidth(80);  // Errors
        batchTable.getColumnModel().getColumn(9).setPreferredWidth(100); // Status

        // Custom renderer for status
        batchTable.getColumnModel().getColumn(9).setCellRenderer(new StatusCellRenderer());

        JScrollPane scrollPane = new JScrollPane(batchTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220), 1));
        scrollPane.getViewport().setBackground(Color.WHITE);
        add(scrollPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Loading...");
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
        JDialog dialog = new JDialog(frame, "Import Payments", true);
        dialog.setSize(900, 600);
        dialog.setLocationRelativeTo(this);

        // Use the existing ImportDialog
        com.payment.ImportDialog importDialog = new com.payment.ImportDialog(frame, importService);
        importDialog.setVisible(true);

        // Refresh after import
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

        String details = String.format(
            "Batch Code: %s\n" +
            "File: %s\n" +
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
            "Batch Code", "File", "Imported At", "Remittance Date",
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

        @Override
        public int getRowCount() {
            return batches.size();
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
            ImportBatch b = batches.get(rowIndex);
            switch (columnIndex) {
                case 0: return b.getBatchCode();
                case 1: return b.getFileName();
                case 2: return b.getImportedAt() != null ? b.getImportedAt().format(dateFormat) : "-";
                case 3: return b.getRemittanceDate() != null ? b.getRemittanceDate().format(remittanceFormat) : "-";
                case 4: return b.getTotalRows();
                case 5: return b.getNewRecords();
                case 6: return b.getDuplicateRecords();
                case 7: return b.getConflictRecords();
                case 8: return b.getErrorRecords();
                case 9: return b.getStatus();
                default: return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex >= 4 && columnIndex <= 8) return Integer.class;
            return String.class;
        }
    }

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final java.util.Map<String, Color> STATUS_COLORS = new java.util.HashMap<>();
        static {
            STATUS_COLORS.put("COMPLETED", new Color(0, 150, 0));
            STATUS_COLORS.put("PENDING", new Color(200, 150, 0));
            STATUS_COLORS.put("FAILED", new Color(200, 0, 0));
            STATUS_COLORS.put("CANCELLED", new Color(150, 150, 150));
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
                setHorizontalAlignment(CENTER);
                setFont(getFont().deriveFont(Font.BOLD, 11f));
            }
            return c;
        }
    }
}