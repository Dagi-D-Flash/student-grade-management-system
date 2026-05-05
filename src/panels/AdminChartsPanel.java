package panels;

import util.DBConnection;
import util.GPACalculator;
import util.ThemeManager;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class AdminChartsPanel extends JPanel {

    private final models.User scopedUser;  // null = admin; non-null = teacher (own students only)

    private ChartPanel barChartPanel;
    private ChartPanel lineChartPanel;
    private ChartPanel pieChartPanel;

    private JComboBox<StudentItem> cbStudent;
    private JTextField tfSearch;
    private JButton btnRefresh;
    private Timer   autoRefresh;
    private final java.util.List<StudentItem> allStudents = new java.util.ArrayList<>();
    private final Runnable themeListener = this::onThemeChanged;

    public AdminChartsPanel() { this(null); }

    public AdminChartsPanel(models.User scopedUser) {
        this.scopedUser = scopedUser;
        setLayout(new BorderLayout(8, 8));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());

        add(buildToolbar(),   BorderLayout.NORTH);
        add(buildChartGrid(), BorderLayout.CENTER);

        loadStudents();
        loadCharts();

        autoRefresh = new Timer(30_000, e -> loadCharts());
        autoRefresh.start();

        ThemeManager.addThemeListener(themeListener);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                    && !isDisplayable()) {
                ThemeManager.removeThemeListener(themeListener);
                autoRefresh.stop();
            }
        });
    }

    private void onThemeChanged() {
        loadCharts();
        barChartPanel.setBackground(ThemeManager.surface());
        lineChartPanel.setBackground(ThemeManager.surface());
        pieChartPanel.setBackground(ThemeManager.surface());
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(ThemeManager.cardBorder());

        JLabel title = new JLabel("Analytics & Charts");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);

        // search field — filters the combo in real-time
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
        cbStudent.addActionListener(e -> loadCharts());

        btnRefresh = ThemeManager.primaryButton("Refresh");
        btnRefresh.addActionListener(e -> { loadStudents(); loadCharts(); });

        controls.add(searchIcon);
        controls.add(tfSearch);
        controls.add(lbl);
        controls.add(cbStudent);
        controls.add(btnRefresh);

        bar.add(title,    BorderLayout.WEST);
        bar.add(controls, BorderLayout.EAST);
        return bar;
    }

    /** Filters the student combo in real-time as the user types. */
    private void filterStudents(String query) {
        String q = query.trim().toLowerCase();
        StudentItem prev = (StudentItem) cbStudent.getSelectedItem();

        cbStudent.removeAllItems();
        for (StudentItem item : allStudents) {
            String label = item.label.toLowerCase();
            // strip WOUR/ for flexible ID matching
            String stripped = label.replace("wour/", "").replace("/", "");
            String qStripped = q.replace("wour/", "").replace("/", "");
            if (q.isEmpty() || label.contains(q) || stripped.contains(qStripped)) {
                cbStudent.addItem(item);
            }
        }

        // restore previous selection if still in list, else auto-select first match
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

    private JComponent buildChartGrid() {
        barChartPanel  = emptyChartPanel();
        lineChartPanel = emptyChartPanel();
        pieChartPanel  = emptyChartPanel();

        JPanel top = new JPanel(new GridLayout(1, 2, 10, 0));
        top.setOpaque(false);
        top.add(wrap(barChartPanel,  "Marks per Subject (Bar)"));
        top.add(wrap(lineChartPanel, "GPA Trend per Semester (Line)"));

        JPanel bottom = new JPanel(new GridLayout(1, 1));
        bottom.setOpaque(false);
        bottom.add(wrap(pieChartPanel, "Pass vs Fail Ratio (Pie)"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setDividerLocation(320);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        split.setOpaque(false);
        return split;
    }

    private JPanel wrap(ChartPanel cp, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(ThemeManager.surface());
        p.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.border()), title));
        p.add(cp, BorderLayout.CENTER);
        return p;
    }

    private ChartPanel emptyChartPanel() {
        ChartPanel cp = new ChartPanel(null);
        cp.setPreferredSize(new Dimension(400, 280));
        cp.setBackground(ThemeManager.surface());
        return cp;
    }

    private void loadStudents() {
        StudentItem selected = (StudentItem) cbStudent.getSelectedItem();
        allStudents.clear();
        cbStudent.removeAllItems();
        String sql;
        if (scopedUser != null) {
            // Teacher: only students enrolled in their courses
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
            if (scopedUser != null) ps.setInt(1, scopedUser.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StudentItem item = new StudentItem(rs.getInt("id"),
                        rs.getString("student_no") + " — " +
                        rs.getString("first_name") + " " + rs.getString("last_name"));
                    allStudents.add(item);
                    cbStudent.addItem(item);
                    if (selected != null && item.id == selected.id)
                        cbStudent.setSelectedItem(item);
                }
            }
        } catch (SQLException ex) { logError(ex); }
    }

    private void loadCharts() {
        StudentItem si = (StudentItem) cbStudent.getSelectedItem();
        if (si == null) return;
        int sid = si.id;
        SwingUtilities.invokeLater(() -> {
            barChartPanel.setChart(buildBarChart(sid));
            lineChartPanel.setChart(buildLineChart(sid));
            pieChartPanel.setChart(buildPieChart(sid));
            barChartPanel.setBackground(ThemeManager.surface());
            lineChartPanel.setBackground(ThemeManager.surface());
            pieChartPanel.setBackground(ThemeManager.surface());
        });
    }

    private JFreeChart buildBarChart(int studentId) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        // When scoped to a teacher: only show subjects from that teacher's courses
        String teacherFilter = scopedUser != null
            ? "AND c.teacher_id = (SELECT id FROM teachers WHERE user_id = " + scopedUser.getId() + ") "
            : "";
        String sql =
            "SELECT sub.name, SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM grades g " +
            "JOIN enrollments e ON e.id = g.enrollment_id " +
            "JOIN courses c ON c.id = e.course_id " +
            "JOIN subjects sub ON sub.id = c.subject_id " +
            "JOIN course_grade_components cgc ON cgc.id = g.component_id AND cgc.course_id = c.id " +
            "WHERE e.student_id = ? " + teacherFilter +
            "GROUP BY e.id, sub.id, sub.name ORDER BY sub.name";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    dataset.addValue(rs.getDouble("avg_pct"), "Weighted Avg %", rs.getString("name"));
            }
        } catch (SQLException ex) { logError(ex); }

        JFreeChart chart = ChartFactory.createBarChart(
            null, "Subject", "Score (%)", dataset, PlotOrientation.VERTICAL, false, true, false);
        applyChartTheme(chart);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.getRangeAxis().setRange(0, 100);
        ((BarRenderer) plot.getRenderer()).setSeriesPaint(0, ThemeManager.accent());
        ((BarRenderer) plot.getRenderer()).setMaximumBarWidth(0.1);
        ((BarRenderer) plot.getRenderer()).setShadowVisible(false);
        plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        styleAxis(plot);
        return chart;
    }

    private JFreeChart buildLineChart(int studentId) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String teacherFilter = scopedUser != null
            ? "AND c.teacher_id = (SELECT id FROM teachers WHERE user_id = " + scopedUser.getId() + ") "
            : "";
        String sql =
            "SELECT c.academic_year, c.semester, sub.credits, " +
            "       SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM grades g " +
            "JOIN enrollments e ON e.id = g.enrollment_id " +
            "JOIN courses c ON c.id = e.course_id " +
            "JOIN subjects sub ON sub.id = c.subject_id " +
            "JOIN course_grade_components cgc ON cgc.id = g.component_id AND cgc.course_id = c.id " +
            "WHERE e.student_id = ? " + teacherFilter +
            "GROUP BY e.id, c.academic_year, c.semester, sub.credits ORDER BY c.academic_year, c.semester";

        LinkedHashMap<String, double[]> semMap = new LinkedHashMap<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getInt("academic_year") + " " + rs.getString("semester");
                    int    cr  = rs.getInt("credits");
                    double gp  = GPACalculator.toGradePoint(rs.getDouble("avg_pct"));
                    semMap.merge(key, new double[]{gp * cr, cr},
                        (a, b) -> new double[]{a[0] + b[0], a[1] + b[1]});
                }
            }
        } catch (SQLException ex) { logError(ex); }

        for (Map.Entry<String, double[]> e : semMap.entrySet()) {
            double gpa = e.getValue()[1] > 0 ? e.getValue()[0] / e.getValue()[1] : 0;
            dataset.addValue(gpa, "GPA", e.getKey());
        }

        JFreeChart chart = ChartFactory.createLineChart(
            null, "Semester", "GPA (4.0)", dataset, PlotOrientation.VERTICAL, false, true, false);
        applyChartTheme(chart);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.getRangeAxis().setRange(0, 4.0);
        LineAndShapeRenderer r = (LineAndShapeRenderer) plot.getRenderer();
        r.setSeriesPaint(0, ThemeManager.SUCCESS);
        r.setSeriesStroke(0, new BasicStroke(2.5f));
        r.setDefaultShapesVisible(true);
        plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        styleAxis(plot);
        return chart;
    }

    private JFreeChart buildPieChart(int studentId) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        int pass = 0, fail = 0;
        String teacherFilter = scopedUser != null
            ? "AND c.teacher_id = (SELECT id FROM teachers WHERE user_id = " + scopedUser.getId() + ") "
            : "";
        String sql =
            "SELECT SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM grades g " +
            "JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "JOIN enrollments e ON e.id = g.enrollment_id " +
            "JOIN courses c ON c.id = e.course_id " +
            "WHERE e.student_id = ? " + teacherFilter +
            "AND cgc.course_id = c.id " +
            "GROUP BY e.id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getDouble("avg_pct") > 50) pass++; else fail++;
                }
            }
        } catch (SQLException ex) { logError(ex); }

        if (pass == 0 && fail == 0) dataset.setValue("No Data", 1);
        else {
            if (pass > 0) dataset.setValue("Pass (" + pass + ")", pass);
            if (fail > 0) dataset.setValue("Fail (" + fail + ")", fail);
        }

        JFreeChart chart = ChartFactory.createPieChart(null, dataset, true, true, false);
        applyChartTheme(chart);
        PiePlot plot = (PiePlot) chart.getPlot();
        plot.setBackgroundPaint(ThemeManager.surface());
        plot.setSectionPaint("Pass (" + pass + ")", ThemeManager.SUCCESS);
        plot.setSectionPaint("Fail (" + fail + ")", ThemeManager.danger());
        plot.setSectionPaint("No Data", ThemeManager.border());
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setLabelFont(ThemeManager.fontBody());
        plot.setLabelBackgroundPaint(ThemeManager.elevated());
        plot.setLabelOutlinePaint(ThemeManager.border());
        plot.setLabelPaint(ThemeManager.text());
        return chart;
    }

    private void applyChartTheme(JFreeChart chart) {
        chart.setBackgroundPaint(ThemeManager.surface());
        chart.setBorderVisible(false);
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(ThemeManager.surface());
            chart.getLegend().setItemPaint(ThemeManager.text());
        }
        if (chart.getPlot() instanceof CategoryPlot) {
            CategoryPlot plot = (CategoryPlot) chart.getPlot();
            plot.setBackgroundPaint(ThemeManager.chartPlotBg());
            plot.setRangeGridlinePaint(ThemeManager.border());
            plot.setDomainGridlinePaint(ThemeManager.border());
            plot.setOutlineVisible(false);
        }
    }

    private void styleAxis(CategoryPlot plot) {
        plot.getRangeAxis().setLabelPaint(ThemeManager.muted());
        plot.getRangeAxis().setTickLabelPaint(ThemeManager.muted());
        plot.getRangeAxis().setAxisLinePaint(ThemeManager.border());
        plot.getDomainAxis().setLabelPaint(ThemeManager.muted());
        plot.getDomainAxis().setTickLabelPaint(ThemeManager.muted());
        plot.getDomainAxis().setAxisLinePaint(ThemeManager.border());
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Chart Error", JOptionPane.ERROR_MESSAGE);
    }

    private void logError(Exception ex) {
        System.err.println("Chart query error: " + ex.getMessage());
    }

    private static class StudentItem {
        int id; String label;
        StudentItem(int id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }
}
