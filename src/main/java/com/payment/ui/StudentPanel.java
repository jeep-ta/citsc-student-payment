package com.payment.ui;

import com.payment.Student;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;

/**
 * Students Panel - Manage student records.
 */
public class StudentPanel extends JPanel {

    private final DatabaseManager db;

    private JTable studentTable;
    private StudentTableModel studentTableModel;
    private JTextField searchField;
    private JComboBox<String> programFilter;
    private JLabel statusLabel;

    // Detail panel
    private JPanel detailPanel;
    private JLabel detailStudentCode;
    private JLabel detailName;
    private JLabel detailProgram;
    private JLabel detailYearLevel;
    private JLabel detailPaymentCount;
    private JLabel detailTotalAmount;

    public StudentPanel() {
        this.db = DatabaseManager.getInstance();
        initializeUI();
        refreshData();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(Color.WHITE);

        // Title
        JLabel titleLabel = new JLabel("Students");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        add(titleLabel, BorderLayout.NORTH);

        // Main split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(650);
        splitPane.setResizeWeight(0.7);
        splitPane.setOneTouchExpandable(true);
        splitPane.setBorder(null);

        // Left side - Student list
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBackground(Color.WHITE);

        // Toolbar
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        toolBar.setBackground(Color.WHITE);
        toolBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        toolBar.add(new JLabel("Search:"));
        searchField = new JTextField(20);
        searchField.setToolTipText("Search by student name...");
        searchField.addActionListener(e -> filterStudents());
        toolBar.add(searchField);

        toolBar.add(new JLabel("Program:"));
        programFilter = new JComboBox<>(new String[]{"All Programs"});
        programFilter.addActionListener(e -> filterStudents());
        toolBar.add(programFilter);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshData());
        toolBar.add(refreshButton);

        leftPanel.add(toolBar, BorderLayout.NORTH);

        // Student table
        studentTableModel = new StudentTableModel();
        studentTable = new JTable(studentTableModel);
        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studentTable.setRowHeight(28);
        studentTable.setShowGrid(false);
        studentTable.setIntercellSpacing(new Dimension(0, 1));
        studentTable.getTableHeader().setReorderingAllowed(false);
        studentTable.getTableHeader().setBackground(new Color(245, 245, 245));
        studentTable.getTableHeader().setFont(studentTable.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        studentTable.setFont(studentTable.getFont().deriveFont(Font.PLAIN, 12f));

        // Column widths
        studentTable.getColumnModel().getColumn(0).setPreferredWidth(50);  // #
        studentTable.getColumnModel().getColumn(1).setPreferredWidth(130); // Student Code
        studentTable.getColumnModel().getColumn(2).setPreferredWidth(200); // Name
        studentTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Program
        studentTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Payments
        studentTable.getColumnModel().getColumn(5).setPreferredWidth(120); // Total

        // Row sorter
        TableRowSorter<StudentTableModel> sorter = new TableRowSorter<>(studentTableModel);
        studentTable.setRowSorter(sorter);

        JScrollPane scrollPane = new JScrollPane(studentTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220), 1));
        scrollPane.getViewport().setBackground(Color.WHITE);
        leftPanel.add(scrollPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Loading...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        leftPanel.add(statusLabel, BorderLayout.SOUTH);

        splitPane.setLeftComponent(leftPanel);

        // Right side - Student detail
        detailPanel = createDetailPanel();
        splitPane.setRightComponent(detailPanel);

        add(splitPane, BorderLayout.CENTER);

        // Selection listener
        studentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = studentTable.getSelectedRow();
                if (selectedRow >= 0) {
                    int modelRow = studentTable.convertRowIndexToModel(selectedRow);
                    showStudentDetails(modelRow);
                } else {
                    clearDetails();
                }
            }
        });
    }

    private JPanel createDetailPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        panel.setPreferredSize(new Dimension(350, 0));

        JLabel titleLabel = new JLabel("Student Details");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(20));

        // Student code
        detailStudentCode = createDetailField("Student Record No.", "-");
        panel.add(detailStudentCode);
        panel.add(Box.createVerticalStrut(10));

        // Name
        detailName = createDetailField("Name", "-");
        panel.add(detailName);
        panel.add(Box.createVerticalStrut(10));

        // Program
        detailProgram = createDetailField("Program", "-");
        panel.add(detailProgram);
        panel.add(Box.createVerticalStrut(10));

        // Year Level
        detailYearLevel = createDetailField("Year Level", "-");
        panel.add(detailYearLevel);
        panel.add(Box.createVerticalStrut(10));

        // Separator
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        panel.add(sep);
        panel.add(Box.createVerticalStrut(15));

        // Summary
        JLabel summaryLabel = new JLabel("Payment Summary");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD, 14f));
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(summaryLabel);
        panel.add(Box.createVerticalStrut(10));

        detailPaymentCount = createDetailField("Total Payments", "0");
        panel.add(detailPaymentCount);
        panel.add(Box.createVerticalStrut(10));

        detailTotalAmount = createDetailField("Total Paid", "₱0.00");
        detailTotalAmount.setForeground(new Color(0, 100, 0));
        panel.add(detailTotalAmount);

        panel.add(Box.createVerticalGlue());

        // View payments button
        JButton viewPaymentsButton = new JButton("View Payment History");
        viewPaymentsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        viewPaymentsButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        viewPaymentsButton.addActionListener(e -> {
            // TODO: Navigate to payments tab with this student filtered
            JOptionPane.showMessageDialog(this, "Navigate to Payments tab to view full history", "Info", JOptionPane.INFORMATION_MESSAGE);
        });
        panel.add(viewPaymentsButton);

        return panel;
    }

    private JLabel createDetailField(String label, String value) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(Color.WHITE);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelComp.getFont().deriveFont(Font.PLAIN, 11f));
        labelComp.setForeground(new Color(120, 120, 120));
        labelComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(valueComp.getFont().deriveFont(Font.PLAIN, 14f));
        valueComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        container.add(labelComp);
        container.add(valueComp);

        return valueComp; // Return value label for updates
    }

    private void showStudentDetails(int modelRow) {
        Student student = studentTableModel.getStudent(modelRow);

        detailStudentCode.setText(student.getStudentCode());
        detailName.setText(student.getName());
        detailProgram.setText(student.getProgram() != null ? student.getProgram() : "-");
        detailYearLevel.setText(student.getYearLevel() != null ? String.valueOf(student.getYearLevel()) : "-");
        detailPaymentCount.setText(String.valueOf(student.getPaymentCount()));
        detailTotalAmount.setText(String.format("₱%,.2f", student.getTotalAmount()));
    }

    private void clearDetails() {
        detailStudentCode.setText("-");
        detailName.setText("-");
        detailProgram.setText("-");
        detailYearLevel.setText("-");
        detailPaymentCount.setText("0");
        detailTotalAmount.setText("₱0.00");
    }

    public void refreshData() {
        SwingWorker<List<Student>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Student> doInBackground() throws Exception {
                return db.getAllStudents();
            }

            @Override
            protected void done() {
                try {
                    List<Student> students = get();
                    studentTableModel.setStudents(students);

                    // Update program filter
                    java.util.Set<String> programs = new java.util.TreeSet<>();
                    for (Student s : students) {
                        if (s.getProgram() != null && !s.getProgram().isEmpty()) {
                            programs.add(s.getProgram());
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

                    statusLabel.setText(String.format("Showing %d students", students.size()));
                } catch (Exception e) {
                    statusLabel.setText("Error loading students: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void filterStudents() {
        TableRowSorter<StudentTableModel> sorter = (TableRowSorter<StudentTableModel>) studentTable.getRowSorter();
        if (sorter == null) return;

        List<RowFilter<Object, Object>> filters = new java.util.ArrayList<>();

        // Name filter
        String searchText = searchField.getText().trim();
        if (!searchText.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + searchText, 2)); // Name column
        }

        // Program filter
        String program = (String) programFilter.getSelectedItem();
        if (program != null && !"All Programs".equals(program)) {
            filters.add(RowFilter.regexFilter("^" + java.util.regex.Pattern.quote(program) + "$", 3)); // Program column
        }

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else if (filters.size() == 1) {
            sorter.setRowFilter(filters.get(0));
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }

        statusLabel.setText(String.format("Showing %d students (filtered)", studentTable.getRowCount()));
    }

    // --- Table Model ---

    private static class StudentTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"#", "Student Code", "Name", "Program", "Payments", "Total Paid"};
        private List<Student> students = List.of();

        public void setStudents(List<Student> students) {
            this.students = students;
            fireTableDataChanged();
        }

        public Student getStudent(int rowIndex) {
            return students.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            return students.size();
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
            Student s = students.get(rowIndex);
            switch (columnIndex) {
                case 0: return rowIndex + 1;
                case 1: return s.getStudentCode();
                case 2: return s.getName();
                case 3: return s.getProgram() != null ? s.getProgram() : "-";
                case 4: return s.getPaymentCount();
                case 5: return String.format("₱%,.2f", s.getTotalAmount());
                default: return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0 || columnIndex == 4) return Integer.class;
            return String.class;
        }
    }
}