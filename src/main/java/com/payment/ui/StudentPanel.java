package com.payment.ui;

import com.payment.Payment;
import com.payment.SimilarStudentCandidate;
import com.payment.Student;
import com.payment.StudentSimilarityService;
import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Students Panel - Manage student records with profile avatar,
 * payment summary cards, fee breakdown, receipts feed, and student merge & undo features.
 */
public class StudentPanel extends JPanel {

    private final DatabaseManager db;
    private final StudentSimilarityService similarityService;

    private JTable studentTable;
    private StudentTableModel studentTableModel;
    private JTextField searchField;
    private JComboBox<String> programFilter;
    private JLabel statusLabel;

    // Detail panel components
    private JPanel detailPanel;
    private CyberAvatarPanel avatarPanel;
    private JLabel detailStudentCode;
    private JLabel detailName;
    private JLabel detailProgramBadge;
    private JLabel detailYearBadge;
    private JLabel detailRegisteredDate;
    private JLabel detailPaymentCount;
    private JLabel detailTotalAmount;

    // Similarity alert banner
    private JPanel similarityAlertCard;
    private JLabel similarityAlertText;
    private SimilarStudentCandidate currentSimilarityCandidate;
    private Student currentSelectedStudent;

    // Fee breakdown labels
    private JLabel intelFeeVal;
    private JLabel tshirtFeeVal;
    private JLabel citNightVal;
    private JLabel penaltiesVal;

    // Receipts feed
    private JPanel receiptsFeedContainer;

    public StudentPanel() {
        this.db = DatabaseManager.getInstance();
        this.similarityService = new StudentSimilarityService(this.db);
        initializeUI();
        refreshData();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(ThemeUtils.BG_DEEPEST);

        // Title with futuristic gradient banner
        JPanel titleBanner = ThemeUtils.createGradientPanel(new Color(15, 23, 42), new Color(10, 14, 26));
        titleBanner.setLayout(new BorderLayout());
        titleBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, ThemeUtils.NEON_CYAN),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLabel = new JLabel("👥 Student Directory & Payment Accounts");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        titleLabel.setForeground(ThemeUtils.NEON_CYAN);
        titleBanner.add(titleLabel, BorderLayout.WEST);

        JLabel subtitleLabel = new JLabel("Select any student to view profile, avatar & payment ledger");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        subtitleLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        titleBanner.add(subtitleLabel, BorderLayout.EAST);

        add(titleBanner, BorderLayout.NORTH);

        // Main split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(600);
        splitPane.setResizeWeight(0.62);
        splitPane.setOneTouchExpandable(true);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        // Left side - Student list
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setOpaque(false);

        // Toolbar Card
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolBar.setBackground(ThemeUtils.BG_CARD);
        toolBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        JLabel searchLbl = new JLabel("Search:");
        searchLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        toolBar.add(searchLbl);

        searchField = new JTextField(16);
        searchField.setToolTipText("Search by student name...");
        searchField.addActionListener(e -> filterStudents());
        toolBar.add(searchField);

        JLabel progLbl = new JLabel("Program:");
        progLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        toolBar.add(progLbl);

        programFilter = new JComboBox<>(new String[]{"All Programs"});
        programFilter.addActionListener(e -> filterStudents());
        toolBar.add(programFilter);

        JButton refreshButton = new JButton("🔄 Refresh");
        ThemeUtils.styleButton(refreshButton, ThemeUtils.NEON_CYAN);
        refreshButton.addActionListener(e -> refreshData());
        toolBar.add(refreshButton);

        JButton mergeButton = new JButton("🔀 Merge Students...");
        ThemeUtils.styleButton(mergeButton, ThemeUtils.NEON_AMBER);
        mergeButton.setToolTipText("Consolidate typo/duplicate student records into one with full undo history");
        mergeButton.addActionListener(e -> {
            int sel = studentTable.getSelectedRow();
            String preselected = null;
            if (sel >= 0) {
                int modelRow = studentTable.convertRowIndexToModel(sel);
                Student s = studentTableModel.getStudent(modelRow);
                if (s != null) preselected = s.getStudentCode();
            }
            openMergeDialog(preselected);
        });
        toolBar.add(mergeButton);

        JButton detectSimilarButton = new JButton("✨ Detect Similar Names");
        ThemeUtils.styleButton(detectSimilarButton, ThemeUtils.NEON_GREEN);
        detectSimilarButton.setToolTipText("Scan and detect students with similar names or typos to review and merge");
        detectSimilarButton.addActionListener(e -> openSimilarDetectorDialog());
        toolBar.add(detectSimilarButton);

        leftPanel.add(toolBar, BorderLayout.NORTH);

        // Student table
        studentTableModel = new StudentTableModel();
        studentTable = new JTable(studentTableModel);
        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ThemeUtils.applyTableTheme(studentTable);

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

        // Center renderers for #, Student Code, Program
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        studentTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        studentTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        studentTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        studentTable.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
        studentTable.getColumnModel().getColumn(5).setCellRenderer(new CurrencyCellRenderer());

        // Real-time document listener
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterStudents(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterStudents(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterStudents(); }
        });

        // Context menu and double-click
        setupContextMenu();

        studentTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int selectedRow = studentTable.getSelectedRow();
                    if (selectedRow >= 0) {
                        int modelRow = studentTable.convertRowIndexToModel(selectedRow);
                        Student student = studentTableModel.getStudent(modelRow);
                        if (student != null) {
                            navigateToStudentPayments(student);
                        }
                    }
                }
            }
        });

        // Selection listener to update detail panel
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

        JScrollPane scrollPane = new JScrollPane(studentTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(ThemeUtils.BG_SURFACE);
        leftPanel.add(scrollPane, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Loading students...");
        statusLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
        leftPanel.add(statusLabel, BorderLayout.SOUTH);

        splitPane.setLeftComponent(leftPanel);

        // Right side - Detail panel
        detailPanel = createDetailPanel();
        splitPane.setRightComponent(detailPanel);

        add(splitPane, BorderLayout.CENTER);
    }

    private void setupContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem viewPaymentsItem = new JMenuItem("View Payment History");
        JMenuItem mergeItem = new JMenuItem("Merge with Another Student...");
        JMenuItem findSimilarItem = new JMenuItem("✨ Detect Similar Names...");
        JMenuItem copyCodeItem = new JMenuItem("Copy Student Code");
        JMenuItem copyNameItem = new JMenuItem("Copy Student Name");

        viewPaymentsItem.addActionListener(e -> {
            int selectedRow = studentTable.getSelectedRow();
            if (selectedRow >= 0) {
                int modelRow = studentTable.convertRowIndexToModel(selectedRow);
                Student s = studentTableModel.getStudent(modelRow);
                if (s != null) {
                    navigateToStudentPayments(s);
                }
            }
        });

        mergeItem.addActionListener(e -> {
            int selectedRow = studentTable.getSelectedRow();
            if (selectedRow >= 0) {
                int modelRow = studentTable.convertRowIndexToModel(selectedRow);
                Student s = studentTableModel.getStudent(modelRow);
                if (s != null) {
                    openMergeDialog(s.getStudentCode());
                }
            } else {
                openMergeDialog(null);
            }
        });

        findSimilarItem.addActionListener(e -> openSimilarDetectorDialog());

        copyCodeItem.addActionListener(e -> {
            int selectedRow = studentTable.getSelectedRow();
            if (selectedRow >= 0) {
                Object val = studentTable.getValueAt(selectedRow, 1);
                if (val != null) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(val.toString()), null);
                }
            }
        });

        copyNameItem.addActionListener(e -> {
            int selectedRow = studentTable.getSelectedRow();
            if (selectedRow >= 0) {
                Object val = studentTable.getValueAt(selectedRow, 2);
                if (val != null) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(val.toString()), null);
                }
            }
        });

        popupMenu.add(viewPaymentsItem);
        popupMenu.add(mergeItem);
        popupMenu.add(findSimilarItem);
        popupMenu.addSeparator();
        popupMenu.add(copyCodeItem);
        popupMenu.add(copyNameItem);

        studentTable.setComponentPopupMenu(popupMenu);
    }

    private void openMergeDialog(String preselectedSourceCode) {
        Window window = SwingUtilities.getWindowAncestor(this);
        MergeStudentsDialog dialog = new MergeStudentsDialog(window, preselectedSourceCode, null, preselectedSourceCode != null ? 1 : 0, this::refreshData);
        dialog.setVisible(true);
    }

    private void openSimilarDetectorDialog() {
        Window window = SwingUtilities.getWindowAncestor(this);
        MergeStudentsDialog dialog = new MergeStudentsDialog(window, 0, this::refreshData);
        dialog.setVisible(true);
    }

    private void navigateToStudentPayments(Student student) {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof MainFrame mainFrame) {
            mainFrame.showPaymentsForStudent(student.getStudentCode());
        }
    }

    private JPanel createDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ThemeUtils.BG_SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        panel.setPreferredSize(new Dimension(400, 0));

        // Header with accent glow & Quick Copy button
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setBackground(ThemeUtils.BG_CARD);
        headerBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(2, 0, 1, 0, ThemeUtils.NEON_CYAN),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        JLabel titleLabel = new JLabel("👤 Student Profile");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setForeground(ThemeUtils.NEON_CYAN);
        headerBar.add(titleLabel, BorderLayout.WEST);

        JButton copyCodeBtn = new JButton("📋 Copy Code");
        ThemeUtils.styleButton(copyCodeBtn, ThemeUtils.TEXT_SECONDARY);
        copyCodeBtn.setFont(copyCodeBtn.getFont().deriveFont(Font.PLAIN, 11f));
        copyCodeBtn.addActionListener(e -> {
            if (currentSelectedStudent != null && currentSelectedStudent.getStudentCode() != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(currentSelectedStudent.getStudentCode()), null);
                JOptionPane.showMessageDialog(this, "Copied student code: " + currentSelectedStudent.getStudentCode(),
                    "Clipboard", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        headerBar.add(copyCodeBtn, BorderLayout.EAST);
        panel.add(headerBar, BorderLayout.NORTH);

        // Scrollable content body
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ThemeUtils.BG_SURFACE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // 1. Profile Card with Avatar & Badges
        JPanel profileCard = ThemeUtils.createFuturisticCard(ThemeUtils.NEON_CYAN);
        profileCard.setLayout(new BorderLayout(12, 0));
        profileCard.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        profileCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        JPanel avatarContainer = new JPanel(new GridBagLayout());
        avatarContainer.setOpaque(false);
        avatarContainer.setPreferredSize(new Dimension(100, 100));
        avatarContainer.setMinimumSize(new Dimension(100, 100));
        avatarContainer.setMaximumSize(new Dimension(100, 100));
        avatarPanel = new CyberAvatarPanel();
        avatarContainer.add(avatarPanel);
        profileCard.add(avatarContainer, BorderLayout.WEST);

        JPanel infoWrapper = new JPanel();
        infoWrapper.setLayout(new BoxLayout(infoWrapper, BoxLayout.Y_AXIS));
        infoWrapper.setOpaque(false);

        detailName = new JLabel("No Student Selected");
        detailName.setFont(detailName.getFont().deriveFont(Font.BOLD, 15f));
        detailName.setForeground(ThemeUtils.TEXT_PRIMARY);
        infoWrapper.add(detailName);
        infoWrapper.add(Box.createVerticalStrut(3));

        detailStudentCode = new JLabel("-");
        detailStudentCode.setFont(detailStudentCode.getFont().deriveFont(Font.PLAIN, 12f));
        detailStudentCode.setForeground(ThemeUtils.TEXT_SECONDARY);
        infoWrapper.add(detailStudentCode);
        infoWrapper.add(Box.createVerticalStrut(6));

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        detailProgramBadge = createPillBadge("Program: -", ThemeUtils.NEON_CYAN);
        badgeRow.add(detailProgramBadge);

        detailYearBadge = createPillBadge("Year: -", ThemeUtils.NEON_PURPLE);
        badgeRow.add(detailYearBadge);
        infoWrapper.add(badgeRow);
        infoWrapper.add(Box.createVerticalStrut(6));

        detailRegisteredDate = new JLabel("📅 Joined: -");
        detailRegisteredDate.setFont(detailRegisteredDate.getFont().deriveFont(Font.PLAIN, 10f));
        detailRegisteredDate.setForeground(ThemeUtils.TEXT_MUTED);
        infoWrapper.add(detailRegisteredDate);

        profileCard.add(infoWrapper, BorderLayout.CENTER);
        contentPanel.add(profileCard);
        contentPanel.add(Box.createVerticalStrut(10));

        // 2. Smart Similarity Alert Banner (Hidden by default)
        similarityAlertCard = ThemeUtils.createFuturisticCard(ThemeUtils.NEON_AMBER);
        similarityAlertCard.setLayout(new BorderLayout(8, 4));
        similarityAlertCard.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        similarityAlertCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        similarityAlertCard.setVisible(false);

        similarityAlertText = new JLabel("⚠️ Similar student detected");
        similarityAlertText.setFont(similarityAlertText.getFont().deriveFont(Font.PLAIN, 11f));
        similarityAlertText.setForeground(ThemeUtils.TEXT_PRIMARY);
        similarityAlertCard.add(similarityAlertText, BorderLayout.CENTER);

        JButton similarityReviewBtn = new JButton("Review Merge");
        ThemeUtils.styleButton(similarityReviewBtn, ThemeUtils.NEON_AMBER);
        similarityReviewBtn.setFont(similarityReviewBtn.getFont().deriveFont(Font.BOLD, 10f));
        similarityReviewBtn.addActionListener(e -> {
            if (currentSimilarityCandidate != null && currentSelectedStudent != null) {
                Student sA = currentSimilarityCandidate.getStudentA();
                Student sB = currentSimilarityCandidate.getStudentB();
                Student other = sA.getStudentCode().equals(currentSelectedStudent.getStudentCode()) ? sB : sA;
                Window window = SwingUtilities.getWindowAncestor(this);
                MergeStudentsDialog dialog = new MergeStudentsDialog(window, currentSelectedStudent.getStudentCode(), other.getStudentCode(), 0, this::refreshData);
                dialog.setVisible(true);
            }
        });
        similarityAlertCard.add(similarityReviewBtn, BorderLayout.EAST);
        contentPanel.add(similarityAlertCard);
        contentPanel.add(Box.createVerticalStrut(10));

        // 3. Financial Summary Cards (2 in a grid row)
        JPanel statsRow = new JPanel(new GridLayout(1, 2, 8, 0));
        statsRow.setOpaque(false);
        statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));

        JPanel countCard = ThemeUtils.createFuturisticCard(ThemeUtils.NEON_CYAN);
        countCard.setLayout(new BoxLayout(countCard, BoxLayout.Y_AXIS));
        countCard.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel countLbl = new JLabel("Total Payments");
        countLbl.setFont(countLbl.getFont().deriveFont(Font.PLAIN, 10f));
        countLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        countCard.add(countLbl);
        countCard.add(Box.createVerticalStrut(2));
        detailPaymentCount = new JLabel("0");
        detailPaymentCount.setFont(detailPaymentCount.getFont().deriveFont(Font.BOLD, 18f));
        detailPaymentCount.setForeground(ThemeUtils.NEON_CYAN);
        countCard.add(detailPaymentCount);
        statsRow.add(countCard);

        JPanel amountCard = ThemeUtils.createFuturisticCard(ThemeUtils.NEON_GREEN);
        amountCard.setLayout(new BoxLayout(amountCard, BoxLayout.Y_AXIS));
        amountCard.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel amtLbl = new JLabel("Total Paid");
        amtLbl.setFont(amtLbl.getFont().deriveFont(Font.PLAIN, 10f));
        amtLbl.setForeground(ThemeUtils.TEXT_SECONDARY);
        amountCard.add(amtLbl);
        amountCard.add(Box.createVerticalStrut(2));
        detailTotalAmount = new JLabel("₱0.00");
        detailTotalAmount.setFont(detailTotalAmount.getFont().deriveFont(Font.BOLD, 18f));
        detailTotalAmount.setForeground(ThemeUtils.NEON_GREEN);
        amountCard.add(detailTotalAmount);
        statsRow.add(amountCard);

        contentPanel.add(statsRow);
        contentPanel.add(Box.createVerticalStrut(10));

        // 4. Fee Category Breakdown
        JPanel feeCard = ThemeUtils.createFuturisticCard(ThemeUtils.NEON_PURPLE);
        feeCard.setLayout(new BoxLayout(feeCard, BoxLayout.Y_AXIS));
        feeCard.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        feeCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        JLabel feeTitle = new JLabel("📊 Fee Category Breakdown");
        feeTitle.setFont(feeTitle.getFont().deriveFont(Font.BOLD, 12f));
        feeTitle.setForeground(ThemeUtils.NEON_PURPLE);
        feeCard.add(feeTitle);
        feeCard.add(Box.createVerticalStrut(8));

        JPanel feeGrid = new JPanel(new GridLayout(2, 2, 8, 6));
        feeGrid.setOpaque(false);

        intelFeeVal = new JLabel("-");
        feeGrid.add(createFeeItem("💻 Intel Fee", intelFeeVal, ThemeUtils.NEON_CYAN));

        tshirtFeeVal = new JLabel("-");
        feeGrid.add(createFeeItem("👕 T-Shirt Fee", tshirtFeeVal, ThemeUtils.NEON_GREEN));

        citNightVal = new JLabel("-");
        feeGrid.add(createFeeItem("🌙 CIT Night", citNightVal, ThemeUtils.NEON_AMBER));

        penaltiesVal = new JLabel("-");
        feeGrid.add(createFeeItem("⚠️ Penalties", penaltiesVal, ThemeUtils.NEON_ROSE));

        feeCard.add(feeGrid);
        contentPanel.add(feeCard);
        contentPanel.add(Box.createVerticalStrut(10));

        // 5. Recent Payment Receipts Feed
        JPanel receiptsSection = new JPanel(new BorderLayout(0, 6));
        receiptsSection.setOpaque(false);

        JLabel receiptsHeader = new JLabel("🧾 Payment Receipts");
        receiptsHeader.setFont(receiptsHeader.getFont().deriveFont(Font.BOLD, 12f));
        receiptsHeader.setForeground(ThemeUtils.TEXT_PRIMARY);
        receiptsSection.add(receiptsHeader, BorderLayout.NORTH);

        receiptsFeedContainer = new JPanel();
        receiptsFeedContainer.setLayout(new BoxLayout(receiptsFeedContainer, BoxLayout.Y_AXIS));
        receiptsFeedContainer.setOpaque(false);
        receiptsSection.add(receiptsFeedContainer, BorderLayout.CENTER);

        contentPanel.add(receiptsSection);
        contentPanel.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(ThemeUtils.BG_SURFACE);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(14);
        panel.add(scrollPane, BorderLayout.CENTER);

        // 6. Bottom Quick Action Bar
        JPanel bottomBar = new JPanel(new GridLayout(1, 3, 6, 0));
        bottomBar.setBackground(ThemeUtils.BG_CARD);
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeUtils.BORDER_COLOR),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        JButton viewPaymentsBtn = new JButton("📋 Ledger");
        viewPaymentsBtn.setFont(viewPaymentsBtn.getFont().deriveFont(Font.BOLD, 11f));
        ThemeUtils.styleButton(viewPaymentsBtn, ThemeUtils.NEON_CYAN);
        viewPaymentsBtn.setToolTipText("Filter and view this student's transactions in the Payments tab");
        viewPaymentsBtn.addActionListener(e -> {
            if (currentSelectedStudent != null) {
                navigateToStudentPayments(currentSelectedStudent);
            } else {
                JOptionPane.showMessageDialog(this, "Please select a student first.", "No Student Selected", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        bottomBar.add(viewPaymentsBtn);

        JButton mergeBtn = new JButton("🔀 Merge");
        mergeBtn.setFont(mergeBtn.getFont().deriveFont(Font.BOLD, 11f));
        ThemeUtils.styleButton(mergeBtn, ThemeUtils.NEON_AMBER);
        mergeBtn.setToolTipText("Consolidate with another student record");
        mergeBtn.addActionListener(e -> {
            if (currentSelectedStudent != null) {
                openMergeDialog(currentSelectedStudent.getStudentCode());
            } else {
                openMergeDialog(null);
            }
        });
        bottomBar.add(mergeBtn);

        JButton similarBtn = new JButton("✨ Similar");
        similarBtn.setFont(similarBtn.getFont().deriveFont(Font.BOLD, 11f));
        ThemeUtils.styleButton(similarBtn, ThemeUtils.NEON_GREEN);
        similarBtn.setToolTipText("Detect students with similar names");
        similarBtn.addActionListener(e -> openSimilarDetectorDialog());
        bottomBar.add(similarBtn);

        panel.add(bottomBar, BorderLayout.SOUTH);

        clearDetails();
        return panel;
    }

    private JLabel createPillBadge(String text, Color accent) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 10f));
        lbl.setForeground(accent);
        lbl.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 25));
        lbl.setOpaque(true);
        lbl.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120), 1),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        return lbl;
    }

    private JPanel createFeeItem(String title, JLabel valLabel, Color accent) {
        JPanel p = new JPanel(new BorderLayout(4, 2));
        p.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.PLAIN, 10f));
        t.setForeground(ThemeUtils.TEXT_SECONDARY);
        valLabel.setFont(valLabel.getFont().deriveFont(Font.BOLD, 12f));
        valLabel.setForeground(accent);
        p.add(t, BorderLayout.NORTH);
        p.add(valLabel, BorderLayout.CENTER);
        return p;
    }

    private void showStudentDetails(int modelRow) {
        Student student = studentTableModel.getStudent(modelRow);
        this.currentSelectedStudent = student;

        if (avatarPanel != null) {
            avatarPanel.setStudent(student);
        }

        detailName.setText(student.getName());
        detailName.setToolTipText(student.getName());
        detailStudentCode.setText(student.getStudentCode());

        String prog = student.getFormattedProgramName();
        detailProgramBadge.setText("💻 " + prog);
        detailProgramBadge.setToolTipText("Program: " + prog);

        String yl = student.getFormattedYearLevel();
        detailYearBadge.setText("🎓 " + yl);

        if (student.getCreatedAt() != null) {
            detailRegisteredDate.setText("📅 Joined: " + student.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        } else {
            detailRegisteredDate.setText("📅 Joined: -");
        }

        detailPaymentCount.setText(String.valueOf(student.getPaymentCount()));
        detailTotalAmount.setText(String.format("₱%,.2f", student.getTotalAmount()));

        // Calculate fee category breakdowns
        List<Payment> payments = student.getPayments();
        double totalIntel = 0.0;
        double totalTshirt = 0.0;
        double totalCitNight = 0.0;
        double totalPenalties = 0.0;
        String tshirtSize = null;

        if (payments != null) {
            for (Payment p : payments) {
                if (p.isActive()) {
                    if (p.getIntelFee() != null) totalIntel += p.getIntelFee();
                    if (p.getTshirtSizing() != null) totalTshirt += p.getTshirtSizing();
                    if (p.getCitNight() != null) totalCitNight += p.getCitNight();
                    if (p.getPenalties() != null) totalPenalties += p.getPenalties();
                    if (p.getRemarks() != null && !p.getRemarks().isBlank() && tshirtSize == null) {
                        String r = p.getRemarks().trim();
                        if (r.toLowerCase().contains("size") || r.length() <= 5) {
                            tshirtSize = r;
                        }
                    }
                }
            }
        }

        intelFeeVal.setText(totalIntel > 0 ? String.format("₱%,.2f", totalIntel) : "-");
        tshirtFeeVal.setText(totalTshirt > 0 ? (String.format("₱%,.2f", totalTshirt) + (tshirtSize != null ? " (" + tshirtSize + ")" : "")) : "-");
        citNightVal.setText(totalCitNight > 0 ? String.format("₱%,.2f", totalCitNight) : "-");
        penaltiesVal.setText(totalPenalties > 0 ? String.format("₱%,.2f", totalPenalties) : "-");

        populateReceiptsFeed(payments);
        checkSimilarityForSelectedStudent(student);
    }

    private void populateReceiptsFeed(List<Payment> payments) {
        receiptsFeedContainer.removeAll();
        if (payments == null || payments.isEmpty()) {
            JPanel emptyCard = ThemeUtils.createFuturisticCard(ThemeUtils.BORDER_COLOR);
            emptyCard.setLayout(new FlowLayout(FlowLayout.CENTER, 8, 8));
            JLabel emptyLbl = new JLabel("<html><small style='color:#64748B;'>No payment receipts recorded</small></html>");
            emptyCard.add(emptyLbl);
            receiptsFeedContainer.add(emptyCard);
        } else {
            List<Payment> sorted = new ArrayList<>(payments);
            sorted.sort((p1, p2) -> Integer.compare(p2.getReceiptNumber(), p1.getReceiptNumber()));
            int limit = Math.min(6, sorted.size());
            for (int i = 0; i < limit; i++) {
                Payment p = sorted.get(i);
                receiptsFeedContainer.add(createReceiptItemCard(p));
                if (i < limit - 1) {
                    receiptsFeedContainer.add(Box.createVerticalStrut(6));
                }
            }
            if (sorted.size() > limit) {
                receiptsFeedContainer.add(Box.createVerticalStrut(4));
                JLabel moreLbl = new JLabel(String.format("<html><small style='color:#00F0FF;'>+ %d more receipt(s) in full ledger</small></html>", sorted.size() - limit));
                moreLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
                receiptsFeedContainer.add(moreLbl);
            }
        }
        receiptsFeedContainer.revalidate();
        receiptsFeedContainer.repaint();
    }

    private JPanel createReceiptItemCard(Payment p) {
        Color accent = p.isActive() ? ThemeUtils.NEON_CYAN : (p.isVoid() ? ThemeUtils.NEON_ROSE : ThemeUtils.NEON_PURPLE);
        JPanel card = ThemeUtils.createFuturisticCard(accent);
        card.setLayout(new BorderLayout(6, 3));
        card.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        JLabel rcptNo = new JLabel("Receipt #" + p.getReceiptNumber());
        rcptNo.setFont(rcptNo.getFont().deriveFont(Font.BOLD, 12f));
        rcptNo.setForeground(ThemeUtils.TEXT_PRIMARY);
        topRow.add(rcptNo, BorderLayout.WEST);

        JLabel amt = new JLabel(String.format("₱%,.2f", p.getTotalAmount()));
        amt.setFont(amt.getFont().deriveFont(Font.BOLD, 12f));
        amt.setForeground(p.isActive() ? ThemeUtils.NEON_GREEN : ThemeUtils.TEXT_MUTED);
        topRow.add(amt, BorderLayout.EAST);

        card.add(topRow, BorderLayout.NORTH);

        StringBuilder subSb = new StringBuilder();
        if (p.getRemittanceDate() != null) {
            subSb.append(p.getRemittanceDate());
        }
        String scope = p.getReceiptKey().displayScope();
        if (scope != null && !scope.equals("Unscoped")) {
            if (subSb.length() > 0) subSb.append(" • ");
            subSb.append(scope);
        }
        if (p.isVoid()) {
            subSb.append(" • <font color='#F43F5E'><b>VOID</b></font>");
        } else if (p.isRefunded()) {
            subSb.append(" • <font color='#A855F7'><b>REFUNDED</b></font>");
        }

        JLabel subLbl = new JLabel("<html><small style='color:#94A3B8;'>" + subSb + "</small></html>");
        card.add(subLbl, BorderLayout.CENTER);

        return card;
    }

    private void checkSimilarityForSelectedStudent(Student student) {
        if (similarityService == null || student == null) {
            similarityAlertCard.setVisible(false);
            return;
        }

        SwingWorker<SimilarStudentCandidate, Void> worker = new SwingWorker<>() {
            @Override
            protected SimilarStudentCandidate doInBackground() {
                List<Student> all = studentTableModel.getStudents();
                List<SimilarStudentCandidate> matches = similarityService.findSimilarForStudent(student, all, 0.85);
                return matches.isEmpty() ? null : matches.get(0);
            }

            @Override
            protected void done() {
                try {
                    SimilarStudentCandidate cand = get();
                    if (cand != null && currentSelectedStudent != null &&
                        currentSelectedStudent.getStudentCode().equals(student.getStudentCode())) {
                        currentSimilarityCandidate = cand;
                        Student other = cand.getStudentA().getStudentCode().equals(student.getStudentCode())
                            ? cand.getStudentB() : cand.getStudentA();
                        similarityAlertText.setText(String.format("<html><b>Similar Name:</b> %s (%d%% match)</html>",
                            other.getName(), cand.getSimilarityPercentage()));
                        similarityAlertCard.setVisible(true);
                    } else {
                        currentSimilarityCandidate = null;
                        similarityAlertCard.setVisible(false);
                    }
                } catch (Exception ignored) {
                    similarityAlertCard.setVisible(false);
                }
            }
        };
        worker.execute();
    }

    private void clearDetails() {
        currentSelectedStudent = null;
        currentSimilarityCandidate = null;
        if (avatarPanel != null) {
            avatarPanel.setStudent(null);
        }
        detailStudentCode.setText("-");
        detailName.setText("No Student Selected");
        detailProgramBadge.setText("Program: -");
        detailYearBadge.setText("Year: -");
        detailRegisteredDate.setText("📅 Joined: -");
        detailPaymentCount.setText("0");
        detailTotalAmount.setText("₱0.00");
        intelFeeVal.setText("-");
        tshirtFeeVal.setText("-");
        citNightVal.setText("-");
        penaltiesVal.setText("-");
        similarityAlertCard.setVisible(false);
        populateReceiptsFeed(Collections.emptyList());
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

    // --- Cyber Student Avatar Component ---

    private static class CyberAvatarPanel extends JPanel {
        private String initials = "🎓";
        private Color ringColor1 = ThemeUtils.NEON_CYAN;
        private Color ringColor2 = ThemeUtils.NEON_PURPLE;

        public CyberAvatarPanel() {
            Dimension dim = new Dimension(165, 165);
            setPreferredSize(dim);
            setMinimumSize(dim);
            setMaximumSize(dim);
            setOpaque(false);
        }

        public void setStudent(Student student) {
            if (student == null || student.getName() == null || student.getName().trim().isEmpty() || "-".equals(student.getName())) {
                this.initials = "🎓";
                this.ringColor1 = ThemeUtils.NEON_CYAN;
                this.ringColor2 = ThemeUtils.NEON_PURPLE;
            } else {
                String name = student.getName().trim();
                String[] parts = name.split("\\s+");
                if (parts.length >= 2) {
                    this.initials = ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
                } else if (name.length() >= 2) {
                    this.initials = name.substring(0, 2).toUpperCase();
                } else {
                    this.initials = name.toUpperCase();
                }
                int hash = Math.abs(name.hashCode());
                Color[] colors = {ThemeUtils.NEON_CYAN, ThemeUtils.NEON_PURPLE, ThemeUtils.NEON_GREEN, ThemeUtils.NEON_AMBER, ThemeUtils.NEON_BLUE};
                this.ringColor1 = colors[hash % colors.length];
                this.ringColor2 = colors[(hash + 1) % colors.length];
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            // Use available space for the large circular avatar
            int size = Math.min(width, height) - 6;
            if (size < 50) size = 50;

            int x = (width - size) / 2;
            int y = (height - size) / 2;

            // Outer dark disc
            g2.setColor(new Color(16, 22, 38));
            g2.fillOval(x, y, size, size);

            // Gradient glowing ring
            GradientPaint gp = new GradientPaint(x, y, ringColor1, x + size, y + size, ringColor2);
            g2.setPaint(gp);
            float strokeWidth = Math.max(3f, size / 40f);
            g2.setStroke(new BasicStroke(strokeWidth));
            g2.drawOval(x + 1, y + 1, size - 2, size - 2);

            // Inner subtle tinted glow
            g2.setColor(new Color(ringColor1.getRed(), ringColor1.getGreen(), ringColor1.getBlue(), 25));
            g2.fillOval(x + 4, y + 4, size - 8, size - 8);

            // Text / Initials - scale font with avatar size
            g2.setColor(ThemeUtils.TEXT_PRIMARY);
            float fontSize = Math.max(24f, size / 3.2f);
            if ("🎓".equals(initials)) {
                g2.setFont(g2.getFont().deriveFont(Font.PLAIN, fontSize));
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (size - fm.stringWidth(initials)) / 2;
                int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(initials, tx, ty);
            } else {
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, fontSize));
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (size - fm.stringWidth(initials)) / 2;
                int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent() - 2;
                g2.drawString(initials, tx, ty);
            }

            g2.dispose();
        }
    }

    // --- Table Model ---

    private static class StudentTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"#", "Student Code", "Name", "Program", "Payments", "Total Paid"};
        private List<Student> students = List.of();

        public void setStudents(List<Student> students) {
            this.students = students;
            fireTableDataChanged();
        }

        public List<Student> getStudents() {
            return students;
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
                case 5: return s.getTotalAmount();
                default: return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0 || columnIndex == 4) return Integer.class;
            if (columnIndex == 5) return Double.class;
            return String.class;
        }
    }

    private static class CurrencyCellRenderer extends DefaultTableCellRenderer {
        private final java.text.DecimalFormat format = new java.text.DecimalFormat("₱#,##0.00");

        public CurrencyCellRenderer() {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof Number num) {
                setText(format.format(num.doubleValue()));
            }
            return this;
        }
    }
}