package com.payment.ui;

import com.payment.AuditService;

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
 */
public class AuditLogPanel extends JPanel {

    private final AuditService auditService;
    private JTable auditTable;
    private AuditTableModel auditTableModel;
    private JComboBox<String> entityTypeFilter;
    private JTextField entityIdFilter;
    private JLabel statusLabel;

    public AuditLogPanel() {
        this.auditService = new AuditService();
        initializeUI();
        loadAuditLogs(200);
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
            BorderFactory.createMatteBorder(0, 0, 2, 0, ThemeUtils.NEON_CYAN),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLabel = new JLabel("📋 Audit Trail & System Logs");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        titleLabel.setForeground(ThemeUtils.NEON_CYAN);
        titleBanner.add(titleLabel, BorderLayout.WEST);

        JLabel subtitleLabel = new JLabel("Financial Record Change History");
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

        JLabel typeLbl = new JLabel("Entity Type:");
        typeLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        toolBar.add(typeLbl);
        entityTypeFilter = new JComboBox<>(new String[]{
            "All", "STUDENT", "PAYMENT", "IMPORT_BATCH", "DATABASE"
        });
        entityTypeFilter.addActionListener(e -> loadAuditLogs(200));
        toolBar.add(entityTypeFilter);

        JLabel idLbl = new JLabel("Entity ID:");
        idLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        toolBar.add(idLbl);
        entityIdFilter = new JTextField(15);
        entityIdFilter.addActionListener(e -> loadAuditLogs(200));
        toolBar.add(entityIdFilter);

        JButton refreshButton = new JButton("🔄 Refresh");
        ThemeUtils.styleButton(refreshButton, ThemeUtils.NEON_CYAN);
        refreshButton.addActionListener(e -> loadAuditLogs(200));
        toolBar.add(refreshButton);

        JButton exportButton = new JButton("📊 Export CSV");
        ThemeUtils.styleButton(exportButton, ThemeUtils.NEON_GREEN);
        exportButton.addActionListener(e -> exportToCSV());
        toolBar.add(exportButton);

        headerPanel.add(toolBar, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // Table
        auditTableModel = new AuditTableModel();
        auditTable = new JTable(auditTableModel);
        ThemeUtils.applyTableTheme(auditTable);
        auditTable.setAutoCreateRowSorter(true);

        // Column widths
        auditTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        auditTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        auditTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        auditTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        auditTable.getColumnModel().getColumn(4).setPreferredWidth(250);
        auditTable.getColumnModel().getColumn(5).setPreferredWidth(250);
        auditTable.getColumnModel().getColumn(6).setPreferredWidth(250);
        auditTable.getColumnModel().getColumn(7).setPreferredWidth(80);

        // Custom renderer for action column
        auditTable.getColumnModel().getColumn(1).setCellRenderer(new ActionCellRenderer());

        JScrollPane scrollPane = new JScrollPane(auditTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(ThemeUtils.BG_SURFACE);
        add(scrollPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Loading audit logs...");
        statusLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(statusLabel, BorderLayout.SOUTH);
    }

    public void loadAuditLogs(int limit) {
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
                writer.println("Timestamp,Action,Entity Type,Entity ID,Old Value,New Value,Reason,User");
                for (int i = 0; i < auditTableModel.getRowCount(); i++) {
                    StringBuilder row = new StringBuilder();
                    for (int j = 0; j < auditTableModel.getColumnCount(); j++) {
                        Object value = auditTableModel.getValueAt(i, j);
                        String str = value != null ? value.toString() : "";
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

        @Override public int getRowCount() { return logs != null ? logs.size() : 0; }
        @Override public int getColumnCount() { return COLUMN_NAMES.length; }
        @Override public String getColumnName(int column) { return COLUMN_NAMES[column]; }

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
            ACTION_COLORS.put("CREATE", ThemeUtils.NEON_GREEN);
            ACTION_COLORS.put("UPDATE", ThemeUtils.NEON_CYAN);
            ACTION_COLORS.put("VOID", ThemeUtils.NEON_ROSE);
            ACTION_COLORS.put("IMPORT", ThemeUtils.NEON_PURPLE);
            ACTION_COLORS.put("MERGE", ThemeUtils.NEON_AMBER);
            ACTION_COLORS.put("UNDO_MERGE", ThemeUtils.NEON_CYAN);
            ACTION_COLORS.put("DELETE", ThemeUtils.NEON_ROSE);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value != null) {
                String action = value.toString();
                Color color = ACTION_COLORS.getOrDefault(action, ThemeUtils.TEXT_SECONDARY);
                if (!isSelected) c.setForeground(color);
                setText(action);
                setHorizontalAlignment(CENTER);
                setFont(getFont().deriveFont(Font.BOLD, 11f));
            }
            return c;
        }
    }
}