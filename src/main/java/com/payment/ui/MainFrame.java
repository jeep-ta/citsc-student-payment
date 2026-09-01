package com.payment.ui;

import com.payment.database.DatabaseManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Main Application Frame with Sidebar Navigation.
 * Modern UI with tabbed sidebar and content panels.
 */
public class MainFrame extends JFrame {

    private final DatabaseManager db = DatabaseManager.getInstance();

    // Sidebar
    private JTree navTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;

    // Content area - CardLayout for switching panels
    private JPanel contentPanel;
    private CardLayout cardLayout;

    // Panels
    private DashboardPanel dashboardPanel;
    private StudentPanel studentPanel;
    private PaymentPanel paymentPanel;
    private ImportPanel importPanel;
    private ReportsPanel reportsPanel;
    private AuditLogPanel auditLogPanel;
    private DataQualityPanel dataQualityPanel;
    private SettingsPanel settingsPanel;

    // Status bar
    private JLabel statusLabel;
    private JLabel dbStatusLabel;

    // Navigation constants
    private static final String NAV_DASHBOARD = "Dashboard";
    private static final String NAV_STUDENTS = "Students";
    private static final String NAV_PAYMENTS = "Payments";
    private static final String NAV_IMPORTS = "Imports";
    private static final String NAV_REPORTS = "Reports";
    private static final String NAV_AUDIT_LOG = "Audit Log";
    private static final String NAV_DATA_QUALITY = "Data Quality";
    private static final String NAV_SETTINGS = "Settings";

    public MainFrame() {
        setTitle("Student Payment Database v2.0");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1400, 900);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(1000, 700));

        // Window listener for graceful shutdown
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveAndExit();
            }
        });

        initializeUI();
        loadInitialData();
    }

    private void initializeUI() {
        // Main layout: Sidebar (West) + Content (Center)
        setLayout(new BorderLayout(0, 0));

        // Sidebar
        JPanel sidebar = createSidebar();
        add(sidebar, BorderLayout.WEST);

        // Content area with CardLayout
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(ThemeUtils.BG_DEEPEST);
        add(contentPanel, BorderLayout.CENTER);

        // Status bar (must be created before initPanels because showPanel uses statusLabel)
        add(createStatusBar(), BorderLayout.SOUTH);

        // Initialize all panels
        initPanels();
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 0));
        sidebar.setPreferredSize(new Dimension(260, 0));
        sidebar.setMinimumSize(new Dimension(260, 0));
        sidebar.setMaximumSize(new Dimension(260, Integer.MAX_VALUE));
        sidebar.setBackground(ThemeUtils.BG_SURFACE);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, ThemeUtils.BORDER_COLOR));

        // Header with app title and futuristic gradient
        JPanel headerPanel = ThemeUtils.createGradientPanel(new Color(15, 23, 42), new Color(10, 14, 26));
        headerPanel.setLayout(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, ThemeUtils.NEON_CYAN),
            new EmptyBorder(18, 16, 18, 16)
        ));
        headerPanel.setPreferredSize(new Dimension(260, 95));

        JLabel appTitle = new JLabel("⚡ CITSC PAYMENT");
        appTitle.setFont(appTitle.getFont().deriveFont(Font.BOLD, 18f));
        appTitle.setForeground(ThemeUtils.NEON_CYAN);

        JLabel versionLabel = new JLabel("CYBER-DARK EDITION • SQLITE");
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 10f));
        versionLabel.setForeground(ThemeUtils.NEON_PURPLE);

        JPanel titleContainer = new JPanel();
        titleContainer.setLayout(new BoxLayout(titleContainer, BoxLayout.Y_AXIS));
        titleContainer.setOpaque(false);
        titleContainer.add(appTitle);
        titleContainer.add(Box.createVerticalStrut(3));
        titleContainer.add(versionLabel);

        headerPanel.add(titleContainer, BorderLayout.WEST);
        sidebar.add(headerPanel, BorderLayout.NORTH);

        // Navigation Tree
        rootNode = new DefaultMutableTreeNode("Navigation");
        addNavNode(NAV_DASHBOARD, "📊 Dashboard");
        addNavNode(NAV_STUDENTS, "👥 Students");
        addNavNode(NAV_PAYMENTS, "💳 Payments");
        addNavNode(NAV_IMPORTS, "📥 Imports");
        addNavNode(NAV_REPORTS, "📈 Reports");
        addNavNode(NAV_AUDIT_LOG, "📋 Audit Log");
        addNavNode(NAV_DATA_QUALITY, "🔍 Data Quality");
        addNavNode(NAV_SETTINGS, "⚙️ Settings");

        treeModel = new DefaultTreeModel(rootNode);
        navTree = new JTree(treeModel);
        navTree.setRootVisible(false);
        navTree.setShowsRootHandles(true);
        navTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        navTree.setBorder(null);
        navTree.setBackground(ThemeUtils.BG_SURFACE);
        navTree.setFont(navTree.getFont().deriveFont(Font.PLAIN, 13f));
        navTree.setRowHeight(38);
        navTree.setCellRenderer(new NavTreeCellRenderer());
        navTree.setFocusable(false);

        // Expand all nodes
        for (int i = 0; i < navTree.getRowCount(); i++) {
            navTree.expandRow(i);
        }

        // Selection listener
        navTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) navTree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof NavItem) {
                NavItem item = (NavItem) node.getUserObject();
                showPanel(item.getPanelName());
            }
        });

        JScrollPane treeScrollPane = new JScrollPane(navTree);
        treeScrollPane.setBorder(null);
        treeScrollPane.getViewport().setBackground(ThemeUtils.BG_SURFACE);
        treeScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        sidebar.add(treeScrollPane, BorderLayout.CENTER);

        // Footer with quick stats
        JPanel footerPanel = new JPanel();
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));
        footerPanel.setBackground(ThemeUtils.BG_SURFACE);
        footerPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        dbStatusLabel = new JLabel("Database: Connecting...");
        dbStatusLabel.setFont(dbStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        dbStatusLabel.setForeground(ThemeUtils.TEXT_SECONDARY);
        dbStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        footerPanel.add(dbStatusLabel);

        JLabel shortcutLabel = new JLabel("<html><small style='color:#64748B;'>Double-click rows for details • Right-click for actions</small></html>");
        shortcutLabel.setFont(shortcutLabel.getFont().deriveFont(Font.PLAIN, 10f));
        shortcutLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        shortcutLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
        footerPanel.add(shortcutLabel);

        sidebar.add(footerPanel, BorderLayout.SOUTH);

        return sidebar;
    }

    private void addNavNode(String panelName, String displayName) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new NavItem(panelName, displayName));
        rootNode.add(node);
    }

    private void initPanels() {
        // Create all panels
        dashboardPanel = new DashboardPanel();
        studentPanel = new StudentPanel();
        paymentPanel = new PaymentPanel();
        importPanel = new ImportPanel();
        reportsPanel = new ReportsPanel();
        auditLogPanel = new AuditLogPanel();
        dataQualityPanel = new DataQualityPanel();
        settingsPanel = new SettingsPanel();

        // Add to card layout with panel names as keys
        contentPanel.add(dashboardPanel, NAV_DASHBOARD);
        contentPanel.add(studentPanel, NAV_STUDENTS);
        contentPanel.add(paymentPanel, NAV_PAYMENTS);
        contentPanel.add(importPanel, NAV_IMPORTS);
        contentPanel.add(reportsPanel, NAV_REPORTS);
        contentPanel.add(auditLogPanel, NAV_AUDIT_LOG);
        contentPanel.add(dataQualityPanel, NAV_DATA_QUALITY);
        contentPanel.add(settingsPanel, NAV_SETTINGS);

        // Show dashboard by default
        showPanel(NAV_DASHBOARD);

        // Select dashboard in tree
        DefaultMutableTreeNode dashboardNode = (DefaultMutableTreeNode) rootNode.getChildAt(0);
        navTree.setSelectionPath(new javax.swing.tree.TreePath(dashboardNode.getPath()));
    }

    public void navigateTo(String panelName) {
        showPanel(panelName);

        // Update tree selection
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            if (node.getUserObject() instanceof NavItem item && panelName.equals(item.getPanelName())) {
                navTree.setSelectionPath(new javax.swing.tree.TreePath(node.getPath()));
                break;
            }
        }
    }

    public void showPaymentsForStudent(String studentQuery) {
        navigateTo(NAV_PAYMENTS);
        if (paymentPanel != null) {
            paymentPanel.filterByStudent(studentQuery);
        }
    }

    public PaymentPanel getPaymentPanel() {
        return paymentPanel;
    }

    public StudentPanel getStudentPanel() {
        return studentPanel;
    }

    public void showPanel(String panelName) {
        cardLayout.show(contentPanel, panelName);
        statusLabel.setText("Viewing: " + panelName);

        // Refresh panel-specific data when shown
        switch (panelName) {
            case NAV_DASHBOARD -> dashboardPanel.refreshData();
            case NAV_STUDENTS -> studentPanel.refreshData();
            case NAV_PAYMENTS -> paymentPanel.refreshData();
            case NAV_IMPORTS -> importPanel.refreshData();
            case NAV_REPORTS -> reportsPanel.refreshData();
            case NAV_AUDIT_LOG -> auditLogPanel.loadAuditLogs(200);
            case NAV_DATA_QUALITY -> dataQualityPanel.scanForIssues();
            case NAV_SETTINGS -> settingsPanel.refreshDatabaseInfo();
        }
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(ThemeUtils.BG_SURFACE);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeUtils.BORDER_COLOR),
            new EmptyBorder(5, 16, 5, 16)
        ));

        statusLabel = new JLabel("System Online • Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(ThemeUtils.TEXT_PRIMARY);
        statusBar.add(statusLabel, BorderLayout.WEST);

        // Right side - time with glowing indicator
        JLabel timeLabel = new JLabel("🟢 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.PLAIN, 11f));
        timeLabel.setForeground(ThemeUtils.NEON_CYAN);
        statusBar.add(timeLabel, BorderLayout.EAST);

        return statusBar;
    }

    private void loadInitialData() {
        // Load database info and check auto-migration in background
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    if (db.isEmpty()) {
                        File jsonFile = new File("student_payment_data.json");
                        if (!jsonFile.exists()) {
                            jsonFile = new File("../student_payment_data.json");
                        }
                        if (jsonFile.exists()) {
                            System.out.println("Empty database detected. Migrating from " + jsonFile.getAbsolutePath());
                            com.payment.database.JsonMigration.migrate();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Auto-migration check failed: " + e.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    File dbFile = new File("student_payment.db");
                    if (dbFile.exists()) {
                        dbStatusLabel.setText(String.format("DB: %s (%.1f MB)",
                            dbFile.getName(), dbFile.length() / (1024.0 * 1024.0)));
                    } else {
                        dbStatusLabel.setText("DB: Not initialized");
                    }
                    if (dashboardPanel != null) {
                        dashboardPanel.refreshData();
                    }
                } catch (Exception e) {
                    dbStatusLabel.setText("DB Error");
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void saveAndExit() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to exit?",
            "Exit Application",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            dispose();
            System.exit(0);
        }
    }

    // --- Navigation Item Class ---
    private static class NavItem {
        private final String panelName;
        private final String displayName;

        NavItem(String panelName, String displayName) {
            this.panelName = panelName;
            this.displayName = displayName;
        }

        String getPanelName() { return panelName; }
        String getDisplayName() { return displayName; }

        @Override
        public String toString() { return displayName; }
    }

    // --- Custom Tree Cell Renderer ---
    private static class NavTreeCellRenderer extends DefaultTreeCellRenderer {
        private static final Color SELECTED_BG = new Color(22, 34, 56);
        private static final Color SELECTED_FG = ThemeUtils.NEON_CYAN;
        private static final Color DEFAULT_FG = ThemeUtils.TEXT_PRIMARY;

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            setBorder(new EmptyBorder(8, 16, 8, 16));
            setIconTextGap(12);
            setFont(getFont().deriveFont(Font.PLAIN, 13f));

            if (value instanceof NavItem) {
                NavItem item = (NavItem) value;
                setText(item.getDisplayName());
            }

            if (selected) {
                setBackground(SELECTED_BG);
                setForeground(SELECTED_FG);
            } else {
                setBackground(ThemeUtils.BG_SURFACE);
                setForeground(DEFAULT_FG);
            }

            // Remove default icon
            if (leaf) {
                setIcon(null);
                setOpenIcon(null);
                setClosedIcon(null);
            }

            setOpaque(true);
            return this;
        }
    }

    public static void main(String[] args) {
        // Set FlatDarkLaf theme with forced deep-dark palette
        try {
            com.formdev.flatlaf.FlatDarkLaf.setup();

            // Force deep obsidian background everywhere
            Color deepBg = ThemeUtils.BG_DEEPEST;
            Color surfaceBg = ThemeUtils.BG_SURFACE;
            Color cardBg = ThemeUtils.BG_CARD;
            Color borderCol = ThemeUtils.BORDER_COLOR;
            Color textPrimary = ThemeUtils.TEXT_PRIMARY;
            Color textSecondary = ThemeUtils.TEXT_SECONDARY;
            Color neonCyan = ThemeUtils.NEON_CYAN;

            // Global Panel/Component backgrounds
            UIManager.put("Panel.background", deepBg);
            UIManager.put("control", surfaceBg);
            UIManager.put("Panel.foreground", textPrimary);

            // Table
            UIManager.put("Table.background", surfaceBg);
            UIManager.put("Table.foreground", textPrimary);
            UIManager.put("Table.selectionBackground", new Color(30, 45, 75));
            UIManager.put("Table.selectionForeground", neonCyan);
            UIManager.put("Table.gridColor", borderCol);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
            UIManager.put("TableHeader.background", new Color(10, 14, 22));
            UIManager.put("TableHeader.foreground", neonCyan);

            // Tree
            UIManager.put("Tree.background", surfaceBg);
            UIManager.put("Tree.foreground", textPrimary);
            UIManager.put("Tree.selectionBackground", new Color(22, 34, 56));
            UIManager.put("Tree.selectionForeground", neonCyan);
            UIManager.put("Tree.rendererFillBackground", true);

            // ScrollPane / Viewport
            UIManager.put("ScrollPane.background", deepBg);
            UIManager.put("Viewport.background", surfaceBg);

            // TextField, TextArea, ComboBox
            UIManager.put("TextField.background", cardBg);
            UIManager.put("TextField.foreground", textPrimary);
            UIManager.put("TextArea.background", cardBg);
            UIManager.put("TextArea.foreground", textPrimary);
            UIManager.put("ComboBox.background", cardBg);
            UIManager.put("ComboBox.foreground", textPrimary);
            UIManager.put("Spinner.background", cardBg);

            // Buttons
            UIManager.put("Button.background", new Color(25, 35, 55));
            UIManager.put("Button.foreground", textPrimary);

            // Labels
            UIManager.put("Label.foreground", textPrimary);

            // Borders / separator
            UIManager.put("Separator.foreground", borderCol);
            UIManager.put("TitledBorder.titleColor", neonCyan);

            // SplitPane
            UIManager.put("SplitPane.background", deepBg);
            UIManager.put("SplitPaneDivider.draggingColor", borderCol);

            // OptionPane (dialogs)
            UIManager.put("OptionPane.background", surfaceBg);
            UIManager.put("OptionPane.messageForeground", textPrimary);

            // CheckBox
            UIManager.put("CheckBox.foreground", textPrimary);

            // PopupMenu / List (context menus, combo dropdowns)
            UIManager.put("PopupMenu.background", surfaceBg);
            UIManager.put("PopupMenu.foreground", textPrimary);
            UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(borderCol));
            UIManager.put("MenuItem.background", surfaceBg);
            UIManager.put("MenuItem.foreground", textPrimary);
            UIManager.put("MenuItem.selectionBackground", new Color(30, 45, 75));
            UIManager.put("MenuItem.selectionForeground", neonCyan);
            UIManager.put("List.background", surfaceBg);
            UIManager.put("List.foreground", textPrimary);
            UIManager.put("List.selectionBackground", new Color(30, 45, 75));
            UIManager.put("List.selectionForeground", neonCyan);

            // TitledBorder
            UIManager.put("TitledBorder.titleColor", neonCyan);
            UIManager.put("TitledBorder.border", BorderFactory.createLineBorder(borderCol));

            // Spinner
            UIManager.put("Spinner.foreground", textPrimary);

            // ScrollBar
            UIManager.put("ScrollBar.background", deepBg);
            UIManager.put("ScrollBar.thumb", new Color(40, 50, 70));
            UIManager.put("ScrollBar.track", surfaceBg);

            // ToolTip
            UIManager.put("ToolTip.background", cardBg);
            UIManager.put("ToolTip.foreground", textPrimary);
            UIManager.put("ToolTip.border", BorderFactory.createLineBorder(borderCol));

            // FileChooser
            UIManager.put("FileChooser.background", surfaceBg);
            UIManager.put("FileChooser.foreground", textPrimary);
            UIManager.put("FileView.directoryIcon", null);

            // ProgressBar
            UIManager.put("ProgressBar.background", cardBg);
            UIManager.put("ProgressBar.foreground", neonCyan);
            UIManager.put("ProgressBar.selectionBackground", neonCyan);
            UIManager.put("ProgressBar.selectionForeground", textPrimary);

            // Arcs
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            UIManager.put("TextComponent.arc", 8);
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // Run on EDT
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            frame.toFront();
            frame.requestFocus();
        });
    }
}