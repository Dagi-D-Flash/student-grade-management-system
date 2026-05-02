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

public class StudentCoursesPanel extends JPanel {

    private final User user;
    private JTable table;
    private DefaultTableModel model;
    private JLabel lblCount;
    private volatile boolean active = true;

    public StudentCoursesPanel(User user) {
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
        JLabel title = new JLabel("My Enrolled Courses");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());
        lblCount = new JLabel("");
        lblCount.setFont(ThemeManager.fontSmall());
        lblCount.setForeground(ThemeManager.muted());
        JButton btnRefresh = ThemeManager.primaryButton("Refresh");
        btnRefresh.addActionListener(e -> load());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(lblCount);
        right.add(btnRefresh);
        bar.add(title, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JScrollPane buildTable() {
        String[] cols = {"Subject Code", "Subject Name", "Teacher", "Section",
                         "Year", "Semester", "Credits", "Status"};
        model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        ThemeManager.styleTable(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(ThemeManager.border()));
        return scroll;
    }

    private void load() {
        model.setRowCount(0);
        String sql =
            "SELECT sub.code, sub.name, sub.credits, " +
            "       CONCAT(t.first_name,' ',t.last_name) AS teacher, " +
            "       c.section, c.academic_year, c.semester, e.status " +
            "FROM enrollments e " +
            "JOIN courses c   ON c.id  = e.course_id " +
            "JOIN subjects sub ON sub.id = c.subject_id " +
            "JOIN teachers t  ON t.id  = c.teacher_id " +
            "JOIN students s  ON s.id  = e.student_id " +
            "WHERE s.user_id = ? " +
            "ORDER BY c.academic_year DESC, c.semester, sub.code";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("teacher"),
                        rs.getString("section"),
                        rs.getInt("academic_year"),
                        rs.getString("semester"),
                        rs.getInt("credits"),
                        rs.getString("status")
                    });
                }
            }
            lblCount.setText(model.getRowCount() + " course" + (model.getRowCount() != 1 ? "s" : ""));
        } catch (SQLException ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }
}
