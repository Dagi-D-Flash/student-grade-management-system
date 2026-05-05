package panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import models.User;
import util.DBConnection;
import util.GPACalculator;
import util.ThemeManager;

public class LeaderboardPanel extends JPanel {
    private final User scopedUser;
    private JPanel podiumPanel;
    private JTable rankTable;
    private DefaultTableModel rankModel;
    private JLabel lblLastUpdated;
    private Timer autoRefreshTimer;
    private JTextField tfSearch;
    private JComboBox<CourseItem> cbCourse;
    private final List<Object[]> allRows;

    public LeaderboardPanel() {
        this((User)null);
    }

    public LeaderboardPanel(User scopedUser) {
        this.allRows = new ArrayList();
        this.scopedUser = scopedUser;
        this.setLayout(new BorderLayout(10, 10));
        this.setBorder(ThemeManager.panelBorder());
        this.setBackground(ThemeManager.bg());
        this.add(this.buildHeader(), "North");
        this.add(this.buildCenter(), "Center");
        if (scopedUser != null) {
            this.loadCourses();
        } else {
            this.load();
        }

        this.autoRefreshTimer = new Timer(30000, (e) -> this.load());
        this.autoRefreshTimer.start();
    }

    private JPanel buildHeader() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(ThemeManager.cardBorder());
        JPanel left = new JPanel();
        left.setOpaque(false);
        bar.add(left, "West");
        this.lblLastUpdated = new JLabel("Last updated: —");
        this.lblLastUpdated.setFont(ThemeManager.fontSmall());
        this.lblLastUpdated.setForeground(ThemeManager.muted());
        JButton btnRefresh = ThemeManager.primaryButton("Refresh");
        btnRefresh.addActionListener((e) -> this.load());
        this.tfSearch = new JTextField(22);
        this.tfSearch.setBackground(ThemeManager.elevated());
        this.tfSearch.setForeground(ThemeManager.text());
        this.tfSearch.setCaretColor(ThemeManager.text());
        this.tfSearch.setFont(ThemeManager.fontBody());
        this.tfSearch.putClientProperty("JTextField.placeholderText", "Search by name or student ID...");
        this.tfSearch.getDocument().addDocumentListener(new DocumentListener() {
            {
                Objects.requireNonNull(LeaderboardPanel.this);
            }

            public void insertUpdate(DocumentEvent e) {
                LeaderboardPanel.this.filterTable(LeaderboardPanel.this.tfSearch.getText());
            }

            public void removeUpdate(DocumentEvent e) {
                LeaderboardPanel.this.filterTable(LeaderboardPanel.this.tfSearch.getText());
            }

            public void changedUpdate(DocumentEvent e) {
                LeaderboardPanel.this.filterTable(LeaderboardPanel.this.tfSearch.getText());
            }
        });
        JPanel controls = new JPanel(new FlowLayout(2, 8, 0));
        controls.setOpaque(false);
        if (this.scopedUser != null) {
            JLabel courseLbl = new JLabel("Course:");
            courseLbl.setFont(ThemeManager.fontBold());
            courseLbl.setForeground(ThemeManager.muted());
            this.cbCourse = new JComboBox();
            this.cbCourse.setPreferredSize(new Dimension(300, 28));
            this.cbCourse.addActionListener((e) -> this.load());
            controls.add(courseLbl);
            controls.add(this.cbCourse);
        }

        controls.add(new JLabel("\ud83d\udd0d"));
        controls.add(this.tfSearch);
        controls.add(this.lblLastUpdated);
        controls.add(btnRefresh);
        bar.add(controls, "East");
        return bar;
    }

    private void loadCourses() {
        if (this.cbCourse != null) {
            this.cbCourse.removeAllItems();
            this.cbCourse.addItem(new CourseItem(-1, "All My Courses"));
            String sql = "SELECT c.id, s.code, s.name, c.section, c.academic_year, c.semester FROM courses c JOIN subjects s ON s.id = c.subject_id JOIN teachers t ON t.id = c.teacher_id JOIN users u    ON u.id = t.user_id WHERE u.id = ? ORDER BY c.academic_year DESC, c.semester, s.code";

            try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
            ) {
                ps.setInt(1, this.scopedUser.getId());

                try (ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        String var10000 = rs.getString("code");
                        String label = var10000 + " — " + rs.getString("name") + " [" + rs.getString("section") + "] " + rs.getInt("academic_year") + " " + rs.getString("semester");
                        this.cbCourse.addItem(new CourseItem(rs.getInt("id"), label));
                    }
                }
            } catch (SQLException ex) {
                System.err.println("LeaderboardPanel courses: " + ex.getMessage());
            }

            this.load();
        }
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        this.podiumPanel = new JPanel(new GridLayout(1, 3, 16, 0));
        this.podiumPanel.setOpaque(false);
        this.podiumPanel.setPreferredSize(new Dimension(0, 180));
        String[] cols = new String[]{"Rank", "Student No", "Name", "Enrolled Courses", "Total Credits", "CGPA", "Letter Grade", "Standing"};
        this.rankModel = new DefaultTableModel(cols, 0) {
            {
                Objects.requireNonNull(LeaderboardPanel.this);
            }

            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        this.rankTable = new JTable(this.rankModel);
        ThemeManager.styleTable(this.rankTable);
        this.rankTable.getColumnModel().getColumn(6).setCellRenderer(this.letterRenderer());
        this.rankTable.getColumnModel().getColumn(0).setCellRenderer(this.rankRenderer());
        this.rankTable.getColumnModel().getColumn(7).setCellRenderer(this.standingRenderer());
        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.setBackground(ThemeManager.surface());
        tableWrapper.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(ThemeManager.border()), "Full Rankings"));
        tableWrapper.add(new JScrollPane(this.rankTable));
        center.add(this.podiumPanel, "North");
        center.add(tableWrapper, "Center");
        return center;
    }

    private void load() {
        this.rankModel.setRowCount(0);
        this.allRows.clear();
        this.podiumPanel.removeAll();
        int courseId = -1;
        if (this.cbCourse != null) {
            CourseItem ci = (CourseItem)this.cbCourse.getSelectedItem();
            courseId = ci != null ? ci.id : -1;
        }

        String gpaHeader = this.scopedUser == null ? "CGPA" : (courseId > 0 ? "Course GPA" : "Courses GPA");
        this.rankTable.getColumnModel().getColumn(5).setHeaderValue(gpaHeader);
        this.rankTable.getTableHeader().repaint();
        List<StudentRank> ranks = this.fetchRanks(courseId);
        ranks.sort(Comparator.comparingDouble((rx) -> rx.cgpa).reversed());

        for(int i = 0; i < Math.min(3, ranks.size()); ++i) {
            this.podiumPanel.add(this.buildPodiumCard((StudentRank)ranks.get(i), i + 1));
        }

        int rank = 1;

        for(StudentRank r : ranks) {
            double pct = this.cgpaToPercent(r.cgpa);
            String letter = r.cgpa >= (double)0.0F ? GPACalculator.toLetterGrade(pct) : "N/A";
            String stand = r.cgpa >= (double)0.0F ? this.standing(r.cgpa) : "N/A";
            Object[] row = new Object[]{rank++, r.studentNo, r.name, r.courseCount, r.totalCredits, r.cgpa >= (double)0.0F ? String.format("%.2f", r.cgpa) : "N/A", letter, stand};
            this.allRows.add(row);
            this.rankModel.addRow(row);
        }

        JLabel var14 = this.lblLastUpdated;
        SimpleDateFormat var10001 = new SimpleDateFormat("HH:mm:ss");
        Date var10002 = new Date();
        var14.setText("Last updated: " + var10001.format(var10002));
        this.podiumPanel.revalidate();
        this.podiumPanel.repaint();
        if (this.tfSearch != null && !this.tfSearch.getText().trim().isEmpty()) {
            this.filterTable(this.tfSearch.getText());
        }

    }

    private void filterTable(String query) {
        this.rankModel.setRowCount(0);
        String q = query.trim().toLowerCase();

        for(Object[] row : this.allRows) {
            String no = row[1].toString().toLowerCase();
            String name = row[2].toString().toLowerCase();
            String noStripped = no.replace("wour/", "").replace("/", "");
            String qStripped = q.replace("wour/", "").replace("/", "");
            if (q.isEmpty() || no.contains(q) || name.contains(q) || noStripped.contains(qStripped)) {
                this.rankModel.addRow(row);
            }
        }

    }

    private JPanel buildPodiumCard(StudentRank r, int position) {
        Color medalColor = position == 1 ? ThemeManager.GOLD : (position == 2 ? ThemeManager.SILVER : ThemeManager.BRONZE);
        String medal = position == 1 ? "\ud83e\udd47" : (position == 2 ? "\ud83e\udd48" : "\ud83e\udd49");
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(ThemeManager.surface());
        card.setBorder(ThemeManager.accentBorder(medalColor));
        JLabel medalLbl = new JLabel(medal + "  #" + position, 0);
        medalLbl.setFont(new Font("SansSerif", 1, 18));
        medalLbl.setForeground(medalColor);
        JLabel nameLbl = new JLabel(r.name, 0);
        nameLbl.setFont(ThemeManager.fontBold());
        nameLbl.setForeground(ThemeManager.text());
        JLabel cgpaLbl = new JLabel(r.cgpa >= (double)0.0F ? String.format("%.2f GPA", r.cgpa) : "N/A", 0);
        cgpaLbl.setFont(new Font("SansSerif", 1, 22));
        cgpaLbl.setForeground(ThemeManager.gpaColor(r.cgpa));
        JLabel standLbl = new JLabel(r.cgpa >= (double)0.0F ? this.standing(r.cgpa) : "", 0);
        standLbl.setFont(ThemeManager.fontSmall());
        standLbl.setForeground(ThemeManager.muted());
        JPanel inner = new JPanel(new GridLayout(4, 1, 0, 2));
        inner.setOpaque(false);
        inner.add(medalLbl);
        inner.add(nameLbl);
        inner.add(cgpaLbl);
        inner.add(standLbl);
        card.add(inner, "Center");
        return card;
    }

    private List<StudentRank> fetchRanks(int courseId) {
        List<StudentRank> list = new ArrayList();
        if (this.scopedUser != null) {
            String courseFilter = courseId > 0 ? "AND c.id = " + courseId + " " : "";
            String sql = "SELECT s.id, s.student_no, s.first_name, s.last_name,        COUNT(DISTINCT e.id) AS course_count,        COALESCE(SUM(sub.credits), 0) AS total_credits,        SUM(gp_sub.grade_point * sub.credits) / NULLIF(SUM(sub.credits), 0) AS course_gpa FROM students s JOIN enrollments e ON e.student_id = s.id JOIN courses c     ON c.id = e.course_id JOIN teachers t    ON t.id = c.teacher_id JOIN users u       ON u.id = t.user_id JOIN subjects sub  ON sub.id = c.subject_id LEFT JOIN (  SELECT e3.id AS enrollment_id,     CASE       WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 90 THEN 4.0       WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 85 THEN 4.0       WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 80 THEN 3.75       WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 75 THEN 3.5       WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 70 THEN 3.0       WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 65 THEN 2.75       WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 60 THEN 2.5       WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 55 THEN 2.0       WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 50 THEN 1.75       WHEN SUM((g2.score/cgc2.max_score)*cgc2.weight) >= 45 THEN 1.0       ELSE 0.0 END AS grade_point   FROM enrollments e3   LEFT JOIN grades g2 ON g2.enrollment_id = e3.id   LEFT JOIN course_grade_components cgc2          ON cgc2.id = g2.component_id AND cgc2.course_id = e3.course_id   GROUP BY e3.id) AS gp_sub ON gp_sub.enrollment_id = e.id WHERE u.id = ? " + courseFilter + "GROUP BY s.id, s.student_no, s.first_name, s.last_name ORDER BY s.first_name";

            try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
            ) {
                ps.setInt(1, this.scopedUser.getId());

                try (ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        StudentRank r = new StudentRank();
                        r.id = rs.getInt("id");
                        r.studentNo = rs.getString("student_no");
                        String var10001 = rs.getString("first_name");
                        r.name = var10001 + " " + rs.getString("last_name");
                        r.courseCount = rs.getInt("course_count");
                        r.totalCredits = rs.getInt("total_credits");
                        double gpa = rs.getDouble("course_gpa");
                        r.cgpa = rs.wasNull() ? (double)-1.0F : gpa;
                        list.add(r);
                    }
                }
            } catch (SQLException ex) {
                System.err.println("LeaderboardPanel (teacher): " + ex.getMessage());
            }
        } else {
            String sql = "SELECT s.id, s.student_no, s.first_name, s.last_name,        COUNT(DISTINCT e.id) AS course_count,        COALESCE(SUM(sub.credits), 0) AS total_credits FROM students s LEFT JOIN enrollments e ON e.student_id = s.id LEFT JOIN courses c     ON c.id = e.course_id LEFT JOIN subjects sub  ON sub.id = c.subject_id GROUP BY s.id, s.student_no, s.first_name, s.last_name ORDER BY s.first_name";

            StudentRank r;
            try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery();
            ) {
                for(; rs.next(); list.add(r)) {
                    r = new StudentRank();
                    r.id = rs.getInt("id");
                    r.studentNo = rs.getString("student_no");
                    String var31 = rs.getString("first_name");
                    r.name = var31 + " " + rs.getString("last_name");
                    r.courseCount = rs.getInt("course_count");
                    r.totalCredits = rs.getInt("total_credits");

                    try {
                        r.cgpa = GPACalculator.getCGPA(r.id);
                    } catch (SQLException var17) {
                        r.cgpa = (double)-1.0F;
                    }
                }
            } catch (SQLException ex) {
                System.err.println("LeaderboardPanel: " + ex.getMessage());
            }
        }

        return list;
    }

    private DefaultTableCellRenderer letterRenderer() {
        return new DefaultTableCellRenderer() {
            {
                Objects.requireNonNull(LeaderboardPanel.this);
            }

            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                this.setHorizontalAlignment(0);
                String s = v != null ? v.toString() : "";
                if (!sel && !s.isEmpty() && !s.equals("N/A")) {
                    switch (s.charAt(0)) {
                        case 'A':
                            this.setBackground(ThemeManager.gradeABg());
                            this.setForeground(ThemeManager.gradeAFg());
                            break;
                        case 'B':
                            this.setBackground(ThemeManager.gradeBBg());
                            this.setForeground(ThemeManager.gradeBFg());
                            break;
                        case 'C':
                            this.setBackground(ThemeManager.gradeCBg());
                            this.setForeground(ThemeManager.gradeCFg());
                            break;
                        case 'D':
                            this.setBackground(ThemeManager.gradeDBg());
                            this.setForeground(ThemeManager.gradeDFg());
                            break;
                        case 'E':
                        default:
                            this.setBackground(ThemeManager.surface());
                            this.setForeground(ThemeManager.text());
                            break;
                        case 'F':
                            this.setBackground(ThemeManager.gradeFBg());
                            this.setForeground(ThemeManager.gradeFFg());
                    }
                }

                return this;
            }
        };
    }

    private DefaultTableCellRenderer standingRenderer() {
        return new DefaultTableCellRenderer() {
            {
                Objects.requireNonNull(LeaderboardPanel.this);
            }

            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                this.setHorizontalAlignment(0);
                this.setFont(ThemeManager.fontBold());
                String s = v != null ? v.toString() : "";
                if (!sel) {
                    switch (s) {
                        case "Distinction":
                            this.setForeground(ThemeManager.gradeAFg());
                            this.setBackground(ThemeManager.gradeABg());
                            break;
                        case "Very Good":
                            this.setForeground(ThemeManager.gradeBFg());
                            this.setBackground(ThemeManager.gradeBBg());
                            break;
                        case "Good":
                            this.setForeground(ThemeManager.gradeCFg());
                            this.setBackground(ThemeManager.gradeCBg());
                            break;
                        case "Pass":
                            this.setForeground(ThemeManager.gradeDFg());
                            this.setBackground(ThemeManager.gradeDBg());
                            break;
                        case "Probation":
                            this.setForeground(ThemeManager.gradeFFg());
                            this.setBackground(ThemeManager.gradeFBg());
                            break;
                        case "Fail":
                            this.setForeground(ThemeManager.gradeFFg());
                            this.setBackground(ThemeManager.gradeFBg());
                            break;
                        default:
                            this.setForeground(ThemeManager.muted());
                            this.setBackground(ThemeManager.surface());
                    }
                }

                return this;
            }
        };
    }

    private DefaultTableCellRenderer rankRenderer() {
        return new DefaultTableCellRenderer() {
            {
                Objects.requireNonNull(LeaderboardPanel.this);
            }

            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                this.setHorizontalAlignment(0);
                this.setFont(ThemeManager.fontBold());
                int rank = v instanceof Integer ? (Integer)v : 0;
                if (!sel) {
                    if (rank == 1) {
                        this.setForeground(ThemeManager.GOLD);
                        this.setBackground(ThemeManager.surface());
                    } else if (rank == 2) {
                        this.setForeground(ThemeManager.SILVER);
                        this.setBackground(ThemeManager.surface());
                    } else if (rank == 3) {
                        this.setForeground(ThemeManager.BRONZE);
                        this.setBackground(ThemeManager.surface());
                    } else {
                        this.setForeground(ThemeManager.text());
                        this.setBackground(ThemeManager.surface());
                    }
                }

                return this;
            }
        };
    }

    private double cgpaToPercent(double cgpa) {
        if (cgpa >= (double)4.0F) {
            return (double)90.0F;
        } else if (cgpa >= (double)3.75F) {
            return (double)85.0F;
        } else if (cgpa >= (double)3.5F) {
            return (double)80.0F;
        } else if (cgpa >= (double)3.0F) {
            return (double)75.0F;
        } else if (cgpa >= (double)2.75F) {
            return (double)70.0F;
        } else if (cgpa >= (double)2.5F) {
            return (double)65.0F;
        } else if (cgpa >= (double)2.0F) {
            return (double)60.0F;
        } else if (cgpa >= (double)1.75F) {
            return (double)55.0F;
        } else {
            return cgpa >= (double)1.0F ? (double)50.0F : (double)45.0F;
        }
    }

    private String standing(double gpa) {
        if (gpa >= (double)3.75F) {
            return "Distinction";
        } else if (gpa >= (double)3.5F) {
            return "Very Good";
        } else if (gpa >= (double)3.0F) {
            return "Good";
        } else if (gpa >= (double)2.0F) {
            return "Pass";
        } else {
            return gpa >= (double)1.0F ? "Probation" : "Fail";
        }
    }

    private static class CourseItem {
        final int id;
        final String label;

        CourseItem(int id, String label) {
            this.id = id;
            this.label = label;
        }

        public String toString() {
            return this.label;
        }
    }

    private static class StudentRank {
        int id;
        int courseCount;
        int totalCredits;
        String studentNo;
        String name;
        double cgpa;
    }
}
