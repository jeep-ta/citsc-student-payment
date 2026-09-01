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
 * Students Panel - Manage student records with profile avatar,
 * payment summary cards, and student merge & undo features.
 */
public class StudentPanel extends JPanel {

    private final DatabaseManager db;

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
        popupMenu.addSeparator();
        popupMenu.add(copyCodeItem);
        popupMenu.add(copyNameItem);

        studentTable.setComponentPopupMenu(popupMenu);
    }

    private void openMergeDialog(String preselectedSourceCode) {
        Window window = SwingUtilities.getWindowAncestor(this);
        MergeStudentsDialog dialog = new MergeStudentsDialog(window, preselectedSourceCode, null, this::refreshData);
        dialog.setVisible(true);
    }

    private void navigateToStudentPayments(Student student) {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof MainFrame mainFrame) {
            mainFrame.showPaymentsForStudent(student.getStudentCode());
        }
    }

    private JPanel createDetailPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ThemeUtils.BG_SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeUtils.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(0, 0, 20, 0)
        ));
        panel.setPreferredSize(new Dimension(380, 0));

        // Header with accent glow
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setBackground(ThemeUtils.BG_CARD);
        headerBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(2, 0, 0, 0, ThemeUtils.NEON_CYAN),
            BorderFactory.createEmptyBorder(14, 18, 10, 18)
        ));
        headerBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        headerBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel("👤 Student Profile");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(ThemeUtils.NEON_CYAN);
        headerBar.add(titleLabel, BorderLayout.WEST);
        panel.add(headerBar);
        panel.add(Box.createVerticalStrut(14));

        // Profile row: Avatar on LEFT matching red circle beside student info on RIGHT
        JPanel profileRow = new JPanel(new BorderLayout(14, 0));
        profileRow.setOpaque(false);
        profileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        profileRow.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 14));
        profileRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        // Left: Avatar container centered vertically with 165x165 dimensions
        JPanel avatarContainer = new JPanel(new GridBagLayout());
        avatarContainer.setOpaque(false);
        avatarContainer.setPreferredSize(new Dimension(165, 165));
        avatarContainer.setMinimumSize(new Dimension(165, 165));
        avatarContainer.setMaximumSize(new Dimension(165, 165));
        avatarPanel = new CyberAvatarPanel();
        avatarContainer.add(avatarPanel);
        profileRow.add(avatarContainer, BorderLayout.WEST);

        // Right: Student Info fields
        JPanel infoWrapper = new JPanel();
        infoWrapper.setLayout(new BoxLayout(infoWrapper, BoxLayout.Y_AXIS));
        infoWrapper.setOpaque(false);
        infoWrapper.setMinimumSize(new Dimension(50, 0));

        // Student code
        detailStudentCode = createDetailField(infoWrapper, "Student Record No.", "-");
        infoWrapper.add(Box.createVerticalStrut(6));

        // Name
        detailName = createDetailField(infoWrapper, "Name", "-");
        infoWrapper.add(Box.createVerticalStrut(6));

        // Program
        detailProgram = createDetailField(infoWrapper, "Program", "-");
        infoWrapper.add(Box.createVerticalStrut(6));

        // Year Level
        detailYearLevel = createDetailField(infoWrapper, "Year Level", "-");

        profileRow.add(infoWrapper, BorderLayout.CENTER);

        panel.add(profileRow);
        panel.add(Box.createVerticalStrut(14));

        // Separator glow line
        JPanel sepLine = new JPanel();
        sepLine.setBackground(ThemeUtils.BORDER_COLOR);
        sepLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sepLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(sepLine);
        panel.add(Box.createVerticalStrut(14));

        // Payment Summary section header
        JPanel summaryHeader = new JPanel(new BorderLayout());
        summaryHeader.setOpaque(false);
        summaryHeader.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
        summaryHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        summaryHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel summaryLabel = new JLabel("💰 Payment Summary");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD, 14f));
        summaryLabel.setForeground(ThemeUtils.NEON_GREEN);
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        summaryHeader.add(summaryLabel, BorderLayout.WEST);
        panel.add(summaryHeader);
        panel.add(Box.createVerticalStrut(10));

        // Summary cards row
        JPanel cardsWrapper = new JPanel();
        cardsWrapper.setLayout(new BoxLayout(cardsWrapper, BoxLayout.Y_AXIS));
        cardsWrapper.setOpaque(false);
        cardsWrapper.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        cardsWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Payment Count card
        JPanel countCard = ThemeUtils.createFuturisticCard(ThemeUtils.NEON_CYAN);
        countCard.setLayout(new BoxLayout(countCard, BoxLayout.Y_AXIS));
        countCard.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        countCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JLabel countLabel = new JLabel("Total Payments");
        countLabel.setFont(countLabel.getFont().deriveFont(Font.PLAIN, 11f));
        countLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        countCard.add(countLabel);
        countCard.add(Box.createVerticalStrut(2));

        detailPaymentCount = new JLabel("0");
        detailPaymentCount.setFont(detailPaymentCount.getFont().deriveFont(Font.BOLD, 20f));
        detailPaymentCount.setForeground(ThemeUtils.NEON_CYAN);
        detailPaymentCount.setAlignmentX(Component.LEFT_ALIGNMENT);
        countCard.add(detailPaymentCount);

        cardsWrapper.add(countCard);
        cardsWrapper.add(Box.createVerticalStrut(8));

        // Total Paid card
        JPanel amountCard = ThemeUtils.createFuturisticCard(ThemeUtils.NEON_GREEN);
        amountCard.setLayout(new BoxLayout(amountCard, BoxLayout.Y_AXIS));
        amountCard.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        amountCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JLabel amountLabel = new JLabel("Total Paid");
        amountLabel.setFont(amountLabel.getFont().deriveFont(Font.PLAIN, 11f));
        amountLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        amountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        amountCard.add(amountLabel);
        amountCard.add(Box.createVerticalStrut(2));

        detailTotalAmount = new JLabel("₱0.00");
        detailTotalAmount.setFont(detailTotalAmount.getFont().deriveFont(Font.BOLD, 20f));
        detailTotalAmount.setForeground(ThemeUtils.NEON_GREEN);
        detailTotalAmount.setAlignmentX(Component.LEFT_ALIGNMENT);
        amountCard.add(detailTotalAmount);

        cardsWrapper.add(amountCard);
        panel.add(cardsWrapper);

        panel.add(Box.createVerticalGlue());

        // Buttons Wrapper
        JPanel btnWrapper = new JPanel();
        btnWrapper.setLayout(new BoxLayout(btnWrapper, BoxLayout.Y_AXIS));
        btnWrapper.setOpaque(false);
        btnWrapper.setBorder(BorderFactory.createEmptyBorder(10, 14, 0, 14));
        btnWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        btnWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton viewPaymentsButton = new JButton("📋 View Payment History");
        viewPaymentsButton.setFont(viewPaymentsButton.getFont().deriveFont(Font.BOLD, 12f));
        viewPaymentsButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        viewPaymentsButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        ThemeUtils.styleButton(viewPaymentsButton, ThemeUtils.NEON_CYAN);
        viewPaymentsButton.addActionListener(e -> {
            int selectedRow = studentTable.getSelectedRow();
            if (selectedRow >= 0) {
                int modelRow = studentTable.convertRowIndexToModel(selectedRow);
                Student student = studentTableModel.getStudent(modelRow);
                if (student != null) {
                    navigateToStudentPayments(student);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a student first.", "No Student Selected", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        btnWrapper.add(viewPaymentsButton);

        panel.add(btnWrapper);

        return panel;
    }

    private JLabel createDetailField(JPanel parent, String label, String value) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.setOpaque(false);
        container.setMinimumSize(new Dimension(50, 36));
        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelComp.getFont().deriveFont(Font.PLAIN, 11f));
        labelComp.setForeground(ThemeUtils.TEXT_SECONDARY);
        labelComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(valueComp.getFont().deriveFont(Font.PLAIN, 13f));
        valueComp.setForeground(ThemeUtils.TEXT_PRIMARY);
        valueComp.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueComp.setMinimumSize(new Dimension(30, 16));

        container.add(labelComp);
        container.add(Box.createVerticalStrut(2));
        container.add(valueComp);

        parent.add(container);
        return valueComp;
    }

    private void showStudentDetails(int modelRow) {
        Student student = studentTableModel.getStudent(modelRow);

        if (avatarPanel != null) {
            avatarPanel.setStudent(student);
        }

        detailStudentCode.setText(student.getStudentCode());
        detailName.setText(student.getName());
        detailName.setToolTipText(student.getName());
        detailProgram.setText(student.getFormattedProgramName());
        detailProgram.setToolTipText(student.getFormattedProgramName());
        detailYearLevel.setText(student.getFormattedYearLevel());
        detailPaymentCount.setText(String.valueOf(student.getPaymentCount()));
        detailTotalAmount.setText(String.format("₱%,.2f", student.getTotalAmount()));
    }

    private void clearDetails() {
        if (avatarPanel != null) {
            avatarPanel.setStudent(null);
        }
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