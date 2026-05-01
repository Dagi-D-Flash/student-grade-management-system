package panels;

import dao.CourseDAO;
import dao.SubjectDAO;
import dao.TeacherDAO;
import models.Course;
import models.Subject;
import models.Teacher;
import util.DBConnection;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class CoursePanel extends JPanel {

    private final CourseDAO  courseDAO  = new CourseDAO();
    private final SubjectDAO subjectDAO = new SubjectDAO();
    private final TeacherDAO teacherDAO = new TeacherDAO();

    private JTable table;
    private DefaultTableModel tableModel;
    private final java.util.List<Object[]> allRows = new java.util.ArrayList<>();

    private JTextField tfSection, tfYear, tfMaxStudents;
    private JComboBox<String>      cbSemester;
    private JComboBox<SubjectItem> cbSubject;
    private JComboBox<TeacherItem> cbTeacher;
    private JButton btnAdd, btnUpdate, btnDelete, btnClear, btnRefreshDropdowns;
    private JLabel lblStatus;

    private int selectedId = -1;

    public CoursePanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());
        add(buildFormPanel(),  BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        loadDropdowns();
        loadTable();
    }

    private JPanel buildFormPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 6));
        outer.setBackground(ThemeManager.surface());
        outer.setBorder(ThemeManager.cardBorder());

        JLabel title = new JLabel("Course Management");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());
        outer.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        tfSection     = field(); tfYear = field(); tfMaxStudents = field();
        cbSemester    = new JComboBox<>(new String[]{"1st", "2nd", "summer"});
        cbSubject     = new JComboBox<>();
        cbTeacher     = new JComboBox<>();

        g.gridy = 0; g.gridwidth = 1; g.weightx = 0;
        g.gridx = 0; form.add(lbl("Subject *"), g);
        g.gridx = 1; g.weightx = 0.4; form.add(cbSubject, g);
        g.gridx = 2; g.weightx = 0; form.add(lbl("Teacher *"), g);
        g.gridx = 3; g.weightx = 0.4; form.add(cbTeacher, g);

        g.gridy = 1; g.weightx = 0;
        g.gridx = 0; form.add(lbl("Section *"), g);
        g.gridx = 1; g.weightx = 0.2; form.add(tfSection, g);
        g.gridx = 2; g.weightx = 0; form.add(lbl("Semester *"), g);
        g.gridx = 3; g.weightx = 0.2; form.add(cbSemester, g);

        g.gridy = 2; g.weightx = 0;
        g.gridx = 0; form.add(lbl("Academic Year *"), g);
        g.gridx = 1; g.weightx = 0.2; form.add(tfYear, g);
        g.gridx = 2; g.weightx = 0; form.add(lbl("Max Students"), g);
        g.gridx = 3; g.weightx = 0.2; form.add(tfMaxStudents, g);

        lblStatus = new JLabel(" ");
        lblStatus.setFont(ThemeManager.fontSmall());
        g.gridx = 0; g.gridy = 3; g.gridwidth = 4; g.weightx = 1;
        form.add(lblStatus, g);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false);
        btnAdd              = ThemeManager.primaryButton("Add Course");
        btnUpdate           = ThemeManager.primaryButton("Update");
        btnDelete           = ThemeManager.dangerButton("Delete");
        btnClear            = ThemeManager.secondaryButton("Clear");
        btnRefreshDropdowns = ThemeManager.secondaryButton("Refresh Lists");
        btnRow.add(btnAdd); btnRow.add(btnUpdate); btnRow.add(btnDelete);
        btnRow.add(btnClear); btnRow.add(btnRefreshDropdowns);

        g.gridy = 4; g.gridwidth = 4;
        form.add(btnRow, g);

        btnAdd.addActionListener(e              -> addCourse());
        btnUpdate.addActionListener(e           -> updateCourse());
        btnDelete.addActionListener(e           -> deleteCourse());
        btnClear.addActionListener(e            -> clearForm());
        btnRefreshDropdowns.addActionListener(e -> loadDropdowns());

        outer.add(form, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildTablePanel() {
        String[] cols = {"ID", "Subject", "Teacher", "Section", "Year", "Semester", "Max Students"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        ThemeManager.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) populateForm();
        });

        JTextField tfSearch = searchField("Search by subject, teacher, section or semester...");
        tfSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filterTable(tfSearch.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filterTable(tfSearch.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(tfSearch.getText()); }
        });

        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setBackground(ThemeManager.surface());
        wrapper.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.border()), "All Courses"));
        wrapper.add(buildSearchBar(tfSearch), BorderLayout.NORTH);
        wrapper.add(new JScrollPane(table),   BorderLayout.CENTER);
        return wrapper;
    }

    private void filterTable(String query) {
        tableModel.setRowCount(0);
        String q = query.trim().toLowerCase();
        for (Object[] row : allRows) {
            // cols: 0=ID,1=Subject,2=Teacher,3=Section,4=Year,5=Semester,6=MaxStudents
            if (q.isEmpty()
                || str(row[1]).toLowerCase().contains(q)
                || str(row[2]).toLowerCase().contains(q)
                || str(row[3]).toLowerCase().contains(q)
                || str(row[4]).toLowerCase().contains(q)
                || str(row[5]).toLowerCase().contains(q)) {
                tableModel.addRow(row);
            }
        }
    }

    private void loadDropdowns() {
        SubjectItem selSub = (SubjectItem) cbSubject.getSelectedItem();
        TeacherItem selTch = (TeacherItem) cbTeacher.getSelectedItem();
        cbSubject.removeAllItems();
        cbTeacher.removeAllItems();
        try {
            for (Subject s : subjectDAO.getAll())
                cbSubject.addItem(new SubjectItem(s.getId(), s.getCode() + " — " + s.getName()));
            for (Teacher t : teacherDAO.getAll())
                cbTeacher.addItem(new TeacherItem(t.getId(), t.getFirstName() + " " + t.getLastName() + " (" + t.getEmployeeNo() + ")"));
            if (selSub != null) for (int i = 0; i < cbSubject.getItemCount(); i++)
                if (cbSubject.getItemAt(i).id == selSub.id) { cbSubject.setSelectedIndex(i); break; }
            if (selTch != null) for (int i = 0; i < cbTeacher.getItemCount(); i++)
                if (cbTeacher.getItemAt(i).id == selTch.id) { cbTeacher.setSelectedIndex(i); break; }
        } catch (SQLException ex) { showError("Failed to load dropdowns: " + ex.getMessage()); }
    }

    private void loadTable() {
        tableModel.setRowCount(0);
        allRows.clear();
        try {
            for (Course c : courseDAO.getAll()) {
                Object[] row = {
                    c.getId(),
                    c.getSubject() != null ? c.getSubject().getCode() + " — " + c.getSubject().getName() : "",
                    c.getTeacher() != null ? c.getTeacher().getFirstName() + " " + c.getTeacher().getLastName() : "",
                    c.getSection(), c.getAcademicYear(), c.getSemester(), c.getMaxStudents()
                };
                allRows.add(row);
                tableModel.addRow(row);
            }
        } catch (SQLException ex) { showError("Failed to load courses: " + ex.getMessage()); }
    }

    private void populateForm() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        selectedId = (int) tableModel.getValueAt(row, 0);
        tfSection.setText(str(tableModel.getValueAt(row, 3)));
        tfYear.setText(str(tableModel.getValueAt(row, 4)));
        cbSemester.setSelectedItem(tableModel.getValueAt(row, 5));
        tfMaxStudents.setText(str(tableModel.getValueAt(row, 6)));

        // Restore subject and teacher dropdowns to match the selected course
        try {
            Course c = courseDAO.getById(selectedId);
            if (c != null) {
                if (c.getSubject() != null) {
                    for (int i = 0; i < cbSubject.getItemCount(); i++) {
                        if (cbSubject.getItemAt(i).id == c.getSubject().getId()) {
                            cbSubject.setSelectedIndex(i); break;
                        }
                    }
                }
                if (c.getTeacher() != null) {
                    for (int i = 0; i < cbTeacher.getItemCount(); i++) {
                        if (cbTeacher.getItemAt(i).id == c.getTeacher().getId()) {
                            cbTeacher.setSelectedIndex(i); break;
                        }
                    }
                }
            }
        } catch (SQLException ex) { System.err.println("Error loading course: " + ex.getMessage()); }
        setStatus("", false);
    }

    private void addCourse() {
        String err = validateForm();
        if (err != null) { setStatus(err, true); return; }
        try {
            SubjectItem si = (SubjectItem) cbSubject.getSelectedItem();
            TeacherItem ti = (TeacherItem) cbTeacher.getSelectedItem();
            if (si == null || ti == null) { setStatus("Select subject and teacher.", true); return; }
            int year = Integer.parseInt(tfYear.getText().trim());
            String sem = (String) cbSemester.getSelectedItem();
            if (courseDAO.existsDuplicate(si.id, ti.id, tfSection.getText().trim(), year, sem, 0)) {
                setStatus("This course already exists (same subject/teacher/section/year/semester).", true); return;
            }
            courseDAO.insert(buildCourse());
            loadTable(); clearForm();
            setStatus("Course added.", false);
        } catch (Exception ex) { showError("Add failed: " + ex.getMessage()); }
    }

    private void updateCourse() {
        if (selectedId < 0) { setStatus("Select a course first.", true); return; }
        String err = validateForm();
        if (err != null) { setStatus(err, true); return; }
        try {
            SubjectItem si = (SubjectItem) cbSubject.getSelectedItem();
            TeacherItem ti = (TeacherItem) cbTeacher.getSelectedItem();
            if (si == null || ti == null) { setStatus("Select subject and teacher.", true); return; }
            int year = Integer.parseInt(tfYear.getText().trim());
            String sem = (String) cbSemester.getSelectedItem();
            if (courseDAO.existsDuplicate(si.id, ti.id, tfSection.getText().trim(), year, sem, selectedId)) {
                setStatus("Another course with the same subject/teacher/section/year/semester already exists.", true); return;
            }
            int newMax = tfMaxStudents.getText().trim().isEmpty() ? 40
                : Integer.parseInt(tfMaxStudents.getText().trim());

            // Check if reducing max_students below current enrollment
            int enrolled = getEnrolledCount(selectedId);
            if (newMax < enrolled) {
                int excess = enrolled - newMax;
                int confirm = JOptionPane.showConfirmDialog(this,
                    "<html><b>Capacity Reduction Warning</b><br><br>" +
                    "Currently enrolled (active): <b>" + enrolled + "</b><br>" +
                    "New maximum: <b>" + newMax + "</b><br>" +
                    "Students to be dropped: <b>" + excess + "</b><br><br>" +
                    "The <b>" + excess + "</b> student(s) with the <b>lowest grades</b> will have<br>" +
                    "their status set to <b>'dropped'</b> (they will NOT be deleted).<br><br>" +
                    "Continue?</html>",
                    "Confirm Capacity Reduction", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) return;
                dropLowestGradeStudents(selectedId, excess);
            }

            Course c = buildCourse();
            c.setId(selectedId);
            courseDAO.update(c);
            loadTable(); clearForm();
            setStatus("Course updated.", false);
        } catch (Exception ex) { showError("Update failed: " + ex.getMessage()); }
    }

    private int getEnrolledCount(int courseId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM enrollments WHERE course_id=? AND status='active'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private void dropLowestGradeStudents(int courseId, int count) throws SQLException {
        // Use a transaction: find lowest-grade enrollments, set status='dropped', rollback on any failure
        String findSql =
            "SELECT e.id, COALESCE(SUM((g.score / cgc.max_score) * cgc.weight), 0) AS total " +
            "FROM enrollments e " +
            "LEFT JOIN grades g ON g.enrollment_id = e.id " +
            "LEFT JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "WHERE e.course_id = ? AND e.status = 'active' " +
            "GROUP BY e.id ORDER BY total ASC LIMIT ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                java.util.List<Integer> toDrop = new java.util.ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(findSql)) {
                    ps.setInt(1, courseId);
                    ps.setInt(2, count);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) toDrop.add(rs.getInt("id"));
                    }
                }
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE enrollments SET status='dropped' WHERE id=?")) {
                    for (int eid : toDrop) {
                        upd.setInt(1, eid);
                        upd.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private void deleteCourse() {
        if (selectedId < 0) { setStatus("Select a course first.", true); return; }
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete this course? All enrollments and grades will also be deleted.",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            courseDAO.delete(selectedId);
            loadTable(); clearForm();
            setStatus("Course deleted.", false);
        } catch (SQLException ex) { showError("Delete failed: " + ex.getMessage()); }
    }

    private Course buildCourse() {
        Course c = new Course();
        SubjectItem si = (SubjectItem) cbSubject.getSelectedItem();
        TeacherItem ti = (TeacherItem) cbTeacher.getSelectedItem();
        Subject sub = new Subject(); sub.setId(si.id); c.setSubject(sub);
        Teacher tch = new Teacher(); tch.setId(ti.id); c.setTeacher(tch);
        c.setSection(tfSection.getText().trim());
        c.setAcademicYear(Integer.parseInt(tfYear.getText().trim()));
        c.setSemester((String) cbSemester.getSelectedItem());
        String max = tfMaxStudents.getText().trim();
        c.setMaxStudents(max.isEmpty() ? 40 : Integer.parseInt(max));
        return c;
    }

    private String validateForm() {
        if (cbSubject.getItemCount() == 0) return "No subjects available. Add subjects first.";
        if (cbTeacher.getItemCount() == 0) return "No teachers available. Add teachers first.";
        if (tfSection.getText().trim().isEmpty()) return "Section is required.";
        String yr = tfYear.getText().trim();
        if (yr.isEmpty()) return "Academic year is required.";
        try {
            int y = Integer.parseInt(yr);
            if (y < 2000 || y > 2100) return "Academic year must be between 2000 and 2100.";
        } catch (NumberFormatException e) { return "Academic year must be a number."; }
        String max = tfMaxStudents.getText().trim();
        if (!max.isEmpty()) {
            try {
                int m = Integer.parseInt(max);
                if (m < 1 || m > 500) return "Max students must be between 1 and 500.";
            } catch (NumberFormatException e) { return "Max students must be a number."; }
        }
        return null;
    }

    private void clearForm() {
        selectedId = -1;
        tfSection.setText(""); tfYear.setText(""); tfMaxStudents.setText("");
        cbSemester.setSelectedIndex(0);
        table.clearSelection();
        setStatus("", false);
    }

    private void setStatus(String msg, boolean error) {
        lblStatus.setText(msg.isEmpty() ? " " : msg);
        lblStatus.setForeground(error ? ThemeManager.danger() : ThemeManager.SUCCESS);
    }

    private JTextField field() {
        JTextField tf = new JTextField();
        tf.setBackground(ThemeManager.elevated());
        tf.setForeground(ThemeManager.text());
        tf.setCaretColor(ThemeManager.text());
        tf.setFont(ThemeManager.fontBody());
        tf.putClientProperty("theme-role", "field");
        return tf;
    }

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.fontBold());
        l.setForeground(ThemeManager.text());
        return l;
    }

    private String str(Object o) { return o != null ? o.toString() : ""; }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private JTextField searchField(String placeholder) {
        JTextField tf = new JTextField();
        tf.setBackground(ThemeManager.elevated());
        tf.setForeground(ThemeManager.text());
        tf.setCaretColor(ThemeManager.text());
        tf.setFont(ThemeManager.fontBody());
        tf.putClientProperty("JTextField.placeholderText", placeholder);
        return tf;
    }

    private JPanel buildSearchBar(JTextField tf) {
        JPanel bar = new JPanel(new BorderLayout(4, 0));
        bar.setOpaque(false);
        bar.setBorder(new javax.swing.border.EmptyBorder(4, 4, 4, 4));
        JLabel icon = new JLabel("\uD83D\uDD0D");
        icon.setForeground(ThemeManager.muted());
        bar.add(icon, BorderLayout.WEST);
        bar.add(tf,   BorderLayout.CENTER);
        return bar;
    }

    private static class SubjectItem {
        int id; String label;
        SubjectItem(int id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }

    private static class TeacherItem {
        int id; String label;
        TeacherItem(int id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }
}
