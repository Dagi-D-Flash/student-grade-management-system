package panels;

import dao.EnrollmentDAO;
import dao.GradeDAO;
import models.Enrollment;
import models.Grade;
import models.User;
import util.DBConnection;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TeacherGradePanel extends JPanel {

    private final GradeDAO gradeDAO = new GradeDAO();
    private final EnrollmentDAO enrollmentDAO = new EnrollmentDAO();
    private final User teacher;

    private JComboBox<CourseItem> cbCourse;
    private JTable studentTable;
    private DefaultTableModel studentModel;
    private JTable gradeTable;
    private DefaultTableModel gradeModel;

    private JTextField tfScore, tfMaxScore, tfWeight, tfRemarks;
    private JComboBox<String> cbGradeType;
    private JLabel lblTotalMarks, lblGradeLetter, lblGradePoint;
    private JButton btnSave, btnUpdate, btnDelete, btnClear;

    private int selectedEnrollmentId = -1;
    private int selectedGradeId = -1;

    public TeacherGradePanel(User teacher) {
        this.teacher = teacher;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(new Color(245, 246, 250));

        add(buildTopBar(),     BorderLayout.NORTH);
        add(buildCenterSplit(), BorderLayout.CENTER);
        add(buildFormPanel(),  BorderLayout.SOUTH);

        loadCourses();
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        bar.add(new JLabel("Course:"));
        cbCourse = new JComboBox<>();
        cbCourse.setPreferredSize(new Dimension(320, 28));
        cbCourse.addActionListener(e -> loadStudents());
        bar.add(cbCourse);
        return bar;
    }

    private JSplitPane buildCenterSplit() {
        studentModel = new DefaultTableModel(
            new String[]{"Enrollment ID", "Student No", "Student Name", "Status"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        studentTable = new JTable(studentModel);
        studentTable.setRowHeight(26);
        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studentTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        studentTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        studentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onStudentSelected();
        });

        gradeModel = new DefaultTableModel(
            new String[]{"Grade ID", "Type", "Score", "Max Score", "Weight", "Weighted %", "Remarks"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        gradeTable = new JTable(gradeModel);
        gradeTable.setRowHeight(26);
        gradeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        gradeTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        gradeTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        gradeTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onGradeSelected();
        });

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Enrolled Students"));
        leftPanel.add(new JScrollPane(studentTable));

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Grades for Selected Student"));
        rightPanel.add(new JScrollPane(gradeTable));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setDividerLocation(380);
        split.setResizeWeight(0.4);
        return split;
    }

    private JPanel buildFormPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(10, 0));
        wrapper.setBackground(Color.WHITE);
        wrapper.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220)),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        cbGradeType = new JComboBox<>(new String[]{"quiz", "assignment", "midterm", "final", "project"});
        tfScore     = new JTextField(7);
        tfMaxScore  = new JTextField(7);
        tfWeight    = new JTextField(5);
        tfRemarks   = new JTextField(14);

        lblTotalMarks  = new JLabel("—");
        lblGradeLetter = new JLabel("—");
        lblGradePoint  = new JLabel("—");

        lblTotalMarks.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblGradeLetter.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblGradePoint.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblGradeLetter.setForeground(new Color(13, 110, 253));

        g.gridy = 0; g.gridwidth = 1; g.weightx = 0;
        g.gridx = 0; fields.add(new JLabel("Grade Type:"), g);
        g.gridx = 1; g.weightx = 0.15; fields.add(cbGradeType, g);
        g.gridx = 2; g.weightx = 0; fields.add(new JLabel("Score:"), g);
        g.gridx = 3; g.weightx = 0.1; fields.add(tfScore, g);
        g.gridx = 4; g.weightx = 0; fields.add(new JLabel("Max Score:"), g);
        g.gridx = 5; g.weightx = 0.1; fields.add(tfMaxScore, g);
        g.gridx = 6; g.weightx = 0; fields.add(new JLabel("Weight:"), g);
        g.gridx = 7; g.weightx = 0.08; fields.add(tfWeight, g);
        g.gridx = 8; g.weightx = 0; fields.add(new JLabel("Remarks:"), g);
        g.gridx = 9; g.weightx = 0.2; fields.add(tfRemarks, g);

        g.gridy = 1; g.weightx = 0;
        g.gridx = 0; fields.add(new JLabel("Weighted %:"), g);
        g.gridx = 1; fields.add(lblTotalMarks, g);
        g.gridx = 2; fields.add(new JLabel("Letter Grade:"), g);
        g.gridx = 3; fields.add(lblGradeLetter, g);
        g.gridx = 4; fields.add(new JLabel("Grade Point:"), g);
        g.gridx = 5; fields.add(lblGradePoint, g);

        tfScore.getDocument().addDocumentListener(new SimpleDocListener(this::recalculate));
        tfMaxScore.getDocument().addDocumentListener(new SimpleDocListener(this::recalculate));
        tfWeight.getDocument().addDocumentListener(new SimpleDocListener(this::recalculate));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setOpaque(false);
        btnSave   = styledBtn("Save",   new Color(25, 135, 84));
        btnUpdate = styledBtn("Update", new Color(13, 110, 253));
        btnDelete = styledBtn("Delete", new Color(220, 53, 69));
        btnClear  = styledBtn("Clear",  new Color(108, 117, 125));
        btnPanel.add(btnSave); btnPanel.add(btnUpdate); btnPanel.add(btnDelete); btnPanel.add(btnClear);

        btnSave.addActionListener(e -> saveGrade());
        btnUpdate.addActionListener(e -> updateGrade());
        btnDelete.addActionListener(e -> deleteGrade());
        btnClear.addActionListener(e -> clearForm());

        wrapper.add(fields, BorderLayout.CENTER);
        wrapper.add(btnPanel, BorderLayout.EAST);
        return wrapper;
    }

    private void loadCourses() {
        cbCourse.removeAllItems();
        String sql = "SELECT c.id, s.code, s.name, c.section, c.academic_year, c.semester " +
                     "FROM courses c " +
                     "JOIN subjects s ON s.id = c.subject_id " +
                     "JOIN teachers t ON t.id = c.teacher_id " +
                     "JOIN users u ON u.id = t.user_id " +
                     "WHERE u.id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teacher.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String label = rs.getString("code") + " - " + rs.getString("name")
                        + " [" + rs.getString("section") + "] "
                        + rs.getInt("academic_year") + " " + rs.getString("semester");
                    cbCourse.addItem(new CourseItem(rs.getInt("id"), label));
                }
            }
        } catch (SQLException ex) {
            showError(ex);
        }
        if (cbCourse.getItemCount() > 0) loadStudents();
    }

    private void loadStudents() {
        studentModel.setRowCount(0);
        selectedEnrollmentId = -1;
        gradeModel.setRowCount(0);
        CourseItem ci = (CourseItem) cbCourse.getSelectedItem();
        if (ci == null) return;
        String sql = "SELECT e.id, s.student_no, s.first_name, s.last_name, e.status " +
                     "FROM enrollments e JOIN students s ON s.id = e.student_id " +
                     "WHERE e.course_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ci.id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    studentModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("student_no"),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        rs.getString("status")
                    });
                }
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void onStudentSelected() {
        int row = studentTable.getSelectedRow();
        if (row < 0) return;
        selectedEnrollmentId = (int) studentModel.getValueAt(row, 0);
        loadGrades();
        clearForm();
    }

    private void loadGrades() {
        gradeModel.setRowCount(0);
        if (selectedEnrollmentId < 0) return;
        String sql = "SELECT * FROM grades WHERE enrollment_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, selectedEnrollmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double score    = rs.getDouble("score");
                    double maxScore = rs.getDouble("max_score");
                    double weight   = rs.getDouble("weight");
                    double weighted = maxScore > 0 ? (score / maxScore) * 100 * weight : 0;
                    gradeModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("grade_type"),
                        score, maxScore, weight,
                        String.format("%.2f", weighted),
                        rs.getString("remarks")
                    });
                }
            }
        } catch (SQLException ex) { showError(ex); }
    }

    private void onGradeSelected() {
        int row = gradeTable.getSelectedRow();
        if (row < 0) return;
        selectedGradeId = (int) gradeModel.getValueAt(row, 0);
        cbGradeType.setSelectedItem(gradeModel.getValueAt(row, 1));
        tfScore.setText(String.valueOf(gradeModel.getValueAt(row, 2)));
        tfMaxScore.setText(String.valueOf(gradeModel.getValueAt(row, 3)));
        tfWeight.setText(String.valueOf(gradeModel.getValueAt(row, 4)));
        tfRemarks.setText((String) gradeModel.getValueAt(row, 6));
        recalculate();
    }

    private void saveGrade() {
        if (selectedEnrollmentId < 0) {
            JOptionPane.showMessageDialog(this, "Select a student first.");
            return;
        }
        try {
            Grade g = buildGradeFromForm();
            Enrollment e = new Enrollment(); e.setId(selectedEnrollmentId);
            g.setEnrollment(e);
            gradeDAO.insert(g);
            loadGrades();
            clearForm();
        } catch (Exception ex) { showError(ex); }
    }

    private void updateGrade() {
        if (selectedGradeId < 0) {
            JOptionPane.showMessageDialog(this, "Select a grade row first.");
            return;
        }
        try {
            Grade g = buildGradeFromForm();
            g.setId(selectedGradeId);
            Enrollment e = new Enrollment(); e.setId(selectedEnrollmentId);
            g.setEnrollment(e);
            gradeDAO.update(g);
            loadGrades();
            clearForm();
        } catch (Exception ex) { showError(ex); }
    }

    private void deleteGrade() {
        if (selectedGradeId < 0) {
            JOptionPane.showMessageDialog(this, "Select a grade row first.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Delete selected grade?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                gradeDAO.delete(selectedGradeId);
                loadGrades();
                clearForm();
            } catch (SQLException ex) { showError(ex); }
        }
    }

    private Grade buildGradeFromForm() {
        Grade g = new Grade();
        g.setGradeType((String) cbGradeType.getSelectedItem());
        g.setScore(parseDouble(tfScore.getText()));
        g.setMaxScore(parseDouble(tfMaxScore.getText()));
        g.setWeight(parseDouble(tfWeight.getText()));
        g.setRemarks(tfRemarks.getText().trim());
        return g;
    }

    private void recalculate() {
        double score    = parseDouble(tfScore.getText());
        double maxScore = parseDouble(tfMaxScore.getText());
        double weight   = parseDouble(tfWeight.getText());

        if (maxScore <= 0) {
            lblTotalMarks.setText("—");
            lblGradeLetter.setText("—");
            lblGradePoint.setText("—");
            return;
        }

        double weighted = (score / maxScore) * 100 * weight;
        double pct      = (score / maxScore) * 100;

        lblTotalMarks.setText(String.format("%.2f", weighted));
        lblGradeLetter.setText(toLetterGrade(pct));
        lblGradePoint.setText(String.format("%.1f", toGradePoint(pct)));
    }

    private String toLetterGrade(double pct) {
        if (pct >= 97) return "A+";
        if (pct >= 93) return "A";
        if (pct >= 90) return "A-";
        if (pct >= 87) return "B+";
        if (pct >= 83) return "B";
        if (pct >= 80) return "B-";
        if (pct >= 77) return "C+";
        if (pct >= 73) return "C";
        if (pct >= 70) return "C-";
        if (pct >= 67) return "D+";
        if (pct >= 63) return "D";
        if (pct >= 60) return "D-";
        return "F";
    }

    private double toGradePoint(double pct) {
        if (pct >= 97) return 4.0;
        if (pct >= 93) return 4.0;
        if (pct >= 90) return 3.7;
        if (pct >= 87) return 3.3;
        if (pct >= 83) return 3.0;
        if (pct >= 80) return 2.7;
        if (pct >= 77) return 2.3;
        if (pct >= 73) return 2.0;
        if (pct >= 70) return 1.7;
        if (pct >= 67) return 1.3;
        if (pct >= 63) return 1.0;
        if (pct >= 60) return 0.7;
        return 0.0;
    }

    private void clearForm() {
        selectedGradeId = -1;
        cbGradeType.setSelectedIndex(0);
        tfScore.setText(""); tfMaxScore.setText(""); tfWeight.setText(""); tfRemarks.setText("");
        lblTotalMarks.setText("—"); lblGradeLetter.setText("—"); lblGradePoint.setText("—");
        gradeTable.clearSelection();
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private JButton styledBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static class CourseItem {
        int id; String label;
        CourseItem(int id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }

    private static class SimpleDocListener implements javax.swing.event.DocumentListener {
        private final Runnable action;
        SimpleDocListener(Runnable action) { this.action = action; }
        public void insertUpdate(javax.swing.event.DocumentEvent e)  { action.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e)  { action.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
    }
}
