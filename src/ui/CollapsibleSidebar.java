package ui;

import util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Collapsible sidebar with smooth cubic ease-in-out animation.
 * Expanded = 240px: icon + label visible.
 * Collapsed = 56px: icon only, label hidden, tooltip shown.
 */
public class CollapsibleSidebar extends JPanel {

    public static final int EXPANDED_W = 240;
    public static final int COLLAPSED_W = 56;
    private static final int ANIM_STEPS = 10;
    private static final int ANIM_DELAY = 8;

    private boolean expanded = true;
    private Timer animTimer;

    // nav data
    private final String[][] navItems;

    // nav state
    private final List<JButton> navButtons = new ArrayList<>();
    private final List<JLabel> navTextLabels = new ArrayList<>();
    private JPanel activeRow = null;
    private JLabel activeIconLbl = null;
    private JLabel activeTextLbl = null;

    // top-section refs (needed for applyTheme)
    private JPanel hamRow;
    private JPanel brandRow;
    private JPanel brandTextPanel;
    private JLabel brandIconLbl;
    private JLabel brandNameLbl;
    private JLabel brandRoleLbl;
    private JButton hamburgerBtn;
    private JPanel navPanel;
    private JScrollPane navScroll;

    private String brandIcon; // stored to regenerate tinted icon on theme change
    private Runnable onToggle;

    public CollapsibleSidebar(String appIcon, String appTitle, String appSubtitle,
            String[][] navItems) {
        this.navItems = navItems;
        this.brandIcon = appIcon;

        setLayout(new BorderLayout());
        setBackground(ThemeManager.sidebarBg());
        setPreferredSize(new Dimension(EXPANDED_W, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, ThemeManager.border()));

        add(buildTop(appIcon, appTitle, appSubtitle), BorderLayout.NORTH);
        navScroll = buildNav();
        add(navScroll, BorderLayout.CENTER);
    }

    public void setOnToggle(Runnable r) {
        this.onToggle = r;
    }

    // ── Top section ───────────────────────────────────────────────────────────

    private JPanel buildTop(String appIcon, String appTitle, String appSubtitle) {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(ThemeManager.sidebarBg());

        // Hamburger row
        hamRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        hamRow.setBackground(ThemeManager.sidebarBg());

        hamburgerBtn = new JButton("\u2630");
        hamburgerBtn.setFont(new Font("SansSerif", Font.PLAIN, 17));
        hamburgerBtn.setForeground(ThemeManager.sidebarText());
        hamburgerBtn.setBackground(ThemeManager.sidebarBg());
        hamburgerBtn.setBorderPainted(false);
        hamburgerBtn.setFocusPainted(false);
        hamburgerBtn.setOpaque(true);
        hamburgerBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hamburgerBtn.setPreferredSize(new Dimension(36, 36));
        hamburgerBtn.setToolTipText("Toggle sidebar");
        hamburgerBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                hamburgerBtn.setBackground(ThemeManager.sidebarHov());
            }

            public void mouseExited(MouseEvent e) {
                hamburgerBtn.setBackground(ThemeManager.sidebarBg());
            }
        });
        hamburgerBtn.addActionListener(e -> toggle());
        hamRow.add(hamburgerBtn);

        // Brand row
        brandRow = new JPanel(new BorderLayout(10, 0));
        brandRow.setBackground(ThemeManager.sidebarBg());
        brandRow.setBorder(new EmptyBorder(4, 10, 12, 10));

        brandIconLbl = new JLabel(emojiIcon(appIcon, 28), SwingConstants.CENTER);
        brandIconLbl.setPreferredSize(new Dimension(36, 36));

        brandTextPanel = new JPanel(new GridLayout(2, 1, 0, 1));
        brandTextPanel.setOpaque(false);

        brandNameLbl = new JLabel(appTitle);
        brandNameLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        brandNameLbl.setForeground(ThemeManager.text());

        brandRoleLbl = new JLabel(appSubtitle);
        brandRoleLbl.setFont(ThemeManager.fontSmall());
        brandRoleLbl.setForeground(ThemeManager.muted());

        brandTextPanel.add(brandNameLbl);
        brandTextPanel.add(brandRoleLbl);

        brandRow.add(brandIconLbl, BorderLayout.WEST);
        brandRow.add(brandTextPanel, BorderLayout.CENTER);

        JSeparator sep = new JSeparator();
        sep.setForeground(ThemeManager.border());
        sep.setBackground(ThemeManager.border());

        top.add(hamRow, BorderLayout.NORTH);
        top.add(brandRow, BorderLayout.CENTER);
        top.add(sep, BorderLayout.SOUTH);
        return top;
    }

    // ── Nav section ───────────────────────────────────────────────────────────

    private JScrollPane buildNav() {
        navPanel = new JPanel();
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.Y_AXIS));
        navPanel.setBackground(ThemeManager.sidebarBg());
        navPanel.setBorder(new EmptyBorder(6, 0, 6, 0));

        for (int i = 0; i < navItems.length; i++) {
            String icon = navItems[i][0];
            String label = navItems[i][1];

            JPanel row = new JPanel(new BorderLayout());
            row.setBackground(ThemeManager.sidebarBg());
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.setToolTipText(label);

            JLabel iconLbl = new JLabel(icon, SwingConstants.CENTER);
            iconLbl.setFont(new Font("SansSerif", Font.PLAIN, 18));
            iconLbl.setForeground(ThemeManager.sidebarText());
            iconLbl.setPreferredSize(new Dimension(COLLAPSED_W, 46));
            iconLbl.setHorizontalAlignment(SwingConstants.CENTER);
            iconLbl.setVerticalAlignment(SwingConstants.CENTER);

            JLabel textLbl = new JLabel(label);
            textLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
            textLbl.setForeground(ThemeManager.sidebarText());
            textLbl.setBorder(new EmptyBorder(0, 2, 0, 12));

            row.add(iconLbl, BorderLayout.WEST);
            row.add(textLbl, BorderLayout.CENTER);

            // Invisible action button (never added to UI, just holds the listener)
            JButton btn = new JButton();
            navButtons.add(btn);
            navTextLabels.add(textLbl);

            final JPanel fRow = row;
            final JLabel fIcon = iconLbl;
            final JLabel fText = textLbl;
            final JButton fBtn = btn;

            row.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    if (fRow != activeRow)
                        fRow.setBackground(ThemeManager.sidebarHov());
                }

                public void mouseExited(MouseEvent e) {
                    if (fRow != activeRow)
                        fRow.setBackground(ThemeManager.sidebarBg());
                }

                public void mouseClicked(MouseEvent e) {
                    setActiveRow(fRow, fIcon, fText, fBtn);
                    java.awt.event.ActionListener[] als = fBtn.getActionListeners();
                    if (als.length > 0)
                        als[0].actionPerformed(
                                new java.awt.event.ActionEvent(fBtn, 0, "nav"));
                }
            });

            navPanel.add(row);
            navPanel.add(Box.createVerticalStrut(2));

            // First item is active by default
            if (i == 0)
                setActiveRow(row, iconLbl, textLbl, btn);
        }

        JScrollPane scroll = new JScrollPane(navPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ThemeManager.sidebarBg());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(ThemeManager.sidebarBg());
        return scroll;
    }

    // ── Active state ──────────────────────────────────────────────────────────

    private void setActiveRow(JPanel row, JLabel iconLbl, JLabel textLbl, JButton btn) {
        if (activeRow != null) {
            activeRow.setBackground(ThemeManager.sidebarBg());
            if (activeIconLbl != null)
                activeIconLbl.setForeground(ThemeManager.sidebarText());
            if (activeTextLbl != null)
                activeTextLbl.setForeground(ThemeManager.sidebarText());
        }
        activeRow = row;
        activeIconLbl = iconLbl;
        activeTextLbl = textLbl;

        row.setBackground(ThemeManager.accent());
        iconLbl.setForeground(Color.WHITE);
        textLbl.setForeground(Color.WHITE);
    }

    /** Register a nav action by index (0-based). */
    public void setNavAction(int index, Runnable action) {
        if (index >= 0 && index < navButtons.size()) {
            JButton btn = navButtons.get(index);
            for (java.awt.event.ActionListener al : btn.getActionListeners())
                btn.removeActionListener(al);
            btn.addActionListener(e -> action.run());
        }
    }

    // ── Toggle / Animation ────────────────────────────────────────────────────

    public void toggle() {
        if (animTimer != null && animTimer.isRunning())
            animTimer.stop();

        expanded = !expanded;
        int startW = getPreferredSize().width;
        int endW = expanded ? EXPANDED_W : COLLAPSED_W;
        int[] step = { 0 };

        // Hide text immediately when collapsing so icons don't get clipped
        if (!expanded) {
            brandTextPanel.setVisible(false);
            for (JLabel lbl : navTextLabels)
                lbl.setVisible(false);
        }

        animTimer = new Timer(ANIM_DELAY, null);
        animTimer.addActionListener(e -> {
            step[0]++;
            double t = (double) step[0] / ANIM_STEPS;
            double ease = easeInOut(t);
            int w = startW + (int) ((endW - startW) * ease);

            setPreferredSize(new Dimension(w, 0));
            revalidate();
            Container parent = getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }

            if (step[0] >= ANIM_STEPS) {
                animTimer.stop();
                setPreferredSize(new Dimension(endW, 0));
                // Show text when fully expanded
                if (expanded) {
                    brandTextPanel.setVisible(true);
                    for (JLabel lbl : navTextLabels)
                        lbl.setVisible(true);
                }
                revalidate();
                if (parent != null) {
                    parent.revalidate();
                    parent.repaint();
                }
                if (onToggle != null)
                    onToggle.run();
            }
        });
        animTimer.start();
    }

    public boolean isExpanded() {
        return expanded;
    }

    // ── Theme refresh ─────────────────────────────────────────────────────────

    public void applyTheme() {
        // Top section
        setBackground(ThemeManager.sidebarBg());
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, ThemeManager.border()));
        hamRow.setBackground(ThemeManager.sidebarBg());
        brandRow.setBackground(ThemeManager.sidebarBg());
        hamburgerBtn.setBackground(ThemeManager.sidebarBg());
        hamburgerBtn.setForeground(ThemeManager.sidebarText());
        // Regenerate icon with correct tint for current theme
        brandIconLbl.setIcon(emojiIcon(brandIcon, 28, ThemeManager.isDarkMode()));
        brandNameLbl.setForeground(ThemeManager.text());
        brandRoleLbl.setForeground(ThemeManager.muted());

        // Nav section
        navPanel.setBackground(ThemeManager.sidebarBg());
        navScroll.getViewport().setBackground(ThemeManager.sidebarBg());
        navScroll.setBackground(ThemeManager.sidebarBg());

        for (Component c : navPanel.getComponents()) {
            if (!(c instanceof JPanel))
                continue;
            JPanel row = (JPanel) c;
            boolean isActive = (row == activeRow);
            row.setBackground(isActive ? ThemeManager.accent() : ThemeManager.sidebarBg());
            for (Component child : row.getComponents()) {
                if (child instanceof JLabel) {
                    ((JLabel) child).setForeground(
                            isActive ? Color.WHITE : ThemeManager.sidebarText());
                }
            }
        }
        revalidate();
        repaint();
    }

    // ── Ease curve ────────────────────────────────────────────────────────────

    private double easeInOut(double t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    /**
     * Renders an emoji string (including ZWJ sequences) using Segoe UI Emoji,
     * converts to white (dark mode) or dark (light mode) monochrome icon.
     * Uses 4× supersampling for crisp rendering at any size.
     */
    private static ImageIcon emojiIcon(String emoji, int size) {
        return emojiIcon(emoji, size, ThemeManager.isDarkMode());
    }

    private static ImageIcon emojiIcon(String emoji, int size, boolean white) {
        int ss = 4; // supersampling factor
        int pad = size / 4;
        int dim = size + pad * 2;
        int bigDim = dim * ss;

        // Step 1: render at 4× size with Segoe UI Emoji
        java.awt.image.BufferedImage big = new java.awt.image.BufferedImage(bigDim, bigDim,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, size * ss));
        FontMetrics fm = g.getFontMetrics();
        int x = (bigDim - fm.stringWidth(emoji)) / 2;
        int y = (bigDim - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(emoji, x, y);
        g.dispose();

        // Step 2: scale down to target size with bicubic (anti-aliased downscale)
        java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(dim, dim,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D gs = scaled.createGraphics();
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        gs.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gs.drawImage(big, 0, 0, dim, dim, null);
        gs.dispose();

        // Step 3: tint — white pixels for dark mode, dark (#1a2235) pixels for light
        // mode
        int tintRGB = white ? 0xFFFFFF : 0x1a2235;
        java.awt.image.BufferedImage result = new java.awt.image.BufferedImage(dim, dim,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < dim; py++) {
            for (int px = 0; px < dim; px++) {
                int argb = scaled.getRGB(px, py);
                int alpha = (argb >> 24) & 0xff;
                int r = (argb >> 16) & 0xff;
                int gv = (argb >> 8) & 0xff;
                int b = (argb) & 0xff;
                int lum = (int) (0.299 * r + 0.587 * gv + 0.114 * b);
                int a = (int) (alpha * (lum / 255.0));
                result.setRGB(px, py, (a << 24) | tintRGB);
            }
        }
        return new ImageIcon(result);
    }
}
