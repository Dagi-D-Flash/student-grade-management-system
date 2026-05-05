package panels;

import models.User;
import util.DBConnection;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TeacherCoursesPanel extends JPanel {

    private final User teacher;
    private JTable table;
    private DefaultTableModel model;
    private JLabel lblCount;
    private Timer autoRefresh;

    public TeacherCoursesPanel(User teacher) {
        this.teacher = teacher;
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);
        load();
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                    break;
                }
                SwingUtilities.invokeLater(this::load);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private JPanel buildHeader() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(ThemeManager.cardBorder());
        JLabel title = new JLabel("My Courses");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());
        lblCount = new JLabel("");
        lblCount.setFont(ThemeManager.fontSmall());
        lblCount.setForeground(ThemeManager.muted());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(lblCount);
        bar.add(title, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JScrollPane buildTable() {
        String[] cols = { "Subject Code", "Subject Name", "Section", "Year", "Semester",
                "Credits", "Enrolled Students", "Max Students" };
        model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        table = new JTable(model);
        ThemeManager.styleTable(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(ThemeManager.border()));
        return scroll;
    }

    private void load() {
        model.setRowCount(0);
        String sql = "SELECT s.code, s.name, s.credits, c.section, c.academic_year, c.semester, " +
                "       c.max_students, COUNT(e.id) AS enrolled " +
                "FROM courses c " +
                "JOIN subjects s  ON s.id = c.subject_id " +
                "JOIN teachers t  ON t.id = c.teacher_id " +
                "JOIN users u     ON u.id = t.user_id " +
                "LEFT JOIN enrollments e ON e.course_id = c.id AND e.status = 'active' " +
                "WHERE u.id = ? " +
                "GROUP BY c.id, s.code, s.name, s.credits, c.section, c.academic_year, c.semester, c.max_students " +
                "ORDER BY c.academic_year DESC, c.semester, s.code";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teacher.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[] {
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("section"),
                            rs.getInt("academic_year"),
                            rs.getString("semester"),
                            rs.getInt("credits"),
                            rs.getInt("enrolled"),
                            rs.getInt("max_students")
                    });
                }
            }
            lblCount.setText(model.getRowCount() + " course" + (model.getRowCount() != 1 ? "s" : ""));
        } catch (SQLException ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }
}
