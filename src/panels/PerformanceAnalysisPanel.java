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

public class PerformanceAnalysisPanel extends JPanel {

    private final User user;
    private int studentId = -1;

    private JLabel lblStatus, lblStatusIcon, lblCGPA;
    private JLabel lblWeakSubject, lblWeakGP, lblWeakLetter;
    private JLabel lblTrend, lblTrendIcon;
    private JLabel lblStanding;
    private JPanel statusCard, weakCard, trendCard;

    private JTable subjectTable;
    private DefaultTableModel subjectModel;

    private JButton btnRefresh;
    private Timer autoRefresh;

    public PerformanceAnalysisPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());

        studentId = resolveStudentId();

        add(buildToolbar(),    BorderLayout.NORTH);
        add(buildCards(),      BorderLayout.CENTER);
        add(buildTablePanel(), BorderLayout.SOUTH);

        load();

        autoRefresh = new Timer(30_000, e -> load());
        autoRefresh.start();
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(ThemeManager.cardBorder());

        JLabel title = new JLabel("Performance Analysis");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());

        btnRefresh = ThemeManager.primaryButton("Refresh");
        btnRefresh.addActionListener(e -> load());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(btnRefresh);

        bar.add(title, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildCards() {
        JPanel row = new JPanel(new GridLayout(1, 3, 14, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(0, 200));

        statusCard = buildEmptyCard();
        weakCard   = buildEmptyCard();
        trendCard  = buildEmptyCard();

        JPanel statusInner = new JPanel(new GridLayout(4, 1, 0, 6));
        statusInner.setOpaque(false);
        lblStatusIcon = centeredLabel("—", 36, Font.PLAIN, ThemeManager.muted());
        lblStatus     = centeredLabel("—", 18, Font.BOLD,  ThemeManager.text());
        lblCGPA       = centeredLabel("CGPA: —", 14, Font.PLAIN, ThemeManager.muted());
        lblStanding   = centeredLabel("—", 12, Font.PLAIN, ThemeManager.muted());
        statusInner.add(lblStatusIcon); statusInner.add(lblStatus);
        statusInner.add(lblCGPA);       statusInner.add(lblStanding);
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

    private void load() {
        if (studentId < 0) return;
        SwingUtilities.invokeLater(() -> {
            loadStatus();
            loadWeak();
            loadTrend();
            loadTable();
        });
    }

    private void loadStatus() {
        try {
            double cgpa = GPACalculator.getCGPA(studentId);
            if (cgpa < 0) {
                lblStatusIcon.setText("?"); lblStatus.setText("No Data");
                lblCGPA.setText("CGPA: N/A"); lblStanding.setText("—");
                statusCard.setBorder(colorBorder(ThemeManager.border())); return;
            }
            lblCGPA.setText(String.format("CGPA: %.2f / 4.0", cgpa));
            lblStanding.setText(standing(cgpa));
            Color c = ThemeManager.gpaColor(cgpa);
            if (cgpa < 2.0) {
                set(lblStatusIcon, "\u26A0", ThemeManager.danger());
                set(lblStatus, "At Risk", ThemeManager.danger());
                statusCard.setBorder(colorBorder(ThemeManager.danger()));
            } else if (cgpa >= 3.5) {
                set(lblStatusIcon, "\u2605", ThemeManager.SUCCESS);
                set(lblStatus, "Excellent", ThemeManager.SUCCESS);
                statusCard.setBorder(colorBorder(ThemeManager.SUCCESS));
            } else if (cgpa >= 3.0) {
                set(lblStatusIcon, "\u2714", ThemeManager.INFO);
                set(lblStatus, "Good Standing", ThemeManager.INFO);
                statusCard.setBorder(colorBorder(ThemeManager.INFO));
            } else {
                set(lblStatusIcon, "\u2197", ThemeManager.WARNING);
                set(lblStatus, "Average", ThemeManager.WARNING);
                statusCard.setBorder(colorBorder(ThemeManager.WARNING));
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void loadWeak() {
        String sql =
            "SELECT sub.name, SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM grades g JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "JOIN enrollments e ON e.id=g.enrollment_id " +
            "JOIN courses c ON c.id=e.course_id JOIN subjects sub ON sub.id=c.subject_id " +
            "WHERE e.student_id=? GROUP BY e.id,sub.name ORDER BY avg_pct ASC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
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
                    lblWeakSubject.setText("No grades yet");
                    lblWeakGP.setText("—"); lblWeakLetter.setText("—");
                }
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void loadTrend() {
        String sql =
            "SELECT c.academic_year, c.semester, sub.credits, " +
            "       SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM grades g JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "JOIN enrollments e ON e.id=g.enrollment_id " +
            "JOIN courses c ON c.id=e.course_id JOIN subjects sub ON sub.id=c.subject_id " +
            "WHERE e.student_id=? " +
            "GROUP BY e.id,c.academic_year,c.semester,sub.credits ORDER BY c.academic_year,c.semester";
        LinkedHashMap<String, double[]> map = new LinkedHashMap<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
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
            set(lblTrend, String.format("Good Progress (+%.2f)", diff), ThemeManager.SUCCESS);
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

    private void loadTable() {
        subjectModel.setRowCount(0);
        String sql =
            "SELECT sub.name, c.semester, c.academic_year, " +
            "       SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM grades g JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "JOIN enrollments e ON e.id=g.enrollment_id " +
            "JOIN courses c ON c.id=e.course_id JOIN subjects sub ON sub.id=c.subject_id " +
            "WHERE e.student_id=? " +
            "GROUP BY e.id,sub.name,c.semester,c.academic_year ORDER BY avg_pct ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double avg = rs.getDouble("avg_pct");
                    double gp  = GPACalculator.toGradePoint(avg);
                    subjectModel.addRow(new Object[]{
                        rs.getString("name"),
                        rs.getInt("academic_year") + " " + rs.getString("semester"),
                        String.format("%.2f", avg),
                        String.format("%.1f", gp),
                        GPACalculator.toLetterGrade(avg),
                        subjectStatus(avg)
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

    private String subjectStatus(double avg) {
        if (avg >= 90) return "Excellent";
        if (avg >= 80) return "Good";
        if (avg >= 70) return "Average";
        if (avg >= 60) return "Passing";
        return "Failing";
    }

    private String standing(double gpa) {
        if (gpa >= 3.75) return "Distinction";
        if (gpa >= 3.5)  return "Very Good";
        if (gpa >= 3.0)  return "Good";
        if (gpa >= 2.0)  return "Pass";
        if (gpa >= 1.0)  return "Probation";
        return "Fail";
    }

    private void set(JLabel l, String text, Color color) {
        l.setText(text); l.setForeground(color);
    }

    private JPanel buildEmptyCard() {
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
                        case "Excellent": setBackground(ThemeManager.gradeABg()); setForeground(ThemeManager.gradeAFg()); break;
                        case "Good":      setBackground(ThemeManager.gradeBBg()); setForeground(ThemeManager.gradeBFg()); break;
                        case "Average":   setBackground(ThemeManager.gradeCBg()); setForeground(ThemeManager.gradeCFg()); break;
                        case "Passing":   setBackground(ThemeManager.gradeDBg()); setForeground(ThemeManager.gradeDFg()); break;
                        case "Failing":   setBackground(ThemeManager.gradeFBg()); setForeground(ThemeManager.gradeFFg()); break;
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
}
