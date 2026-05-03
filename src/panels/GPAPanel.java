package panels;

import models.User;
import util.DBConnection;
import util.GPACalculator;
import util.GPACalculator.SemesterGPA;

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
    private JComboBox<String> cbSemester;
    private JButton btnRefresh;

    private int studentId = -1;

    public GPAPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        setBackground(new Color(245, 246, 250));

        add(buildCGPACard(),     BorderLayout.NORTH);
        add(buildCenterSplit(),  BorderLayout.CENTER);

        studentId = resolveStudentId();
        loadAll();
    }

    private JPanel buildCGPACard() {
        JPanel card = new JPanel(new BorderLayout(20, 0));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(13, 110, 253), 2),
            BorderFactory.createEmptyBorder(16, 24, 16, 24)
        ));

        JPanel left = new JPanel(new GridLayout(1, 3, 30, 0));
        left.setOpaque(false);

        lblCGPA       = bigLabel("—", new Color(13, 110, 253), 38);
        lblCGPALetter = bigLabel("—", new Color(25, 135, 84),  28);
        lblCGPAStatus = bigLabel("—", new Color(108, 117, 125), 16);

        left.add(statBlock("CGPA (4.0 Scale)", lblCGPA));
        left.add(statBlock("Overall Letter Grade", lblCGPALetter));
        left.add(statBlock("Academic Standing",    lblCGPAStatus));

        btnRefresh = new JButton("Refresh");
        btnRefresh.setBackground(new Color(13, 110, 253));
        btnRefresh.setForeground(Color.WHITE);
        btnRefresh.setFocusPainted(false);
        btnRefresh.setBorderPainted(false);
        btnRefresh.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
        semesterTable.setRowHeight(28);
        semesterTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        semesterTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        semesterTable.setGridColor(new Color(230, 230, 230));
        semesterTable.setSelectionBackground(new Color(210, 230, 255));
        colorizeCol(semesterTable, 5);
        semesterTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onSemesterSelected();
        });

        JPanel footer = buildSemesterFooter();

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder("GPA per Semester"));
        wrapper.add(new JScrollPane(semesterTable), BorderLayout.CENTER);
        wrapper.add(footer, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildSemesterFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 6));
        footer.setBackground(new Color(240, 248, 255));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 220, 240)));

        lblSemGPA     = new JLabel("GPA: —");
        lblSemCredits = new JLabel("Credits: —");
        lblSemCourses = new JLabel("Courses: —");

        lblSemGPA.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblSemCredits.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblSemCourses.setFont(new Font("SansSerif", Font.PLAIN, 12));

        footer.add(new JLabel("Selected Semester →"));
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
        subjectTable.setRowHeight(28);
        subjectTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subjectTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        subjectTable.setGridColor(new Color(230, 230, 230));
        subjectTable.setSelectionBackground(new Color(210, 230, 255));
        colorizeCol(subjectTable, 6);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder("Subject-wise GPA Contribution"));
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
                lblCGPA.setForeground(new Color(108, 117, 125));
            } else {
                double pct = cgpaToPercent(cgpa);
                lblCGPA.setText(String.format("%.2f", cgpa));
                lblCGPA.setForeground(GPACalculator.gpaColor(cgpa));
                lblCGPALetter.setText(GPACalculator.toLetterGrade(pct));
                lblCGPALetter.setForeground(GPACalculator.gpaColor(cgpa));
                lblCGPAStatus.setText(academicStanding(cgpa));
                lblCGPAStatus.setForeground(GPACalculator.gpaColor(cgpa));
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
                    s.academicYear,
                    s.semester,
                    s.courseCount,
                    s.totalCredits,
                    String.format("%.2f", s.gpa),
                    letter,
                    academicStanding(s.gpa)
                });
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void loadSubjectTable() {
        String sql =
            "SELECT sub.code, sub.name, c.academic_year, c.semester, sub.credits, " +
            "       SUM(g.score / g.max_score * 100 * g.weight) / SUM(g.weight) AS avg_pct " +
            "FROM enrollments e " +
            "JOIN courses c    ON c.id   = e.course_id " +
            "JOIN subjects sub ON sub.id = c.subject_id " +
            "JOIN grades g     ON g.enrollment_id = e.id AND g.max_score > 0 " +
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
        String gpa     = (String) semesterModel.getValueAt(row, 4);
        String credits = String.valueOf(semesterModel.getValueAt(row, 3));
        String courses = String.valueOf(semesterModel.getValueAt(row, 2));
        String year    = String.valueOf(semesterModel.getValueAt(row, 0));
        String sem     = (String) semesterModel.getValueAt(row, 1);

        lblSemGPA.setText("GPA: " + gpa);
        lblSemCredits.setText("Credits: " + credits);
        lblSemCourses.setText("Courses: " + courses);

        filterSubjectTable(year, sem);
    }

    private void filterSubjectTable(String year, String sem) {
        subjectModel.setRowCount(0);
        String sql =
            "SELECT sub.code, sub.name, c.academic_year, c.semester, sub.credits, " +
            "       SUM(g.score / g.max_score * 100 * g.weight) / SUM(g.weight) AS avg_pct " +
            "FROM enrollments e " +
            "JOIN courses c    ON c.id   = e.course_id " +
            "JOIN subjects sub ON sub.id = c.subject_id " +
            "JOIN grades g     ON g.enrollment_id = e.id AND g.max_score > 0 " +
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
        if (cgpa >= 4.0) return 97;
        if (cgpa >= 3.7) return 90;
        if (cgpa >= 3.3) return 87;
        if (cgpa >= 3.0) return 83;
        if (cgpa >= 2.7) return 80;
        if (cgpa >= 2.3) return 77;
        if (cgpa >= 2.0) return 73;
        if (cgpa >= 1.7) return 70;
        if (cgpa >= 1.3) return 67;
        if (cgpa >= 1.0) return 63;
        if (cgpa >= 0.7) return 60;
        return 0;
    }

    private String academicStanding(double gpa) {
        if (gpa >= 3.7) return "Summa Cum Laude";
        if (gpa >= 3.5) return "Magna Cum Laude";
        if (gpa >= 3.0) return "Cum Laude";
        if (gpa >= 2.0) return "Good Standing";
        if (gpa >= 1.0) return "Probation";
        return "Academic Warning";
    }

    private JPanel statBlock(String title, JLabel valueLabel) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel t = new JLabel(title, SwingConstants.CENTER);
        t.setFont(new Font("SansSerif", Font.PLAIN, 11));
        t.setForeground(new Color(120, 120, 120));
        p.add(t, BorderLayout.NORTH);
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
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean sel, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, sel, focus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                String v = value != null ? value.toString() : "";
                if (!sel) {
                    switch (v) {
                        case "A+": case "A": case "A-":
                            setBackground(new Color(212, 237, 218)); setForeground(new Color(21, 87, 36)); break;
                        case "B+": case "B": case "B-":
                            setBackground(new Color(204, 229, 255)); setForeground(new Color(0, 64, 133)); break;
                        case "C+": case "C": case "C-":
                            setBackground(new Color(255, 243, 205)); setForeground(new Color(133, 100, 4)); break;
                        case "D+": case "D": case "D-":
                            setBackground(new Color(255, 228, 196)); setForeground(new Color(133, 60, 0)); break;
                        case "F":
                            setBackground(new Color(248, 215, 218)); setForeground(new Color(114, 28, 36)); break;
                        default:
                            setBackground(Color.WHITE); setForeground(Color.BLACK);
                    }
                }
                return this;
            }
        });
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}
