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
import java.util.ArrayList;
import java.util.List;

public class StudentGradePanel extends JPanel {

    private final User user;

    private JTable gradeTable;
    private DefaultTableModel gradeModel;
    private JTable subjectTable;
    private DefaultTableModel subjectModel;

    private JLabel lblGPA, lblHighest, lblLowest, lblPassed;
    private JButton btnRefresh;

    // All grade rows cached for filtering
    private final List<Object[]> allGradeRows = new ArrayList<>();
    // Title border label for grade details panel
    private javax.swing.border.TitledBorder gradeDetailsBorder;

    public StudentGradePanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());
        add(buildSummaryBar(), BorderLayout.NORTH);
        add(buildMainSplit(),  BorderLayout.CENTER);
        loadAll();
    }

    private JPanel buildSummaryBar() {
        lblGPA     = new JLabel("—", SwingConstants.CENTER);
        lblHighest = new JLabel("—", SwingConstants.CENTER);
        lblLowest  = new JLabel("—", SwingConstants.CENTER);
        lblPassed  = new JLabel("—", SwingConstants.CENTER);

        lblGPA.setFont(new Font("SansSerif", Font.BOLD, 28));
        lblHighest.setFont(new Font("SansSerif", Font.BOLD, 22));
        lblLowest.setFont(new Font("SansSerif", Font.BOLD, 22));
        lblPassed.setFont(new Font("SansSerif", Font.BOLD, 22));

        Color cGPA    = ThemeManager.accent();
        Color cHigh   = ThemeManager.gradeAFg();
        Color cLow    = ThemeManager.gradeDFg();
        Color cPassed = ThemeManager.gradeBFg();

        lblGPA.setForeground(cGPA);
        lblHighest.setForeground(cHigh);
        lblLowest.setForeground(cLow);
        lblPassed.setForeground(cPassed);

        // Small icon-style refresh button
        btnRefresh = new JButton("\u21BB");
        btnRefresh.setFont(new Font("SansSerif", Font.PLAIN, 14));
        btnRefresh.setForeground(ThemeManager.muted());
        btnRefresh.setBackground(ThemeManager.surface());
        btnRefresh.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        btnRefresh.setFocusPainted(false);
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefresh.setToolTipText("Refresh");
        btnRefresh.addActionListener(e -> loadAll());

        JPanel cards = new JPanel(new GridLayout(1, 4, 12, 0));
        cards.setOpaque(false);
        cards.add(accentCard("Cumulative GPA",  lblGPA,     cGPA,    "\uD83C\uDF93", btnRefresh));
        cards.add(accentCard("Highest Score",   lblHighest, cHigh,   "\u2B06",       null));
        cards.add(accentCard("Lowest Score",    lblLowest,  cLow,    "\u2B07",       null));
        cards.add(accentCard("Subjects Passed", lblPassed,  cPassed, "\u2714",       null));

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(ThemeManager.bg());
        bar.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        bar.add(cards, BorderLayout.CENTER);
        return bar;
    }

    /** Card with a colored left accent strip, icon, value and optional action widget. */
    private JPanel accentCard(String title, JLabel valueLabel, Color accent, String icon, JComponent action) {
        JPanel card = new JPanel(new BorderLayout(0, 4)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(accent);
                g.fillRect(0, 0, 4, getHeight());
            }
        };
        card.setBackground(ThemeManager.surface());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.border()),
            BorderFactory.createEmptyBorder(12, 16, 12, 12)
        ));

        JLabel iconLabel = new JLabel(icon + "  " + title);
        iconLabel.setFont(ThemeManager.fontSmall());
        iconLabel.setForeground(ThemeManager.muted());

        if (action != null) {
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            header.add(iconLabel, BorderLayout.CENTER);
            header.add(action,    BorderLayout.EAST);
            card.add(header,     BorderLayout.NORTH);
        } else {
            card.add(iconLabel,  BorderLayout.NORTH);
        }
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JSplitPane buildMainSplit() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildGradeTable(), buildSubjectTable());
        split.setDividerLocation(500);
        split.setResizeWeight(0.5);
        split.setBorder(null);
        return split;
    }

    private JPanel buildGradeTable() {
        String[] cols = {"Subject", "Grade Type", "Score", "Max Score", "Weight", "Weighted %", "Letter"};
        gradeModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        gradeTable = new JTable(gradeModel);
        ThemeManager.styleTable(gradeTable);
        gradeTable.getColumnModel().getColumn(6).setCellRenderer(letterRenderer());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ThemeManager.surface());
        gradeDetailsBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.border()), "Grade Details");
        wrapper.setBorder(gradeDetailsBorder);
        wrapper.add(new JScrollPane(gradeTable));
        return wrapper;
    }

    private JPanel buildSubjectTable() {
        String[] cols = {"Subject", "Credits", "Avg %", "Grade Point", "Letter", "Status"};
        subjectModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        subjectTable = new JTable(subjectModel);
        ThemeManager.styleTable(subjectTable);
        subjectTable.getColumnModel().getColumn(4).setCellRenderer(letterRenderer());

        // Filter grade details when a subject row is selected
        subjectTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = subjectTable.getSelectedRow();
            if (row < 0) {
                showAllGrades();
            } else {
                String subjectName = subjectModel.getValueAt(row, 0).toString();
                filterGradesBySubject(subjectName);
            }
        });

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ThemeManager.surface());
        wrapper.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.border()), "Subject Summary  (click a row to view grades)"));
        wrapper.add(new JScrollPane(subjectTable));
        return wrapper;
    }

    private void loadAll() {
        gradeModel.setRowCount(0);
        subjectModel.setRowCount(0);
        allGradeRows.clear();
        if (subjectTable != null) subjectTable.clearSelection();
        if (gradeDetailsBorder != null) {
            gradeDetailsBorder.setTitle("Grade Details");
        }
        int studentId = resolveStudentId();
        if (studentId < 0) return;
        loadGrades(studentId);
        loadSubjects(studentId);
        loadStats(studentId);
        // Auto-select first subject so grade details aren't empty on load
        if (subjectModel.getRowCount() > 0) {
            subjectTable.setRowSelectionInterval(0, 0);
        }
    }

    private void loadGrades(int studentId) {
        allGradeRows.clear();
        String sql =
            "SELECT sub.name, cgc.component_name AS grade_type, g.score, cgc.max_score, cgc.weight " +
            "FROM grades g " +
            "JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "JOIN enrollments e ON e.id = g.enrollment_id " +
            "JOIN courses c     ON c.id = e.course_id " +
            "JOIN subjects sub  ON sub.id = c.subject_id " +
            "WHERE e.student_id = ? " +
            "ORDER BY sub.name, cgc.component_name";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double score  = rs.getDouble("score");
                    double max    = rs.getDouble("max_score");
                    double weight = rs.getDouble("weight");
                    double wpct   = (score / max) * weight;
                    double pct    = (score / max) * 100;
                    Object[] row = {
                        rs.getString("name"),
                        rs.getString("grade_type"),
                        score, max, weight,
                        String.format("%.2f", wpct),
                        GPACalculator.toLetterGrade(pct)
                    };
                    allGradeRows.add(row);
                    gradeModel.addRow(row);
                }
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void filterGradesBySubject(String subjectName) {
        gradeModel.setRowCount(0);
        for (Object[] row : allGradeRows) {
            if (subjectName.equals(row[0])) {
                gradeModel.addRow(row);
            }
        }
        if (gradeDetailsBorder != null) {
            gradeDetailsBorder.setTitle("Grade Details  —  " + subjectName);
            gradeTable.getParent().getParent().repaint();
        }
    }

    private void showAllGrades() {
        gradeModel.setRowCount(0);
        for (Object[] row : allGradeRows) {
            gradeModel.addRow(row);
        }
        if (gradeDetailsBorder != null) {
            gradeDetailsBorder.setTitle("Grade Details");
            gradeTable.getParent().getParent().repaint();
        }
    }

    private void loadSubjects(int studentId) {
        String sql =
            "SELECT sub.name, sub.credits, " +
            "       SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM grades g " +
            "JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "JOIN enrollments e ON e.id = g.enrollment_id " +
            "JOIN courses c     ON c.id = e.course_id " +
            "JOIN subjects sub  ON sub.id = c.subject_id " +
            "WHERE e.student_id = ? " +
            "GROUP BY e.id, sub.name, sub.credits ORDER BY sub.name";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double avg = rs.getDouble("avg_pct");
                    double gp  = GPACalculator.toGradePoint(avg);
                    subjectModel.addRow(new Object[]{
                        rs.getString("name"),
                        rs.getInt("credits"),
                        String.format("%.2f", avg),
                        String.format("%.2f", gp),
                        GPACalculator.toLetterGrade(avg),
                        subjectStatus(avg)
                    });
                }
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void loadStats(int studentId) {
        // Highest and lowest subject weighted average
        String sql =
            "SELECT MAX(subject_avg) AS highest, MIN(subject_avg) AS lowest, " +
            "       SUM(CASE WHEN subject_avg > 50 THEN 1 ELSE 0 END) AS passed " +
            "FROM (" +
            "  SELECT SUM((g.score / cgc.max_score) * cgc.weight) AS subject_avg " +
            "  FROM grades g " +
            "  JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "  JOIN enrollments e ON e.id = g.enrollment_id " +
            "  WHERE e.student_id = ? " +
            "  GROUP BY e.id" +
            ") AS subject_totals";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double high = rs.getDouble("highest");
                    double low  = rs.getDouble("lowest");
                    int passed  = rs.getInt("passed");
                    lblHighest.setText(rs.wasNull() ? "—" : String.format("%.1f%%", high));
                    lblLowest.setText(rs.wasNull()  ? "—" : String.format("%.1f%%", low));
                    lblPassed.setText(rs.wasNull()  ? "—" : String.valueOf(passed));
                }
            }
        } catch (SQLException ex) { showError(ex); }

        try {
            double cgpa = GPACalculator.getCGPA(studentId);
            if (cgpa < 0) {
                lblGPA.setText("N/A");
                lblGPA.setForeground(ThemeManager.muted());
            } else {
                lblGPA.setText(String.format("%.2f", cgpa));
                lblGPA.setForeground(ThemeManager.gpaColor(cgpa));
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

    private String subjectStatus(double avg) {
        if (avg >= 90) return "Distinction";
        if (avg >= 75) return "Very Good";
        if (avg >= 60) return "Good";
        if (avg >  50) return "Pass";
        return "Fail";
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

    private void showError(Exception ex) {
        System.err.println("Error: " + ex.getMessage());
    }
}
