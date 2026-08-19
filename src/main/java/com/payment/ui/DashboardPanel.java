package com.payment.ui;

import com.payment.AuditService;
import com.payment.ImportBatch;
import com.payment.Payment;
import com.payment.Student;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Dashboard Panel - Shows overview statistics and recent activity.
 */
public class DashboardPanel extends JPanel {

    private final DatabaseManager db;
    private final AuditService auditService;

    // Summary cards
    private JLabel studentCountLabel;
    private JLabel paymentCountLabel;
    private JLabel totalCollectedLabel;
    private JLabel batchCountLabel;

    // Recent activity
    private JTable recentImportsTable;
    private RecentImportsTableModel recentImportsModel;

    public DashboardPanel() {
        this.db = DatabaseManager.getInstance();
        this.auditService = new AuditService();
        initializeUI();
        refreshData();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(15, 15, 15, 15));
        setBackground(Color.WHITE);

        // Title
        JLabel titleLabel = new JLabel("Dashboard");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        titleLabel.setBorder(new EmptyBorder(0, 0, 15, 0));
        add(titleLabel, BorderLayout.NORTH);

        // Main content area with scroll
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

        // Summary cards row
        JPanel summaryPanel = createSummaryCards();
        summaryPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        contentPanel.add(summaryPanel);
        contentPanel.add(Box.createVerticalStrut(20));

        // Recent activity sections
        JPanel activityPanel = createActivitySections();
        contentPanel.add(activityPanel);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createSummaryCards() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 15, 15));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(0, 0, 0, 0));

        panel.add(createSummaryCard("Students", "0", new Color(0, 120, 215), "Total registered students", l -> studentCountLabel = l));
        panel.add(createSummaryCard("Payments", "0", new Color(0, 150, 0), "Total payment records", l -> paymentCountLabel = l));
        panel.add(createSummaryCard("Total Collected", "₱0.00", new Color(0, 100, 0), "Sum of all payments", l -> totalCollectedLabel = l));
        panel.add(createSummaryCard("Import Batches", "0", new Color(150, 0, 150), "Completed imports", l -> batchCountLabel = l));

        return panel;
    }

    private JPanel createSummaryCard(String title, String value, Color accentColor, String subtitle, java.util.function.Consumer<JLabel> valueLabelConsumer) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            new EmptyBorder(15, 20, 15, 20)
        ));

        // Accent bar at top
        JPanel accentBar = new JPanel();
        accentBar.setBackground(accentColor);
        accentBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        accentBar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 4));
        card.add(accentBar);
        card.add(Box.createVerticalStrut(10));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 13f));
        titleLabel.setForeground(new Color(100, 100, 100));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(5));

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 28f));
        valueLabel.setForeground(Color.BLACK);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(valueLabel);

        if (valueLabelConsumer != null) {
            valueLabelConsumer.accept(valueLabel);
        }

        card.add(Box.createVerticalStrut(5));

        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 11f));
        subtitleLabel.setForeground(new Color(150, 150, 150));
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(subtitleLabel);

        return card;
    }

    private JPanel createActivitySections() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 15, 15));
        panel.setBackground(Color.WHITE);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        // Recent Imports
        JPanel importsPanel = createSectionPanel("Recent Import Batches", createRecentImportsTable());
        panel.add(importsPanel);

        // Recent Audit Activity
        JPanel auditPanel = createSectionPanel("Recent Activity", createRecentAuditTable());
        panel.add(auditPanel);

        return panel;
    }

    private JPanel createSectionPanel(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            new EmptyBorder(0, 0, 0, 0)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setBorder(new EmptyBorder(15, 15, 5, 15));
        panel.add(titleLabel, BorderLayout.NORTH);

        content.setBorder(new EmptyBorder(0, 10, 10, 10));
        panel.add(content, BorderLayout.CENTER);

        return panel;
    }

    private JScrollPane createRecentImportsTable() {
        recentImportsModel = new RecentImportsTableModel();
        recentImportsTable = new JTable(recentImportsModel);
        recentImportsTable.setRowHeight(28);
        recentImportsTable.setShowGrid(false);
        recentImportsTable.setIntercellSpacing(new Dimension(0, 1));
        recentImportsTable.getTableHeader().setReorderingAllowed(false);
        recentImportsTable.getTableHeader().setBackground(new Color(245, 245, 245));
        recentImportsTable.getTableHeader().setFont(recentImportsTable.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        recentImportsTable.setFont(recentImportsTable.getFont().deriveFont(Font.PLAIN, 12f));

        recentImportsTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        recentImportsTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        recentImportsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        recentImportsTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        recentImportsTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        recentImportsTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        recentImportsTable.getColumnModel().getColumn(6).setPreferredWidth(80);

        JScrollPane scrollPane = new JScrollPane(recentImportsTable);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setPreferredSize(new Dimension(400, 250));
        return scrollPane;
    }

    private JScrollPane createRecentAuditTable() {
        AuditTableModel auditModel = new AuditTableModel();
        JTable auditTable = new JTable(auditModel);
        auditTable.setRowHeight(28);
        auditTable.setShowGrid(false);
        auditTable.setIntercellSpacing(new Dimension(0, 1));
        auditTable.getTableHeader().setReorderingAllowed(false);
        auditTable.getTableHeader().setBackground(new Color(245, 245, 245));
        auditTable.getTableHeader().setFont(auditTable.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        auditTable.setFont(auditTable.getFont().deriveFont(Font.PLAIN, 12f));

        auditTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        auditTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        auditTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        auditTable.getColumnModel().getColumn(3).setPreferredWidth(180);

        // Custom renderer for action column
        auditTable.getColumnModel().getColumn(1).setCellRenderer(new ActionCellRenderer());

        JScrollPane scrollPane = new JScrollPane(auditTable);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setPreferredSize(new Dimension(400, 250));

        // Load audit data
        SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Map<String, Object>> doInBackground() {
                return auditService.getRecentAuditLogs(10);
            }

            @Override
            protected void done() {
                try {
                    auditModel.setLogs(get());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();

        return scrollPane;
    }

    public void refreshData() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private int studentCount = 0;
            private int paymentCount = 0;
            private double totalCollected = 0;
            private int batchCount = 0;
            private List<ImportBatch> recentBatches = List.of();

            @Override
            protected Void doInBackground() throws Exception {
                studentCount = db.getStudentCount();
                paymentCount = db.getPaymentCount();

                List<Student> students = db.getAllStudents();
                for (Student s : students) {
                    totalCollected += s.getTotalAmount();
                }

                recentBatches = db.getAllImportBatches();
                batchCount = recentBatches.size();

                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions

                    if (studentCountLabel != null) studentCountLabel.setText(String.valueOf(studentCount));
                    if (paymentCountLabel != null) paymentCountLabel.setText(String.valueOf(paymentCount));
                    if (totalCollectedLabel != null) totalCollectedLabel.setText(String.format("₱%,.2f", totalCollected));
                    if (batchCountLabel != null) batchCountLabel.setText(String.valueOf(batchCount));

                    recentImportsModel.setBatches(recentBatches.subList(0, Math.min(5, recentBatches.size())));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    // --- Table Models ---

    private static class RecentImportsTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Batch Code", "File", "Date", "Records", "New", "Duplicates", "Status"};
        private List<ImportBatch> batches = List.of();

        public void setBatches(List<ImportBatch> batches) {
            this.batches = batches;
            fireTableDataChanged();
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
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");
            switch (columnIndex) {
                case 0: return b.getBatchCode();
                case 1: return b.getFileName();
                case 2: return b.getImportedAt() != null ? b.getImportedAt().format(fmt) : "-";
                case 3: return b.getTotalRows();
                case 4: return b.getNewRecords();
                case 5: return b.getDuplicateRecords();
                case 6: return b.getStatus();
                default: return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex >= 2 && columnIndex <= 5) return Integer.class;
            return String.class;
        }
    }

    private static class AuditTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Timestamp", "Action", "Entity", "Details"};
        private List<Map<String, Object>> logs = List.of();

        public void setLogs(List<Map<String, Object>> logs) {
            this.logs = logs;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return logs.size();
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
            Map<String, Object> log = logs.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    Object ts = log.get("timestamp");
                    if (ts != null) {
                        try {
                            return java.time.LocalDateTime.parse(ts.toString()).format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"));
                        } catch (Exception e) {
                            return ts.toString().substring(0, Math.min(16, ts.toString().length()));
                        }
                    }
                    return "-";
                case 1: return log.get("action");
                case 2: return log.get("entity_type") + " " + log.get("entity_id");
                case 3: return log.get("reason");
                default: return null;
            }
        }
    }

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
                setFont(getFont().deriveFont(Font.BOLD, 11f));
            }
            return c;
        }
    }
}