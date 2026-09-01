package com.payment.ui;

import com.payment.Payment;
import com.payment.Student;
import com.payment.StudentMergeRecord;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for merging student records (fixing typos / duplicates)
 * and managing merge history with complete Undo support.
 */
public class MergeStudentsDialog extends JDialog {

    private final DatabaseManager db;
    private final Runnable onDataChanged;

    private List<Student> allStudents = new ArrayList<>();

    // Tab 1: Merge Execution
    private JComboBox<StudentComboItem> sourceCombo;
    private JComboBox<StudentComboItem> targetCombo;
    private JLabel sourceDetailsLabel;
    private JLabel targetDetailsLabel;
    private JLabel impactSummaryLabel;
    private JTextField reasonField;
    private JButton executeMergeButton;

    // Tab 2: Merge History & Undo
    private JTable historyTable;
    private MergeHistoryTableModel historyTableModel;
    private JButton undoButton;

    public MergeStudentsDialog(Window parent, String preselectedSourceCode, String preselectedTargetCode, Runnable onDataChanged) {
        super(parent, "Student Record Merge & History", ModalityType.APPLICATION_MODAL);
        this.db = DatabaseManager.getInstance();
        this.onDataChanged = onDataChanged;

        initializeUI();
        loadStudents(preselectedSourceCode, preselectedTargetCode);
        loadHistory();

        setSize(850, 620);
        setLocationRelativeTo(parent);
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(ThemeUtils.BG_DEEPEST);

        // Header Banner
        JPanel titleBanner = ThemeUtils.createGradientPanel(new Color(15, 23, 42), new Color(10, 14, 26));
        titleBanner.setLayout(new BorderLayout());
        titleBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, ThemeUtils.NEON_AMBER),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLabel = new JLabel("🔀 Student Data Consolidation & Merge Manager");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setForeground(ThemeUtils.NEON_AMBER);
        titleBanner.add(titleLabel, BorderLayout.WEST);

        JLabel subtitleLabel = new JLabel("Resolve typos & duplicates with 100% reversible undo");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 11f));
        subtitleLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        titleBanner.add(subtitleLabel, BorderLayout.EAST);

        add(titleBanner, BorderLayout.NORTH);

        // Tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(ThemeUtils.BG_SURFACE);
        tabbedPane.setForeground(ThemeUtils.TEXT_PRIMARY);

        tabbedPane.addTab("  🔀 Merge Students  ", createMergeTab());
        tabbedPane.addTab("  ↩️ Merge History & Undo  ", createHistoryTab());

        add(tabbedPane, BorderLayout.CENTER);

        // Bottom close bar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bottomBar.setBackground(ThemeUtils.BG_CARD);
        bottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeUtils.BORDER_COLOR));

        JButton closeButton = new JButton("Close");
        ThemeUtils.styleButton(closeButton, ThemeUtils.TEXT_SECONDARY);
        closeButton.addActionListener(e -> dispose());
        bottomBar.add(closeButton);

        add(bottomBar, BorderLayout.SOUTH);
    }

    private JPanel createMergeTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(ThemeUtils.BG_DEEPEST);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        // Selector row (Source on Left -> Target on Right)
        JPanel selectorRow = new JPanel(new GridLayout(1, 2, 15, 0));
        selectorRow.setOpaque(false);

        // Left: Source Student
        JPanel sourceCard = ThemeUtils.createFuturisticCard(ThemeUtils.NEON_ROSE);
        sourceCard.setLayout(new BoxLayout(sourceCard, BoxLayout.Y_AXIS));
        sourceCard.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JLabel srcTitle = new JLabel("1. Source Student (Typo / Duplicate)");
        srcTitle.setFont(srcTitle.getFont().deriveFont(Font.BOLD, 13f));
        srcTitle.setForeground(ThemeUtils.NEON_ROSE);
        sourceCard.add(srcTitle);
        sourceCard.add(Box.createVerticalStrut(4));

        JLabel srcHint = new JLabel("<html><small style='color:#94A3B8;'>This student's payments will be transferred and the record archived.</small></html>");
        sourceCard.add(srcHint);
        sourceCard.add(Box.createVerticalStrut(8));

        sourceCombo = new JComboBox<>();
        sourceCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        sourceCombo.addActionListener(e -> updateMergePreview());
        sourceCard.add(sourceCombo);
        sourceCard.add(Box.createVerticalStrut(10));

        sourceDetailsLabel = new JLabel("<html><i>Select a source student</i></html>");
        sourceDetailsLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        sourceCard.add(sourceDetailsLabel);

        selectorRow.add(sourceCard);

        // Right: Target Student
        JPanel targetCard = ThemeUtils.createFuturisticCard(ThemeUtils.NEON_GREEN);
        targetCard.setLayout(new BoxLayout(targetCard, BoxLayout.Y_AXIS));
        targetCard.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JLabel tgtTitle = new JLabel("2. Target Student (Primary / Correct Record)");
        tgtTitle.setFont(tgtTitle.getFont().deriveFont(Font.BOLD, 13f));
        tgtTitle.setForeground(ThemeUtils.NEON_GREEN);
        targetCard.add(tgtTitle);
        targetCard.add(Box.createVerticalStrut(4));

        JLabel tgtHint = new JLabel("<html><small style='color:#94A3B8;'>This primary student will receive all transferred payments.</small></html>");
        targetCard.add(tgtHint);
        targetCard.add(Box.createVerticalStrut(8));

        targetCombo = new JComboBox<>();
        targetCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        targetCombo.addActionListener(e -> updateMergePreview());
        targetCard.add(targetCombo);
        targetCard.add(Box.createVerticalStrut(10));

        targetDetailsLabel = new JLabel("<html><i>Select a target student</i></html>");
        targetDetailsLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        targetCard.add(targetDetailsLabel);

        selectorRow.add(targetCard);

        panel.add(selectorRow, BorderLayout.NORTH);

        // Center: Impact preview & Reason
        JPanel middlePanel = new JPanel();
        middlePanel.setLayout(new BoxLayout(middlePanel, BoxLayout.Y_AXIS));
        middlePanel.setOpaque(false);
        middlePanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // Impact card
        JPanel impactCard = ThemeUtils.createFuturisticCard(ThemeUtils.NEON_CYAN);
        impactCard.setLayout(new BorderLayout());
        impactCard.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        impactCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));

        impactSummaryLabel = new JLabel("Select distinct source and target students above to preview consolidation.");
        impactSummaryLabel.setFont(impactSummaryLabel.getFont().deriveFont(Font.PLAIN, 13f));
        impactSummaryLabel.setForeground(ThemeUtils.TEXT_PRIMARY);
        impactCard.add(impactSummaryLabel, BorderLayout.CENTER);
        middlePanel.add(impactCard);
        middlePanel.add(Box.createVerticalStrut(12));

        // Reason container
        JPanel reasonContainer = new JPanel(new BorderLayout(8, 0));
        reasonContainer.setOpaque(false);
        reasonContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

        JLabel reasonLbl = new JLabel("Reason / Notes:");
        reasonLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        reasonContainer.add(reasonLbl, BorderLayout.WEST);

        reasonField = new JTextField("Name typo consolidation");
        reasonContainer.add(reasonField, BorderLayout.CENTER);
        middlePanel.add(reasonContainer);

        panel.add(middlePanel, BorderLayout.CENTER);

        // Action button
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        actionPanel.setOpaque(false);

        executeMergeButton = new JButton("⚡ Execute Student Merge");
        executeMergeButton.setFont(executeMergeButton.getFont().deriveFont(Font.BOLD, 13f));
        ThemeUtils.styleButton(executeMergeButton, ThemeUtils.NEON_AMBER);
        executeMergeButton.setEnabled(false);
        executeMergeButton.addActionListener(e -> executeMerge());
        actionPanel.add(executeMergeButton);

        panel.add(actionPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(ThemeUtils.BG_DEEPEST);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // History Table
        historyTableModel = new MergeHistoryTableModel();
        historyTable = new JTable(historyTableModel);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ThemeUtils.applyTableTheme(historyTable);
        historyTable.setAutoCreateRowSorter(true);

        historyTable.getColumnModel().getColumn(0).setPreferredWidth(120); // Date
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(100); // Code
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(160); // Source
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(160); // Target
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(70);  // Rcpts
        historyTable.getColumnModel().getColumn(5).setPreferredWidth(150); // Reason
        historyTable.getColumnModel().getColumn(6).setPreferredWidth(90);  // Status

        historyTable.getColumnModel().getColumn(6).setCellRenderer(new MergeStatusCellRenderer());

        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(ThemeUtils.BG_SURFACE);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Bottom toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        toolbar.setBackground(ThemeUtils.BG_CARD);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        undoButton = new JButton("↩️ Undo Selected Merge");
        ThemeUtils.styleButton(undoButton, ThemeUtils.NEON_CYAN);
        undoButton.addActionListener(e -> undoSelectedMerge());
        toolbar.add(undoButton);

        JButton refreshHistButton = new JButton("🔄 Refresh History");
        ThemeUtils.styleButton(refreshHistButton, ThemeUtils.TEXT_SECONDARY);
        refreshHistButton.addActionListener(e -> loadHistory());
        toolbar.add(refreshHistButton);

        panel.add(toolbar, BorderLayout.SOUTH);

        return panel;
    }

    private void loadStudents(String preselectedSource, String preselectedTarget) {
        SwingWorker<List<Student>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Student> doInBackground() throws Exception {
                return db.getAllStudents();
            }

            @Override
            protected void done() {
                try {
                    allStudents = get();
                    sourceCombo.removeAllItems();
                    targetCombo.removeAllItems();

                    for (Student s : allStudents) {
                        StudentComboItem item = new StudentComboItem(s);
                        sourceCombo.addItem(item);
                        targetCombo.addItem(item);
                    }

                    // Pre-select if given
                    if (preselectedSource != null) {
                        for (int i = 0; i < sourceCombo.getItemCount(); i++) {
                            if (preselectedSource.equalsIgnoreCase(sourceCombo.getItemAt(i).student.getStudentCode())) {
                                sourceCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }

                    if (preselectedTarget != null) {
                        for (int i = 0; i < targetCombo.getItemCount(); i++) {
                            if (preselectedTarget.equalsIgnoreCase(targetCombo.getItemAt(i).student.getStudentCode())) {
                                targetCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }

                    updateMergePreview();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void updateMergePreview() {
        StudentComboItem srcItem = (StudentComboItem) sourceCombo.getSelectedItem();
        StudentComboItem tgtItem = (StudentComboItem) targetCombo.getSelectedItem();

        if (srcItem == null || tgtItem == null) {
            executeMergeButton.setEnabled(false);
            return;
        }

        Student src = srcItem.student;
        Student tgt = tgtItem.student;

        // Update details text
        sourceDetailsLabel.setText(String.format("<html><b>%s</b> (%s)<br>Program: %s | Payments: %d (₱%,.2f)</html>",
            src.getName(), src.getStudentCode(),
            src.getProgram() != null ? src.getProgram() : "-",
            src.getPaymentCount(), src.getTotalAmount()));

        targetDetailsLabel.setText(String.format("<html><b>%s</b> (%s)<br>Program: %s | Payments: %d (₱%,.2f)</html>",
            tgt.getName(), tgt.getStudentCode(),
            tgt.getProgram() != null ? tgt.getProgram() : "-",
            tgt.getPaymentCount(), tgt.getTotalAmount()));

        if (src.getStudentCode().equalsIgnoreCase(tgt.getStudentCode())) {
            impactSummaryLabel.setText("⚠️ Source and target cannot be the same student.");
            impactSummaryLabel.setForeground(ThemeUtils.NEON_ROSE);
            executeMergeButton.setEnabled(false);
            return;
        }

        int combinedPayments = src.getPaymentCount() + tgt.getPaymentCount();
        double combinedTotal = src.getTotalAmount() + tgt.getTotalAmount();

        impactSummaryLabel.setText(String.format("<html><b>Merge Impact:</b> Move <b>%d payments</b> (₱%,.2f) from %s into %s.<br>" +
                "New Target Record: <b>%d total payments</b> totaling <b>₱%,.2f</b>. Source student will be safely archived.</html>",
            src.getPaymentCount(), src.getTotalAmount(), src.getName(), tgt.getName(), combinedPayments, combinedTotal));
        impactSummaryLabel.setForeground(ThemeUtils.TEXT_PRIMARY);

        executeMergeButton.setEnabled(true);
    }

    private void executeMerge() {
        StudentComboItem srcItem = (StudentComboItem) sourceCombo.getSelectedItem();
        StudentComboItem tgtItem = (StudentComboItem) targetCombo.getSelectedItem();
        if (srcItem == null || tgtItem == null) return;

        Student src = srcItem.student;
        Student tgt = tgtItem.student;
        String reason = reasonField.getText().trim();

        int confirm = JOptionPane.showConfirmDialog(this,
            String.format("Are you sure you want to merge:\n\n" +
                "Source (Typo/Duplicate): %s (%s) — %d payments\n" +
                "INTO Target: %s (%s)\n\n" +
                "All %d payments will be reassigned to %s.\n" +
                "You can undo this at any time in the 'Merge History' tab.",
                src.getName(), src.getStudentCode(), src.getPaymentCount(),
                tgt.getName(), tgt.getStudentCode(),
                src.getPaymentCount(), tgt.getName()),
            "Confirm Student Merge", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            StudentMergeRecord record = db.mergeStudents(src.getStudentCode(), tgt.getStudentCode(), reason, "user");
            JOptionPane.showMessageDialog(this,
                String.format("Merge successful!\n\nMerge Code: %s\nTransferred Payments: %d",
                    record.getMergeCode(), record.getReceiptNumbersList().size()),
                "Merge Completed", JOptionPane.INFORMATION_MESSAGE);

            if (onDataChanged != null) {
                onDataChanged.run();
            }

            // Reload data
            loadStudents(null, null);
            loadHistory();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error executing merge: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void loadHistory() {
        SwingWorker<List<StudentMergeRecord>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<StudentMergeRecord> doInBackground() throws Exception {
                return db.getAllStudentMerges();
            }

            @Override
            protected void done() {
                try {
                    List<StudentMergeRecord> list = get();
                    historyTableModel.setMerges(list);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void undoSelectedMerge() {
        int selectedRow = historyTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select an active merge from the table to undo.", "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = historyTable.convertRowIndexToModel(selectedRow);
        StudentMergeRecord record = historyTableModel.getMerge(modelRow);
        if (record == null) return;

        if (record.isReverted()) {
            JOptionPane.showMessageDialog(this, "This merge has already been undone.", "Already Reverted", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            String.format("Undo merge '%s'?\n\n" +
                "• Restores student: %s (%s)\n" +
                "• Reverts %d payments back from %s (%s)",
                record.getMergeCode(),
                record.getSourceName(), record.getSourceStudentCode(),
                record.getReceiptNumbersList().size(),
                record.getTargetName(), record.getTargetStudentCode()),
            "Confirm Undo Merge", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            boolean success = db.undoStudentMerge(record.getMergeCode(), "user");
            if (success) {
                JOptionPane.showMessageDialog(this,
                    String.format("Student %s (%s) and all payments successfully restored!",
                        record.getSourceName(), record.getSourceStudentCode()),
                    "Merge Undone", JOptionPane.INFORMATION_MESSAGE);

                if (onDataChanged != null) {
                    onDataChanged.run();
                }

                loadStudents(null, null);
                loadHistory();
            } else {
                JOptionPane.showMessageDialog(this, "Could not undo merge. It may already be reverted.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error undoing merge: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    // --- Helper Classes ---

    private static class StudentComboItem {
        final Student student;

        StudentComboItem(Student student) {
            this.student = student;
        }

        @Override
        public String toString() {
            return String.format("%s - %s (%s)", student.getStudentCode(), student.getName(),
                student.getProgram() != null ? student.getProgram() : "-");
        }
    }

    private static class MergeHistoryTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
            "Date", "Merge Code", "Source Student (Typo)", "Target Student", "Receipts", "Reason", "Status"
        };
        private List<StudentMergeRecord> merges = new ArrayList<>();
        private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        public void setMerges(List<StudentMergeRecord> merges) {
            this.merges = merges != null ? merges : new ArrayList<>();
            fireTableDataChanged();
        }

        public StudentMergeRecord getMerge(int row) {
            if (row >= 0 && row < merges.size()) return merges.get(row);
            return null;
        }

        @Override public int getRowCount() { return merges.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            StudentMergeRecord m = merges.get(row);
            switch (col) {
                case 0: return m.getMergedAt() != null ? m.getMergedAt().format(fmt) : "-";
                case 1: return m.getMergeCode();
                case 2: return m.getSourceName() + " (" + m.getSourceStudentCode() + ")";
                case 3: return m.getTargetName() + " (" + m.getTargetStudentCode() + ")";
                case 4: return m.getReceiptNumbersList().size();
                case 5: return m.getReason() != null ? m.getReason() : "-";
                case 6: return m.getStatus();
                default: return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 4) return Integer.class;
            return String.class;
        }
    }

    private static class MergeStatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value != null) {
                String status = value.toString();
                if ("ACTIVE".equalsIgnoreCase(status)) {
                    if (!isSelected) c.setForeground(ThemeUtils.NEON_GREEN);
                    setText("MERGED");
                } else if ("REVERTED".equalsIgnoreCase(status)) {
                    if (!isSelected) c.setForeground(ThemeUtils.TEXT_MUTED);
                    setText("UNDONE");
                }
                setHorizontalAlignment(CENTER);
                setFont(getFont().deriveFont(Font.BOLD, 11f));
            }
            return c;
        }
    }
}
