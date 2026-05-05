package panels;

import models.User;
import util.DBConnection;
import util.GPACalculator;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class StudentSearchPanel extends JPanel {

    private final User scopedUser; // null = admin (all courses); non-null = teacher (own courses only)

    private JTextField tfSearch;
    private JComboBox<CourseItem> cbCourse;
    private JComboBox<String> cbGpaMin, cbGpaMax;
    private JButton btnReset;
    private JLabel lblResultCount;

    private JTable resultTable;
    private DefaultTableModel tableModel;

    private Timer debounceTimer;

    public StudentSearchPanel() {
        this(null);
    }

    public StudentSearchPanel(User scopedUser) {
        this.scopedUser = scopedUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());

        add(buildFilterBar(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);

        loadCourses();
        search();
    }

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(ThemeManager.cardBorder());

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0;
        g.gridy = 0;
        g.weightx = 0;
        bar.add(boldLabel("Search:"), g);

        tfSearch = new JTextField();
        tfSearch.setPreferredSize(new Dimension(220, 28));
        tfSearch.setBackground(ThemeManager.elevated());
        tfSearch.setForeground(ThemeManager.text());
        tfSearch.setCaretColor(ThemeManager.text());
        tfSearch.putClientProperty("JTextField.placeholderText", "Search by name or student ID...");
        g.gridx = 1;
        g.weightx = 0.35;
        bar.add(tfSearch, g);

        g.gridx = 2;
        g.weightx = 0;
        bar.add(boldLabel("Course:"), g);

        cbCourse = new JComboBox<>();
        cbCourse.setPreferredSize(new Dimension(200, 28));
        g.gridx = 3;
        g.weightx = 0.3;
        bar.add(cbCourse, g);

        g.gridx = 4;
        g.weightx = 0;
        bar.add(boldLabel("GPA Min:"), g);

        String[] gpaOptions = { "Any", "0.0", "1.0", "1.5", "2.0", "2.5", "3.0", "3.5", "3.7" };
        cbGpaMin = new JComboBox<>(gpaOptions);
        cbGpaMin.setPreferredSize(new Dimension(80, 28));
        g.gridx = 5;
        g.weightx = 0.1;
        bar.add(cbGpaMin, g);

        g.gridx = 6;
        g.weightx = 0;
        bar.add(boldLabel("Max:"), g);

        String[] gpaMaxOptions = { "Any", "1.0", "1.5", "2.0", "2.5", "3.0", "3.5", "3.7", "4.0" };
        cbGpaMax = new JComboBox<>(gpaMaxOptions);
        cbGpaMax.setSelectedItem("Any");
        cbGpaMax.setPreferredSize(new Dimension(80, 28));
        g.gridx = 7;
        g.weightx = 0.1;
        bar.add(cbGpaMax, g);

        btnReset = ThemeManager.secondaryButton("Reset");
        g.gridx = 8;
        g.weightx = 0;
        bar.add(btnReset, g);

        lblResultCount = new JLabel("Results: 0");
        lblResultCount.setFont(ThemeManager.fontSmall());
        lblResultCount.setForeground(ThemeManager.muted());
        g.gridx = 0;
        g.gridy = 1;
        g.gridwidth = 9;
        g.weightx = 1;
        bar.add(lblResultCount, g);

        debounceTimer = new Timer(250, e -> search());
        debounceTimer.setRepeats(false);

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                debounceTimer.restart();
            }

            public void removeUpdate(DocumentEvent e) {
                debounceTimer.restart();
            }

            public void changedUpdate(DocumentEvent e) {
                debounceTimer.restart();
            }
        };
        tfSearch.getDocument().addDocumentListener(dl);
        cbCourse.addActionListener(e -> search());
        cbGpaMin.addActionListener(e -> search());
        cbGpaMax.addActionListener(e -> search());
        btnReset.addActionListener(e -> resetFilters());

        return bar;
    }

    private JPanel buildTablePanel() {
        // Column 6 header is dynamic — updated in search() based on course selection
        String[] cols = {
                "Student No", "First Name", "Last Name", "Gender", "Phone",
                "Enrolled Courses", "GPA", "Letter Grade", "Standing", "Status"
        };
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        resultTable = new JTable(tableModel);
        ThemeManager.styleTable(resultTable);
        resultTable.setAutoCreateRowSorter(true);
        resultTable.getColumnModel().getColumn(7).setCellRenderer(letterRenderer());
        resultTable.getColumnModel().getColumn(9).setCellRenderer(statusRenderer());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ThemeManager.surface());
        wrapper.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ThemeManager.border()), "Search Results"));
        wrapper.add(new JScrollPane(resultTable));
        return wrapper;
    }

    private void loadCourses() {
        cbCourse.removeAllItems();
        cbCourse.addItem(new CourseItem(-1, scopedUser != null ? "All My Courses" : "All Courses"));
        String sql;
        if (scopedUser != null) {
            // Teacher: only their own courses
            sql = "SELECT c.id, sub.code, sub.name, c.section, c.academic_year, c.semester " +
                    "FROM courses c JOIN subjects sub ON sub.id = c.subject_id " +
                    "JOIN teachers t ON t.id = c.teacher_id " +
                    "JOIN users u ON u.id = t.user_id " +
                    "WHERE u.id = ? " +
                    "ORDER BY c.academic_year DESC, c.semester, sub.name";
        } else {
            sql = "SELECT c.id, sub.code, sub.name, c.section, c.academic_year, c.semester " +
                    "FROM courses c JOIN subjects sub ON sub.id = c.subject_id " +
                    "ORDER BY c.academic_year DESC, c.semester, sub.name";
        }
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            if (scopedUser != null)
                ps.setInt(1, scopedUser.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String label = rs.getString("code") + " - " + rs.getString("name")
                            + " [" + rs.getString("section") + "] "
                            + rs.getInt("academic_year") + " " + rs.getString("semester");
                    cbCourse.addItem(new CourseItem(rs.getInt("id"), label));
                }
            }
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    private void search() {
        tableModel.setRowCount(0);

        String nameFilter = tfSearch.getText().trim().toLowerCase();
        CourseItem ci = (CourseItem) cbCourse.getSelectedItem();
        int courseId = ci != null ? ci.id : -1;
        double gpaMin = parseGpa((String) cbGpaMin.getSelectedItem(), 0.0);
        double gpaMax = parseGpa((String) cbGpaMax.getSelectedItem(), 4.0);

        // Update column header to reflect what GPA means in this context
        if (scopedUser != null) {
            String gpaHeader = courseId > 0 ? "Course GPA" : "Courses GPA";
            resultTable.getColumnModel().getColumn(6).setHeaderValue(gpaHeader);
            resultTable.getTableHeader().repaint();
        }

        List<StudentRow> rows = fetchStudents(nameFilter, courseId);

        int count = 0;
        for (StudentRow r : rows) {
            if (r.cgpa >= 0 && (r.cgpa < gpaMin || r.cgpa > gpaMax))
                continue;
            if (r.cgpa < 0 && !"Any".equals(cbGpaMin.getSelectedItem()))
                continue;

            double pct = cgpaToPercent(r.cgpa < 0 ? 0 : r.cgpa);
            String letter = r.cgpa < 0 ? "N/A" : GPACalculator.toLetterGrade(pct);
            String stand = r.cgpa < 0 ? "N/A" : academicStanding(r.cgpa);
            String status = r.cgpa < 0 ? "No Grades"
                    : (r.cgpa < 2.0 ? "At Risk" : (r.cgpa >= 3.5 ? "Distinction" : "Pass"));

            tableModel.addRow(new Object[] {
                    r.studentNo, r.firstName, r.lastName, r.gender, r.phone,
                    r.courseCount,
                    r.cgpa < 0 ? "N/A" : String.format("%.2f", r.cgpa),
                    letter, stand, status
            });
            count++;
        }
        lblResultCount.setText("Results: " + count + " student" + (count != 1 ? "s" : ""));
    }

    private List<StudentRow> fetchStudents(String nameFilter, int courseId) {
        List<StudentRow> list = new ArrayList<>();

        // The GPA subquery converts avg_pct → grade point using the same scale as
        // GPACalculator
        String gpaCaseExpr = "CASE " +
                "  WHEN avg_pct >= 90 THEN 4.0 WHEN avg_pct >= 85 THEN 4.0 " +
                "  WHEN avg_pct >= 80 THEN 3.75 WHEN avg_pct >= 75 THEN 3.5 " +
                "  WHEN avg_pct >= 70 THEN 3.0  WHEN avg_pct >= 65 THEN 2.75 " +
                "  WHEN avg_pct >= 60 THEN 2.5  WHEN avg_pct >= 55 THEN 2.0 " +
                "  WHEN avg_pct >= 50 THEN 1.75 WHEN avg_pct >= 45 THEN 1.0 " +
                "  ELSE 0.0 END";

        StringBuilder sql = new StringBuilder();

        if (scopedUser != null && courseId > 0) {
            // Specific course selected: GPA = grade point for that one course
            sql.append(
                    "SELECT s.id, s.student_no, s.first_name, s.last_name, s.gender, s.phone, " +
                            "       1 AS course_count, " +
                            "       (SELECT " + gpaCaseExpr.replace("avg_pct",
                                    "COALESCE(SUM((g2.score/cgc2.max_score)*cgc2.weight),NULL)")
                            +
                            "        FROM enrollments e2 " +
                            "        LEFT JOIN grades g2 ON g2.enrollment_id = e2.id " +
                            "        LEFT JOIN course_grade_components cgc2 " +
                            "               ON cgc2.id = g2.component_id AND cgc2.course_id = e2.course_id " +
                            "        WHERE e2.student_id = s.id AND e2.course_id = ?) AS scoped_gpa " +
                            "FROM students s " +
                            "JOIN enrollments e ON e.student_id = s.id " +
                            "JOIN courses c ON c.id = e.course_id " +
                            "JOIN teachers t ON t.id = c.teacher_id " +
                            "JOIN users u ON u.id = t.user_id " +
                            "WHERE u.id = ? AND c.id = ? ");
        } else if (scopedUser != null) {
            // All teacher's courses: credit-weighted GPA across all teacher's courses
            sql.append(
                    "SELECT s.id, s.student_no, s.first_name, s.last_name, s.gender, s.phone, " +
                            "       COUNT(DISTINCT e.id) AS course_count, " +
                            "       SUM(gp.grade_point * sub.credits) / NULLIF(SUM(sub.credits), 0) AS scoped_gpa " +
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
                            "WHERE u.id = ? ");
        } else {
            // Admin: global CGPA computed after fetch (kept as before)
            sql.append(
                    "SELECT s.id, s.student_no, s.first_name, s.last_name, s.gender, s.phone, " +
                            "       COUNT(DISTINCT e.id) AS course_count, NULL AS scoped_gpa " +
                            "FROM students s " +
                            "LEFT JOIN enrollments e ON e.student_id = s.id " +
                            "LEFT JOIN courses c ON c.id = e.course_id ");
            if (courseId > 0)
                sql.append("WHERE e.course_id = ? ");
            else
                sql.append("WHERE 1=1 ");
        }

        // Name filter
        if (!nameFilter.isEmpty())
            sql.append("AND (LOWER(s.first_name) LIKE ? OR LOWER(s.last_name) LIKE ? " +
                    "OR LOWER(CONCAT(s.first_name,' ',s.last_name)) LIKE ? OR LOWER(s.student_no) LIKE ?) ");

        sql.append("GROUP BY s.id, s.student_no, s.first_name, s.last_name, s.gender, s.phone ");
        sql.append("ORDER BY s.first_name, s.last_name");

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (scopedUser != null && courseId > 0) {
                ps.setInt(idx++, courseId); // subquery course filter
                ps.setInt(idx++, scopedUser.getId());
                ps.setInt(idx++, courseId);
            } else if (scopedUser != null) {
                ps.setInt(idx++, scopedUser.getId());
            } else if (courseId > 0) {
                ps.setInt(idx++, courseId);
            }
            if (!nameFilter.isEmpty()) {
                String like = "%" + nameFilter + "%";
                ps.setString(idx++, like);
                ps.setString(idx++, like);
                ps.setString(idx++, like);
                ps.setString(idx++, like);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StudentRow r = new StudentRow();
                    r.id = rs.getInt("id");
                    r.studentNo = rs.getString("student_no");
                    r.firstName = rs.getString("first_name");
                    r.lastName = rs.getString("last_name");
                    r.gender = rs.getString("gender") != null ? rs.getString("gender") : "—";
                    r.phone = rs.getString("phone") != null ? rs.getString("phone") : "—";
                    r.courseCount = rs.getInt("course_count");
                    if (scopedUser != null) {
                        double gpa = rs.getDouble("scoped_gpa");
                        r.cgpa = rs.wasNull() ? -1 : gpa;
                    } else {
                        // Admin: compute global CGPA
                        try {
                            r.cgpa = GPACalculator.getCGPA(r.id);
                        } catch (SQLException e) {
                            r.cgpa = -1;
                        }
                    }
                    list.add(r);
                }
            }
        } catch (SQLException ex) {
            showError(ex);
        }
        return list;
    }

    private void resetFilters() {
        tfSearch.setText("");
        cbCourse.setSelectedIndex(0);
        cbGpaMin.setSelectedIndex(0);
        cbGpaMax.setSelectedIndex(0);
        search();
    }

    private double parseGpa(String val, double fallback) {
        if (val == null || val.equals("Any"))
            return fallback;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double cgpaToPercent(double cgpa) {
        if (cgpa >= 4.0)
            return 90;
        if (cgpa >= 3.75)
            return 85;
        if (cgpa >= 3.5)
            return 80;
        if (cgpa >= 3.0)
            return 75;
        if (cgpa >= 2.75)
            return 70;
        if (cgpa >= 2.5)
            return 65;
        if (cgpa >= 2.0)
            return 60;
        if (cgpa >= 1.75)
            return 55;
        if (cgpa >= 1.0)
            return 50;
        return 45;
    }

    private String academicStanding(double gpa) {
        if (gpa >= 3.75)
            return "Distinction";
        if (gpa >= 3.5)
            return "Very Good";
        if (gpa >= 3.0)
            return "Good";
        if (gpa >= 2.0)
            return "Pass";
        if (gpa >= 1.0)
            return "Probation";
        return "Fail";
    }

    private JLabel boldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.fontBold());
        l.setForeground(ThemeManager.text());
        return l;
    }

    private DefaultTableCellRenderer letterRenderer() {
        return new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                String s = v != null ? v.toString() : "";
                if (!sel && !s.isEmpty()) {
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

    private DefaultTableCellRenderer statusRenderer() {
        return new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(ThemeManager.fontBold());
                String s = v != null ? v.toString() : "";
                if (!sel) {
                    switch (s) {
                        case "Distinction":
                            setBackground(ThemeManager.gradeABg());
                            setForeground(ThemeManager.gradeAFg());
                            break;
                        case "Pass":
                            setBackground(ThemeManager.gradeBBg());
                            setForeground(ThemeManager.gradeBFg());
                            break;
                        case "At Risk":
                            setBackground(ThemeManager.gradeFBg());
                            setForeground(ThemeManager.gradeFFg());
                            break;
                        case "No Grades":
                            setBackground(ThemeManager.elevated());
                            setForeground(ThemeManager.muted());
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

    private void showError(Exception ex) {
        System.err.println("Error: " + ex.getMessage());
    }

    private static class StudentRow {
        int id, courseCount;
        String studentNo, firstName, lastName, gender, phone;
        double cgpa;
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
