package com.payment.ui;

import com.payment.Payment;
import com.payment.Student;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Payments Panel - View and manage payment records.
 */
public class PaymentPanel extends JPanel {

    private final DatabaseManager db;

    private JTable paymentTable;
    private PaymentTableModel paymentTableModel;
    private JTextField searchField;
    private JComboBox<String> programFilter;
    private JComboBox<String> statusFilter;
    private JTextField receiptField;
    private JLabel statusLabel;

    public PaymentPanel() {
        this.db = DatabaseManager.getInstance();
        initializeUI();
        refreshData();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(Color.WHITE);

        // Title
        JLabel titleLabel = new JLabel("Payments");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        add(titleLabel, BorderLayout.NORTH);

        // Toolbar
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        toolBar.setBackground(Color.WHITE);
        toolBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        toolBar.add(new JLabel("Search:"));
        searchField = new JTextField(18);
        searchField.setToolTipText("Search by student name...");
        searchField.addActionListener(e -> filterPayments());
        toolBar.add(searchField);

        toolBar.add(new JLabel("Receipt #:"));
        receiptField = new JTextField(10);
        receiptField.addActionListener(e -> filterPayments());
        toolBar.add(receiptField);

        toolBar.add(new JLabel("Program:"));
        programFilter = new JComboBox<>(new String[]{"All Programs"});
        programFilter.addActionListener(e -> filterPayments());
        toolBar.add(programFilter);

        toolBar.add(new JLabel("Status:"));
        statusFilter = new JComboBox<>(new String[]{"All", "ACTIVE", "VOID"});
        statusFilter.addActionListener(e -> filterPayments());
        toolBar.add(statusFilter);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshData());
        toolBar.add(refreshButton);

        add(toolBar, BorderLayout.NORTH);

        // Payment table
        paymentTableModel = new PaymentTableModel();
        paymentTable = new JTable(paymentTableModel);
        paymentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        paymentTable.setRowHeight(28);
        paymentTable.setShowGrid(false);
        paymentTable.setIntercellSpacing(new Dimension(0, 1));
        paymentTable.getTableHeader().setReorderingAllowed(false);
        paymentTable.getTableHeader().setBackground(new Color(245, 245, 245));
        paymentTable.getTableHeader().setFont(paymentTable.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        paymentTable.setFont(paymentTable.getFont().deriveFont(Font.PLAIN, 12f));

        // Column widths
        paymentTable.getColumnModel().getColumn(0).setPreferredWidth(50);   // #
        paymentTable.getColumnModel().getColumn(1).setPreferredWidth(100);  // Receipt
        paymentTable.getColumnModel().getColumn(2).setPreferredWidth(120);  // Student Code
        paymentTable.getColumnModel().getColumn(3).setPreferredWidth(180);  // Name
        paymentTable.getColumnModel().getColumn(4).setPreferredWidth(100);  // Program
        paymentTable.getColumnModel().getColumn(5).setPreferredWidth(100);  // Date
        paymentTable.getColumnModel().getColumn(6).setPreferredWidth(90);   // Intel Fee
        paymentTable.getColumnModel().getColumn(7).setPreferredWidth(90);   // T-Shirt
        paymentTable.getColumnModel().getColumn(8).setPreferredWidth(90);   // Penalties
        paymentTable.getColumnModel().getColumn(9).setPreferredWidth(90);   // CIT Night
        paymentTable.getColumnModel().getColumn(10).setPreferredWidth(100); // Received By
        paymentTable.getColumnModel().getColumn(11).setPreferredWidth(80);  // Status
        paymentTable.getColumnModel().getColumn(12).setPreferredWidth(100); // Total

        // Custom renderers
        paymentTable.getColumnModel().getColumn(11).setCellRenderer(new StatusCellRenderer());
        paymentTable.getColumnModel().getColumn(12).setCellRenderer(new CurrencyCellRenderer());

        // Row sorter
        TableRowSorter<PaymentTableModel> sorter = new TableRowSorter<>(paymentTableModel);
        paymentTable.setRowSorter(sorter);

        JScrollPane scrollPane = new JScrollPane(paymentTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220), 1));
        scrollPane.getViewport().setBackground(Color.WHITE);
        add(scrollPane, BorderLayout.CENTER);

        // Status bar with summary
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(Color.WHITE);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        statusLabel = new JLabel("Loading...");
        statusPanel.add(statusLabel, BorderLayout.WEST);

        JLabel summaryLabel = new JLabel("Total: ₱0.00");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD, 14f));
        summaryLabel.setForeground(new Color(0, 100, 0));
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        statusPanel.add(summaryLabel, BorderLayout.EAST);

        add(statusPanel, BorderLayout.SOUTH);

        // Store reference for summary update
        this.summaryLabel = summaryLabel;
    }

    private JLabel summaryLabel;

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
                    for (Payment p : payments) {
                        if (p.getProgram() != null && !p.getProgram().isEmpty()) {
                            programs.add(p.getProgram());
                        }
                    }
                    String selected = (String) programFilter.getSelectedItem();
                    programFilter.removeAllItems();
                    programFilter.addItem("All Programs");
                    for (String p : programs) {
                        programFilter.addItem(p);
                    }
                    if (selected != null) {
                        programFilter.setSelectedItem(selected);
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

        // Name search
        String searchText = searchField.getText().trim();
        if (!searchText.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + searchText, 3)); // Name column
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

        // Program filter
        String program = (String) programFilter.getSelectedItem();
        if (program != null && !"All Programs".equals(program)) {
            filters.add(RowFilter.regexFilter("^" + java.util.regex.Pattern.quote(program) + "$", 4));
        }

        // Status filter
        String status = (String) statusFilter.getSelectedItem();
        if (status != null && !"All".equals(status)) {
            filters.add(RowFilter.regexFilter("^" + status + "$", 11));
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

    private static class PaymentTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
            "#", "Receipt #", "Student Code", "Student Name", "Program",
            "Remittance Date", "Intel Fee", "T-Shirt", "Penalties", "CIT Night",
            "Received By", "Status", "Total"
        };
        private List<Payment> payments = List.of();
        private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        public void setPayments(List<Payment> payments) {
            this.payments = payments;
            fireTableDataChanged();
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
                case 11: return p.getStatus() != null ? p.getStatus() : "ACTIVE";
                case 12: return p.getTotalAmount();
                default: return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0 || columnIndex == 1) return Integer.class;
            if (columnIndex >= 6 && columnIndex <= 12) return Double.class;
            return String.class;
        }
    }

    // --- Cell Renderers ---

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final java.util.Map<String, Color> STATUS_COLORS = new java.util.HashMap<>();
        static {
            STATUS_COLORS.put("ACTIVE", new Color(0, 150, 0));
            STATUS_COLORS.put("VOID", new Color(200, 0, 0));
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

    private static class CurrencyCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof Number) {
                setText(String.format("₱%,.2f", ((Number) value).doubleValue()));
                setHorizontalAlignment(RIGHT);
            }
            return c;
        }
    }
}