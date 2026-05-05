package panels;

import models.User;
import util.DBConnection;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SchedulePanel extends JPanel {

    private final User user;
    private final String role;
    private JTable table;
    private DefaultTableModel model;
    private JLabel lblCount;
    private volatile boolean active = true;

    public SchedulePanel(User user) {
        this.user = user;
        this.role = user.getRole();
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildTable(),  BorderLayout.CENTER);
        load();
        startBackgroundRefresh();
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                    && !isDisplayable()) active = false;
        });
    }

    private void startBackgroundRefresh() {
        Thread t = new Thread(() -> {
            while (active) {
                try { Thread.sleep(3000); } catch (InterruptedException ex) { break; }
                if (!active) break;
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
        JLabel title = new JLabel("Class Schedule");
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
        String[] cols = {"Day", "Start", "End", "Subject", "Section", "Year", "Semester", "Room",
                         role.equals("teacher") ? "Students" : "Teacher"};
        model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        ThemeManager.styleTable(table);
        table.getColumnModel().getColumn(0).setCellRenderer(dayRenderer());
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(ThemeManager.border()));
        return scroll;
    }

    private void load() {
        model.setRowCount(0);
        String sql;
        if ("teacher".equals(role)) {
            sql =
                "SELECT sc.day_of_week, sc.start_time, sc.end_time, sc.room, " +
                "       sub.name AS subject_name, c.section, c.academic_year, c.semester, " +
                "       COUNT(e.id) AS student_count " +
                "FROM schedules sc " +
                "JOIN courses c   ON c.id  = sc.course_id " +
                "JOIN subjects sub ON sub.id = c.subject_id " +
                "JOIN teachers t  ON t.id  = c.teacher_id " +
                "JOIN users u     ON u.id  = t.user_id " +
                "LEFT JOIN enrollments e ON e.course_id = c.id AND e.status = 'active' " +
                "WHERE u.id = ? " +
                "GROUP BY sc.id, sc.day_of_week, sc.start_time, sc.end_time, sc.room, " +
                "         sub.name, c.section, c.academic_year, c.semester " +
                "ORDER BY FIELD(sc.day_of_week,'Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'), sc.start_time";
        } else {
            sql =
                "SELECT sc.day_of_week, sc.start_time, sc.end_time, sc.room, " +
                "       sub.name AS subject_name, c.section, c.academic_year, c.semester, " +
                "       CONCAT(t.first_name,' ',t.last_name) AS teacher_name " +
                "FROM schedules sc " +
                "JOIN courses c    ON c.id  = sc.course_id " +
                "JOIN subjects sub ON sub.id = c.subject_id " +
                "JOIN teachers t   ON t.id  = c.teacher_id " +
                "JOIN enrollments e ON e.course_id = c.id " +
                "JOIN students s   ON s.id  = e.student_id " +
                "WHERE s.user_id = ? AND e.status = 'active' " +
                "ORDER BY FIELD(sc.day_of_week,'Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'), sc.start_time";
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String start = rs.getTime("start_time").toString().substring(0, 5);
                    String end   = rs.getTime("end_time").toString().substring(0, 5);
                    Object last  = "teacher".equals(role)
                        ? rs.getInt("student_count")
                        : rs.getString("teacher_name");
                    model.addRow(new Object[]{
                        rs.getString("day_of_week"),
                        start, end,
                        rs.getString("subject_name"),
                        rs.getString("section"),
                        rs.getInt("academic_year"),
                        rs.getString("semester"),
                        rs.getString("room") != null ? rs.getString("room") : "—",
                        last
                    });
                }
            }
            lblCount.setText(model.getRowCount() + " session" + (model.getRowCount() != 1 ? "s" : ""));
        } catch (SQLException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.toLowerCase().contains("doesn't exist")) {
                lblCount.setText("Schedule table not set up yet — run schema.sql");
            } else {
                lblCount.setText("Error: " + msg);
            }
        }
    }

    private DefaultTableCellRenderer dayRenderer() {
        return new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                setFont(ThemeManager.fontBold());
                if (!sel) {
                    String day = v != null ? v.toString() : "";
                    switch (day) {
                        case "Monday":    setForeground(ThemeManager.accent()); break;
                        case "Tuesday":   setForeground(ThemeManager.INFO);    break;
                        case "Wednesday": setForeground(ThemeManager.SUCCESS); break;
                        case "Thursday":  setForeground(ThemeManager.WARNING); break;
                        case "Friday":    setForeground(ThemeManager.DANGER);  break;
                        default:          setForeground(ThemeManager.muted());
                    }
                    setBackground(ThemeManager.surface());
                }
                return this;
            }
        };
    }
}
