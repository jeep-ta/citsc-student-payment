package com.payment.ui;

import com.payment.Payment;
import com.payment.Student;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reports Panel - Generate and export reports.
 */
public class ReportsPanel extends JPanel {

    private final DatabaseManager db;

    private JComboBox<String> reportTypeCombo;
    private JTextField dateFromField;
    private JTextField dateToField;
    private JComboBox<String> programFilter;
    private JTable reportTable;
    private ReportTableModel reportTableModel;
    private JLabel statusLabel;

    public ReportsPanel() {
        this.db = DatabaseManager.getInstance();
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(Color.WHITE);

        // Title
        JLabel titleLabel = new JLabel("Reports");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        add(titleLabel, BorderLayout.NORTH);

        // Controls panel
        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setBackground(Color.WHITE);
        controlsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        // Row 1: Report type
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        row1.setBackground(Color.WHITE);
        row1.add(new JLabel("Report Type:"));
        reportTypeCombo = new JComboBox<>(new String[]{
            "Student Payment Report",
            "Collection Summary",
            "Remittance Report",
            "Receiver Report",
            "Import Batch Report"
        });
        reportTypeCombo.setPreferredSize(new Dimension(250, 28));
        row1.add(reportTypeCombo);
        controlsPanel.add(row1);

        // Row 2: Filters
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        row2.setBackground(Color.WHITE);
        row2.add(new JLabel("Date From:"));
        dateFromField = new JTextField(12);
        dateFromField.setToolTipText("yyyy-MM-dd");
        row2.add(dateFromField);

        row2.add(new JLabel("Date To:"));
        dateToField = new JTextField(12);
        dateToField.setToolTipText("yyyy-MM-dd");
        row2.add(dateToField);

        row2.add(new JLabel("Program:"));
        programFilter = new JComboBox<>(new String[]{"All Programs"});
        programFilter.setPreferredSize(new Dimension(150, 28));
        row2.add(programFilter);
        controlsPanel.add(row2);

        // Row 3: Buttons
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        row3.setBackground(Color.WHITE);

        JButton generateButton = new JButton("Generate Report");
        generateButton.addActionListener(e -> generateReport());
        row3.add(generateButton);

        JButton exportCsvButton = new JButton("Export CSV");
        exportCsvButton.addActionListener(e -> exportCSV());
        row3.add(exportCsvButton);

        JButton exportExcelButton = new JButton("Export Excel");
        exportExcelButton.addActionListener(e -> exportExcel());
        row3.add(exportExcelButton);

        controlsPanel.add(row3);

        add(controlsPanel, BorderLayout.NORTH);

        // Report table
        reportTableModel = new ReportTableModel();
        reportTable = new JTable(reportTableModel);
        reportTable.setRowHeight(28);
        reportTable.setShowGrid(false);
        reportTable.setIntercellSpacing(new Dimension(0, 1));
        reportTable.getTableHeader().setReorderingAllowed(false);
        reportTable.getTableHeader().setBackground(new Color(245, 245, 245));
        reportTable.getTableHeader().setFont(reportTable.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        reportTable.setFont(reportTable.getFont().deriveFont(Font.PLAIN, 12f));
        reportTable.setAutoCreateRowSorter(true);

        JScrollPane scrollPane = new JScrollPane(reportTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220), 1));
        scrollPane.getViewport().setBackground(Color.WHITE);
        add(scrollPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Select a report type and click Generate");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void generateReport() {
        String reportType = (String) reportTypeCombo.getSelectedItem();
        if (reportType == null) return;

        statusLabel.setText("Generating report...");
        reportTableModel.setData(null, new String[0]);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private List<Map<String, Object>> data = List.of();
            private String[] columns = new String[0];

            @Override
            protected Void doInBackground() throws Exception {
                switch (reportType) {
                    case "Student Payment Report":
                        data = generateStudentPaymentReport();
                        columns = new String[]{"Student Code", "Name", "Program", "Receipt #", "Date", "Intel Fee", "T-Shirt", "Penalties", "CIT Night", "Received By", "Total"};
                        break;
                    case "Collection Summary":
                        data = generateCollectionSummary();
                        columns = new String[]{"Category", "Amount"};
                        break;
                    case "Remittance Report":
                        data = generateRemittanceReport();
                        columns = new String[]{"Remittance Date", "Receipt Count", "Total Amount"};
                        break;
                    case "Receiver Report":
                        data = generateReceiverReport();
                        columns = new String[]{"Received By", "Receipt Count", "Total Amount"};
                        break;
                    case "Import Batch Report":
                        data = generateImportBatchReport();
                        columns = new String[]{"Batch Code", "File", "Imported At", "Remittance Date", "Records", "New", "Duplicates", "Conflicts", "Errors", "Status"};
                        break;
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    reportTableModel.setData(data, columns);
                    statusLabel.setText(String.format("Report generated: %d rows", data.size()));
                } catch (Exception e) {
                    statusLabel.setText("Error generating report: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private List<Map<String, Object>> generateStudentPaymentReport() throws Exception {
        List<Payment> payments = db.getAllPayments();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return payments.stream().map(p -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("Student Code", p.getStudentId() != null ? p.getStudentId() : "-");
            row.put("Name", p.getName());
            row.put("Program", p.getProgram() != null ? p.getProgram() : "-");
            row.put("Receipt #", p.getReceiptNumber());
            row.put("Date", p.getRemittanceDate() != null ? p.getRemittanceDate().format(fmt) : "-");
            row.put("Intel Fee", p.getIntelFee() != null ? p.getIntelFee() : 0.0);
            row.put("T-Shirt", p.getTshirtSizing() != null ? p.getTshirtSizing() : 0.0);
            row.put("Penalties", p.getPenalties() != null ? p.getPenalties() : 0.0);
            row.put("CIT Night", p.getCitNight() != null ? p.getCitNight() : 0.0);
            row.put("Received By", p.getReceivedBy() != null ? p.getReceivedBy() : "-");
            row.put("Total", p.getTotalAmount());
            return row;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> generateCollectionSummary() throws Exception {
        List<Payment> payments = db.getAllPayments();

        double intelTotal = payments.stream()
            .filter(p -> "ACTIVE".equals(p.getStatus()))
            .mapToDouble(p -> p.getIntelFee() != null ? p.getIntelFee() : 0).sum();
        double tshirtTotal = payments.stream()
            .filter(p -> "ACTIVE".equals(p.getStatus()))
            .mapToDouble(p -> p.getTshirtSizing() != null ? p.getTshirtSizing() : 0).sum();
        double penaltiesTotal = payments.stream()
            .filter(p -> "ACTIVE".equals(p.getStatus()))
            .mapToDouble(p -> p.getPenalties() != null ? p.getPenalties() : 0).sum();
        double citTotal = payments.stream()
            .filter(p -> "ACTIVE".equals(p.getStatus()))
            .mapToDouble(p -> p.getCitNight() != null ? p.getCitNight() : 0).sum();
        double grandTotal = payments.stream()
            .filter(p -> "ACTIVE".equals(p.getStatus()))
            .mapToDouble(Payment::getTotalAmount).sum();

        Map<String, Object> row1 = new java.util.LinkedHashMap<>();
        row1.put("Category", "Intel Fee");
        row1.put("Amount", intelTotal);

        Map<String, Object> row2 = new java.util.LinkedHashMap<>();
        row2.put("Category", "T-Shirt Sizing");
        row2.put("Amount", tshirtTotal);

        Map<String, Object> row3 = new java.util.LinkedHashMap<>();
        row3.put("Category", "Penalties");
        row3.put("Amount", penaltiesTotal);

        Map<String, Object> row4 = new java.util.LinkedHashMap<>();
        row4.put("Category", "CIT Night");
        row4.put("Amount", citTotal);

        Map<String, Object> row5 = new java.util.LinkedHashMap<>();
        row5.put("Category", "TOTAL");
        row5.put("Amount", grandTotal);

        return List.of(row1, row2, row3, row4, row5);
    }

    private List<Map<String, Object>> generateRemittanceReport() throws Exception {
        List<Payment> payments = db.getAllPayments();

        Map<LocalDate, List<Payment>> byDate = payments.stream()
            .filter(p -> p.getRemittanceDate() != null && "ACTIVE".equals(p.getStatus()))
            .collect(Collectors.groupingBy(Payment::getRemittanceDate));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return byDate.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("Remittance Date", e.getKey().format(fmt));
                row.put("Receipt Count", e.getValue().size());
                double total = e.getValue().stream().mapToDouble(Payment::getTotalAmount).sum();
                row.put("Total Amount", total);
                return row;
            })
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> generateReceiverReport() throws Exception {
        List<Payment> payments = db.getAllPayments();

        Map<String, List<Payment>> byReceiver = payments.stream()
            .filter(p -> "ACTIVE".equals(p.getStatus()))
            .collect(Collectors.groupingBy(p -> p.getReceivedBy() != null ? p.getReceivedBy() : "Unknown"));

        return byReceiver.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("Received By", e.getKey());
                row.put("Receipt Count", e.getValue().size());
                double total = e.getValue().stream().mapToDouble(Payment::getTotalAmount).sum();
                row.put("Total Amount", total);
                return row;
            })
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> generateImportBatchReport() throws Exception {
        List<com.payment.ImportBatch> batches = db.getAllImportBatches();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        DateTimeFormatter remFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return batches.stream().map(b -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("Batch Code", b.getBatchCode());
            row.put("File", b.getFileName());
            row.put("Imported At", b.getImportedAt() != null ? b.getImportedAt().format(fmt) : "-");
            row.put("Remittance Date", b.getRemittanceDate() != null ? b.getRemittanceDate().format(remFmt) : "-");
            row.put("Records", b.getTotalRows());
            row.put("New", b.getNewRecords());
            row.put("Duplicates", b.getDuplicateRecords());
            row.put("Conflicts", b.getConflictRecords());
            row.put("Errors", b.getErrorRecords());
            row.put("Status", b.getStatus());
            return row;
        }).collect(Collectors.toList());
    }

    private void exportCSV() {
        if (reportTableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No data to export", "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV Files", "csv"));
        String reportType = (String) reportTypeCombo.getSelectedItem();
        String safeName = reportType != null ? reportType.replace(" ", "_") : "Report";
        fileChooser.setSelectedFile(new File(safeName + "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(fileChooser.getSelectedFile()))) {
                // Header
                for (int i = 0; i < reportTableModel.getColumnCount(); i++) {
                    if (i > 0) writer.print(",");
                    writer.print("\"" + reportTableModel.getColumnName(i) + "\"");
                }
                writer.println();

                // Data
                for (int row = 0; row < reportTableModel.getRowCount(); row++) {
                    for (int col = 0; col < reportTableModel.getColumnCount(); col++) {
                        if (col > 0) writer.print(",");
                        Object val = reportTableModel.getValueAt(row, col);
                        String str = val != null ? val.toString() : "";
                        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
                            str = "\"" + str.replace("\"", "\"\"") + "\"";
                        }
                        writer.print(str);
                    }
                    writer.println();
                }
                statusLabel.setText("Exported to " + fileChooser.getSelectedFile().getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportExcel() {
        if (reportTableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No data to export", "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel Files (*.xlsx)", "xlsx"));
        String reportType = (String) reportTypeCombo.getSelectedItem();
        String safeName = reportType != null ? reportType.replace(" ", "_") : "Report";
        fileChooser.setSelectedFile(new File(safeName + "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try (var fos = new java.io.FileOutputStream(fileChooser.getSelectedFile());
                 var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {

                var sheet = workbook.createSheet("Report");

                // Create header style
                var headerFont = workbook.createFont();
                headerFont.setBold(true);
                var headerStyle = workbook.createCellStyle();
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

                // Header row
                var headerRow = sheet.createRow(0);
                for (int i = 0; i < reportTableModel.getColumnCount(); i++) {
                    var cell = headerRow.createCell(i);
                    cell.setCellValue(reportTableModel.getColumnName(i));
                    cell.setCellStyle(headerStyle);
                }

                // Data rows
                for (int row = 0; row < reportTableModel.getRowCount(); row++) {
                    var dataRow = sheet.createRow(row + 1);
                    for (int col = 0; col < reportTableModel.getColumnCount(); col++) {
                        Object val = reportTableModel.getValueAt(row, col);
                        var cell = dataRow.createCell(col);
                        if (val instanceof Number) {
                            cell.setCellValue(((Number) val).doubleValue());
                        } else {
                            cell.setCellValue(val != null ? val.toString() : "");
                        }
                    }
                }

                // Auto-size columns
                for (int i = 0; i < reportTableModel.getColumnCount(); i++) {
                    sheet.autoSizeColumn(i);
                }

                workbook.write(fos);
                statusLabel.setText("Exported to " + fileChooser.getSelectedFile().getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }

    // --- Table Model ---

    private static class ReportTableModel extends AbstractTableModel {
        private List<Map<String, Object>> data = List.of();
        private String[] columns = new String[0];

        public void setData(List<Map<String, Object>> data, String[] columns) {
            this.data = data != null ? data : List.of();
            this.columns = columns != null ? columns : new String[0];
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return column < columns.length ? columns[column] : "Column " + column;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex >= data.size() || columnIndex >= columns.length) return null;
            Map<String, Object> row = data.get(rowIndex);
            return row.get(columns[columnIndex]);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (data.isEmpty()) return String.class;
            Object sample = data.get(0).get(columns[columnIndex]);
            if (sample instanceof Number) return Double.class;
            if (sample instanceof Integer) return Integer.class;
            return String.class;
        }
    }
}