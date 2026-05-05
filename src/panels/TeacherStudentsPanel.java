package panels;

import models.User;
import util.DBConnection;
import util.GPACalculator;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TeacherStudentsPanel extends JPanel {

    private final User teacher;
    private JTable table;
    private DefaultTableModel model;
    private JLabel lblCount;
    private JComboBox<CourseItem> cbCourse;
    private java.util.List<Object[]> allRows = new java.util.ArrayList<>();
    private volatile boolean active = true;
    private String currentQuery = "";
    private boolean ungradedOnly = false;
    private JButton btnUngradedFilter;

    // Callback: called when a student row is clicked; receives student_no string
    private java.util.function.Consumer<String> onStudentSelected;

    /**
     * Set a callback that fires when a student row is double-clicked. Receives the
     * student_no.
     */
    public void setOnStudentSelected(java.util.function.Consumer<String> cb) {
        this.onStudentSelected = cb;
    }

    public TeacherStudentsPanel(User teacher) {
        this.teacher = teacher;
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);
        loadCourses();
        Thread t = new Thread(() -> {
            while (active) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    break;
                }
                if (active)
                    SwingUtilities.invokeLater(this::load);
            }
        });
        t.setDaemon(true);
        t.start();
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                    && !isDisplayable())
                active = false;
        });
    }

    private JPanel buildHeader() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(ThemeManager.cardBorder());

        // ── Row 1: title + search ─────────────────────────────────────────────
        JPanel row1 = new JPanel(new BorderLayout(8, 0));
        row1.setOpaque(false);

        JLabel title = new JLabel("My Students");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());

        JTextField tfSearch = new JTextField();
        tfSearch.setBackground(ThemeManager.elevated());
        tfSearch.setForeground(ThemeManager.text());
        tfSearch.setCaretColor(ThemeManager.text());
        tfSearch.setFont(ThemeManager.fontBody());
        tfSearch.putClientProperty("JTextField.placeholderText", "Search by name or student ID...");
        tfSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterRows(tfSearch.getText());
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterRows(tfSearch.getText());
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterRows(tfSearch.getText());
            }
        });

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        searchBar.setOpaque(false);
        JLabel searchIcon = new JLabel("\uD83D\uDD0D");
        searchIcon.setForeground(ThemeManager.muted());
        searchBar.add(searchIcon);
        searchBar.add(tfSearch);
        tfSearch.setPreferredSize(new Dimension(220, 28));

        row1.add(title, BorderLayout.WEST);
        row1.add(searchBar, BorderLayout.CENTER);

        // ── Row 2: course filter + count + ungraded toggle ────────────────────
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row2.setOpaque(false);

        JLabel lbl = new JLabel("Course:");
        lbl.setFont(ThemeManager.fontBold());
        lbl.setForeground(ThemeManager.muted());
        row2.add(lbl);

        cbCourse = new JComboBox<>();
        cbCourse.setPreferredSize(new Dimension(280, 28));
        cbCourse.addActionListener(e -> load());
        row2.add(cbCourse);

        lblCount = new JLabel("");
        lblCount.setFont(ThemeManager.fontSmall());
        lblCount.setForeground(ThemeManager.muted());
        row2.add(lblCount);

        btnUngradedFilter = ThemeManager.secondaryButton("⚠ Ungraded Only");
        btnUngradedFilter.setToolTipText("Show only students with no grades entered yet");
        btnUngradedFilter.addActionListener(e -> toggleUngradedFilter());
        row2.add(btnUngradedFilter);

        bar.add(row1);
        bar.add(row2);
        return bar;
    }

    private void filterRows(String query) {
        currentQuery = query;
        model.setRowCount(0);
        String q = query.trim().toLowerCase();
        for (Object[] row : allRows) {
            String no = row[0].toString().toLowerCase();
            String name = (row[1].toString() + " " + row[2].toString()).toLowerCase();
            String noStripped = no.replace("wour/", "").replace("/", "");
            String qStripped = q.replace("wour/", "").replace("/", "");
            boolean matchesSearch = q.isEmpty() || no.contains(q) || name.contains(q)
                    || noStripped.contains(qStripped);
            // row[9] = hasGrades boolean (reliable regardless of course mode)
            boolean hasGrades = row.length > 9 && Boolean.TRUE.equals(row[9]);
            if (matchesSearch && (!ungradedOnly || !hasGrades))
                model.addRow(java.util.Arrays.copyOf(row, 9)); // only show 9 visible cols
        }
        int cnt = model.getRowCount();
        lblCount.setText(cnt + " student" + (cnt != 1 ? "s" : "")
                + (ungradedOnly ? "  (ungraded)" : ""));
    }

    private void toggleUngradedFilter() {
        ungradedOnly = !ungradedOnly;
        if (ungradedOnly) {
            btnUngradedFilter.setBackground(ThemeManager.WARNING);
            btnUngradedFilter.setForeground(Color.WHITE);
        } else {
            btnUngradedFilter.setBackground(null);
            btnUngradedFilter.setForeground(ThemeManager.text());
        }
        filterRows(currentQuery);
    }

    /** Called from TeacherDashboard when the "No Grades Yet" card is clicked. */
    public void showUngraded() {
        if (!ungradedOnly)
            toggleUngradedFilter();
    }

    private JScrollPane buildTable() {
        String[] cols = { "Student No", "First Name", "Last Name", "Gender",
                "Phone", "Avg %", "Letter", "GPA", "Status" };
        model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        table = new JTable(model);
        ThemeManager.styleTable(table);
        table.getColumnModel().getColumn(6).setCellRenderer(letterRenderer());

        // Double-click → fire onStudentSelected callback with student_no
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && onStudentSelected != null) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        String studentNo = model.getValueAt(row, 0).toString();
                        onStudentSelected.accept(studentNo);
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(ThemeManager.border()));
        return scroll;
    }

    private void loadCourses() {
        cbCourse.removeAllItems();
        cbCourse.addItem(new CourseItem(-1, "All My Courses"));
        String sql = "SELECT c.id, s.code, s.name, c.section, c.academic_year, c.semester " +
                "FROM courses c " +
                "JOIN subjects s ON s.id = c.subject_id " +
                "JOIN teachers t ON t.id = c.teacher_id " +
                "JOIN users u    ON u.id = t.user_id " +
                "WHERE u.id = ? " +
                "ORDER BY c.academic_year DESC, c.semester, s.code";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teacher.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String label = rs.getString("code") + " \u2014 " + rs.getString("name")
                            + " [" + rs.getString("section") + "] "
                            + rs.getInt("academic_year") + " " + rs.getString("semester");
                    cbCourse.addItem(new CourseItem(rs.getInt("id"), label));
                }
            }
        } catch (SQLException ex) {
            System.err.println("Error: " + ex.getMessage());
        }
        load();
    }

    private void load() {
        model.setRowCount(0);
        CourseItem ci = (CourseItem) cbCourse.getSelectedItem();
        int courseId = ci != null ? ci.id : -1;

        // Update column header dynamically
        String gpaHeader = courseId > 0 ? "Course GPA" : "Courses GPA";
        table.getColumnModel().getColumn(7).setHeaderValue(gpaHeader);
        table.getTableHeader().repaint();

        String sql;
        if (courseId > 0) {
            // Specific course: grade point for that one course
            sql = "SELECT s.id, s.student_no, s.first_name, s.last_name, s.gender, s.phone, e.status, " +
                    "       SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
                    "FROM enrollments e " +
                    "JOIN students s ON s.id = e.student_id " +
                    "JOIN courses c  ON c.id = e.course_id " +
                    "JOIN teachers t ON t.id = c.teacher_id " +
                    "JOIN users u    ON u.id = t.user_id " +
                    "LEFT JOIN grades g ON g.enrollment_id = e.id " +
                    "LEFT JOIN course_grade_components cgc " +
                    "       ON cgc.id = g.component_id AND cgc.course_id = e.course_id " +
                    "WHERE u.id = ? AND c.id = ? " +
                    "GROUP BY s.id, s.student_no, s.first_name, s.last_name, s.gender, s.phone, e.status " +
                    "ORDER BY s.first_name, s.last_name";
        } else {
            // All teacher's courses: credit-weighted GPA across all assigned courses
            sql = "SELECT s.id, s.student_no, s.first_name, s.last_name, s.gender, s.phone, " +
                    "       MAX(e.status) AS status, " +
                    "       SUM(gp.grade_point * sub.credits) / NULLIF(SUM(sub.credits), 0) AS avg_pct " +
                    "FROM students s " +
                    "JOIN enrollments e ON e.student_id = s.id " +
                    "JOIN courses c ON c.id = e.course_id " +
                    "JOIN subjects sub ON sub.id = c.subject_id " +
                    "JOIN teachers t ON t.id = c.teacher_id " +
                    "JOIN users u ON u.id = t.user_id " +
                    "LEFT JOIN (" +
                    "  SELECT e2.id AS enrollment_id, " +
                    "    CASE " +
                    "      WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 90 THEN 4.0 " +
                    "      WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 85 THEN 4.0 " +
                    "      WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 80 THEN 3.75 " +
                    "      WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 75 THEN 3.5 " +
                    "      WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 70 THEN 3.0 " +
                    "      WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 65 THEN 2.75 " +
                    "      WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 60 THEN 2.5 " +
                    "      WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 55 THEN 2.0 " +
                    "      WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 50 THEN 1.75 " +
                    "      WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 45 THEN 1.0 " +
                    "      ELSE 0.0 END AS grade_point " +
                    "  FROM enrollments e2 " +
                    "  LEFT JOIN grades g2 ON g2.enrollment_id = e2.id " +
                    "  LEFT JOIN course_grade_components cgc2 " +
                    "         ON cgc2.id = g2.component_id AND cgc2.course_id = e2.course_id " +
                    "  GROUP BY e2.id" +
                    ") AS gp ON gp.enrollment_id = e.id " +
                    "WHERE u.id = ? " +
                    "GROUP BY s.id, s.student_no, s.first_name, s.last_name, s.gender, s.phone " +
                    "ORDER BY s.first_name, s.last_name";
        }

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teacher.getId());
            if (courseId > 0)
                ps.setInt(2, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                allRows.clear();
                while (rs.next()) {
                    double avg = rs.getDouble("avg_pct");
                    boolean hasGrades = !rs.wasNull();

                    String letter;
                    String gpaVal;
                    String avgPctDisplay;

                    if (!hasGrades) {
                        letter = "—";
                        gpaVal = "—";
                        avgPctDisplay = "—";
                    } else if (courseId > 0) {
                        // avg is a weighted percentage (0–100)
                        letter = GPACalculator.toLetterGrade(avg);
                        gpaVal = String.format("%.2f", GPACalculator.toGradePoint(avg));
                        avgPctDisplay = String.format("%.1f", avg);
                    } else {
                        // avg is already a credit-weighted GPA (0–4.0)
                        // Convert GPA → percent for letter grade lookup
                        letter = GPACalculator.toLetterGrade(gpaToPercent(avg));
                        gpaVal = String.format("%.2f", avg);
                        avgPctDisplay = "—"; // avg% not meaningful across multiple courses
                    }

                    Object[] row = {
                            rs.getString("student_no"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("gender") != null ? rs.getString("gender") : "—",
                            rs.getString("phone") != null ? rs.getString("phone") : "—",
                            avgPctDisplay,
                            letter,
                            gpaVal,
                            rs.getString("status"),
                            hasGrades // hidden col 9 — used by ungraded filter
                    };
                    allRows.add(row);
                }
            }
            filterRows(currentQuery);
        } catch (SQLException ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }

    private DefaultTableCellRenderer letterRenderer() {
        return new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                setHorizontalAlignment(SwingConstants.CENTER);
                String s = v != null ? v.toString() : "";
                if (!sel && !s.isEmpty() && !s.equals("—")) {
                    switch (s.charAt(0)) {
                        case 'A':
                            setBackground(ThemeManager.gradeABg());
                            setForeground(ThemeManager.gradeAFg());
                            break;
                        case 'B':
                            setBackground(ThemeManager.gradeBBg());
                            setForeground(ThemeManager.gradeBFg());
                            break;
                        case 'C':
                            setBackground(ThemeManager.gradeCBg());
                            setForeground(ThemeManager.gradeCFg());
                            break;
                        case 'D':
                            setBackground(ThemeManager.gradeDBg());
                            setForeground(ThemeManager.gradeDFg());
                            break;
                        case 'F':
                            setBackground(ThemeManager.gradeFBg());
                            setForeground(ThemeManager.gradeFFg());
                            break;
                        default:
                            setBackground(ThemeManager.surface());
                            setForeground(ThemeManager.text());
                    }
                }
                return this;
            }
        };
    }

    /**
     * Converts a 0–4.0 GPA back to an approximate percentage for letter grade
     * lookup.
     */
    private static double gpaToPercent(double gpa) {
        if (gpa >= 4.0)
            return 90;
        if (gpa >= 3.75)
            return 85;
        if (gpa >= 3.5)
            return 80;
        if (gpa >= 3.0)
            return 75;
        if (gpa >= 2.75)
            return 70;
        if (gpa >= 2.5)
            return 65;
        if (gpa >= 2.0)
            return 60;
        if (gpa >= 1.75)
            return 55;
        if (gpa >= 1.0)
            return 50;
        return 45;
    }

    private static class CourseItem {
        int id;
        String label;

        CourseItem(int id, String label) {
            this.id = id;
            this.label = label;
        }

        public String toString() {
            return label;
        }
    }
}