package com.payment.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Centralized theme utilities for a futuristic, deep cyber-dark UI with gradients and neon accents.
 */
public class ThemeUtils {
    // Deep obsidian & space navy palette
    public static final Color BG_DEEPEST = new Color(8, 10, 15);       // #080A0F
    public static final Color BG_SURFACE = new Color(13, 17, 24);      // #0D1118
    public static final Color BG_CARD = new Color(18, 24, 38);         // #121826
    public static final Color BG_CARD_HOVER = new Color(26, 34, 52);   // #1A2234
    public static final Color BORDER_COLOR = new Color(30, 41, 59);    // #1E293B
    public static final Color BORDER_GLOW = new Color(56, 189, 248, 60);

    // Futuristic Neon Accents
    public static final Color NEON_CYAN = new Color(0, 240, 255);      // #00F0FF
    public static final Color NEON_PURPLE = new Color(168, 85, 247);   // #A855F7
    public static final Color NEON_GREEN = new Color(0, 255, 157);     // #00FF9D
    public static final Color NEON_AMBER = new Color(245, 158, 11);    // #F59E0B
    public static final Color NEON_ROSE = new Color(244, 63, 94);      // #F43F5E
    public static final Color NEON_BLUE = new Color(59, 130, 246);     // #3B82F6

    // Typography
    public static final Color TEXT_PRIMARY = new Color(241, 245, 249);  // #F1F5F9
    public static final Color TEXT_SECONDARY = new Color(148, 163, 184);// #94A3B8
    public static final Color TEXT_MUTED = new Color(100, 116, 139);    // #64748B

    /**
     * Creates a JPanel with a smooth vertical or diagonal gradient.
     */
    public static JPanel createGradientPanel(Color topColor, Color bottomColor) {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, topColor, 0, getHeight(), bottomColor);
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
                g2d.dispose();
            }
        };
    }

    /**
     * Creates a futuristic card panel with subtle top glow and border.
     */
    public static JPanel createFuturisticCard(Color accentGlow) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Background
                g2d.setColor(BG_CARD);
                g2d.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

                // Top Accent Line
                if (accentGlow != null) {
                    GradientPaint gp = new GradientPaint(0, 0, accentGlow, getWidth(), 0, new Color(accentGlow.getRed(), accentGlow.getGreen(), accentGlow.getBlue(), 30));
                    g2d.setPaint(gp);
                    g2d.fillRoundRect(0, 0, getWidth() - 1, 4, 12, 12);
                }

                // Border
                g2d.setColor(BORDER_COLOR);
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2d.dispose();
            }
        };
        card.setOpaque(false);
        return card;
    }

    /**
     * Styles a table for the cyber-dark theme with crisp readability.
     */
    public static void applyTableTheme(JTable table) {
        table.setBackground(BG_SURFACE);
        table.setForeground(TEXT_PRIMARY);
        table.setSelectionBackground(new Color(30, 45, 75));
        table.setSelectionForeground(NEON_CYAN);
        table.setGridColor(BORDER_COLOR);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setRowHeight(30);

        JTableHeader header = table.getTableHeader();
        header.setBackground(new Color(10, 14, 22));
        header.setForeground(NEON_CYAN);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
    }

    /**
     * Styles a JButton with a dark background and neon accent foreground.
     */
    public static void styleButton(JButton button, Color accentColor) {
        button.setBackground(new Color(25, 35, 55));
        button.setForeground(accentColor != null ? accentColor : TEXT_PRIMARY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
    }

    /**
     * Creates a dark-themed titled border with a neon accent title color.
     */
    public static javax.swing.border.TitledBorder styleTitledBorder(String title, Color accentColor) {
        javax.swing.border.TitledBorder border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1), title);
        border.setTitleColor(accentColor != null ? accentColor : NEON_CYAN);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 13f));
        return border;
    }

    /**
     * Sets dark background on multiple components at once.
     */
    public static void applyDarkBackground(Color bg, JComponent... components) {
        for (JComponent c : components) {
            c.setBackground(bg);
            c.setOpaque(true);
        }
    }
}
