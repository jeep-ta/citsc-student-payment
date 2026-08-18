package com.payment;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

public class StudentPaymentApp extends JFrame {
    private JTable studentTable;
    private DefaultTableModel studentTableModel;
    private JTable paymentTable;
    private DefaultTableModel paymentTableModel;
    private JLabel statusLabel;
    private List<Student> students;

    public StudentPaymentApp() {
        setTitle("Student Payment Database");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);

        initializeUI();
        loadData();
    }

    private void initializeUI() {
        // Main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top toolbar
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton importButton = new JButton("Import Excel");
        importButton.addActionListener(e -> importExcel());
        toolBar.add(importButton);

        toolBar.addSeparator();

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshData());
        toolBar.add(refreshButton);

        toolBar.addSeparator();

        JTextField searchField = new JTextField(20);
        searchField.setToolTipText("Search by student name...");
        searchField.addActionListener(e -> filterStudents(searchField.getText()));
        toolBar.add(new JLabel("Search: "));
        toolBar.add(searchField);

        mainPanel.add(toolBar, BorderLayout.NORTH);

        // Split pane for student list and payment details
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.35);
        splitPane.setOneTouchExpandable(true);
        splitPane.setContinuousLayout(true);

        // Left panel - Student List
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Students (Click to view payments)"));

        String[] studentColumns = {"#", "Student Name", "Program", "Payments", "Total Amount"};
        studentTableModel = new DefaultTableModel(studentColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0 || columnIndex == 3) return Integer.class;
                if (columnIndex == 4) return Double.class;
                return String.class;
            }
        };

        studentTable = new JTable(studentTableModel);
        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studentTable.setRowHeight(25);
        studentTable.getTableHeader().setReorderingAllowed(false);
        studentTable.getColumnModel().getColumn(0).setMaxWidth(50);
        studentTable.getColumnModel().getColumn(0).setMinWidth(50);
        studentTable.getColumnModel().getColumn(3).setMaxWidth(80);
        studentTable.getColumnModel().getColumn(3).setMinWidth(80);
        studentTable.getColumnModel().getColumn(4).setMaxWidth(120);
        studentTable.getColumnModel().getColumn(4).setMinWidth(100);

        // Add row sorter for student table
        TableRowSorter<DefaultTableModel> studentSorter = new TableRowSorter<>(studentTableModel);
        studentTable.setRowSorter(studentSorter);

        JScrollPane studentScrollPane = new JScrollPane(studentTable);
        leftPanel.add(studentScrollPane, BorderLayout.CENTER);

        // Student info panel
        JPanel studentInfoPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        studentInfoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        studentInfoPanel.setPreferredSize(new Dimension(380, 150));
        leftPanel.add(studentInfoPanel, BorderLayout.SOUTH);

        splitPane.setLeftComponent(leftPanel);

        // Right panel - Payment Details
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Payment Details"));
        rightPanel.setMinimumSize(new Dimension(500, 400));
        rightPanel.setPreferredSize(new Dimension(700, 500));

        String[] paymentColumns = {"Receipt #", "Program", "Intel Fee", "T-Shirt", "Penalties", "CIT Night", "Received By", "Remarks", "Total"};
        paymentTableModel = new DefaultTableModel(paymentColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Integer.class;
                if (columnIndex >= 2 && columnIndex <= 8) return Double.class;
                return String.class;
            }
        };

        paymentTable = new JTable(paymentTableModel);
        paymentTable.setRowHeight(25);
        paymentTable.getTableHeader().setReorderingAllowed(false);
        paymentTable.getColumnModel().getColumn(0).setMaxWidth(90);
        paymentTable.getColumnModel().getColumn(0).setMinWidth(90);
        paymentTable.getColumnModel().getColumn(2).setMaxWidth(90);
        paymentTable.getColumnModel().getColumn(2).setMinWidth(80);
        paymentTable.getColumnModel().getColumn(3).setMaxWidth(90);
        paymentTable.getColumnModel().getColumn(3).setMinWidth(80);
        paymentTable.getColumnModel().getColumn(4).setMaxWidth(90);
        paymentTable.getColumnModel().getColumn(4).setMinWidth(80);
        paymentTable.getColumnModel().getColumn(5).setMaxWidth(90);
        paymentTable.getColumnModel().getColumn(5).setMinWidth(80);
        paymentTable.getColumnModel().getColumn(8).setMaxWidth(100);
        paymentTable.getColumnModel().getColumn(8).setMinWidth(90);

        JScrollPane paymentScrollPane = new JScrollPane(paymentTable);
        paymentScrollPane.setMinimumSize(new Dimension(400, 300));
        paymentScrollPane.setPreferredSize(new Dimension(600, 400));
        rightPanel.add(paymentScrollPane, BorderLayout.CENTER);

        // Payment summary panel
        JPanel paymentSummaryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        paymentSummaryPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel totalLabel = new JLabel("Total: ₱0.00");
        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 14f));
        paymentSummaryPanel.add(totalLabel);

        rightPanel.add(paymentSummaryPanel, BorderLayout.SOUTH);

        splitPane.setRightComponent(rightPanel);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        // Add selection listener
        studentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = studentTable.getSelectedRow();
                if (selectedRow >= 0) {
                    int modelRow = studentTable.convertRowIndexToModel(selectedRow);
                    showStudentPayments(modelRow, studentInfoPanel, totalLabel);
                }
            }
        });

        // Double-click to show payment details
        studentTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = studentTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        int modelRow = studentTable.convertRowIndexToModel(row);
                        showStudentPayments(modelRow, studentInfoPanel, totalLabel);
                    }
                }
            }
        });

        add(mainPanel);
    }

    private void loadData() {
        // Try to load the default Excel file
        File defaultFile = new File("Payment Import Jul 28, 2026.xlsx");
        if (defaultFile.exists()) {
            try {
                students = ExcelImporter.importFromExcel(defaultFile.getAbsolutePath());
                populateStudentTable();
                statusLabel.setText("Loaded " + students.size() + " students from default file");
            } catch (IOException ex) {
                statusLabel.setText("Error loading default file: " + ex.getMessage());
                ex.printStackTrace();
            }
        } else {
            statusLabel.setText("Default Excel file not found. Use Import button to load data.");
        }
    }

    private void importExcel() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Excel File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel Files", "xlsx", "xls"));

        // Set default directory to project folder
        fileChooser.setCurrentDirectory(new File("."));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                students = ExcelImporter.importFromExcel(selectedFile.getAbsolutePath());
                populateStudentTable();
                statusLabel.setText("Loaded " + students.size() + " students from " + selectedFile.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error importing file: " + ex.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
                statusLabel.setText("Import failed: " + ex.getMessage());
            }
        }
    }

    private void refreshData() {
        // Re-import from default file
        File defaultFile = new File("Payment Import Jul 28, 2026.xlsx");
        if (defaultFile.exists()) {
            try {
                students = ExcelImporter.importFromExcel(defaultFile.getAbsolutePath());
                populateStudentTable();
                statusLabel.setText("Refreshed: " + students.size() + " students loaded");
            } catch (IOException ex) {
                statusLabel.setText("Refresh failed: " + ex.getMessage());
            }
        } else {
            statusLabel.setText("Default file not found for refresh");
        }
    }

    private void populateStudentTable() {
        studentTableModel.setRowCount(0);

        if (students == null) return;

        int index = 1;
        for (Student student : students) {
            studentTableModel.addRow(new Object[]{
                index++,
                student.getName(),
                getPrimaryProgram(student),
                student.getPaymentCount(),
                student.getTotalAmount()
            });
        }
    }

    private String getPrimaryProgram(Student student) {
        if (student.getPayments().isEmpty()) return "";
        // Get the most common program
        Map<String, Integer> programCount = new HashMap<>();
        for (Payment p : student.getPayments()) {
            programCount.put(p.getProgram(), programCount.getOrDefault(p.getProgram(), 0) + 1);
        }
        return programCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");
    }

    private void showStudentPayments(int modelRow, JPanel studentInfoPanel, JLabel totalLabel) {
        if (students == null || modelRow >= students.size()) return;

        Student student = students.get(modelRow);
        paymentTableModel.setRowCount(0);
        studentInfoPanel.removeAll();

        // Student info
        JLabel nameLabel = new JLabel("Student: " + student.getName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        studentInfoPanel.add(nameLabel);

        JLabel programLabel = new JLabel("Program: " + getPrimaryProgram(student));
        studentInfoPanel.add(programLabel);

        JLabel countLabel = new JLabel("Total Payments: " + student.getPaymentCount());
        studentInfoPanel.add(countLabel);

        JLabel totalAmountLabel = new JLabel("Total Amount: ₱" + String.format("%,.2f", student.getTotalAmount()));
        totalAmountLabel.setFont(totalAmountLabel.getFont().deriveFont(Font.BOLD, 14f));
        totalAmountLabel.setForeground(new Color(0, 100, 0));
        studentInfoPanel.add(totalAmountLabel);

        studentInfoPanel.revalidate();
        studentInfoPanel.repaint();

        // Populate payment table
        for (Payment payment : student.getPayments()) {
            paymentTableModel.addRow(new Object[]{
                payment.getReceiptNumber(),
                payment.getProgram(),
                payment.getIntelFee() != null ? payment.getIntelFee() : "-",
                payment.getTshirtSizing() != null ? payment.getTshirtSizing() : "-",
                payment.getPenalties() != null ? payment.getPenalties() : "-",
                payment.getCitNight() != null ? payment.getCitNight() : "-",
                payment.getReceivedBy(),
                payment.getRemarks(),
                payment.getTotalAmount()
            });
        }

        totalLabel.setText("Total: ₱" + String.format("%,.2f", student.getTotalAmount()));
    }

    private void filterStudents(String searchText) {
        TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) studentTable.getRowSorter();
        if (sorter != null) {
            if (searchText.trim().isEmpty()) {
                sorter.setRowFilter(null);
            } else {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText, 1)); // Filter on column 1 (Name)
            }
        }
    }

    public static void main(String[] args) {
        // Set system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Run on EDT
        SwingUtilities.invokeLater(() -> {
            StudentPaymentApp app = new StudentPaymentApp();
            app.setVisible(true);
        });
    }
}