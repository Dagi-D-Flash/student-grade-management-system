package util;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class ThemeManager {

    private static final Preferences PREFS    = Preferences.userNodeForPackage(ThemeManager.class);
    private static final String      PREF_KEY = "darkMode";
    private static boolean           darkMode = PREFS.getBoolean(PREF_KEY, true);

    private static final List<Runnable> themeListeners = new ArrayList<>();

    // ── DARK palette ──────────────────────────────────────────────────────────
    private static final Color DK_BG          = new Color(0x0f172a);
    private static final Color DK_SURFACE     = new Color(0x111827);
    private static final Color DK_ELEVATED    = new Color(0x1f2937);
    private static final Color DK_BORDER      = new Color(0x374151);
    private static final Color DK_TEXT        = new Color(0xe5e7eb);
    private static final Color DK_MUTED       = new Color(0x9ca3af);
    private static final Color DK_ACCENT      = new Color(0x10a37f);
    private static final Color DK_ACCENT_H    = new Color(0x0e8e6d);
    private static final Color DK_DANGER      = new Color(0xef4444);
    private static final Color DK_SIDEBAR     = new Color(0x1a2235);
    private static final Color DK_SIDEBAR_HOV = new Color(0x243047);

    // ── LIGHT palette ─────────────────────────────────────────────────────────
    private static final Color LT_BG          = new Color(0xffffff);
    private static final Color LT_SURFACE     = new Color(0xf9fafb);
    private static final Color LT_ELEVATED    = new Color(0xf3f4f6);
    private static final Color LT_BORDER      = new Color(0xe5e7eb);
    private static final Color LT_TEXT        = new Color(0x111827);
    private static final Color LT_MUTED       = new Color(0x6b7280);
    private static final Color LT_ACCENT      = new Color(0x10a37f);
    private static final Color LT_ACCENT_H    = new Color(0x0e8e6d);
    private static final Color LT_DANGER      = new Color(0xdc2626);
    private static final Color LT_SIDEBAR     = new Color(0xe8edf5);
    private static final Color LT_SIDEBAR_HOV = new Color(0xd5dce8);

    // ── Semantic colors ───────────────────────────────────────────────────────
    public static final Color SUCCESS = new Color(0x10a37f);
    public static final Color WARNING = new Color(0xf59e0b);
    public static final Color DANGER  = new Color(0xef4444);
    public static final Color INFO    = new Color(0x3b82f6);
    public static final Color GOLD    = new Color(0xf59e0b);
    public static final Color SILVER  = new Color(0x9ca3af);
    public static final Color BRONZE  = new Color(0xb45309);

    // ── Both palettes as arrays for fast "is this one of our colors?" lookup ──
    private static final Color[] ALL_BG       = {DK_BG,       LT_BG};
    private static final Color[] ALL_SURFACE  = {DK_SURFACE,  LT_SURFACE};
    private static final Color[] ALL_ELEVATED = {DK_ELEVATED, LT_ELEVATED};
    private static final Color[] ALL_TEXT     = {DK_TEXT,     LT_TEXT};
    private static final Color[] ALL_MUTED    = {DK_MUTED,    LT_MUTED};
    private static final Color[] ALL_DANGER   = {DK_DANGER,   LT_DANGER};
    private static final Color[] ALL_SIDEBAR  = {DK_SIDEBAR,  LT_SIDEBAR};
    private static final Color[] ALL_SIDEBAR_HOV = {DK_SIDEBAR_HOV, LT_SIDEBAR_HOV};
    private static final Color[] ALL_SIDEBAR_TEXT = {new Color(0x94a3b8), new Color(0x475569)};

    // ── Grade badge colors ────────────────────────────────────────────────────
    public static Color gradeABg() { return darkMode ? new Color(0x064e3b) : new Color(0xd1fae5); }
    public static Color gradeAFg() { return darkMode ? new Color(0x6ee7b7) : new Color(0x065f46); }
    public static Color gradeBBg() { return darkMode ? new Color(0x1e3a5f) : new Color(0xdbeafe); }
    public static Color gradeBFg() { return darkMode ? new Color(0x93c5fd) : new Color(0x1e40af); }
    public static Color gradeCBg() { return darkMode ? new Color(0x451a03) : new Color(0xfef3c7); }
    public static Color gradeCFg() { return darkMode ? new Color(0xfcd34d) : new Color(0x92400e); }
    public static Color gradeDBg() { return darkMode ? new Color(0x431407) : new Color(0xffedd5); }
    public static Color gradeDFg() { return darkMode ? new Color(0xfb923c) : new Color(0x9a3412); }
    public static Color gradeFBg() { return darkMode ? new Color(0x450a0a) : new Color(0xfee2e2); }
    public static Color gradeFFg() { return darkMode ? new Color(0xfca5a5) : new Color(0x991b1b); }

    // ── Dynamic accessors ─────────────────────────────────────────────────────
    public static Color bg()          { return darkMode ? DK_BG          : LT_BG;          }
    public static Color surface()     { return darkMode ? DK_SURFACE      : LT_SURFACE;     }
    public static Color elevated()    { return darkMode ? DK_ELEVATED     : LT_ELEVATED;    }
    public static Color border()      { return darkMode ? DK_BORDER       : LT_BORDER;      }
    public static Color text()        { return darkMode ? DK_TEXT         : LT_TEXT;        }
    public static Color muted()       { return darkMode ? DK_MUTED        : LT_MUTED;       }
    public static Color accent()      { return darkMode ? DK_ACCENT       : LT_ACCENT;      }
    public static Color accentH()     { return darkMode ? DK_ACCENT_H     : LT_ACCENT_H;    }
    public static Color danger()      { return darkMode ? DK_DANGER       : LT_DANGER;      }
    public static Color sidebarBg()   { return darkMode ? DK_SIDEBAR      : LT_SIDEBAR;     }
    public static Color sidebarHov()  { return darkMode ? DK_SIDEBAR_HOV  : LT_SIDEBAR_HOV; }
    public static Color sidebarText() { return darkMode ? new Color(0x94a3b8) : new Color(0x475569); }
    public static Color sidebarActiveText() { return Color.WHITE; }

    public static Color chartPlotBg() {
        return darkMode ? new Color(0x1f2937) : new Color(0xeef2ff);
    }

    public static Color gpaColor(double gpa) {
        if (gpa >= 3.5) return SUCCESS;
        if (gpa >= 3.0) return INFO;
        if (gpa >= 2.0) return WARNING;
        return DANGER;
    }

    // ── Borders ───────────────────────────────────────────────────────────────
    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border(), 1),
            BorderFactory.createEmptyBorder(14, 16, 14, 16));
    }

    public static Border accentBorder(Color color) {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color, 2),
            BorderFactory.createEmptyBorder(14, 16, 14, 16));
    }

    public static Border panelBorder() {
        return BorderFactory.createEmptyBorder(14, 14, 14, 14);
    }

    // ── Fonts ─────────────────────────────────────────────────────────────────
    public static Font fontBody()   { return new Font("SansSerif", Font.PLAIN,  13); }
    public static Font fontSmall()  { return new Font("SansSerif", Font.PLAIN,  11); }
    public static Font fontBold()   { return new Font("SansSerif", Font.BOLD,   13); }
    public static Font fontTitle()  { return new Font("SansSerif", Font.BOLD,   17); }
    public static Font fontHeader() { return new Font("SansSerif", Font.BOLD,   15); }
    public static Font fontMono()   { return new Font("Monospaced", Font.PLAIN, 12); }

    // ── Button factories ──────────────────────────────────────────────────────
    public static JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(accent());
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setFont(fontBold());
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
        b.putClientProperty("theme-role", "primary");
        return b;
    }

    public static JButton dangerButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(danger());
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setFont(fontBold());
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
        b.putClientProperty("theme-role", "danger");
        return b;
    }

    public static JButton secondaryButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(elevated());
        b.setForeground(muted());
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setFont(fontBold());
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
        b.putClientProperty("theme-role", "secondary");
        return b;
    }

    public static void styleTable(JTable table) {
        table.setBackground(surface());
        table.setForeground(text());
        table.setGridColor(border());
        table.setSelectionBackground(elevated());
        table.setSelectionForeground(accent());
        table.setRowHeight(32);
        table.setFont(fontBody());
        table.getTableHeader().setBackground(elevated());
        table.getTableHeader().setForeground(muted());
        table.getTableHeader().setFont(fontBold());
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.putClientProperty("theme-role", "table");
    }

    // ── Listener system ───────────────────────────────────────────────────────
    public static void addThemeListener(Runnable listener) {
        themeListeners.add(listener);
    }

    public static void removeThemeListener(Runnable listener) {
        themeListeners.remove(listener);
    }

    // ── Startup ───────────────────────────────────────────────────────────────
    public static void applyStartupTheme() {
        try {
            if (darkMode) FlatDarkLaf.setup();
            else          FlatLightLaf.setup();
            applyUIDefaults();
        } catch (Exception e) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
        }
    }

    // ── Toggle ────────────────────────────────────────────────────────────────
    public static void toggleTheme() {
        darkMode = !darkMode;
        PREFS.putBoolean(PREF_KEY, darkMode);
        try {
            // Switch FlatLaf L&F
            if (darkMode) FlatDarkLaf.setup();
            else          FlatLightLaf.setup();
            applyUIDefaults();

            // Update every open window
            for (Window w : Window.getWindows()) {
                if (!w.isDisplayable()) continue;
                // updateComponentTreeUI handles FlatLaf-managed components (borders, scrollbars, etc.)
                SwingUtilities.updateComponentTreeUI(w);
                // Then re-apply our custom colors on top — covers everything we set manually
                reapplyAll(w);
                w.revalidate();
                w.repaint();
            }

            // Notify sidebar/topbar listeners
            for (Runnable r : new ArrayList<>(themeListeners)) r.run();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Theme switch failed: " + e.getMessage(),
                "Theme Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static boolean isDarkMode() { return darkMode; }

    // ── Toggle button ─────────────────────────────────────────────────────────
    public static JToggleButton createToggleButton() {
        JToggleButton btn = new JToggleButton(darkMode ? "\u2600  Light" : "\uD83C\uDF19  Dark");
        btn.setSelected(darkMode);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(fontSmall());
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.putClientProperty("theme-role", "toggle");
        btn.addActionListener(e -> toggleTheme());
        return btn;
    }

    // ── Full recursive re-apply ───────────────────────────────────────────────
    /**
     * Walks every component in the container and re-applies our custom colors
     * by recognising which palette color was previously set.
     * This works without any tagging — it just checks if the current color
     * matches any known palette entry and swaps it to the new palette.
     */
    private static void reapplyAll(Container container) {
        for (Component c : container.getComponents()) {

            if (c instanceof JToggleButton) {
                JToggleButton tb = (JToggleButton) c;
                if ("toggle".equals(tb.getClientProperty("theme-role"))) {
                    tb.setText(darkMode ? "\u2600  Light" : "\uD83C\uDF19  Dark");
                    tb.setSelected(darkMode);
                }

            } else if (c instanceof JButton) {
                JButton btn = (JButton) c;
                String role = (String) btn.getClientProperty("theme-role");
                if ("primary".equals(role)) {
                    btn.setBackground(accent()); btn.setForeground(Color.WHITE);
                } else if ("danger".equals(role)) {
                    btn.setBackground(danger()); btn.setForeground(Color.WHITE);
                } else if ("secondary".equals(role)) {
                    btn.setBackground(elevated()); btn.setForeground(muted());
                } else {
                    // Untagged button — remap by current color
                    remapBg(btn);
                    remapFg(btn);
                }

            } else if (c instanceof JTable) {
                JTable t = (JTable) c;
                if ("table".equals(t.getClientProperty("theme-role"))) {
                    styleTable(t);
                } else {
                    remapBg(t); remapFg(t);
                }

            } else if (c instanceof JTextField || c instanceof JPasswordField) {
                JTextComponent tf = (JTextComponent) c;
                if ("field".equals(tf.getClientProperty("theme-role"))) {
                    tf.setBackground(elevated());
                    tf.setForeground(text());
                    tf.setCaretColor(text());
                } else {
                    remapBg(tf); remapFg(tf);
                }

            } else if (c instanceof JTextArea) {
                JTextArea ta = (JTextArea) c;
                if (!"log".equals(ta.getClientProperty("theme-role"))) {
                    remapBg(ta); remapFg(ta);
                }

            } else if (c instanceof JLabel) {
                remapFg((JComponent) c);

            } else if (c instanceof JPanel) {
                remapBg((JComponent) c);

            } else if (c instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane) c;
                remapBg(sp);
                sp.getViewport().setBackground(mapBg(sp.getViewport().getBackground()));

            } else if (c instanceof JSplitPane) {
                remapBg((JComponent) c);
            }

            // Recurse
            if (c instanceof Container) reapplyAll((Container) c);
        }
    }

    /** Re-map a component's background if it matches any known palette color. */
    private static void remapBg(JComponent c) {
        Color cur = c.getBackground();
        if (cur == null) return;
        Color mapped = mapBg(cur);
        if (mapped != null) c.setBackground(mapped);
    }

    /** Re-map a component's foreground if it matches any known palette color. */
    private static void remapFg(JComponent c) {
        Color cur = c.getForeground();
        if (cur == null) return;
        Color mapped = mapFg(cur);
        if (mapped != null) c.setForeground(mapped);
    }

    /**
     * Given a color that was set from the OLD palette, return the equivalent
     * color from the NEW palette. Returns null if not a palette color.
     */
    private static Color mapBg(Color c) {
        if (matches(c, ALL_BG))          return bg();
        if (matches(c, ALL_SURFACE))     return surface();
        if (matches(c, ALL_ELEVATED))    return elevated();
        if (matches(c, ALL_SIDEBAR))     return sidebarBg();
        if (matches(c, ALL_SIDEBAR_HOV)) return sidebarHov();
        return null;
    }

    private static Color mapFg(Color c) {
        if (matches(c, ALL_TEXT))         return text();
        if (matches(c, ALL_MUTED))        return muted();
        if (matches(c, ALL_DANGER))       return danger();
        if (matches(c, ALL_SIDEBAR_TEXT)) return sidebarText();
        return null;
    }

    /** True if color c matches any color in the array (by RGB value). */
    private static boolean matches(Color c, Color[] palette) {
        if (c == null) return false;
        int rgb = c.getRGB();
        for (Color p : palette) if (p.getRGB() == rgb) return true;
        return false;
    }

    // ── UI defaults ───────────────────────────────────────────────────────────
    private static void applyUIDefaults() {
        UIManager.put("Button.arc",                   10);
        UIManager.put("Component.arc",                8);
        UIManager.put("TextComponent.arc",            8);
        UIManager.put("ScrollBar.width",              8);
        UIManager.put("ScrollBar.thumbArc",           999);
        UIManager.put("ScrollBar.trackArc",           999);
        UIManager.put("Table.rowHeight",              32);
        UIManager.put("Table.showHorizontalLines",    Boolean.TRUE);
        UIManager.put("Table.showVerticalLines",      Boolean.FALSE);
        UIManager.put("Table.intercellSpacing",       new Dimension(0, 1));
        UIManager.put("SplitPane.dividerSize",        5);
        UIManager.put("TabbedPane.showTabSeparators", Boolean.TRUE);
        UIManager.put("TitledBorder.titleFont",       fontBold());
        UIManager.put("PasswordField.showRevealButton", Boolean.TRUE);
        UIManager.put("Component.focusWidth",         2);
        UIManager.put("Button.minimumWidth",          80);
    }
}
