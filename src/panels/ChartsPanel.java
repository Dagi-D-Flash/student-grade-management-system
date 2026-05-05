package panels;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Paint;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import models.User;
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
import util.DBConnection;
import util.GPACalculator;
import util.ThemeManager;

public class ChartsPanel extends JPanel {
    private final User user;
    private int studentId = -1;
    private ChartPanel barChartPanel;
    private ChartPanel lineChartPanel;
    private ChartPanel pieChartPanel;
    private JButton btnRefresh;
    private Timer autoRefresh;
    private final Runnable themeListener = this::onThemeChanged;

    public ChartsPanel(User user) {
        this.user = user;
        this.setLayout(new BorderLayout(8, 8));
        this.setBorder(ThemeManager.panelBorder());
        this.setBackground(ThemeManager.bg());
        this.studentId = this.resolveStudentId();
        this.add(this.buildToolbar(), "North");
        this.add(this.buildChartGrid(), "Center");
        this.loadCharts();
        this.autoRefresh = new Timer(30000, (e) -> this.loadCharts());
        this.autoRefresh.start();
        ThemeManager.addThemeListener(this.themeListener);
        this.addHierarchyListener((e) -> {
            if ((e.getChangeFlags() & 2L) != 0L && !this.isDisplayable()) {
                ThemeManager.removeThemeListener(this.themeListener);
                this.autoRefresh.stop();
            }

        });
    }

    private void onThemeChanged() {
        this.loadCharts();
        this.barChartPanel.setBackground(ThemeManager.surface());
        this.lineChartPanel.setBackground(ThemeManager.surface());
        this.pieChartPanel.setBackground(ThemeManager.surface());
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(ThemeManager.cardBorder());
        JLabel title = new JLabel("My Performance Charts");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());
        this.btnRefresh = ThemeManager.primaryButton("Refresh");
        this.btnRefresh.addActionListener((e) -> this.loadCharts());
        JPanel right = new JPanel(new FlowLayout(2, 0, 0));
        right.setOpaque(false);
        right.add(this.btnRefresh);
        bar.add(title, "West");
        bar.add(right, "East");
        return bar;
    }

    private JComponent buildChartGrid() {
        this.barChartPanel = this.emptyChartPanel();
        this.lineChartPanel = this.emptyChartPanel();
        this.pieChartPanel = this.emptyChartPanel();
        JPanel top = new JPanel(new GridLayout(1, 2, 10, 0));
        top.setOpaque(false);
        top.add(this.wrap(this.barChartPanel, "Marks per Subject (Bar)"));
        top.add(this.wrap(this.lineChartPanel, "GPA Trend per Semester (Line)"));
        JPanel bottom = new JPanel(new GridLayout(1, 1));
        bottom.setOpaque(false);
        bottom.add(this.wrap(this.pieChartPanel, "Pass vs Fail Ratio (Pie)"));
        JSplitPane split = new JSplitPane(0, top, bottom);
        split.setDividerLocation(320);
        split.setResizeWeight(0.6);
        split.setBorder((Border)null);
        split.setOpaque(false);
        return split;
    }

    private JPanel wrap(ChartPanel cp, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(ThemeManager.surface());
        p.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(ThemeManager.border()), title));
        p.add(cp, "Center");
        return p;
    }

    private ChartPanel emptyChartPanel() {
        ChartPanel cp = new ChartPanel((JFreeChart)null);
        cp.setPreferredSize(new Dimension(400, 280));
        cp.setBackground(ThemeManager.surface());
        return cp;
    }

    private void loadCharts() {
        if (this.studentId >= 0) {
            SwingUtilities.invokeLater(() -> {
                this.barChartPanel.setChart(this.buildBarChart());
                this.lineChartPanel.setChart(this.buildLineChart());
                this.pieChartPanel.setChart(this.buildPieChart());
                this.barChartPanel.setBackground(ThemeManager.surface());
                this.lineChartPanel.setBackground(ThemeManager.surface());
                this.pieChartPanel.setBackground(ThemeManager.surface());
            });
        }
    }

    private JFreeChart buildBarChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String sql = "SELECT sub.name, SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct FROM grades g JOIN course_grade_components cgc ON cgc.id = g.component_id JOIN enrollments e ON e.id=g.enrollment_id JOIN courses c ON c.id=e.course_id JOIN subjects sub ON sub.id=c.subject_id WHERE e.student_id=? GROUP BY sub.id,sub.name ORDER BY sub.name";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, this.studentId);

            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    dataset.addValue(rs.getDouble("avg_pct"), "Weighted Avg %", rs.getString("name"));
                }
            }
        } catch (SQLException ex) {
            this.logError(ex);
        }

        JFreeChart chart = ChartFactory.createBarChart((String)null, "Subject", "Score (%)", dataset, PlotOrientation.VERTICAL, false, true, false);
        this.applyChartTheme(chart);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.getRangeAxis().setRange((double)0.0F, (double)100.0F);
        ((BarRenderer)plot.getRenderer()).setSeriesPaint(0, ThemeManager.accent());
        ((BarRenderer)plot.getRenderer()).setMaximumBarWidth(0.1);
        ((BarRenderer)plot.getRenderer()).setShadowVisible(false);
        plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        this.styleAxis(plot);
        return chart;
    }

    private JFreeChart buildLineChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String sql = "SELECT c.academic_year, c.semester, sub.credits,        SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct FROM grades g JOIN course_grade_components cgc ON cgc.id = g.component_id JOIN enrollments e ON e.id=g.enrollment_id JOIN courses c ON c.id=e.course_id JOIN subjects sub ON sub.id=c.subject_id WHERE e.student_id=? GROUP BY e.id,c.academic_year,c.semester,sub.credits ORDER BY c.academic_year,c.semester";
        LinkedHashMap<String, double[]> semMap = new LinkedHashMap();

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, this.studentId);

            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    int var10000 = rs.getInt("academic_year");
                    String key = var10000 + " " + rs.getString("semester");
                    int cr = rs.getInt("credits");
                    double gp = GPACalculator.toGradePoint(rs.getDouble("avg_pct"));
                    semMap.merge(key, new double[]{gp * (double)cr, (double)cr}, (a, b) -> new double[]{a[0] + b[0], a[1] + b[1]});
                }
            }
        } catch (SQLException ex) {
            this.logError(ex);
        }

        for(Map.Entry<String, double[]> e : semMap.entrySet()) {
            double gpa = ((double[])e.getValue())[1] > (double)0.0F ? ((double[])e.getValue())[0] / ((double[])e.getValue())[1] : (double)0.0F;
            dataset.addValue(gpa, "GPA", (Comparable)e.getKey());
        }

        JFreeChart chart = ChartFactory.createLineChart((String)null, "Semester", "GPA (4.0)", dataset, PlotOrientation.VERTICAL, false, true, false);
        this.applyChartTheme(chart);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.getRangeAxis().setRange((double)0.0F, (double)4.0F);
        LineAndShapeRenderer r = (LineAndShapeRenderer)plot.getRenderer();
        r.setSeriesPaint(0, ThemeManager.SUCCESS);
        r.setSeriesStroke(0, new BasicStroke(2.5F));
        r.setDefaultShapesVisible(true);
        plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        this.styleAxis(plot);
        return chart;
    }

    private JFreeChart buildPieChart() {
        DefaultPieDataset dataset = new DefaultPieDataset();
        int pass = 0;
        int fail = 0;
        String sql = "SELECT SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct FROM grades g JOIN course_grade_components cgc ON cgc.id=g.component_id JOIN enrollments e ON e.id=g.enrollment_id WHERE e.student_id=? GROUP BY e.id";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, this.studentId);

            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    if (rs.getDouble("avg_pct") > (double)50.0F) {
                        ++pass;
                    } else {
                        ++fail;
                    }
                }
            }
        } catch (SQLException ex) {
            this.logError(ex);
        }

        if (pass == 0 && fail == 0) {
            dataset.setValue("No Data", (double)1.0F);
        } else {
            if (pass > 0) {
                dataset.setValue("Pass (" + pass + ")", (double)pass);
            }

            if (fail > 0) {
                dataset.setValue("Fail (" + fail + ")", (double)fail);
            }
        }

        JFreeChart chart = ChartFactory.createPieChart((String)null, dataset, true, true, false);
        this.applyChartTheme(chart);
        PiePlot plot = (PiePlot)chart.getPlot();
        plot.setBackgroundPaint(ThemeManager.surface());
        plot.setSectionPaint("Pass (" + pass + ")", ThemeManager.SUCCESS);
        plot.setSectionPaint("Fail (" + fail + ")", ThemeManager.danger());
        plot.setSectionPaint("No Data", ThemeManager.border());
        plot.setOutlineVisible(false);
        plot.setShadowPaint((Paint)null);
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
            CategoryPlot plot = (CategoryPlot)chart.getPlot();
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

    private int resolveStudentId() {
        String sql = "SELECT id FROM students WHERE user_id = ?";

        try {
            try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
            ) {
                ps.setInt(1, this.user.getId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int var5 = rs.getInt("id");
                        return var5;
                    } else {
                        return -1;
                    }
                }
            }
        } catch (SQLException ex) {
            this.logError(ex);
            return -1;
        }
    }

    private void logError(Exception ex) {
        System.err.println("Chart query error: " + ex.getMessage());
    }
}
