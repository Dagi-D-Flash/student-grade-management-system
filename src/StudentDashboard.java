import models.User;
import panels.ChartsPanel;
import panels.GPAPanel;
import panels.PerformanceAnalysisPanel;
import panels.SchedulePanel;
import panels.StudentCoursesPanel;
import panels.StudentEnrollmentPanel;
import panels.StudentGradePanel;
import panels.TranscriptPanel;
import ui.CollapsibleSidebar;
import util.DBConnection;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.Connection;

public class StudentDashboard extends JFrame {

    private JPanel            contentPanel;
    private CardLayout        cardLayout;
    private final User        currentUser;
    private CollapsibleSidebar sidebar;
    private JPanel            topBarPanel;
    private JLabel            pageLabel;
    private final Runnable    themeListener = this::applyTheme;

    // home card stat labels — updated on every Dashboard visit
    private JLabel valEnrolled, valGrades, valAvg;

    private static final String[][] NAV = {
        {"\uD83C\uDFE0", "Dashboard"},
        {"\uD83D\uDCDD", "My Grades"},
        {"\uD83D\uDCCA", "GPA & CGPA"},
        {"\uD83D\uDCC8", "Charts"},
        {"\uD83C\uDFAF", "Performance"},
        {"\uD83D\uDCCB", "Transcript"},
        {"\uD83D\uDCDA", "My Courses"},
        {"\uD83D\uDCDD", "Enrollment"},
        {"\uD83D\uDC64", "Profile"}
    };

    public StudentDashboard(User user) {
        this.currentUser = user;
        setTitle("Student Dashboard — GMS");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 780);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(ThemeManager.bg());

        sidebar = new CollapsibleSidebar(
            "\uD83E\uDDD1\u200D\uD83C\uDF93", "GMS Student", "Student Portal", NAV);

        String[] cards = {"Dashboard","My Grades","GPA & CGPA","Charts",
                          "Performance","Transcript","My Courses","Enrollment","Profile"};
        for (int i = 0; i < cards.length; i++) {
            final String card = cards[i];
            sidebar.setNavAction(i, () -> {
                if ("Dashboard".equals(card)) refreshHomeCard();
                cardLayout.show(contentPanel, card);
                pageLabel.setText(card);
            });
        }

        JPanel mainPanel = buildMain();
        add(sidebar,   BorderLayout.WEST);
        add(mainPanel, BorderLayout.CENTER);

        ThemeManager.addThemeListener(themeListener);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent e) {
                ThemeManager.removeThemeListener(themeListener);
            }
        });

        setVisible(true);
    }

    private void applyTheme() {
        sidebar.applyTheme();
        topBarPanel.setBackground(ThemeManager.surface());
        topBarPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.border()),
            new EmptyBorder(10, 20, 10, 16)));
        pageLabel.setForeground(ThemeManager.text());
        contentPanel.setBackground(ThemeManager.bg());
        revalidate(); repaint();
    }

    private JPanel buildMain() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(ThemeManager.bg());

        topBarPanel = buildTopBar();
        main.add(topBarPanel, BorderLayout.NORTH);

        cardLayout   = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(ThemeManager.bg());

        contentPanel.add(buildHomeCard(),                        "Dashboard");
        contentPanel.add(new StudentGradePanel(currentUser),    "My Grades");
        contentPanel.add(new GPAPanel(currentUser),             "GPA & CGPA");
        contentPanel.add(new ChartsPanel(currentUser),          "Charts");
        contentPanel.add(new PerformanceAnalysisPanel(currentUser), "Performance");
        contentPanel.add(new TranscriptPanel(currentUser),      "Transcript");
        contentPanel.add(new StudentCoursesPanel(currentUser),  "My Courses");
        contentPanel.add(new StudentEnrollmentPanel(currentUser),"Enrollment");
        contentPanel.add(new panels.StudentProfilePanel(currentUser), "Profile");

        main.add(contentPanel, BorderLayout.CENTER);
        return main;
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.border()),
            new EmptyBorder(10, 20, 10, 16)));

        pageLabel = new JLabel("Dashboard");
        pageLabel.setFont(ThemeManager.fontTitle());
        pageLabel.setForeground(ThemeManager.text());
        bar.add(pageLabel, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JLabel userLbl = new JLabel(currentUser.getUsername());
        userLbl.setFont(ThemeManager.fontSmall());
        userLbl.setForeground(ThemeManager.muted());

        JToggleButton themeToggle = ThemeManager.createToggleButton();

        JButton signOut = new JButton("Sign out");
        signOut.setFont(ThemeManager.fontSmall());
        signOut.setForeground(ThemeManager.danger());
        signOut.setBackground(ThemeManager.elevated());
        signOut.setBorderPainted(false);
        signOut.setFocusPainted(false);
        signOut.setOpaque(true);
        signOut.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        signOut.addActionListener(e -> {
            ThemeManager.removeThemeListener(themeListener);
            dispose();
            new LoginUI();
        });

        right.add(userLbl);
        right.add(themeToggle);
        right.add(signOut);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildHomeCard() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 24));
        wrapper.setBackground(ThemeManager.bg());
        wrapper.setBorder(new EmptyBorder(28, 28, 28, 28));

        JLabel heading = new JLabel("Welcome, " + currentUser.getUsername() + " \uD83D\uDC4B");
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(ThemeManager.text());
        wrapper.add(heading, BorderLayout.NORTH);

        valEnrolled = statValueLabel(ThemeManager.accent());
        valGrades   = statValueLabel(ThemeManager.INFO);
        valAvg      = statValueLabel(ThemeManager.WARNING);

        JPanel grid = new JPanel(new GridLayout(1, 3, 16, 16));
        grid.setOpaque(false);
        grid.add(statCard("Enrolled Courses", valEnrolled, "\uD83D\uDCDA"));
        grid.add(statCard("Grades Received",  valGrades,   "\uD83D\uDCDD"));
        grid.add(statCard("Average Score",    valAvg,      "\uD83C\uDFC6"));
        wrapper.add(grid, BorderLayout.CENTER);

        refreshHomeCard();
        return wrapper;
    }

    private void refreshHomeCard() {
        new SwingWorker<Object[], Void>() {
            protected Object[] doInBackground() throws Exception {
                int enrolled = 0, grades = 0;
                double avgScore = 0;
                try (Connection conn = DBConnection.getConnection()) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(DISTINCT e.id) FROM enrollments e JOIN students s ON s.id=e.student_id WHERE s.user_id=? AND e.status='active'")) {
                        ps.setInt(1, currentUser.getId());
                        try (java.sql.ResultSet rs = ps.executeQuery()) { if (rs.next()) enrolled = rs.getInt(1); }
                    }
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(g.id) FROM grades g JOIN enrollments e ON e.id=g.enrollment_id JOIN students s ON s.id=e.student_id WHERE s.user_id=?")) {
                        ps.setInt(1, currentUser.getId());
                        try (java.sql.ResultSet rs = ps.executeQuery()) { if (rs.next()) grades = rs.getInt(1); }
                    }
                    String avgSql =
                        "SELECT COALESCE(AVG(subject_avg),0) FROM (" +
                        "  SELECT SUM((g.score/cgc.max_score)*cgc.weight) AS subject_avg " +
                        "  FROM grades g " +
                        "  JOIN course_grade_components cgc ON cgc.id=g.component_id " +
                        "  JOIN enrollments e ON e.id=g.enrollment_id " +
                        "  JOIN students s ON s.id=e.student_id " +
                        "  WHERE s.user_id=? AND cgc.course_id=e.course_id " +
                        "  GROUP BY e.id" +
                        ") AS t";
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(avgSql)) {
                        ps.setInt(1, currentUser.getId());
                        try (java.sql.ResultSet rs = ps.executeQuery()) { if (rs.next()) avgScore = rs.getDouble(1); }
                    }
                }
                return new Object[]{enrolled, grades, avgScore};
            }
            protected void done() {
                try {
                    Object[] v = get();
                    if (valEnrolled != null) valEnrolled.setText(String.valueOf(v[0]));
                    if (valGrades   != null) valGrades.setText(String.valueOf(v[1]));
                    if (valAvg      != null) valAvg.setText(String.format("%.1f%%", (Double) v[2]));
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private JLabel statValueLabel(Color color) {
        JLabel l = new JLabel("…");
        l.setFont(new Font("SansSerif", Font.BOLD, 36));
        l.setForeground(color);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }

    private JPanel statCard(String title, JLabel valueLabel, String emoji) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(ThemeManager.surface());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.border()),
            new EmptyBorder(24, 24, 24, 24)));
        JLabel emojiLbl = new JLabel(emoji, SwingConstants.CENTER);
        emojiLbl.setFont(new Font("SansSerif", Font.PLAIN, 26));
        JLabel lbl = new JLabel(title, SwingConstants.CENTER);
        lbl.setFont(ThemeManager.fontSmall());
        lbl.setForeground(ThemeManager.muted());
        card.add(emojiLbl,   BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(lbl,        BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildProfileCard() {
        // ── fetch data ────────────────────────────────────────────────────────
        String email = "—", phone = "—", gender = "—", dob = "—",
               studentNo = "—", address = "—", firstName = "—", lastName = "—";
        double cgpa = -1;
        int    totalCredits = 0, totalCourses = 0;

        try (Connection conn = DBConnection.getConnection()) {
            // student info
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT s.student_no, s.first_name, s.last_name, s.gender, s.phone, " +
                    "       s.address, s.date_of_birth, u.email " +
                    "FROM students s JOIN users u ON u.id = s.user_id WHERE u.id = ?")) {
                ps.setInt(1, currentUser.getId());
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        studentNo = nvl(rs.getString("student_no"));
                        firstName = nvl(rs.getString("first_name"));
                        lastName  = nvl(rs.getString("last_name"));
                        gender    = nvl(rs.getString("gender"));
                        phone     = nvl(rs.getString("phone"));
                        address   = nvl(rs.getString("address"));
                        email     = nvl(rs.getString("email"));
                        dob       = rs.getDate("date_of_birth") != null
                            ? new java.text.SimpleDateFormat("MMM dd, yyyy")
                                  .format(rs.getDate("date_of_birth")) : "—";
                    }
                }
            }
            // stats
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(DISTINCT e.id) AS courses, " +
                    "       COALESCE(SUM(sub.credits),0) AS credits " +
                    "FROM students s " +
                    "JOIN enrollments e ON e.student_id = s.id " +
                    "JOIN courses c     ON c.id = e.course_id " +
                    "JOIN subjects sub  ON sub.id = c.subject_id " +
                    "WHERE s.user_id = ?")) {
                ps.setInt(1, currentUser.getId());
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalCourses = rs.getInt("courses");
                        totalCredits = rs.getInt("credits");
                    }
                }
            }
            // cgpa
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM students WHERE user_id = ?")) {
                ps.setInt(1, currentUser.getId());
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        try { cgpa = util.GPACalculator.getCGPA(rs.getInt("id")); }
                        catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}

        String fullName = (firstName + " " + lastName).trim();
        if (fullName.isEmpty() || fullName.equals(" ")) fullName = currentUser.getUsername();

        // ── root panel ────────────────────────────────────────────────────────
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.bg());
        root.setBorder(new EmptyBorder(24, 24, 24, 24));

        // ── hero banner (gradient + avatar) ───────────────────────────────────
        Color c1 = ThemeManager.accent();
        Color c2 = ThemeManager.accentH();
        JPanel hero = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, c1, getWidth(), getHeight(), c2);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
            }
        };
        hero.setOpaque(false);
        hero.setPreferredSize(new Dimension(0, 200));
        hero.setBorder(new EmptyBorder(28, 32, 28, 32));

        // avatar circle
        String initials = getInitials(firstName, lastName, currentUser.getUsername());
        JPanel avatarCircle = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // outer glow ring
                g2.setColor(new Color(255, 255, 255, 60));
                g2.fillOval(4, 4, getWidth() - 8, getHeight() - 8);
                // inner circle
                g2.setColor(new Color(0, 0, 0, 50));
                g2.fillOval(8, 8, getWidth() - 16, getHeight() - 16);
                // initials
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 36));
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(initials)) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(initials, x, y);
                g2.dispose();
            }
        };
        avatarCircle.setOpaque(false);
        avatarCircle.setPreferredSize(new Dimension(96, 96));

        // name + student no
        JPanel heroText = new JPanel();
        heroText.setLayout(new BoxLayout(heroText, BoxLayout.Y_AXIS));
        heroText.setOpaque(false);
        heroText.setBorder(new EmptyBorder(0, 20, 0, 0));

        JLabel nameLbl = new JLabel(fullName);
        nameLbl.setFont(new Font("SansSerif", Font.BOLD, 26));
        nameLbl.setForeground(Color.WHITE);

        JLabel noLbl = new JLabel(studentNo);
        noLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        noLbl.setForeground(new Color(255, 255, 255, 200));

        JLabel roleBadge = new JLabel("  \u2022  Student  ");
        roleBadge.setFont(new Font("SansSerif", Font.BOLD, 11));
        roleBadge.setForeground(c1);
        roleBadge.setBackground(Color.WHITE);
        roleBadge.setOpaque(true);
        roleBadge.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        heroText.add(nameLbl);
        heroText.add(Box.createVerticalStrut(4));
        heroText.add(noLbl);
        heroText.add(Box.createVerticalStrut(10));
        heroText.add(roleBadge);

        JPanel heroLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        heroLeft.setOpaque(false);
        heroLeft.add(avatarCircle);
        heroLeft.add(heroText);
        hero.add(heroLeft, BorderLayout.CENTER);

        // ── stats strip ───────────────────────────────────────────────────────
        String cgpaStr = cgpa >= 0 ? String.format("%.2f", cgpa) : "N/A";
        Color  cgpaCol = cgpa >= 0 ? ThemeManager.gpaColor(cgpa) : ThemeManager.muted();

        JPanel statsStrip = new JPanel(new GridLayout(1, 3, 1, 0));
        statsStrip.setBackground(ThemeManager.elevated());
        statsStrip.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.border()));
        statsStrip.add(statTile("\uD83C\uDF93", "CGPA", cgpaStr, cgpaCol));
        statsStrip.add(statTile("\uD83D\uDCDA", "Courses", String.valueOf(totalCourses), ThemeManager.accent()));
        statsStrip.add(statTile("\u2B50", "Credits", String.valueOf(totalCredits), ThemeManager.gradeBFg()));

        // ── info grid ─────────────────────────────────────────────────────────
        JPanel infoGrid = new JPanel(new GridBagLayout());
        infoGrid.setBackground(ThemeManager.surface());
        infoGrid.setBorder(new EmptyBorder(20, 28, 24, 28));

        String[][] fields = {
            {"\uD83D\uDCE7", "Email",         email},
            {"\uD83D\uDCDE", "Phone",         phone},
            {"\u26A7",       "Gender",        capitalize(gender)},
            {"\uD83C\uDF82", "Date of Birth", dob},
            {"\uD83D\uDCCD", "Address",       address},
            {"\uD83D\uDD11", "Username",      currentUser.getUsername()},
        };

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 10, 6, 10);
        gc.fill   = GridBagConstraints.HORIZONTAL;

        for (int i = 0; i < fields.length; i++) {
            int col = (i % 2) * 3;
            int row = i / 2;

            // icon
            gc.gridx = col; gc.gridy = row; gc.weightx = 0;
            JLabel iconLbl = new JLabel(fields[i][0]);
            iconLbl.setFont(new Font("SansSerif", Font.PLAIN, 16));
            infoGrid.add(iconLbl, gc);

            // label
            gc.gridx = col + 1; gc.weightx = 0;
            JLabel keyLbl = new JLabel(fields[i][1]);
            keyLbl.setFont(ThemeManager.fontBold());
            keyLbl.setForeground(ThemeManager.muted());
            infoGrid.add(keyLbl, gc);

            // value
            gc.gridx = col + 2; gc.weightx = 0.45;
            JLabel valLbl = new JLabel(fields[i][2]);
            valLbl.setFont(ThemeManager.fontBody());
            valLbl.setForeground(ThemeManager.text());
            infoGrid.add(valLbl, gc);
        }

        // ── assemble card ─────────────────────────────────────────────────────
        JPanel card = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ThemeManager.surface());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder());

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(ThemeManager.surface());
        body.add(statsStrip, BorderLayout.NORTH);
        body.add(infoGrid,   BorderLayout.CENTER);

        card.add(hero, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);

        // outer shadow-like border
        JPanel shadow = new JPanel(new BorderLayout());
        shadow.setBackground(ThemeManager.bg());
        shadow.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.border(), 1),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        shadow.add(card);

        // constrain width so it doesn't stretch absurdly wide
        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(ThemeManager.bg());
        GridBagConstraints cc = new GridBagConstraints();
        cc.fill    = GridBagConstraints.BOTH;
        cc.weightx = 1; cc.weighty = 1;
        cc.insets  = new Insets(0, 0, 0, 0);
        center.add(shadow, cc);

        root.add(center, BorderLayout.CENTER);
        return root;
    }

    /** Three-line stat tile used in the profile stats strip. */
    private JPanel statTile(String icon, String label, String value, Color valueColor) {
        JPanel tile = new JPanel(new GridLayout(3, 1, 0, 2));
        tile.setBackground(ThemeManager.elevated());
        tile.setBorder(new EmptyBorder(12, 0, 12, 0));

        JLabel iconLbl = new JLabel(icon, SwingConstants.CENTER);
        iconLbl.setFont(new Font("SansSerif", Font.PLAIN, 18));

        JLabel valLbl = new JLabel(value, SwingConstants.CENTER);
        valLbl.setFont(new Font("SansSerif", Font.BOLD, 20));
        valLbl.setForeground(valueColor);

        JLabel keyLbl = new JLabel(label, SwingConstants.CENTER);
        keyLbl.setFont(ThemeManager.fontSmall());
        keyLbl.setForeground(ThemeManager.muted());

        tile.add(iconLbl);
        tile.add(valLbl);
        tile.add(keyLbl);
        return tile;
    }

    private String getInitials(String first, String last, String fallback) {
        if (first != null && !first.equals("—") && last != null && !last.equals("—"))
            return (first.substring(0, 1) + last.substring(0, 1)).toUpperCase();
        if (fallback != null && !fallback.isEmpty())
            return fallback.substring(0, Math.min(2, fallback.length())).toUpperCase();
        return "?";
    }

    private String capitalize(String s) {
        if (s == null || s.equals("—") || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private String nvl(String s) { return s != null ? s : "—"; }

}

