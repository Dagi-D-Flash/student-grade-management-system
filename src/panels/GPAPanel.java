package panels;

import models.User;
import util.DBConnection;
import util.GPACalculator;
import util.GPACalculator.SemesterGPA;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class GPAPanel extends JPanel {

    private final User user;

    private JTable semesterTable;
    private DefaultTableModel semesterModel;
    private JTable subjectTable;
    private DefaultTableModel subjectModel;

    private JLabel lblCGPA, lblCGPALetter, lblCGPAStatus;
    private JLabel lblSemGPA, lblSemCredits, lblSemCourses;
    private JButton btnRefresh;

    private int studentId = -1;

    public GPAPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());

        add(buildCGPACard(),    BorderLayout.NORTH);
        add(buildCenterSplit(), BorderLayout.CENTER);

        studentId = resolveStudentId();
        loadAll();
    }

    private JPanel buildCGPACard() {
        JPanel card = new JPanel(new BorderLayout(20, 0));
        card.setBackground(ThemeManager.surface());
        card.setBorder(ThemeManager.accentBorder(ThemeManager.accent()));

        JPanel left = new JPanel(new GridLayout(1, 3, 30, 0));
        left.setOpaque(false);

        lblCGPA       = bigLabel("—", ThemeManager.accent(), 38);
        lblCGPALetter = bigLabel("—", ThemeManager.SUCCESS,  28);
        lblCGPAStatus = bigLabel("—", ThemeManager.muted(),  16);

        left.add(statBlock("CGPA (4.0 Scale)",    lblCGPA));
        left.add(statBlock("Overall Letter Grade", lblCGPALetter));
        left.add(statBlock("Academic Standing",    lblCGPAStatus));

        btnRefresh = ThemeManager.primaryButton("Refresh");
        btnRefresh.addActionListener(e -> loadAll());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(btnRefresh);

        card.add(left,  BorderLayout.CENTER);
        card.add(right, BorderLayout.EAST);
        return card;
    }

    private JSplitPane buildCenterSplit() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildSemesterPanel(), buildSubjectPanel());
        split.setDividerLocation(420);
        split.setResizeWeight(0.45);
        split.setBorder(null);
        return split;
    }

    private JPanel buildSemesterPanel() {
        String[] cols = {"Academic Year", "Semester", "Courses", "Credits", "GPA", "Letter", "Standing"};
        semesterModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        semesterTable = new JTable(semesterModel);
        ThemeManager.styleTable(semesterTable);
        colorizeCol(semesterTable, 5);
        semesterTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onSemesterSelected();
        });

        JPanel footer = buildSemesterFooter();

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ThemeManager.surface());
        wrapper.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.border()), "GPA per Semester"));
        wrapper.add(new JScrollPane(semesterTable), BorderLayout.CENTER);
        wrapper.add(footer, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildSemesterFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 6));
        footer.setBackground(ThemeManager.elevated());
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeManager.border()));

        lblSemGPA     = new JLabel("GPA: —");
        lblSemCredits = new JLabel("Credits: —");
        lblSemCourses = new JLabel("Courses: —");

        lblSemGPA.setFont(ThemeManager.fontBold());
        lblSemGPA.setForeground(ThemeManager.text());
        lblSemCredits.setFont(ThemeManager.fontBody());
        lblSemCredits.setForeground(ThemeManager.muted());
        lblSemCourses.setFont(ThemeManager.fontBody());
        lblSemCourses.setForeground(ThemeManager.muted());

        JLabel arrow = new JLabel("Selected Semester \u2192");
        arrow.setFont(ThemeManager.fontSmall());
        arrow.setForeground(ThemeManager.muted());

        footer.add(arrow);
        footer.add(lblSemGPA);
        footer.add(lblSemCredits);
        footer.add(lblSemCourses);
        return footer;
    }

    private JPanel buildSubjectPanel() {
        String[] cols = {"Subject", "Year", "Semester", "Credits", "Avg %", "GPA Points", "Letter", "Grade Point"};
        subjectModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        subjectTable = new JTable(subjectModel);
        ThemeManager.styleTable(subjectTable);
        colorizeCol(subjectTable, 6);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ThemeManager.surface());
        wrapper.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.border()), "Subject-wise GPA Contribution"));
        wrapper.add(new JScrollPane(subjectTable));
        return wrapper;
    }

    private void loadAll() {
        semesterModel.setRowCount(0);
        subjectModel.setRowCount(0);
        if (studentId < 0) return;
        loadCGPA();
        loadSemesterTable();
        loadSubjectTable();
    }

    private void loadCGPA() {
        try {
            double cgpa = GPACalculator.getCGPA(studentId);
            if (cgpa < 0) {
                lblCGPA.setText("N/A");
                lblCGPALetter.setText("N/A");
                lblCGPAStatus.setText("No grades yet");
                lblCGPA.setForeground(ThemeManager.muted());
            } else {
                double pct = cgpaToPercent(cgpa);
                lblCGPA.setText(String.format("%.2f", cgpa));
                lblCGPA.setForeground(ThemeManager.gpaColor(cgpa));
                lblCGPALetter.setText(GPACalculator.toLetterGrade(pct));
                lblCGPALetter.setForeground(ThemeManager.gpaColor(cgpa));
                lblCGPAStatus.setText(academicStanding(cgpa));
                lblCGPAStatus.setForeground(ThemeManager.gpaColor(cgpa));
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void loadSemesterTable() {
        try {
            List<SemesterGPA> list = GPACalculator.getSemesterGPAs(studentId);
            for (SemesterGPA s : list) {
                double pct    = cgpaToPercent(s.gpa);
                String letter = s.gpa > 0 ? GPACalculator.toLetterGrade(pct) : "N/A";
                semesterModel.addRow(new Object[]{
                    s.academicYear, s.semester, s.courseCount, s.totalCredits,
                    String.format("%.2f", s.gpa), letter, academicStanding(s.gpa)
                });
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void loadSubjectTable() {
        String sql =
            "SELECT sub.code, sub.name, c.academic_year, c.semester, sub.credits, " +
            "       SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM enrollments e " +
            "JOIN courses c    ON c.id   = e.course_id " +
            "JOIN subjects sub ON sub.id = c.subject_id " +
            "JOIN grades g     ON g.enrollment_id = e.id " +
            "JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "WHERE e.student_id = ? " +
            "GROUP BY e.id, sub.code, sub.name, c.academic_year, c.semester, sub.credits " +
            "ORDER BY c.academic_year, c.semester, sub.code";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double avg     = rs.getDouble("avg_pct");
                    double gp      = GPACalculator.toGradePoint(avg);
                    int    credits = rs.getInt("credits");
                    subjectModel.addRow(new Object[]{
                        rs.getString("code") + " - " + rs.getString("name"),
                        rs.getInt("academic_year"),
                        rs.getString("semester"),
                        credits,
                        String.format("%.2f", avg),
                        String.format("%.2f", gp * credits),
                        GPACalculator.toLetterGrade(avg),
                        String.format("%.1f", gp)
                    });
                }
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void onSemesterSelected() {
        int row = semesterTable.getSelectedRow();
        if (row < 0) {
            lblSemGPA.setText("GPA: —");
            lblSemCredits.setText("Credits: —");
            lblSemCourses.setText("Courses: —");
            return;
        }
        lblSemGPA.setText("GPA: " + semesterModel.getValueAt(row, 4));
        lblSemCredits.setText("Credits: " + semesterModel.getValueAt(row, 3));
        lblSemCourses.setText("Courses: " + semesterModel.getValueAt(row, 2));
        filterSubjectTable(
            String.valueOf(semesterModel.getValueAt(row, 0)),
            (String) semesterModel.getValueAt(row, 1)
        );
    }

    private void filterSubjectTable(String year, String sem) {
        subjectModel.setRowCount(0);
        String sql =
            "SELECT sub.code, sub.name, c.academic_year, c.semester, sub.credits, " +
            "       SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM enrollments e " +
            "JOIN courses c    ON c.id   = e.course_id " +
            "JOIN subjects sub ON sub.id = c.subject_id " +
            "JOIN grades g     ON g.enrollment_id = e.id " +
            "JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "WHERE e.student_id = ? AND c.academic_year = ? AND c.semester = ? " +
            "GROUP BY e.id, sub.code, sub.name, c.academic_year, c.semester, sub.credits";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, Integer.parseInt(year));
            ps.setString(3, sem);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double avg     = rs.getDouble("avg_pct");
                    double gp      = GPACalculator.toGradePoint(avg);
                    int    credits = rs.getInt("credits");
                    subjectModel.addRow(new Object[]{
                        rs.getString("code") + " - " + rs.getString("name"),
                        rs.getInt("academic_year"),
                        rs.getString("semester"),
                        credits,
                        String.format("%.2f", avg),
                        String.format("%.2f", gp * credits),
                        GPACalculator.toLetterGrade(avg),
                        String.format("%.1f", gp)
                    });
                }
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private int resolveStudentId() {
        String sql = "SELECT id FROM students WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException ex) { showError(ex); }
        return -1;
    }

    private double cgpaToPercent(double cgpa) {
        if (cgpa >= 4.0)  return 90;
        if (cgpa >= 3.75) return 85;
        if (cgpa >= 3.5)  return 80;
        if (cgpa >= 3.0)  return 75;
        if (cgpa >= 2.75) return 70;
        if (cgpa >= 2.5)  return 65;
        if (cgpa >= 2.0)  return 60;
        if (cgpa >= 1.75) return 55;
        if (cgpa >= 1.0)  return 50;
        return 45;
    }

    private String academicStanding(double gpa) {
        if (gpa >= 3.75) return "Distinction";
        if (gpa >= 3.5)  return "Very Good";
        if (gpa >= 3.0)  return "Good";
        if (gpa >= 2.0)  return "Pass";
        if (gpa >= 1.0)  return "Probation";
        return "Fail";
    }

    private JPanel statBlock(String title, JLabel valueLabel) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel t = new JLabel(title, SwingConstants.CENTER);
        t.setFont(ThemeManager.fontSmall());
        t.setForeground(ThemeManager.muted());
        p.add(t,          BorderLayout.NORTH);
        p.add(valueLabel, BorderLayout.CENTER);
        return p;
    }

    private JLabel bigLabel(String text, Color color, int size) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", Font.BOLD, size));
        l.setForeground(color);
        return l;
    }

    private void colorizeCol(JTable table, int col) {
        table.getColumnModel().getColumn(col).setCellRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean sel, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, sel, focus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                String v = value != null ? value.toString() : "";
                if (!sel) {
                    char c = v.isEmpty() ? ' ' : v.charAt(0);
                    switch (c) {
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
        });
    }

    private void showError(Exception ex) {
        System.err.println("Error: " + ex.getMessage());
    }
}
