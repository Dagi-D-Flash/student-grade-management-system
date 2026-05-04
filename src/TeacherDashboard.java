import models.User;
import panels.AdminChartsPanel;
import panels.EnrollmentPanel;
import panels.LeaderboardPanel;
import panels.SchedulePanel;
import panels.StudentSearchPanel;
import panels.TeacherAnalysisPanel;
import panels.TeacherCoursesPanel;
import panels.TeacherGradePanel;
import panels.TeacherStudentsPanel;
import panels.TranscriptPanel;
import ui.CollapsibleSidebar;
import util.DBConnection;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;

public class TeacherDashboard extends JFrame {

    private JPanel contentPanel;
    private CardLayout cardLayout;
    private final User currentUser;
    private CollapsibleSidebar sidebar;
    private JPanel topBarPanel;
    private JLabel pageLabel;
    private final Runnable themeListener = this::applyTheme;

    // home card stat labels — updated on every Dashboard visit
    private JLabel valCourses, valStudents, valPending;
    private panels.TeacherStudentsPanel studentsPanel;

    private static final String[][] NAV = {
            { "\uD83C\uDFE0", "Dashboard" },
            { "\uD83D\uDCDA", "My Courses" },
            { "\uD83D\uDC65", "My Students" },
            { "\u270F\uFE0F", "Enter Grades" },
            { "\uD83D\uDCC8", "Charts" },
            { "\uD83C\uDFC6", "Leaderboard" },
            { "\uD83D\uDD0D", "Search Students" },
            { "\uD83C\uDFAF", "Analysis" },
            { "\uD83D\uDCCB", "Transcript" },
            { "\uD83D\uDCDD", "Enrollments" },
            { "\uD83D\uDDD3", "Schedule" }
    };

    public TeacherDashboard(User user) {
        this.currentUser = user;
        setTitle("Teacher Dashboard — GMS");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 780);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(ThemeManager.bg());

        sidebar = new CollapsibleSidebar(
                "\uD83E\uDDD1\u200D\uD83C\uDFEB", "GMS Teacher", "Educator Portal", NAV);

        TeacherGradePanel gradePanel = new TeacherGradePanel(currentUser);
        studentsPanel = new panels.TeacherStudentsPanel(currentUser);

        sidebar.setOnToggle(() -> gradePanel.setSidebarExpanded(sidebar.isExpanded()));
        gradePanel.setSidebarExpanded(true);

        // Double-clicking a student in My Students → navigate to Enter Grades and
        // select them
        studentsPanel.setOnStudentSelected(studentNo -> {
            gradePanel.selectStudentByNo(studentNo);
            cardLayout.show(contentPanel, "Enter Grades");
            pageLabel.setText("Enter Grades");
        });

        String[] cards = { "Dashboard", "My Courses", "My Students", "Enter Grades",
                "Charts", "Leaderboard", "Search Students", "Analysis",
                "Transcript", "Enrollments", "Schedule" };
        for (int i = 0; i < cards.length; i++) {
            final String card = cards[i];
            sidebar.setNavAction(i, () -> {
                if ("Dashboard".equals(card))
                    refreshHomeCard();
                cardLayout.show(contentPanel, card);
                pageLabel.setText(card);
            });
        }

        JPanel mainPanel = buildMain(gradePanel, studentsPanel);
        add(sidebar, BorderLayout.WEST);
        add(mainPanel, BorderLayout.CENTER);

        // Setup keyboard shortcuts for navigation
        setupKeyboardShortcuts();

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
        revalidate();
        repaint();
    }

    private JPanel buildMain(TeacherGradePanel gradePanel, panels.TeacherStudentsPanel studentsPanel) {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(ThemeManager.bg());

        topBarPanel = buildTopBar();
        main.add(topBarPanel, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(ThemeManager.bg());

        contentPanel.add(buildHomeCard(), "Dashboard");
        contentPanel.add(new TeacherCoursesPanel(currentUser), "My Courses");
        contentPanel.add(studentsPanel, "My Students");
        contentPanel.add(gradePanel, "Enter Grades");
        contentPanel.add(new AdminChartsPanel(currentUser), "Charts");
        contentPanel.add(new LeaderboardPanel(currentUser), "Leaderboard");
        contentPanel.add(new StudentSearchPanel(currentUser), "Search Students");
        contentPanel.add(new TeacherAnalysisPanel(currentUser), "Analysis");
        contentPanel.add(new TranscriptPanel(currentUser), "Transcript");
        contentPanel.add(new EnrollmentPanel(currentUser), "Enrollments");
        contentPanel.add(new SchedulePanel(currentUser), "Schedule");

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
        JLabel heading = new JLabel("Welcome back, " + currentUser.getUsername() + " \uD83D\uDC4B");
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(ThemeManager.text());
        wrapper.add(heading, BorderLayout.NORTH);

        valCourses = statValueLabel(ThemeManager.accent());
        valStudents = statValueLabel(ThemeManager.SUCCESS);
        valPending = statValueLabel(ThemeManager.DANGER);

        JPanel grid = new JPanel(new GridLayout(1, 3, 16, 16));
        grid.setOpaque(false);
        grid.add(statCard("My Courses", valCourses, "\uD83D\uDCDA"));
        grid.add(statCard("Active Students", valStudents, "\uD83D\uDC65"));

        // Clickable "No Grades Yet" card — navigates to My Students with ungraded
        // filter
        JPanel pendingCard = statCard("No Grades Yet", valPending, "\u23F3");
        pendingCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pendingCard.setToolTipText("Click to see students with no grades entered");
        pendingCard.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (studentsPanel != null)
                    studentsPanel.showUngraded();
                cardLayout.show(contentPanel, "My Students");
                pageLabel.setText("My Students");
            }

            public void mouseEntered(java.awt.event.MouseEvent e) {
                pendingCard.setBackground(ThemeManager.elevated());
            }

            public void mouseExited(java.awt.event.MouseEvent e) {
                pendingCard.setBackground(ThemeManager.surface());
            }
        });
        grid.add(pendingCard);
        wrapper.add(grid, BorderLayout.CENTER);

        refreshHomeCard();
        return wrapper;
    }

    private void refreshHomeCard() {
        new SwingWorker<int[], Void>() {
            protected int[] doInBackground() throws Exception {
                int courses = 0, students = 0, pending = 0;
                String sqlCourses = "SELECT COUNT(*) FROM courses c JOIN teachers t ON t.id=c.teacher_id JOIN users u ON u.id=t.user_id WHERE u.id=?";
                String sqlStudents = "SELECT COUNT(DISTINCT e.student_id) FROM enrollments e JOIN courses c ON c.id=e.course_id JOIN teachers t ON t.id=c.teacher_id JOIN users u ON u.id=t.user_id WHERE u.id=? AND e.status='active'";
                String sqlPending = "SELECT COUNT(DISTINCT e.id) FROM enrollments e " +
                        "JOIN courses c ON c.id=e.course_id " +
                        "JOIN teachers t ON t.id=c.teacher_id " +
                        "JOIN users u ON u.id=t.user_id " +
                        "WHERE u.id=? AND e.status='active' " +
                        "AND NOT EXISTS (" +
                        "  SELECT 1 FROM grades g " +
                        "  JOIN course_grade_components cgc ON cgc.id=g.component_id " +
                        "  WHERE g.enrollment_id=e.id AND cgc.course_id=e.course_id" +
                        ")";
                try (java.sql.Connection conn = DBConnection.getConnection()) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(sqlCourses)) {
                        ps.setInt(1, currentUser.getId());
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next())
                                courses = rs.getInt(1);
                        }
                    }
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(sqlStudents)) {
                        ps.setInt(1, currentUser.getId());
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next())
                                students = rs.getInt(1);
                        }
                    }
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(sqlPending)) {
                        ps.setInt(1, currentUser.getId());
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next())
                                pending = rs.getInt(1);
                        }
                    }
                }
                return new int[] { courses, students, pending };
            }

            protected void done() {
                try {
                    int[] v = get();
                    if (valCourses != null)
                        valCourses.setText(String.valueOf(v[0]));
                    if (valStudents != null)
                        valStudents.setText(String.valueOf(v[1]));
                    if (valPending != null)
                        valPending.setText(String.valueOf(v[2]));
                } catch (Exception ignored) {
                }
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
        card.add(emojiLbl, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(lbl, BorderLayout.SOUTH);
        return card;
    }

    private JPanel placeholder(String title, String description) {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(ThemeManager.bg());
        panel.setBorder(new EmptyBorder(28, 28, 28, 28));
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 20));
        lbl.setForeground(ThemeManager.text());
        JLabel desc = new JLabel(description);
        desc.setFont(ThemeManager.fontBody());
        desc.setForeground(ThemeManager.muted());
        JPanel top = new JPanel(new GridLayout(2, 1, 0, 4));
        top.setOpaque(false);
        top.add(lbl);
        top.add(desc);
        JTable table = new JTable(8, 5);
        ThemeManager.styleTable(table);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Setup keyboard shortcuts for teacher dashboard navigation
     */
    private void setupKeyboardShortcuts() {
        // Create key bindings for navigation
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        // Navigation shortcuts
        String[] shortcuts = {
                "ctrl 1", "ctrl 2", "ctrl 3", "ctrl 4", "ctrl 5",
                "ctrl 6", "ctrl 7", "ctrl 8", "ctrl 9", "ctrl 0", "ctrl MINUS"
        };

        String[] cards = { "Dashboard", "My Courses", "My Students", "Enter Grades",
                "Charts", "Leaderboard", "Search Students", "Analysis",
                "Transcript", "Enrollments", "Schedule" };

        for (int i = 0; i < shortcuts.length && i < cards.length; i++) {
            final String card = cards[i];
            final int index = i;

            inputMap.put(KeyStroke.getKeyStroke(shortcuts[i]), "nav_" + i);
            actionMap.put("nav_" + i, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if ("Dashboard".equals(card))
                        refreshHomeCard();
                    cardLayout.show(contentPanel, card);
                    pageLabel.setText(card);
                    sidebar.setSelectedIndex(index); // Highlight sidebar item
                }
            });
        }

        // Additional shortcuts
        inputMap.put(KeyStroke.getKeyStroke("F5"), "refresh");
        actionMap.put("refresh", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                refreshHomeCard();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("ctrl Q"), "quit");
        actionMap.put("quit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ThemeManager.removeThemeListener(themeListener);
                dispose();
                new LoginUI();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "toggle_sidebar");
        actionMap.put("toggle_sidebar", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sidebar.toggle();
            }
        });
    }
}