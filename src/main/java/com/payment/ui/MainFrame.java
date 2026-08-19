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
        contentPanel.setBackground(Color.WHITE);
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
        sidebar.setBackground(new Color(248, 249, 250));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(220, 220, 220)));

        // Header with app title
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(0, 82, 136));
        headerPanel.setBorder(new EmptyBorder(20, 15, 20, 15));
        headerPanel.setPreferredSize(new Dimension(260, 100));

        JLabel appTitle = new JLabel("Payment Database");
        appTitle.setFont(appTitle.getFont().deriveFont(Font.BOLD, 20f));
        appTitle.setForeground(Color.WHITE);

        JLabel versionLabel = new JLabel("v2.0 SQLite Edition");
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 11f));
        versionLabel.setForeground(new Color(180, 200, 220));

        JPanel titleContainer = new JPanel();
        titleContainer.setLayout(new BoxLayout(titleContainer, BoxLayout.Y_AXIS));
        titleContainer.setOpaque(false);
        titleContainer.add(appTitle);
        titleContainer.add(Box.createVerticalStrut(2));
        titleContainer.add(versionLabel);

        headerPanel.add(titleContainer, BorderLayout.WEST);
        sidebar.add(headerPanel, BorderLayout.NORTH);

        // Navigation Tree
        rootNode = new DefaultMutableTreeNode("Navigation");
        addNavNode(NAV_DASHBOARD, "📊 Dashboard");
        addNavNode(NAV_STUDENTS, "👥 Students");
        addNavNode(NAV_PAYMENTS, "💳 Payments");
        addNavNode(NAV_IMPORTS, "📥 Imports");
        addNavNode(NAV_REPORTS, "📊 Reports");
        addNavNode(NAV_AUDIT_LOG, "📋 Audit Log");
        addNavNode(NAV_DATA_QUALITY, "🔍 Data Quality");
        addNavNode(NAV_SETTINGS, "⚙️ Settings");

        treeModel = new DefaultTreeModel(rootNode);
        navTree = new JTree(treeModel);
        navTree.setRootVisible(false);
        navTree.setShowsRootHandles(true);
        navTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        navTree.setBorder(null);
        navTree.setBackground(new Color(248, 249, 250));
        navTree.setFont(navTree.getFont().deriveFont(Font.PLAIN, 13f));
        navTree.setRowHeight(36);
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
        treeScrollPane.getViewport().setBackground(new Color(248, 249, 250));
        treeScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        sidebar.add(treeScrollPane, BorderLayout.CENTER);

        // Footer with quick stats
        JPanel footerPanel = new JPanel();
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));
        footerPanel.setBackground(new Color(248, 249, 250));
        footerPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        dbStatusLabel = new JLabel("Database: Connecting...");
        dbStatusLabel.setFont(dbStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        dbStatusLabel.setForeground(new Color(120, 120, 120));
        dbStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        footerPanel.add(dbStatusLabel);

        JLabel shortcutLabel = new JLabel("<html><small>Double-click rows for details • Right-click for actions</small></html>");
        shortcutLabel.setFont(shortcutLabel.getFont().deriveFont(Font.PLAIN, 10f));
        shortcutLabel.setForeground(new Color(150, 150, 150));
        shortcutLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        shortcutLabel.setBorder(new EmptyBorder(5, 0, 0, 0));
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

    private void showPanel(String panelName) {
        cardLayout.show(contentPanel, panelName);
        statusLabel.setText("Viewing: " + panelName);

        // Refresh panel-specific data when shown
        switch (panelName) {
            case NAV_DASHBOARD -> dashboardPanel.refreshData();
            case NAV_STUDENTS -> studentPanel.refreshData();
            case NAV_PAYMENTS -> paymentPanel.refreshData();
            case NAV_IMPORTS -> importPanel.refreshData();
            case NAV_REPORTS -> { /* reports refresh on generate */ }
            case NAV_AUDIT_LOG -> auditLogPanel.loadAuditLogs(200);
            case NAV_DATA_QUALITY -> dataQualityPanel.scanForIssues();
            case NAV_SETTINGS -> settingsPanel.refreshDatabaseInfo();
        }
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(new Color(245, 245, 245));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)),
            new EmptyBorder(4, 12, 4, 12)
        ));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(new Color(80, 80, 80));
        statusBar.add(statusLabel, BorderLayout.WEST);

        // Right side - time
        JLabel timeLabel = new JLabel(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.PLAIN, 11f));
        timeLabel.setForeground(new Color(120, 120, 120));
        statusBar.add(timeLabel, BorderLayout.EAST);

        return statusBar;
    }

    private void loadInitialData() {
        // Load database info in background
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    // Update sidebar DB status
                    File dbFile = new File("student_payment.db");
                    if (dbFile.exists()) {
                        dbStatusLabel.setText(String.format("Database: %s (%.1f MB)",
                            dbFile.getName(), dbFile.length() / (1024.0 * 1024.0)));
                    } else {
                        dbStatusLabel.setText("Database: Not initialized");
                    }
                } catch (Exception e) {
                    dbStatusLabel.setText("Database: Error");
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
        private static final Color SELECTED_BG = new Color(0, 102, 170);
        private static final Color SELECTED_FG = Color.WHITE;
        private static final Color DEFAULT_FG = new Color(60, 60, 60);
        private static final Color HOVER_BG = new Color(230, 240, 250);

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            setBorder(new EmptyBorder(8, 15, 8, 15));
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
                setBackground(new Color(248, 249, 250));
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
        // Set system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            // Improve rendering
            UIManager.put("Tree.rendererFillBackground", true);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Run on EDT
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}