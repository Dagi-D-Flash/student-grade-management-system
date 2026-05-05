package panels;

import models.User;
import util.DBConnection;
import util.GPACalculator;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class TeacherAnalysisPanel extends JPanel {

    private final User teacher;   // null = admin (no scoping)

    private JComboBox<StudentItem> cbStudent;
    private JComboBox<CourseItem>  cbCourse;   // teacher-only course filter
    private final java.util.List<StudentItem> allStudents = new java.util.ArrayList<>();
    private JTextField tfSearch;
    private JLabel lblStatus, lblStatusIcon, lblGPA, lblStanding;
    private JLabel lblWeakSubject, lblWeakGP, lblWeakLetter;
    private JLabel lblTrendIcon, lblTrend;
    private JPanel statusCard, weakCard, trendCard;
    private JTable subjectTable;
    private DefaultTableModel subjectModel;
    private JButton btnRefresh;
    private Timer autoRefresh;

    public TeacherAnalysisPanel() { this(null); }

    public TeacherAnalysisPanel(User teacher) {
        this.teacher = teacher;
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());

        add(buildToolbar(),    BorderLayout.NORTH);
        add(buildCards(),      BorderLayout.CENTER);
        add(buildTablePanel(), BorderLayout.SOUTH);

        if (teacher != null) loadCourses(); else loadStudents();

        autoRefresh = new Timer(30_000, e -> load());
        autoRefresh.start();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Returns the currently selected course id, or -1 for "all". */
    private int selectedCourseId() {
        if (cbCourse == null) return -1;
        CourseItem ci = (CourseItem) cbCourse.getSelectedItem();
        return ci != null ? ci.id : -1;
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(ThemeManager.cardBorder());

        // Empty left spacer — same pattern as Charts panel
        JPanel left = new JPanel();
        left.setOpaque(false);
        bar.add(left, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);

        // Course filter — teacher only
        if (teacher != null) {
            JLabel courseLbl = new JLabel("Course:");
            courseLbl.setFont(ThemeManager.fontBold());
            courseLbl.setForeground(ThemeManager.muted());
            cbCourse = new JComboBox<>();
            cbCourse.setPreferredSize(new Dimension(260, 28));
            cbCourse.addActionListener(e -> {
                loadStudents();   // re-scope student list to selected course
            });
            controls.add(courseLbl);
            controls.add(cbCourse);
        }

        // Search field
        JLabel searchIcon = new JLabel("\uD83D\uDD0D");
        searchIcon.setForeground(ThemeManager.muted());
        tfSearch = new JTextField(16);
        tfSearch.setBackground(ThemeManager.elevated());
        tfSearch.setForeground(ThemeManager.text());
        tfSearch.setCaretColor(ThemeManager.text());
        tfSearch.setFont(ThemeManager.fontBody());
        tfSearch.putClientProperty("JTextField.placeholderText", "Search by name or ID...");
        tfSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filterStudents(tfSearch.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filterStudents(tfSearch.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterStudents(tfSearch.getText()); }
        });

        JLabel lbl = new JLabel("Student:");
        lbl.setForeground(ThemeManager.muted());
        cbStudent = new JComboBox<>();
        cbStudent.setPreferredSize(new Dimension(240, 28));
        cbStudent.addActionListener(e -> load());

        btnRefresh = ThemeManager.primaryButton("Refresh");
        btnRefresh.addActionListener(e -> {
            if (teacher != null) loadCourses(); else loadStudents();
        });

        controls.add(searchIcon);
        controls.add(tfSearch);
        controls.add(lbl);
        controls.add(cbStudent);
        controls.add(btnRefresh);

        bar.add(controls, BorderLayout.EAST);
        return bar;
    }

    /** Loads teacher's courses into cbCourse, then triggers loadStudents(). */
    private void loadCourses() {
        if (cbCourse == null) return;
        cbCourse.removeAllItems();
        cbCourse.addItem(new CourseItem(-1, "All My Courses"));
        String sql =
            "SELECT c.id, s.code, s.name, c.section, c.academic_year, c.semester " +
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
        } catch (SQLException ex) { showError(ex); }
        loadStudents();
    }

    private void filterStudents(String query) {
        String q = query.trim().toLowerCase();
        StudentItem prev = (StudentItem) cbStudent.getSelectedItem();

        cbStudent.removeAllItems();
        for (StudentItem item : allStudents) {
            String label     = item.label.toLowerCase();
            String stripped  = label.replace("wour/", "").replace("/", "");
            String qStripped = q.replace("wour/", "").replace("/", "");
            if (q.isEmpty() || label.contains(q) || stripped.contains(qStripped))
                cbStudent.addItem(item);
        }

        if (prev != null) {
            for (int i = 0; i < cbStudent.getItemCount(); i++) {
                if (cbStudent.getItemAt(i).id == prev.id) {
                    cbStudent.setSelectedIndex(i);
                    return;
                }
            }
        }
        if (cbStudent.getItemCount() > 0) cbStudent.setSelectedIndex(0);
    }

    private JPanel buildCards() {
        JPanel row = new JPanel(new GridLayout(1, 3, 14, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(0, 200));

        statusCard = emptyCard();
        weakCard   = emptyCard();
        trendCard  = emptyCard();

        JPanel statusInner = new JPanel(new GridLayout(4, 1, 0, 6));
        statusInner.setOpaque(false);
        lblStatusIcon = centeredLabel("—", 36, Font.PLAIN, ThemeManager.muted());
        lblStatus     = centeredLabel("—", 18, Font.BOLD,  ThemeManager.text());
        lblGPA        = centeredLabel("GPA: —", 14, Font.PLAIN, ThemeManager.muted());
        lblStanding   = centeredLabel("—", 12, Font.PLAIN, ThemeManager.muted());
        statusInner.add(lblStatusIcon); statusInner.add(lblStatus);
        statusInner.add(lblGPA);        statusInner.add(lblStanding);
        statusCard.add(sectionTitle("Performance Status"), BorderLayout.NORTH);
        statusCard.add(statusInner, BorderLayout.CENTER);

        JPanel weakInner = new JPanel(new GridLayout(3, 1, 0, 6));
        weakInner.setOpaque(false);
        lblWeakSubject = centeredLabel("—", 15, Font.BOLD,  ThemeManager.danger());
        lblWeakGP      = centeredLabel("Grade Point: —", 13, Font.PLAIN, ThemeManager.muted());
        lblWeakLetter  = centeredLabel("—", 28, Font.BOLD,  ThemeManager.danger());
        weakInner.add(lblWeakSubject); weakInner.add(lblWeakLetter); weakInner.add(lblWeakGP);
        weakCard.add(sectionTitle("Weakest Subject"), BorderLayout.NORTH);
        weakCard.add(weakInner, BorderLayout.CENTER);

        JPanel trendInner = new JPanel(new GridLayout(2, 1, 0, 6));
        trendInner.setOpaque(false);
        lblTrendIcon = centeredLabel("—", 40, Font.PLAIN, ThemeManager.muted());
        lblTrend     = centeredLabel("—", 14, Font.BOLD,  ThemeManager.text());
        trendInner.add(lblTrendIcon); trendInner.add(lblTrend);
        trendCard.add(sectionTitle("GPA Trend"), BorderLayout.NORTH);
        trendCard.add(trendInner, BorderLayout.CENTER);

        row.add(statusCard); row.add(weakCard); row.add(trendCard);
        return row;
    }

    private JPanel buildTablePanel() {
        String[] cols = {"Subject", "Semester", "Avg %", "Grade Point", "Letter", "Status"};
        subjectModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        subjectTable = new JTable(subjectModel);
        ThemeManager.styleTable(subjectTable);
        subjectTable.getColumnModel().getColumn(4).setCellRenderer(letterRenderer());
        subjectTable.getColumnModel().getColumn(5).setCellRenderer(statusRenderer());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ThemeManager.surface());
        wrapper.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.border()), "Subject-wise Analysis"));
        wrapper.setPreferredSize(new Dimension(0, 260));
        wrapper.add(new JScrollPane(subjectTable));
        return wrapper;
    }

    private void loadStudents() {
        allStudents.clear();
        cbStudent.removeAllItems();
        int courseId = selectedCourseId();

        String sql;
        if (teacher != null && courseId > 0) {
            // Specific course selected
            sql = "SELECT DISTINCT s.id, s.student_no, s.first_name, s.last_name " +
                  "FROM students s " +
                  "JOIN enrollments e ON e.student_id = s.id " +
                  "WHERE e.course_id = ? " +
                  "ORDER BY s.first_name, s.last_name";
        } else if (teacher != null) {
            // All teacher's courses
            sql = "SELECT DISTINCT s.id, s.student_no, s.first_name, s.last_name " +
                  "FROM students s " +
                  "JOIN enrollments e ON e.student_id = s.id " +
                  "JOIN courses c ON c.id = e.course_id " +
                  "JOIN teachers t ON t.id = c.teacher_id " +
                  "JOIN users u ON u.id = t.user_id " +
                  "WHERE u.id = ? " +
                  "ORDER BY s.first_name, s.last_name";
        } else {
            sql = "SELECT id, student_no, first_name, last_name FROM students ORDER BY first_name, last_name";
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (teacher != null && courseId > 0) ps.setInt(1, courseId);
            else if (teacher != null)             ps.setInt(1, teacher.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StudentItem item = new StudentItem(rs.getInt("id"),
                        rs.getString("student_no") + " - " +
                        rs.getString("first_name") + " " + rs.getString("last_name"));
                    allStudents.add(item);
                    cbStudent.addItem(item);
                }
            }
        } catch (SQLException ex) { showError(ex); }
        if (cbStudent.getItemCount() > 0) load();
    }

    private void load() {
        StudentItem si = (StudentItem) cbStudent.getSelectedItem();
        if (si == null) return;
        int sid = si.id;
        SwingUtilities.invokeLater(() -> {
            loadStatus(sid);
            loadWeak(sid);
            loadTrend(sid);
            loadTable(sid);
        });
    }

    private void loadStatus(int sid) {
        int courseId = selectedCourseId();
        try {
            double gpa;
            String gpaLabel;
            if (teacher != null) {
                // Compute credit-weighted GPA scoped to teacher's courses (or specific course)
                gpa = computeScopedGpa(sid, courseId);
                gpaLabel = courseId > 0 ? "Course GPA" : "Courses GPA";
            } else {
                gpa = GPACalculator.getCGPA(sid);
                gpaLabel = "CGPA";
            }
            if (gpa < 0) {
                lblStatusIcon.setText("?"); lblStatus.setText("No Data");
                lblGPA.setText(gpaLabel + ": N/A"); lblStanding.setText("—");
                statusCard.setBorder(colorBorder(ThemeManager.border())); return;
            }
            lblGPA.setText(String.format(gpaLabel + ": %.2f / 4.0", gpa));
            lblStanding.setText(standing(gpa));
            if (gpa < 2.0) {
                set(lblStatusIcon, "\u26A0", ThemeManager.danger());
                set(lblStatus, "At Risk", ThemeManager.danger());
                statusCard.setBorder(colorBorder(ThemeManager.danger()));
            } else if (gpa >= 3.5) {
                set(lblStatusIcon, "\u2605", ThemeManager.SUCCESS);
                set(lblStatus, "Distinction", ThemeManager.SUCCESS);
                statusCard.setBorder(colorBorder(ThemeManager.SUCCESS));
            } else if (gpa >= 3.0) {
                set(lblStatusIcon, "\u2714", ThemeManager.INFO);
                set(lblStatus, "Good", ThemeManager.INFO);
                statusCard.setBorder(colorBorder(ThemeManager.INFO));
            } else if (gpa >= 2.0) {
                set(lblStatusIcon, "\u2197", ThemeManager.WARNING);
                set(lblStatus, "Pass", ThemeManager.WARNING);
                statusCard.setBorder(colorBorder(ThemeManager.WARNING));
            } else {
                set(lblStatusIcon, "\u26A0", ThemeManager.danger());
                set(lblStatus, "Probation", ThemeManager.danger());
                statusCard.setBorder(colorBorder(ThemeManager.danger()));
            }
        } catch (SQLException ex) { showError(ex); }
    }

    /**
     * Computes credit-weighted GPA for a student scoped to:
     * - a specific course (courseId > 0), or
     * - all of this teacher's courses (courseId = -1)
     */
    private double computeScopedGpa(int studentId, int courseId) throws SQLException {
        String sql;
        if (courseId > 0) {
            sql = "SELECT SUM((g.score/cgc.max_score)*cgc.weight) AS avg_pct, sub.credits " +
                  "FROM enrollments e " +
                  "JOIN courses c ON c.id = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "LEFT JOIN grades g ON g.enrollment_id = e.id " +
                  "LEFT JOIN course_grade_components cgc " +
                  "       ON cgc.id = g.component_id AND cgc.course_id = c.id " +
                  "WHERE e.student_id = ? AND e.course_id = ? " +
                  "GROUP BY e.id, sub.credits";
        } else {
            sql = "SELECT SUM((g.score/cgc.max_score)*cgc.weight) AS avg_pct, sub.credits " +
                  "FROM enrollments e " +
                  "JOIN courses c ON c.id = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "JOIN teachers t ON t.id = c.teacher_id " +
                  "JOIN users u ON u.id = t.user_id " +
                  "LEFT JOIN grades g ON g.enrollment_id = e.id " +
                  "LEFT JOIN course_grade_components cgc " +
                  "       ON cgc.id = g.component_id AND cgc.course_id = c.id " +
                  "WHERE e.student_id = ? AND u.id = ? " +
                  "GROUP BY e.id, sub.credits";
        }
        double totalPoints = 0; int totalCredits = 0; boolean hasData = false;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, courseId > 0 ? courseId : teacher.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double avg = rs.getDouble("avg_pct");
                    if (rs.wasNull()) continue;
                    int cr = rs.getInt("credits");
                    totalPoints  += GPACalculator.toGradePoint(avg) * cr;
                    totalCredits += cr;
                    hasData = true;
                }
            }
        }
        return (hasData && totalCredits > 0) ? totalPoints / totalCredits : -1;
    }

    private void loadWeak(int sid) {
        int courseId = selectedCourseId();
        String sql;
        if (teacher != null && courseId > 0) {
            sql = "SELECT sub.name, SUM((g.score/cgc.max_score)*cgc.weight) AS avg_pct " +
                  "FROM grades g " +
                  "JOIN enrollments e ON e.id = g.enrollment_id " +
                  "JOIN courses c ON c.id = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "JOIN course_grade_components cgc ON cgc.id = g.component_id AND cgc.course_id = c.id " +
                  "WHERE e.student_id = ? AND c.id = ? " +
                  "GROUP BY e.id, sub.name ORDER BY avg_pct ASC LIMIT 1";
        } else if (teacher != null) {
            sql = "SELECT sub.name, SUM((g.score/cgc.max_score)*cgc.weight) AS avg_pct " +
                  "FROM grades g " +
                  "JOIN enrollments e ON e.id = g.enrollment_id " +
                  "JOIN courses c ON c.id = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "JOIN course_grade_components cgc ON cgc.id = g.component_id AND cgc.course_id = c.id " +
                  "JOIN teachers t ON t.id = c.teacher_id " +
                  "JOIN users u ON u.id = t.user_id " +
                  "WHERE e.student_id = ? AND u.id = ? " +
                  "GROUP BY e.id, sub.name ORDER BY avg_pct ASC LIMIT 1";
        } else {
            sql = "SELECT sub.name, SUM((g.score/cgc.max_score)*cgc.weight) AS avg_pct " +
                  "FROM grades g " +
                  "JOIN enrollments e ON e.id = g.enrollment_id " +
                  "JOIN courses c ON c.id = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "JOIN course_grade_components cgc ON cgc.id = g.component_id AND cgc.course_id = c.id " +
                  "WHERE e.student_id = ? " +
                  "GROUP BY e.id, sub.name ORDER BY avg_pct ASC LIMIT 1";
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sid);
            if (teacher != null) ps.setInt(2, courseId > 0 ? courseId : teacher.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double avg = rs.getDouble("avg_pct");
                    double gp  = GPACalculator.toGradePoint(avg);
                    Color c    = avg < 60 ? ThemeManager.danger() : ThemeManager.WARNING;
                    set(lblWeakSubject, rs.getString("name"), c);
                    set(lblWeakLetter, GPACalculator.toLetterGrade(avg), c);
                    lblWeakGP.setText(String.format("Grade Point: %.1f  |  Avg: %.1f%%", gp, avg));
                    weakCard.setBorder(colorBorder(c));
                } else {
                    set(lblWeakSubject, "No grades yet", ThemeManager.muted());
                    lblWeakGP.setText("—"); lblWeakLetter.setText("—");
                    weakCard.setBorder(colorBorder(ThemeManager.border()));
                }
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void loadTrend(int sid) {
        int courseId = selectedCourseId();
        String sql;
        if (teacher != null && courseId > 0) {
            sql = "SELECT c.academic_year, c.semester, sub.credits, " +
                  "       SUM((g.score/cgc.max_score)*cgc.weight) AS avg_pct " +
                  "FROM grades g " +
                  "JOIN enrollments e ON e.id = g.enrollment_id " +
                  "JOIN courses c ON c.id = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "JOIN course_grade_components cgc ON cgc.id = g.component_id AND cgc.course_id = c.id " +
                  "WHERE e.student_id = ? AND c.id = ? " +
                  "GROUP BY e.id, c.academic_year, c.semester, sub.credits ORDER BY c.academic_year, c.semester";
        } else if (teacher != null) {
            sql = "SELECT c.academic_year, c.semester, sub.credits, " +
                  "       SUM((g.score/cgc.max_score)*cgc.weight) AS avg_pct " +
                  "FROM grades g " +
                  "JOIN enrollments e ON e.id = g.enrollment_id " +
                  "JOIN courses c ON c.id = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "JOIN course_grade_components cgc ON cgc.id = g.component_id AND cgc.course_id = c.id " +
                  "JOIN teachers t ON t.id = c.teacher_id " +
                  "JOIN users u ON u.id = t.user_id " +
                  "WHERE e.student_id = ? AND u.id = ? " +
                  "GROUP BY e.id, c.academic_year, c.semester, sub.credits ORDER BY c.academic_year, c.semester";
        } else {
            sql = "SELECT c.academic_year, c.semester, sub.credits, " +
                  "       SUM((g.score/cgc.max_score)*cgc.weight) AS avg_pct " +
                  "FROM grades g " +
                  "JOIN enrollments e ON e.id = g.enrollment_id " +
                  "JOIN courses c ON c.id = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "JOIN course_grade_components cgc ON cgc.id = g.component_id AND cgc.course_id = c.id " +
                  "WHERE e.student_id = ? " +
                  "GROUP BY e.id, c.academic_year, c.semester, sub.credits ORDER BY c.academic_year, c.semester";
        }
        LinkedHashMap<String, double[]> map = new LinkedHashMap<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sid);
            if (teacher != null) ps.setInt(2, courseId > 0 ? courseId : teacher.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getInt("academic_year") + "|" + rs.getString("semester");
                    int cr = rs.getInt("credits");
                    double gp = GPACalculator.toGradePoint(rs.getDouble("avg_pct"));
                    map.merge(key, new double[]{gp * cr, cr}, (a, b) -> new double[]{a[0]+b[0], a[1]+b[1]});
                }
            }
        } catch (SQLException ex) { showError(ex); }

        List<Double> gpas = new ArrayList<>();
        for (double[] v : map.values()) gpas.add(v[1] > 0 ? v[0] / v[1] : 0.0);

        if (gpas.size() < 2) {
            lblTrendIcon.setText("—"); lblTrend.setText("Not enough data");
            lblTrend.setForeground(ThemeManager.muted());
            trendCard.setBorder(colorBorder(ThemeManager.border())); return;
        }
        double diff = gpas.get(gpas.size()-1) - gpas.get(gpas.size()-2);
        if (diff > 0.1) {
            set(lblTrendIcon, "\u2191", ThemeManager.SUCCESS);
            set(lblTrend, String.format("Improving (+%.2f)", diff), ThemeManager.SUCCESS);
            trendCard.setBorder(colorBorder(ThemeManager.SUCCESS));
        } else if (diff < -0.1) {
            set(lblTrendIcon, "\u2193", ThemeManager.danger());
            set(lblTrend, String.format("Declining (%.2f)", diff), ThemeManager.danger());
            trendCard.setBorder(colorBorder(ThemeManager.danger()));
        } else {
            set(lblTrendIcon, "\u2192", ThemeManager.WARNING);
            set(lblTrend, "Stable", ThemeManager.WARNING);
            trendCard.setBorder(colorBorder(ThemeManager.WARNING));
        }
    }

    private void loadTable(int sid) {
        subjectModel.setRowCount(0);
        int courseId = selectedCourseId();
        String sql;
        if (teacher != null && courseId > 0) {
            sql = "SELECT sub.name, c.semester, c.academic_year, " +
                  "       SUM((g.score/cgc.max_score)*cgc.weight) AS avg_pct " +
                  "FROM grades g " +
                  "JOIN enrollments e ON e.id = g.enrollment_id " +
                  "JOIN courses c ON c.id = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "JOIN course_grade_components cgc ON cgc.id = g.component_id AND cgc.course_id = c.id " +
                  "WHERE e.student_id = ? AND c.id = ? " +
                  "GROUP BY e.id, sub.name, c.semester, c.academic_year ORDER BY avg_pct ASC";
        } else if (teacher != null) {
            sql = "SELECT sub.name, c.semester, c.academic_year, " +
                  "       SUM((g.score/cgc.max_score)*cgc.weight) AS avg_pct " +
                  "FROM grades g " +
                  "JOIN enrollments e ON e.id = g.enrollment_id " +
                  "JOIN courses c ON c.id = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "JOIN course_grade_components cgc ON cgc.id = g.component_id AND cgc.course_id = c.id " +
                  "JOIN teachers t ON t.id = c.teacher_id " +
                  "JOIN users u ON u.id = t.user_id " +
                  "WHERE e.student_id = ? AND u.id = ? " +
                  "GROUP BY e.id, sub.name, c.semester, c.academic_year ORDER BY avg_pct ASC";
        } else {
            sql = "SELECT sub.name, c.semester, c.academic_year, " +
                  "       SUM((g.score/cgc.max_score)*cgc.weight) AS avg_pct " +
                  "FROM grades g " +
                  "JOIN enrollments e ON e.id = g.enrollment_id " +
                  "JOIN courses c ON c.id = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "JOIN course_grade_components cgc ON cgc.id = g.component_id AND cgc.course_id = c.id " +
                  "WHERE e.student_id = ? " +
                  "GROUP BY e.id, sub.name, c.semester, c.academic_year ORDER BY avg_pct ASC";
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sid);
            if (teacher != null) ps.setInt(2, courseId > 0 ? courseId : teacher.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double avg = rs.getDouble("avg_pct");
                    double gp  = GPACalculator.toGradePoint(avg);
                    subjectModel.addRow(new Object[]{
                        rs.getString("name"),
                        rs.getInt("academic_year") + " " + rs.getString("semester"),
                        String.format("%.2f", avg),
                        String.format("%.2f", gp),
                        GPACalculator.toLetterGrade(avg),
                        subjectStatus(avg)
                    });
                }
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private String subjectStatus(double avg) {
        if (avg >= 90) return "Distinction";
        if (avg >= 75) return "Very Good";
        if (avg >= 60) return "Good";
        if (avg >  50) return "Pass";
        if (avg >= 40) return "Probation";
        return "Fail";
    }

    private String standing(double gpa) {
        if (gpa >= 3.75) return "Distinction";
        if (gpa >= 3.5)  return "Very Good";
        if (gpa >= 3.0)  return "Good";
        if (gpa >= 2.0)  return "Pass";
        if (gpa >= 1.0)  return "Probation";
        return "Fail";
    }

    private void set(JLabel l, String text, Color color) { l.setText(text); l.setForeground(color); }

    private JPanel emptyCard() {
        JPanel c = new JPanel(new BorderLayout(0, 8));
        c.setBackground(ThemeManager.surface());
        c.setBorder(colorBorder(ThemeManager.border()));
        return c;
    }

    private javax.swing.border.Border colorBorder(Color color) {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color, 2),
            new EmptyBorder(14, 14, 14, 14)
        );
    }

    private JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.fontBold());
        l.setForeground(ThemeManager.muted());
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return l;
    }

    private JLabel centeredLabel(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }

    private DefaultTableCellRenderer letterRenderer() {
        return new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                setHorizontalAlignment(SwingConstants.CENTER);
                String s = v != null ? v.toString() : "";
                if (!sel && !s.isEmpty()) {
                    switch (s.charAt(0)) {
                        case 'A': setBackground(ThemeManager.gradeABg()); setForeground(ThemeManager.gradeAFg()); break;
                        case 'B': setBackground(ThemeManager.gradeBBg()); setForeground(ThemeManager.gradeBFg()); break;
                        case 'C': setBackground(ThemeManager.gradeCBg()); setForeground(ThemeManager.gradeCFg()); break;
                        case 'D': setBackground(ThemeManager.gradeDBg()); setForeground(ThemeManager.gradeDFg()); break;
                        case 'F': setBackground(ThemeManager.gradeFBg()); setForeground(ThemeManager.gradeFFg()); break;
                        default:  setBackground(ThemeManager.surface()); setForeground(ThemeManager.text());
                    }
                }
                return this;
            }
        };
    }

    private DefaultTableCellRenderer statusRenderer() {
        return new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(ThemeManager.fontBold());
                String s = v != null ? v.toString() : "";
                if (!sel) {
                    switch (s) {
                        case "Distinction": setBackground(ThemeManager.gradeABg()); setForeground(ThemeManager.gradeAFg()); break;
                        case "Very Good":   setBackground(ThemeManager.gradeBBg()); setForeground(ThemeManager.gradeBFg()); break;
                        case "Good":        setBackground(ThemeManager.gradeCBg()); setForeground(ThemeManager.gradeCFg()); break;
                        case "Pass":        setBackground(ThemeManager.gradeDBg()); setForeground(ThemeManager.gradeDFg()); break;
                        case "Probation":
                        case "Fail":        setBackground(ThemeManager.gradeFBg()); setForeground(ThemeManager.gradeFFg()); break;
                        default: setBackground(ThemeManager.surface()); setForeground(ThemeManager.text());
                    }
                }
                return this;
            }
        };
    }

    private void showError(Exception ex) {
        System.err.println("Error: " + ex.getMessage());
    }

    private static class StudentItem {
        int id; String label;
        StudentItem(int id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }

    private static class CourseItem {
        final int id; final String label;
        CourseItem(int id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }
}
