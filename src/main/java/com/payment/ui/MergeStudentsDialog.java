package com.payment.ui;

import com.payment.Payment;
import com.payment.SimilarStudentCandidate;
import com.payment.Student;
import com.payment.StudentMergeRecord;
import com.payment.StudentSimilarityService;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dialog for detecting similar student names, executing student merges,
 * and managing merge history with complete Undo support.
 */
public class MergeStudentsDialog extends JDialog {

    private final DatabaseManager db;
    private final StudentSimilarityService similarityService;
    private final Runnable onDataChanged;

    private List<Student> allStudents = new ArrayList<>();
    private JTabbedPane tabbedPane;

    // Tab 1: Similar Students Detector
    private JTable candidatesTable;
    private CandidateTableModel candidateTableModel;
    private List<SimilarStudentCandidate> currentCandidates = new ArrayList<>();
    private JComboBox<ThresholdOption> thresholdCombo;
    private JCheckBox sameProgramCheckBox;
    private JCheckBox showDismissedCheckBox;
    private JTextField searchFilterField;
    private JLabel candidateCountLabel;
    private JButton scanButton;
    private JPanel reviewContainer;
    private SimilarStudentCandidate selectedCandidate;
    private JRadioButton keepTargetRadio;
    private JRadioButton keepSourceRadio;
    private JTextField candidateMergeReasonField;
    private JButton executeCandidateMergeButton;
    private JButton dismissCandidateButton;

    // Tab 2: Manual Merge Execution
    private JComboBox<StudentComboItem> sourceCombo;
    private JComboBox<StudentComboItem> targetCombo;
    private JLabel sourceDetailsLabel;
    private JLabel targetDetailsLabel;
    private JLabel impactSummaryLabel;
    private JTextField reasonField;
    private JButton executeMergeButton;

    // Tab 3: Merge History & Undo
    private JTable historyTable;
    private MergeHistoryTableModel historyTableModel;
    private JButton undoButton;

    public MergeStudentsDialog(Window parent, String preselectedSourceCode, String preselectedTargetCode, Runnable onDataChanged) {
        this(parent, preselectedSourceCode, preselectedTargetCode, preselectedSourceCode != null ? 1 : 0, onDataChanged);
    }

    public MergeStudentsDialog(Window parent, int initialTab, Runnable onDataChanged) {
        this(parent, null, null, initialTab, onDataChanged);
    }

    public MergeStudentsDialog(Window parent, String preselectedSourceCode, String preselectedTargetCode, int initialTab, Runnable onDataChanged) {
        super(parent, "Student Record Consolidation & Similar Name Detector", ModalityType.APPLICATION_MODAL);
        this.db = DatabaseManager.getInstance();
        this.similarityService = new StudentSimilarityService(this.db);
        this.onDataChanged = onDataChanged;

        initializeUI();
        loadStudents(preselectedSourceCode, preselectedTargetCode, () -> {
            if (initialTab == 0) {
                scanForSimilarNames();
            }
        });
        loadHistory();

        if (initialTab >= 0 && initialTab < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(initialTab);
        }

        setSize(1100, 720);
        setMinimumSize(new Dimension(950, 620));
        setLocationRelativeTo(parent);
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(ThemeUtils.BG_DEEPEST);

        // Header Banner
        JPanel titleBanner = ThemeUtils.createGradientPanel(new Color(15, 23, 42), new Color(10, 14, 26));
        titleBanner.setLayout(new BorderLayout());
        titleBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, ThemeUtils.NEON_CYAN),
            BorderFactory.createEmptyBorder(12, 18, 12, 18)
        ));

        JLabel titleLabel = new JLabel("⚡ Student Name Similarity & Merge Center");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 19f));
        titleLabel.setForeground(ThemeUtils.NEON_CYAN);
        titleBanner.add(titleLabel, BorderLayout.WEST);

        JLabel subtitleLabel = new JLabel("Intelligent typo detection • Directional consolidation • 100% reversible undo");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        subtitleLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        titleBanner.add(subtitleLabel, BorderLayout.EAST);

        add(titleBanner, BorderLayout.NORTH);

        // Tabs
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(ThemeUtils.BG_SURFACE);
        tabbedPane.setForeground(ThemeUtils.TEXT_PRIMARY);

        tabbedPane.addTab("  ✨ Detect Similar Names  ", createSimilarityTab());
        tabbedPane.addTab("  🔀 Manual Merge  ", createMergeTab());
        tabbedPane.addTab("  ↩️ Merge History & Undo  ", createHistoryTab());

        add(tabbedPane, BorderLayout.CENTER);

        // Bottom close bar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        bottomBar.setBackground(ThemeUtils.BG_CARD);
        bottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeUtils.BORDER_COLOR));

        JButton closeButton = new JButton("Close");
        ThemeUtils.styleButton(closeButton, ThemeUtils.TEXT_SECONDARY);
        closeButton.addActionListener(e -> dispose());
        bottomBar.add(closeButton);

        add(bottomBar, BorderLayout.SOUTH);
    }

    // =========================================================================
    // TAB 1: SIMILAR NAMES DETECTOR
    // =========================================================================

    private JPanel createSimilarityTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(ThemeUtils.BG_DEEPEST);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // Top Filter & Action Bar
        JPanel topBar = new JPanel(new BorderLayout(10, 6));
        topBar.setBackground(ThemeUtils.BG_CARD);
        topBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        // Controls (Left)
        JPanel controlsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        controlsLeft.setOpaque(false);

        scanButton = new JButton("⚡ Scan for Similar Names");
        ThemeUtils.styleButton(scanButton, ThemeUtils.NEON_CYAN);
        scanButton.setFont(scanButton.getFont().deriveFont(Font.BOLD, 12f));
        scanButton.addActionListener(e -> scanForSimilarNames());
        controlsLeft.add(scanButton);

        JLabel thresholdLbl = new JLabel("Sensitivity:");
        thresholdLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        controlsLeft.add(thresholdLbl);

        thresholdCombo = new JComboBox<>(new ThresholdOption[] {
            new ThresholdOption("Strict / High (≥ 88%)", 0.88),
            new ThresholdOption("Recommended (≥ 80%)", 0.80),
            new ThresholdOption("Broad / All Potential (≥ 70%)", 0.70)
        });
        thresholdCombo.setSelectedIndex(1); // Default to 80%
        thresholdCombo.addActionListener(e -> applyCandidateFilters());
        controlsLeft.add(thresholdCombo);

        sameProgramCheckBox = new JCheckBox("Same Program Only");
        sameProgramCheckBox.setOpaque(false);
        sameProgramCheckBox.setForeground(ThemeUtils.TEXT_PRIMARY);
        sameProgramCheckBox.addActionListener(e -> applyCandidateFilters());
        controlsLeft.add(sameProgramCheckBox);

        showDismissedCheckBox = new JCheckBox("Show Dismissed");
        showDismissedCheckBox.setOpaque(false);
        showDismissedCheckBox.setForeground(ThemeUtils.TEXT_MUTED);
        showDismissedCheckBox.addActionListener(e -> scanForSimilarNames());
        controlsLeft.add(showDismissedCheckBox);

        topBar.add(controlsLeft, BorderLayout.WEST);

        // Search & Count (Right)
        JPanel controlsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        controlsRight.setOpaque(false);

        searchFilterField = new JTextField(14);
        searchFilterField.putClientProperty("JTextField.placeholderText", "Filter candidate names...");
        searchFilterField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                applyCandidateFilters();
            }
        });
        controlsRight.add(searchFilterField);

        candidateCountLabel = new JLabel("Click 'Scan' to detect");
        candidateCountLabel.setForeground(ThemeUtils.NEON_AMBER);
        candidateCountLabel.setFont(candidateCountLabel.getFont().deriveFont(Font.BOLD, 12f));
        controlsRight.add(candidateCountLabel);

        topBar.add(controlsRight, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        // Split Pane: Left = Table of candidate pairs, Right = Side-by-Side Review Card
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setOpaque(false);
        splitPane.setDividerLocation(460);
        splitPane.setBorder(null);

        // Left Table
        candidateTableModel = new CandidateTableModel();
        candidatesTable = new JTable(candidateTableModel);
        candidatesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ThemeUtils.applyTableTheme(candidatesTable);
        candidatesTable.setAutoCreateRowSorter(true);

        candidatesTable.getColumnModel().getColumn(0).setPreferredWidth(75);  // Score
        candidatesTable.getColumnModel().getColumn(1).setPreferredWidth(180); // Candidate 1
        candidatesTable.getColumnModel().getColumn(2).setPreferredWidth(180); // Candidate 2

        candidatesTable.getColumnModel().getColumn(0).setCellRenderer(new ConfidenceScoreRenderer());

        candidatesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = candidatesTable.getSelectedRow();
                if (selectedRow >= 0) {
                    int modelRow = candidatesTable.convertRowIndexToModel(selectedRow);
                    SimilarStudentCandidate candidate = candidateTableModel.getCandidate(modelRow);
                    displayCandidateReview(candidate);
                } else {
                    displayCandidateReview(null);
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(candidatesTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1));
        tableScroll.getViewport().setBackground(ThemeUtils.BG_SURFACE);
        splitPane.setLeftComponent(tableScroll);

        // Right Review Container
        reviewContainer = new JPanel(new BorderLayout());
        reviewContainer.setOpaque(false);
        displayCandidateReview(null); // Initial empty state
        splitPane.setRightComponent(reviewContainer);

        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private void scanForSimilarNames() {
        scanButton.setEnabled(false);
        candidateCountLabel.setText("Scanning student database...");
        candidateCountLabel.setForeground(ThemeUtils.TEXT_SECONDARY);

        ThresholdOption opt = (ThresholdOption) thresholdCombo.getSelectedItem();
        double threshold = opt != null ? opt.threshold : 0.80;
        boolean sameProgram = sameProgramCheckBox.isSelected();
        boolean includeDismissed = showDismissedCheckBox.isSelected();

        SwingWorker<List<SimilarStudentCandidate>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SimilarStudentCandidate> doInBackground() {
                return similarityService.findSimilarStudents(allStudents, threshold, sameProgram, includeDismissed);
            }

            @Override
            protected void done() {
                try {
                    currentCandidates = get();
                    applyCandidateFilters();
                } catch (Exception e) {
                    e.printStackTrace();
                    candidateCountLabel.setText("Error during scan");
                    candidateCountLabel.setForeground(ThemeUtils.NEON_ROSE);
                } finally {
                    scanButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void applyCandidateFilters() {
        if (currentCandidates == null) return;

        ThresholdOption opt = (ThresholdOption) thresholdCombo.getSelectedItem();
        double threshold = opt != null ? opt.threshold : 0.80;
        boolean sameProgram = sameProgramCheckBox.isSelected();
        String query = searchFilterField != null ? searchFilterField.getText().trim().toLowerCase() : "";

        List<SimilarStudentCandidate> filtered = currentCandidates.stream()
            .filter(c -> c.getSimilarityScore() >= threshold)
            .filter(c -> !sameProgram || c.isSameProgram())
            .filter(c -> {
                if (query.isEmpty()) return true;
                String n1 = c.getStudentA().getName().toLowerCase();
                String n2 = c.getStudentB().getName().toLowerCase();
                String c1 = c.getStudentA().getStudentCode().toLowerCase();
                String c2 = c.getStudentB().getStudentCode().toLowerCase();
                return n1.contains(query) || n2.contains(query) || c1.contains(query) || c2.contains(query);
            })
            .collect(Collectors.toList());

        candidateTableModel.setCandidates(filtered);

        if (filtered.isEmpty()) {
            candidateCountLabel.setText("No similar pairs found");
            candidateCountLabel.setForeground(ThemeUtils.TEXT_MUTED);
            displayCandidateReview(null);
        } else {
            candidateCountLabel.setText(String.format("Found %d candidate pair%s",
                filtered.size(), filtered.size() == 1 ? "" : "s"));
            candidateCountLabel.setForeground(ThemeUtils.NEON_AMBER);
            if (candidatesTable.getRowCount() > 0) {
                candidatesTable.setRowSelectionInterval(0, 0);
            }
        }
    }

    private void displayCandidateReview(SimilarStudentCandidate candidate) {
        this.selectedCandidate = candidate;
        reviewContainer.removeAll();

        if (candidate == null) {
            JPanel emptyPanel = ThemeUtils.createFuturisticCard(ThemeUtils.BORDER_COLOR);
            emptyPanel.setLayout(new GridBagLayout());
            JLabel emptyLabel = new JLabel("<html><center><b style='font-size:14px;color:#94A3B8;'>No Candidate Selected</b><br>" +
                "<span style='color:#64748B;'>Select a similar student pair from the list on the left to review details,<br>" +
                "choose merge direction, or mark as dismissed.</span></center></html>");
            emptyPanel.add(emptyLabel);
            reviewContainer.add(emptyPanel, BorderLayout.CENTER);
            reviewContainer.revalidate();
            reviewContainer.repaint();
            return;
        }

        Student sA = candidate.getStudentA();
        Student sB = candidate.getStudentB();
        Student recommendedTarget = candidate.getRecommendedTarget();
        Student recommendedSource = candidate.getRecommendedSource();

        JPanel card = ThemeUtils.createFuturisticCard(ThemeUtils.NEON_AMBER);
        card.setLayout(new BorderLayout(10, 10));
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // Top match header badge
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        headerPanel.setOpaque(false);

        JPanel scorePill = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        scorePill.setOpaque(false);

        JLabel scoreBadge = new JLabel(String.format("⚡ %d%% MATCH", candidate.getSimilarityPercentage()));
        scoreBadge.setFont(scoreBadge.getFont().deriveFont(Font.BOLD, 13f));
        scoreBadge.setForeground(candidate.getConfidence() == SimilarStudentCandidate.Confidence.HIGH
            ? ThemeUtils.NEON_GREEN : ThemeUtils.NEON_AMBER);
        scorePill.add(scoreBadge);

        if (candidate.isSameProgram()) {
            JLabel progBadge = new JLabel("• Same Program (" + sA.getFormattedProgramName() + ")");
            progBadge.setForeground(ThemeUtils.NEON_CYAN);
            progBadge.setFont(progBadge.getFont().deriveFont(Font.BOLD, 11f));
            scorePill.add(progBadge);
        }

        headerPanel.add(scorePill, BorderLayout.WEST);

        JLabel reasonLabel = new JLabel("<html><span style='color:#94A3B8;'>" + candidate.getReasonSummary() + "</span></html>");
        reasonLabel.setFont(reasonLabel.getFont().deriveFont(Font.PLAIN, 11f));
        headerPanel.add(reasonLabel, BorderLayout.SOUTH);

        card.add(headerPanel, BorderLayout.NORTH);

        // Center: Side-by-Side Student Information
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        JPanel studentComparisonRow = new JPanel(new GridLayout(1, 2, 12, 0));
        studentComparisonRow.setOpaque(false);

        // Student A Card
        JPanel cardA = createStudentMiniCard(sA, "Candidate A", ThemeUtils.NEON_CYAN);
        studentComparisonRow.add(cardA);

        // Student B Card
        JPanel cardB = createStudentMiniCard(sB, "Candidate B", ThemeUtils.NEON_PURPLE);
        studentComparisonRow.add(cardB);

        centerPanel.add(studentComparisonRow);
        centerPanel.add(Box.createVerticalStrut(12));

        // Direction Selection & Reason Panel
        JPanel actionBox = ThemeUtils.createFuturisticCard(ThemeUtils.BORDER_COLOR);
        actionBox.setLayout(new BoxLayout(actionBox, BoxLayout.Y_AXIS));
        actionBox.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel decisionTitle = new JLabel("🔀 Merge Direction Decision:");
        decisionTitle.setFont(decisionTitle.getFont().deriveFont(Font.BOLD, 12f));
        decisionTitle.setForeground(ThemeUtils.NEON_AMBER);
        actionBox.add(decisionTitle);
        actionBox.add(Box.createVerticalStrut(6));

        // Radio buttons
        ButtonGroup dirGroup = new ButtonGroup();
        keepTargetRadio = new JRadioButton(String.format(
            "<html>Keep <b>%s</b> as Primary Record — Merge <i>%s</i> into it <span style='color:#00FF9D;'>(Recommended)</span></html>",
            recommendedTarget.getName(), recommendedSource.getName()));
        keepTargetRadio.setOpaque(false);
        keepTargetRadio.setForeground(ThemeUtils.TEXT_PRIMARY);
        keepTargetRadio.setSelected(true);

        keepSourceRadio = new JRadioButton(String.format(
            "<html>Keep <b>%s</b> as Primary Record — Merge <i>%s</i> into it</html>",
            recommendedSource.getName(), recommendedTarget.getName()));
        keepSourceRadio.setOpaque(false);
        keepSourceRadio.setForeground(ThemeUtils.TEXT_PRIMARY);

        dirGroup.add(keepTargetRadio);
        dirGroup.add(keepSourceRadio);

        actionBox.add(keepTargetRadio);
        actionBox.add(Box.createVerticalStrut(3));
        actionBox.add(keepSourceRadio);
        actionBox.add(Box.createVerticalStrut(8));

        // Reason input
        JPanel reasonRow = new JPanel(new BorderLayout(8, 0));
        reasonRow.setOpaque(false);
        JLabel reasonLbl = new JLabel("Consolidation Reason:");
        reasonLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        reasonRow.add(reasonLbl, BorderLayout.WEST);

        candidateMergeReasonField = new JTextField(
            String.format("Consolidate similar name typo (%d%% similarity)", candidate.getSimilarityPercentage()));
        reasonRow.add(candidateMergeReasonField, BorderLayout.CENTER);
        actionBox.add(reasonRow);

        centerPanel.add(actionBox);
        card.add(centerPanel, BorderLayout.CENTER);

        // Bottom Action Buttons
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        buttonRow.setOpaque(false);

        dismissCandidateButton = new JButton("❌ Not a Duplicate (Dismiss)");
        ThemeUtils.styleButton(dismissCandidateButton, ThemeUtils.TEXT_MUTED);
        dismissCandidateButton.setToolTipText("Dismiss this candidate pair so it will not appear in future scans");
        dismissCandidateButton.addActionListener(e -> dismissCurrentCandidate());
        buttonRow.add(dismissCandidateButton);

        JButton manualAssistButton = new JButton("Open in Manual Tab");
        ThemeUtils.styleButton(manualAssistButton, ThemeUtils.NEON_BLUE);
        manualAssistButton.addActionListener(e -> {
            Student target = keepTargetRadio.isSelected() ? recommendedTarget : recommendedSource;
            Student source = keepTargetRadio.isSelected() ? recommendedSource : recommendedTarget;
            selectInManualMergeTab(source.getStudentCode(), target.getStudentCode());
        });
        buttonRow.add(manualAssistButton);

        executeCandidateMergeButton = new JButton("⚡ Execute Merge");
        ThemeUtils.styleButton(executeCandidateMergeButton, ThemeUtils.NEON_AMBER);
        executeCandidateMergeButton.setFont(executeCandidateMergeButton.getFont().deriveFont(Font.BOLD, 12f));
        executeCandidateMergeButton.addActionListener(e -> executeCurrentCandidateMerge());
        buttonRow.add(executeCandidateMergeButton);

        card.add(buttonRow, BorderLayout.SOUTH);

        reviewContainer.add(card, BorderLayout.CENTER);
        reviewContainer.revalidate();
        reviewContainer.repaint();
    }

    private JPanel createStudentMiniCard(Student student, String tag, Color accentColor) {
        JPanel p = ThemeUtils.createFuturisticCard(accentColor);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel tagLbl = new JLabel(tag + ": " + student.getStudentCode());
        tagLbl.setFont(tagLbl.getFont().deriveFont(Font.BOLD, 11f));
        tagLbl.setForeground(accentColor);
        p.add(tagLbl);
        p.add(Box.createVerticalStrut(3));

        JLabel nameLbl = new JLabel(student.getName());
        nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD, 14f));
        nameLbl.setForeground(ThemeUtils.TEXT_PRIMARY);
        p.add(nameLbl);
        p.add(Box.createVerticalStrut(4));

        String prog = student.getProgram() != null ? student.getProgram() : "-";
        String yl = student.getFormattedYearLevel();
        JLabel progLbl = new JLabel("Program: " + prog + " | " + yl);
        progLbl.setFont(progLbl.getFont().deriveFont(Font.PLAIN, 12f));
        progLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        p.add(progLbl);
        p.add(Box.createVerticalStrut(6));

        int payCount = student.getPaymentCount();
        double total = student.getTotalAmount();
        JLabel payLbl = new JLabel(String.format("Payments: %d record%s totaling ₱%,.2f",
            payCount, payCount == 1 ? "" : "s", total));
        payLbl.setFont(payLbl.getFont().deriveFont(Font.BOLD, 12f));
        payLbl.setForeground(ThemeUtils.NEON_GREEN);
        p.add(payLbl);
        p.add(Box.createVerticalStrut(6));

        // Recent receipts summary
        List<Payment> payments = student.getPayments();
        if (payments != null && !payments.isEmpty()) {
            StringBuilder receiptsSb = new StringBuilder("<html><small style='color:#94A3B8;'>Receipts: ");
            int showLimit = Math.min(4, payments.size());
            for (int i = 0; i < showLimit; i++) {
                Payment pay = payments.get(i);
                if (i > 0) receiptsSb.append(", ");
                receiptsSb.append("#").append(pay.getReceiptNumber())
                    .append(" (₱").append(String.format("%,.0f", pay.getTotalAmount())).append(")");
            }
            if (payments.size() > showLimit) {
                receiptsSb.append(", +").append(payments.size() - showLimit).append(" more");
            }
            receiptsSb.append("</small></html>");
            JLabel rcptLbl = new JLabel(receiptsSb.toString());
            p.add(rcptLbl);
        } else {
            JLabel noRcptLbl = new JLabel("<html><small style='color:#64748B;'>No payment receipts recorded</small></html>");
            p.add(noRcptLbl);
        }

        return p;
    }

    private void executeCurrentCandidateMerge() {
        if (selectedCandidate == null) return;

        Student recommendedTarget = selectedCandidate.getRecommendedTarget();
        Student recommendedSource = selectedCandidate.getRecommendedSource();

        Student target = keepTargetRadio.isSelected() ? recommendedTarget : recommendedSource;
        Student source = keepTargetRadio.isSelected() ? recommendedSource : recommendedTarget;

        String reason = candidateMergeReasonField.getText().trim();
        if (reason.isEmpty()) reason = "Consolidating similar name typo";

        int confirm = JOptionPane.showConfirmDialog(this,
            String.format("Are you sure you want to merge:\n\n" +
                "Source (Typo/Duplicate): %s (%s) — %d payments\n" +
                "INTO Target (Primary): %s (%s)\n\n" +
                "All %d payments will be reassigned to %s.\n" +
                "The source record will be safely archived.\n" +
                "You can undo this at any time in the 'Merge History' tab.",
                source.getName(), source.getStudentCode(), source.getPaymentCount(),
                target.getName(), target.getStudentCode(),
                source.getPaymentCount(), target.getName()),
            "Confirm Student Consolidation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            StudentMergeRecord record = db.mergeStudents(source.getStudentCode(), target.getStudentCode(), reason, "user");
            JOptionPane.showMessageDialog(this,
                String.format("Merge successful!\n\nMerge Code: %s\nTransferred Payments: %d\nPrimary Student: %s",
                    record.getMergeCode(), record.getReceiptNumbersList().size(), target.getName()),
                "Consolidation Completed", JOptionPane.INFORMATION_MESSAGE);

            if (onDataChanged != null) {
                onDataChanged.run();
            }

            // Remove candidate pairs containing the merged student
            String mergedSourceCode = source.getStudentCode();
            currentCandidates.removeIf(c ->
                c.getStudentA().getStudentCode().equalsIgnoreCase(mergedSourceCode) ||
                c.getStudentB().getStudentCode().equalsIgnoreCase(mergedSourceCode));

            applyCandidateFilters();

            // Refresh student lists and merge history in background
            loadStudents(null, null, null);
            loadHistory();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error executing merge: " + ex.getMessage(), "Merge Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void dismissCurrentCandidate() {
        if (selectedCandidate == null) return;

        Student sA = selectedCandidate.getStudentA();
        Student sB = selectedCandidate.getStudentB();

        int confirm = JOptionPane.showConfirmDialog(this,
            String.format("Mark these students as NOT a duplicate?\n\n" +
                "• %s (%s)\n" +
                "• %s (%s)\n\n" +
                "This pair will be dismissed and excluded from future similar name scans.",
                sA.getName(), sA.getStudentCode(),
                sB.getName(), sB.getStudentCode()),
            "Dismiss Duplicate Candidate", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            db.dismissStudentSimilarity(sA.getStudentCode(), sB.getStudentCode(), "Marked not a duplicate by user", "user");

            String pairKey = selectedCandidate.getPairKey();
            currentCandidates.removeIf(c -> c.getPairKey().equals(pairKey));
            applyCandidateFilters();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error dismissing candidate: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void selectInManualMergeTab(String sourceCode, String targetCode) {
        tabbedPane.setSelectedIndex(1); // Manual tab
        for (int i = 0; i < sourceCombo.getItemCount(); i++) {
            if (sourceCode.equalsIgnoreCase(sourceCombo.getItemAt(i).student.getStudentCode())) {
                sourceCombo.setSelectedIndex(i);
                break;
            }
        }
        for (int i = 0; i < targetCombo.getItemCount(); i++) {
            if (targetCode.equalsIgnoreCase(targetCombo.getItemAt(i).student.getStudentCode())) {
                targetCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    // =========================================================================
    // TAB 2: MANUAL MERGE EXECUTION
    // =========================================================================

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

    private void updateMergePreview() {
        StudentComboItem srcItem = (StudentComboItem) sourceCombo.getSelectedItem();
        StudentComboItem tgtItem = (StudentComboItem) targetCombo.getSelectedItem();

        if (srcItem == null || tgtItem == null) {
            executeMergeButton.setEnabled(false);
            return;
        }

        Student src = srcItem.student;
        Student tgt = tgtItem.student;

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
            loadStudents(null, null, null);
            loadHistory();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error executing merge: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    // =========================================================================
    // TAB 3: MERGE HISTORY & UNDO
    // =========================================================================

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
                    "Merge Reverted", JOptionPane.INFORMATION_MESSAGE);

                if (onDataChanged != null) {
                    onDataChanged.run();
                }

                loadStudents(null, null, null);
                loadHistory();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to revert merge.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error reverting merge: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    // =========================================================================
    // DATA LOADING HELPERS
    // =========================================================================

    private void loadStudents(String preselectedSource, String preselectedTarget, Runnable onComplete) {
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

                    if (onComplete != null) {
                        onComplete.run();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
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

    // =========================================================================
    // INNER CLASSES & TABLE MODELS
    // =========================================================================

    private static class ThresholdOption {
        final String label;
        final double threshold;

        ThresholdOption(String label, double threshold) {
            this.label = label;
            this.threshold = threshold;
        }

        @Override
        public String toString() { return label; }
    }

    private static class CandidateTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
            "Match", "Candidate 1", "Candidate 2", "Match Details / Heuristics"
        };
        private List<SimilarStudentCandidate> candidates = new ArrayList<>();

        public void setCandidates(List<SimilarStudentCandidate> list) {
            this.candidates = list != null ? list : new ArrayList<>();
            fireTableDataChanged();
        }

        public SimilarStudentCandidate getCandidate(int row) {
            if (row >= 0 && row < candidates.size()) return candidates.get(row);
            return null;
        }

        @Override public int getRowCount() { return candidates.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            SimilarStudentCandidate c = candidates.get(row);
            Student s1 = c.getStudentA();
            Student s2 = c.getStudentB();
            switch (col) {
                case 0: return c;
                case 1: return String.format("%s (%s - %s)", s1.getName(), s1.getStudentCode(),
                    s1.getProgram() != null ? s1.getProgram() : "-");
                case 2: return String.format("%s (%s - %s)", s2.getName(), s2.getStudentCode(),
                    s2.getProgram() != null ? s2.getProgram() : "-");
                case 3: return c.getReasonSummary();
                default: return null;
            }
        }
    }

    private static class ConfidenceScoreRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof SimilarStudentCandidate cand) {
                setText(String.format(" %d%% ", cand.getSimilarityPercentage()));
                setHorizontalAlignment(CENTER);
                setFont(getFont().deriveFont(Font.BOLD, 12f));

                if (!isSelected) {
                    if (cand.getConfidence() == SimilarStudentCandidate.Confidence.HIGH) {
                        c.setForeground(ThemeUtils.NEON_GREEN);
                    } else if (cand.getConfidence() == SimilarStudentCandidate.Confidence.MEDIUM) {
                        c.setForeground(ThemeUtils.NEON_AMBER);
                    } else {
                        c.setForeground(ThemeUtils.TEXT_SECONDARY);
                    }
                }
            }
            return c;
        }
    }

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
