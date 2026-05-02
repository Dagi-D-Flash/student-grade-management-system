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

public class StudentEnrollmentPanel extends JPanel {

    private final User user;
    private JTable table;
    private DefaultTableModel model;
    private JLabel lblSummary;
    private volatile boolean active = true;

    public StudentEnrollmentPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildTable(),  BorderLayout.CENTER);
        load();
        Thread t = new Thread(() -> {
            while (active) {
                try { Thread.sleep(2000); } catch (InterruptedException ex) { break; }
                if (active) SwingUtilities.invokeLater(this::load);
            }
        });
        t.setDaemon(true);
        t.start();
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                    && !isDisplayable()) active = false;
        });
    }

    private JPanel buildHeader() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(ThemeManager.cardBorder());
        JLabel title = new JLabel("My Enrollment History");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());
        lblSummary = new JLabel("");
        lblSummary.setFont(ThemeManager.fontSmall());
        lblSummary.setForeground(ThemeManager.muted());
        JButton btnRefresh = ThemeManager.primaryButton("Refresh");
        btnRefresh.addActionListener(e -> load());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(lblSummary);
        right.add(btnRefresh);
        bar.add(title, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JScrollPane buildTable() {
        String[] cols = {"Subject", "Teacher", "Section", "Year", "Semester",
                         "Credits", "Avg %", "Letter", "GPA Pts", "Status", "Enrolled On"};
        model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        ThemeManager.styleTable(table);
        table.getColumnModel().getColumn(7).setCellRenderer(letterRenderer());
        table.getColumnModel().getColumn(9).setCellRenderer(statusRenderer());
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(ThemeManager.border()));
        return scroll;
    }

    private void load() {
        model.setRowCount(0);
        String sql =
            "SELECT sub.code, sub.name, sub.credits, " +
            "       CONCAT(t.first_name,' ',t.last_name) AS teacher, " +
            "       c.section, c.academic_year, c.semester, e.status, e.enrolled_at, " +
            "       SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM enrollments e " +
            "JOIN courses c    ON c.id  = e.course_id " +
            "JOIN subjects sub ON sub.id = c.subject_id " +
            "JOIN teachers t   ON t.id  = c.teacher_id " +
            "JOIN students s   ON s.id  = e.student_id " +
            "LEFT JOIN grades g ON g.enrollment_id = e.id " +
            "LEFT JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "WHERE s.user_id = ? " +
            "GROUP BY e.id, sub.code, sub.name, sub.credits, teacher, " +
            "         c.section, c.academic_year, c.semester, e.status, e.enrolled_at " +
            "ORDER BY c.academic_year DESC, c.semester, sub.code";
        int active = 0, completed = 0;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double avg = rs.getDouble("avg_pct");
                    boolean hasGrades = !rs.wasNull() && avg > 0;
                    double gp = hasGrades ? GPACalculator.toGradePoint(avg) : 0;
                    String letter = hasGrades ? GPACalculator.toLetterGrade(avg) : "—";
                    String status = rs.getString("status");
                    if ("active".equals(status)) active++;
                    else if ("completed".equals(status)) completed++;
                    model.addRow(new Object[]{
                        rs.getString("code") + " — " + rs.getString("name"),
                        rs.getString("teacher"),
                        rs.getString("section"),
                        rs.getInt("academic_year"),
                        rs.getString("semester"),
                        rs.getInt("credits"),
                        hasGrades ? String.format("%.1f", avg) : "—",
                        letter,
                        hasGrades ? String.format("%.2f", gp) : "—",
                        status,
                        rs.getTimestamp("enrolled_at") != null
                            ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(rs.getTimestamp("enrolled_at")) : ""
                    });
                }
            }
            lblSummary.setText(model.getRowCount() + " total  |  " + active + " active  |  " + completed + " completed");
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
                        case "active":    setBackground(ThemeManager.gradeABg()); setForeground(ThemeManager.gradeAFg()); break;
                        case "completed": setBackground(ThemeManager.gradeBBg()); setForeground(ThemeManager.gradeBFg()); break;
                        case "dropped":   setBackground(ThemeManager.gradeFBg()); setForeground(ThemeManager.gradeFFg()); break;
                        default:          setBackground(ThemeManager.surface()); setForeground(ThemeManager.text());
                    }
                }
                return this;
            }
        };
    }
}
