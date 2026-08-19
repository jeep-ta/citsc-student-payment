package com.payment;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Audit Log Panel - displays audit trail for financial records.
 * Can be embedded in the main UI as a tab or shown as a dialog.
 */
public class AuditLogPanel extends JPanel {

    private final AuditService auditService;
    private JTable auditTable;
    private AuditTableModel auditTableModel;
    private JComboBox<String> entityTypeFilter;
    private JTextField entityIdFilter;
    private JButton refreshButton;
    private JLabel statusLabel;

    public AuditLogPanel() {
        this.auditService = new AuditService();
        initializeUI();
        loadAuditLogs(100); // Load initial data
    }

    private void initializeUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top toolbar
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolBar.setBorder(BorderFactory.createTitledBorder("Filters"));

        toolBar.add(new JLabel("Entity Type:"));
        entityTypeFilter = new JComboBox<>(new String[]{
            "All", "STUDENT", "PAYMENT", "IMPORT_BATCH", "DATABASE"
        });
        entityTypeFilter.addActionListener(e -> refresh());
        toolBar.add(entityTypeFilter);

        toolBar.add(new JLabel("Entity ID:"));
        entityIdFilter = new JTextField(15);
        entityIdFilter.addActionListener(e -> refresh());
        toolBar.add(entityIdFilter);

        refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refresh());
        toolBar.add(refreshButton);

        JButton exportButton = new JButton("Export to CSV");
        exportButton.addActionListener(e -> exportToCSV());
        toolBar.add(exportButton);

        add(toolBar, BorderLayout.NORTH);

        // Table
        auditTableModel = new AuditTableModel();
        auditTable = new JTable(auditTableModel);
        auditTable.setRowHeight(25);
        auditTable.setAutoCreateRowSorter(true);
        auditTable.getTableHeader().setReorderingAllowed(false);
        auditTable.getColumnModel().getColumn(0).setPreferredWidth(150); // Timestamp
        auditTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // Action
        auditTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Entity Type
        auditTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Entity ID
        auditTable.getColumnModel().getColumn(4).setPreferredWidth(200); // Old Value
        auditTable.getColumnModel().getColumn(5).setPreferredWidth(200); // New Value
        auditTable.getColumnModel().getColumn(6).setPreferredWidth(200); // Reason
        auditTable.getColumnModel().getColumn(7).setPreferredWidth(80);  // User

        // Custom renderer for action column
        auditTable.getColumnModel().getColumn(1).setCellRenderer(new ActionCellRenderer());

        JScrollPane scrollPane = new JScrollPane(auditTable);
        scrollPane.setPreferredSize(new Dimension(900, 400));
        add(scrollPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Loading...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void loadAuditLogs(int limit) {
        SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Map<String, Object>> doInBackground() throws Exception {
                if ("All".equals(entityTypeFilter.getSelectedItem())) {
                    return auditService.getRecentAuditLogs(limit);
                } else {
                    String entityId = entityIdFilter.getText().trim();
                    if (entityId.isEmpty()) {
                        return auditService.getRecentAuditLogs(limit);
                    }
                    return auditService.getAuditLogsForEntity(
                        entityTypeFilter.getSelectedItem().toString(),
                        entityId
                    );
                }
            }

            @Override
            protected void done() {
                try {
                    List<Map<String, Object>> logs = get();
                    auditTableModel.setLogs(logs);
                    statusLabel.setText(String.format("Showing %d audit log entries", logs.size()));
                } catch (Exception e) {
                    statusLabel.setText("Error loading audit logs: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void refresh() {
        loadAuditLogs(200);
    }

    private void exportToCSV() {
        if (auditTableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No data to export", "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV Files", "csv"));
        fileChooser.setSelectedFile(new java.io.File("audit_log_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(fileChooser.getSelectedFile())) {
                // Write header
                writer.println("Timestamp,Action,Entity Type,Entity ID,Old Value,New Value,Reason,User");

                // Write data
                for (int i = 0; i < auditTableModel.getRowCount(); i++) {
                    StringBuilder row = new StringBuilder();
                    for (int j = 0; j < auditTableModel.getColumnCount(); j++) {
                        Object value = auditTableModel.getValueAt(i, j);
                        String str = value != null ? value.toString() : "";
                        // Escape commas and quotes
                        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
                            str = "\"" + str.replace("\"", "\"\"") + "\"";
                        }
                        row.append(str);
                        if (j < auditTableModel.getColumnCount() - 1) row.append(",");
                    }
                    writer.println(row);
                }
                statusLabel.setText("Exported to " + fileChooser.getSelectedFile().getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- Table Model ---

    private static class AuditTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {
            "Timestamp", "Action", "Entity Type", "Entity ID", "Old Value", "New Value", "Reason", "User"
        };

        private List<Map<String, Object>> logs;

        public void setLogs(List<Map<String, Object>> logs) {
            this.logs = logs;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return logs != null ? logs.size() : 0;
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
            if (logs == null || rowIndex >= logs.size()) return null;
            Map<String, Object> row = logs.get(rowIndex);

            switch (columnIndex) {
                case 0: return formatTimestamp(row.get("timestamp"));
                case 1: return row.get("action");
                case 2: return row.get("entity_type");
                case 3: return row.get("entity_id");
                case 4: return row.get("old_value");
                case 5: return row.get("new_value");
                case 6: return row.get("reason");
                case 7: return row.get("user");
                default: return null;
            }
        }

        private String formatTimestamp(Object ts) {
            if (ts == null) return "";
            try {
                String str = ts.toString();
                LocalDateTime dt = LocalDateTime.parse(str);
                return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e) {
                return ts.toString();
            }
        }
    }

    // --- Cell Renderer for Action Column ---

    private static class ActionCellRenderer extends DefaultTableCellRenderer {
        private static final java.util.Map<String, Color> ACTION_COLORS = new java.util.HashMap<>();
        static {
            ACTION_COLORS.put("CREATE", new Color(0, 150, 0));
            ACTION_COLORS.put("UPDATE", new Color(0, 100, 200));
            ACTION_COLORS.put("VOID", new Color(200, 0, 0));
            ACTION_COLORS.put("IMPORT", new Color(150, 0, 150));
            ACTION_COLORS.put("MERGE", new Color(0, 150, 150));
            ACTION_COLORS.put("DELETE", new Color(150, 0, 0));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value != null) {
                String action = value.toString();
                Color color = ACTION_COLORS.getOrDefault(action, Color.BLACK);
                if (!isSelected) {
                    c.setForeground(color);
                }
                setText(action);
                setHorizontalAlignment(CENTER);
            }
            return c;
        }
    }

    // --- Main for testing ---
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            JFrame frame = new JFrame("Audit Log Viewer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1000, 600);
            frame.setLocationRelativeTo(null);
            frame.add(new AuditLogPanel());
            frame.setVisible(true);
        });
    }
}