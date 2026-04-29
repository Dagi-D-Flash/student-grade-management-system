import models.User;
import panels.AdminChartsPanel;
import panels.CoursePanel;
import panels.EnrollmentPanel;
import panels.LeaderboardPanel;
import panels.StudentPanel;
import panels.StudentSearchPanel;
import panels.SubjectPanel;
import panels.TeacherPanel;
import panels.TranscriptPanel;
import ui.CollapsibleSidebar;
import util.DBConnection;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class AdminDashboard extends JFrame {

    private JPanel            contentPanel;
    private CardLayout        cardLayout;
    private final User        currentUser;
    private CollapsibleSidebar sidebar;
    private JPanel            topBarPanel;
    private JLabel            pageLabel;
    private final Runnable    themeListener = this::applyTheme;

    // home card stat labels — updated on every Dashboard visit
    private JLabel valUsers, valStudents, valTeachers, valCourses,
                   valSubjects, valGrades, valEnrollments, valAbovePass;

    private static final String[][] NAV = {
        {"\uD83D\uDCCA", "Dashboard"},
        {"\uD83C\uDFC6", "Leaderboard"},
        {"\uD83D\uDCC8", "Charts"},
        {"\uD83D\uDD0D", "Search Students"},
        {"\uD83D\uDCCB", "Transcript"},
        {"\uD83D\uDC65", "Manage Students"},
        {"\uD83D\uDC68\u200D\uD83C\uDFEB", "Manage Teachers"},
        {"\uD83D\uDCDA", "Courses"},
        {"\uD83D\uDCD6", "Subjects"},
        {"\uD83D\uDCDD", "Enrollments"}
    };

    public AdminDashboard(User user) {
        this.currentUser = user;
        setTitle("Admin Dashboard — GMS");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 780);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(ThemeManager.bg());

        sidebar = new CollapsibleSidebar(
            "\uD83E\uDDD1\u200D\uD83D\uDCBC", "GMS Admin", "Administrator", NAV);

        // Wire nav actions
        String[] cards = {"Dashboard","Leaderboard","Charts","Search Students",
                          "Transcript","Manage Students","Manage Teachers",
                          "Courses","Subjects","Enrollments"};
        for (int i = 0; i < cards.length; i++) {
            final String card = cards[i];
            sidebar.setNavAction(i, () -> {
                if ("Dashboard".equals(card)) {
                    contentPanel.remove(0);
                    contentPanel.add(buildHomeCard(), "Dashboard", 0);
                }
                cardLayout.show(contentPanel, card);
                pageLabel.setText(card);
            });
        }

        JPanel mainPanel = buildMain();
        add(sidebar,    BorderLayout.WEST);
        add(mainPanel,  BorderLayout.CENTER);

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

        contentPanel.add(buildHomeCard(),                   "Dashboard");
        contentPanel.add(new LeaderboardPanel(),            "Leaderboard");
        contentPanel.add(new AdminChartsPanel(),            "Charts");
        contentPanel.add(new StudentSearchPanel(),          "Search Students");
        contentPanel.add(new TranscriptPanel(currentUser), "Transcript");
        contentPanel.add(new StudentPanel(),                "Manage Students");
        contentPanel.add(new TeacherPanel(),                "Manage Teachers");
        contentPanel.add(new CoursePanel(),                 "Courses");
        contentPanel.add(new SubjectPanel(),                "Subjects");
        contentPanel.add(new EnrollmentPanel(),             "Enrollments");

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

        JLabel heading = new JLabel("Overview");
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(ThemeManager.text());
        wrapper.add(heading, BorderLayout.NORTH);

        valUsers       = statValueLabel(ThemeManager.accent());
        valStudents    = statValueLabel(ThemeManager.accent());
        valTeachers    = statValueLabel(ThemeManager.INFO);
        valCourses     = statValueLabel(ThemeManager.WARNING);
        valSubjects    = statValueLabel(ThemeManager.accent());
        valGrades      = statValueLabel(ThemeManager.INFO);
        valEnrollments = statValueLabel(ThemeManager.WARNING);
        valAbovePass   = statValueLabel(ThemeManager.SUCCESS);

        JPanel grid = new JPanel(new GridLayout(2, 4, 16, 16));
        grid.setOpaque(false);
        grid.add(statCard("Total Users",     valUsers,       "\uD83D\uDC64"));
        grid.add(statCard("Students",        valStudents,    "\uD83C\uDF93"));
        grid.add(statCard("Teachers",        valTeachers,    "\uD83D\uDC68\u200D\uD83C\uDFEB"));
        grid.add(statCard("Courses",         valCourses,     "\uD83D\uDCDA"));
        grid.add(statCard("Subjects",        valSubjects,    "\uD83D\uDCD6"));
        grid.add(statCard("Grades",          valGrades,      "\uD83D\uDCDD"));
        grid.add(statCard("Enrollments",     valEnrollments, "\uD83D\uDCCB"));
        grid.add(statCard("Above Pass (>50%)", valAbovePass,   "\u2705"));
        wrapper.add(grid, BorderLayout.CENTER);

        refreshHomeCard();
        return wrapper;
    }

    private void refreshHomeCard() {
        new SwingWorker<int[], Void>() {
            protected int[] doInBackground() throws Exception {
                int users = 0, students = 0, teachers = 0, courses = 0,
                    subjects = 0, grades = 0, enrollments = 0, abovePass = 0;
                try (java.sql.Connection conn = DBConnection.getConnection()) {
                    users       = count(conn, "SELECT COUNT(*) FROM users");
                    students    = count(conn, "SELECT COUNT(*) FROM students");
                    teachers    = count(conn, "SELECT COUNT(*) FROM teachers");
                    courses     = count(conn, "SELECT COUNT(*) FROM courses");
                    subjects    = count(conn, "SELECT COUNT(*) FROM subjects");
                    grades      = count(conn, "SELECT COUNT(*) FROM grades");
                    enrollments = count(conn, "SELECT COUNT(*) FROM enrollments");
                    abovePass   = count(conn,
                        "SELECT COUNT(DISTINCT student_id) FROM (" +
                        "  SELECT e.student_id, " +
                        "         SUM((g.score / cgc.max_score) * cgc.weight) AS course_total " +
                        "  FROM enrollments e " +
                        "  JOIN grades g ON g.enrollment_id = e.id " +
                        "  JOIN course_grade_components cgc " +
                        "       ON cgc.id = g.component_id AND cgc.course_id = e.course_id " +
                        "  GROUP BY e.student_id, e.id " +
                        "  HAVING course_total > 50" +
                        ") AS t");
                } catch (Exception ex) { System.err.println("AdminDashboard stats: " + ex.getMessage()); }
                return new int[]{users, students, teachers, courses, subjects, grades, enrollments, abovePass};
            }
            protected void done() {
                try {
                    int[] v = get();
                    if (valUsers       != null) valUsers.setText(String.valueOf(v[0]));
                    if (valStudents    != null) valStudents.setText(String.valueOf(v[1]));
                    if (valTeachers    != null) valTeachers.setText(String.valueOf(v[2]));
                    if (valCourses     != null) valCourses.setText(String.valueOf(v[3]));
                    if (valSubjects    != null) valSubjects.setText(String.valueOf(v[4]));
                    if (valGrades      != null) valGrades.setText(String.valueOf(v[5]));
                    if (valEnrollments != null) valEnrollments.setText(String.valueOf(v[6]));
                    if (valAbovePass   != null) valAbovePass.setText(String.valueOf(v[7]));
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private int count(java.sql.Connection conn, String sql) {
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception ignored) {}
        return 0;
    }

    private JLabel statValueLabel(Color color) {
        JLabel l = new JLabel("…");
        l.setFont(new Font("SansSerif", Font.BOLD, 30));
        l.setForeground(color);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }

    private JPanel statCard(String title, JLabel valueLabel, String emoji) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(ThemeManager.surface());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.border()),
            new EmptyBorder(20, 20, 20, 20)));
        JLabel emojiLbl = new JLabel(emoji, SwingConstants.CENTER);
        emojiLbl.setFont(new Font("SansSerif", Font.PLAIN, 22));
        JLabel lbl = new JLabel(title, SwingConstants.CENTER);
        lbl.setFont(ThemeManager.fontSmall());
        lbl.setForeground(ThemeManager.muted());
        card.add(emojiLbl,   BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(lbl,        BorderLayout.SOUTH);
        return card;
    }
}
