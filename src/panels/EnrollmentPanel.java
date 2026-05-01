package panels;

import dao.EnrollmentDAO;
import models.Enrollment;
import models.Course;
import models.Student;
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

public class EnrollmentPanel extends JPanel {

    private final EnrollmentDAO enrollmentDAO = new EnrollmentDAO();
    private final User scopedUser;   // null = admin (full access); non-null = teacher (own courses only)
    private final boolean isTeacher;

    private JTable table;
    private DefaultTableModel tableModel;
    private final java.util.List<Object[]> allRows = new java.util.ArrayList<>();

    private JComboBox<StudentItem> cbStudent;
    private JComboBox<CourseItem>  cbCourse;
    private JComboBox<CourseItem>  cbCourseFilter;  // teacher-only table filter
    private JComboBox<String>      cbStatus;
    private JButton btnEnroll, btnUpdate, btnDrop, btnClear, btnRefresh;
    private JLabel lblStatus;

    private int selectedEnrollmentId = -1;

    public EnrollmentPanel() { this(null); }

    public EnrollmentPanel(User scopedUser) {
        this.scopedUser = scopedUser;
        this.isTeacher  = scopedUser != null && "teacher".equals(scopedUser.getRole());
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());
        add(buildFormPanel(),  BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        loadDropdowns();
        if (isTeacher) loadCourseFilter();
        loadTable();
    }

    private JPanel buildFormPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 6));
        outer.setBackground(ThemeManager.surface());
        outer.setBorder(ThemeManager.cardBorder());

        JLabel title = new JLabel(isTeacher ? "Enrollment Management (My Courses)" : "Enrollment Management");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());
        outer.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 6, 5, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        cbStudent = new JComboBox<>();
        cbCourse  = new JComboBox<>();
        cbStatus  = new JComboBox<>(new String[]{"active", "dropped", "completed"});

        cbStudent.setPreferredSize(new Dimension(280, 28));
        cbCourse.setPreferredSize(new Dimension(320, 28));

        // Teachers can only update status — student/course selection only needed for admin enroll
        if (!isTeacher) {
            g.gridy = 0; g.gridwidth = 1; g.weightx = 0;
            g.gridx = 0; form.add(lbl("Student *"), g);
            g.gridx = 1; g.weightx = 0.45; form.add(cbStudent, g);
            g.gridx = 2; g.weightx = 0; form.add(lbl("Status"), g);
            g.gridx = 3; g.weightx = 0.15; form.add(cbStatus, g);

            g.gridy = 1; g.weightx = 0;
            g.gridx = 0; form.add(lbl("Course *"), g);
            g.gridx = 1; g.weightx = 0.6; g.gridwidth = 3; form.add(cbCourse, g);
        } else {
            // Teacher: only show status selector (for updating selected enrollment)
            g.gridy = 0; g.gridwidth = 1; g.weightx = 0;
            g.gridx = 0; form.add(lbl("Status"), g);
            g.gridx = 1; g.weightx = 0.3; form.add(cbStatus, g);

            JLabel hint = new JLabel("Select an enrollment from the table below to update its status.");
            hint.setFont(ThemeManager.fontSmall());
            hint.setForeground(ThemeManager.muted());
            g.gridx = 2; g.weightx = 0.7; g.gridwidth = 2; form.add(hint, g);
        }

        lblStatus = new JLabel(" ");
        lblStatus.setFont(ThemeManager.fontSmall());
        g.gridx = 0; g.gridy = isTeacher ? 1 : 2; g.gridwidth = 4; g.weightx = 1;
        form.add(lblStatus, g);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false);
        btnUpdate  = ThemeManager.primaryButton("Update Status");
        btnDrop    = ThemeManager.dangerButton("Drop");
        btnClear   = ThemeManager.secondaryButton("Clear");
        btnRefresh = ThemeManager.secondaryButton("Refresh");
        btnRow.add(btnUpdate); btnRow.add(btnDrop); btnRow.add(btnClear); btnRow.add(btnRefresh);

        if (!isTeacher) {
            btnEnroll = ThemeManager.primaryButton("Enroll");
            btnRow.add(btnEnroll, 0);   // add at front
            btnEnroll.addActionListener(e -> enroll());
        }

        g.gridy = isTeacher ? 2 : 3; g.gridwidth = 4;
        form.add(btnRow, g);

        btnUpdate.addActionListener(e  -> updateStatus());
        btnDrop.addActionListener(e    -> dropEnrollment());
        btnClear.addActionListener(e   -> clearForm());
        btnRefresh.addActionListener(e -> { loadDropdowns(); if (isTeacher) loadCourseFilter(); loadTable(); });

        outer.add(form, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildTablePanel() {
        String[] cols = {"ID", "Student No", "Student Name", "Subject", "Section", "Year", "Semester", "Status", "Enrolled At", "_studentId", "_courseId"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        // hide the two internal ID columns
        table.getColumnModel().getColumn(9).setMinWidth(0);  table.getColumnModel().getColumn(9).setMaxWidth(0);
        table.getColumnModel().getColumn(10).setMinWidth(0); table.getColumnModel().getColumn(10).setMaxWidth(0);
        ThemeManager.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) populateForm();
        });

        JTextField tfSearch = searchField("Search by student name, ID, subject or status...");
        tfSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filterTable(tfSearch.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filterTable(tfSearch.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(tfSearch.getText()); }
        });

        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setBackground(ThemeManager.surface());
        wrapper.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.border()),
            isTeacher ? "My Course Enrollments" : "All Enrollments"));

        // For teachers: add a course filter bar above the search bar
        if (isTeacher) {
            JPanel courseBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            courseBar.setOpaque(false);
            JLabel courseLbl = new JLabel("Filter by Course:");
            courseLbl.setFont(ThemeManager.fontBold());
            courseLbl.setForeground(ThemeManager.muted());
            cbCourseFilter = new JComboBox<>();
            cbCourseFilter.setPreferredSize(new Dimension(360, 28));
            cbCourseFilter.addActionListener(e -> loadTable());
            courseBar.add(courseLbl);
            courseBar.add(cbCourseFilter);
            wrapper.add(courseBar, BorderLayout.NORTH);
            wrapper.add(buildSearchBar(tfSearch), BorderLayout.CENTER);
            wrapper.add(new JScrollPane(table),   BorderLayout.SOUTH);
            // Give the table most of the space
            wrapper.setLayout(new BorderLayout(0, 4));
            JPanel topBar = new JPanel(new BorderLayout(0, 2));
            topBar.setOpaque(false);
            topBar.add(courseBar,             BorderLayout.NORTH);
            topBar.add(buildSearchBar(tfSearch), BorderLayout.SOUTH);
            wrapper.add(topBar,               BorderLayout.NORTH);
            wrapper.add(new JScrollPane(table), BorderLayout.CENTER);
        } else {
            wrapper.add(buildSearchBar(tfSearch), BorderLayout.NORTH);
            wrapper.add(new JScrollPane(table),   BorderLayout.CENTER);
        }

        return wrapper;
    }

    /** Loads teacher's courses into cbCourseFilter. Called after constructor. */
    private void loadCourseFilter() {
        if (cbCourseFilter == null) return;
        cbCourseFilter.removeAllItems();
        cbCourseFilter.addItem(new CourseItem(-1, "All My Courses"));
        String sql =
            "SELECT c.id, s.code, s.name, c.section, c.academic_year, c.semester " +
            "FROM courses c " +
            "JOIN subjects s ON s.id = c.subject_id " +
            "JOIN teachers t ON t.id = c.teacher_id " +
            "JOIN users u    ON u.id = t.user_id " +
            "WHERE u.id = ? " +
            "ORDER BY c.academic_year DESC, c.semester, s.code";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, scopedUser.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String label = rs.getString("code") + " \u2014 " + rs.getString("name")
                        + "  [" + rs.getString("section") + "]  "
                        + rs.getInt("academic_year") + " " + rs.getString("semester");
                    cbCourseFilter.addItem(new CourseItem(rs.getInt("id"), label));
                }
            }
        } catch (SQLException ex) { showError("Failed to load courses: " + ex.getMessage()); }
    }

    private void filterTable(String query) {
        tableModel.setRowCount(0);
        String q = query.trim().toLowerCase();
        for (Object[] row : allRows) {
            // cols: 0=ID,1=StudentNo,2=StudentName,3=Subject,4=Section,5=Year,6=Semester,7=Status,8=EnrolledAt
            if (q.isEmpty()
                || str(row[1]).toLowerCase().contains(q)
                || str(row[2]).toLowerCase().contains(q)
                || str(row[3]).toLowerCase().contains(q)
                || str(row[4]).toLowerCase().contains(q)
                || str(row[7]).toLowerCase().contains(q)) {
                tableModel.addRow(row);
            }
        }
    }

    private void loadDropdowns() {
        // Admin only — teachers don't enroll students
        if (isTeacher) return;

        StudentItem selS = (StudentItem) cbStudent.getSelectedItem();
        CourseItem  selC = (CourseItem)  cbCourse.getSelectedItem();
        cbStudent.removeAllItems();
        cbCourse.removeAllItems();

        String sqlS = "SELECT id, student_no, first_name, last_name FROM students ORDER BY first_name, last_name";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                StudentItem item = new StudentItem(rs.getInt("id"),
                    rs.getString("student_no") + " — " +
                    rs.getString("first_name") + " " + rs.getString("last_name"));
                cbStudent.addItem(item);
                if (selS != null && item.id == selS.id) cbStudent.setSelectedItem(item);
            }
        } catch (SQLException ex) { showError("Failed to load students: " + ex.getMessage()); }

        String sqlC =
            "SELECT c.id, s.code, s.name, c.section, c.academic_year, c.semester " +
            "FROM courses c JOIN subjects s ON s.id = c.subject_id " +
            "ORDER BY c.academic_year DESC, c.semester, s.code";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlC);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CourseItem item = new CourseItem(rs.getInt("id"),
                    rs.getString("code") + " — " + rs.getString("name") +
                    "  [" + rs.getString("section") + "]  " +
                    rs.getInt("academic_year") + " " + rs.getString("semester"));
                cbCourse.addItem(item);
                if (selC != null && item.id == selC.id) cbCourse.setSelectedItem(item);
            }
        } catch (SQLException ex) { showError("Failed to load courses: " + ex.getMessage()); }
    }

    private void loadTable() {
        tableModel.setRowCount(0);
        allRows.clear();

        // Determine course filter for teacher
        int filterCourseId = -1;
        if (isTeacher && cbCourseFilter != null) {
            CourseItem ci = (CourseItem) cbCourseFilter.getSelectedItem();
            filterCourseId = ci != null ? ci.id : -1;
        }

        String sql;
        if (isTeacher && filterCourseId > 0) {
            // Specific course selected
            sql = "SELECT e.id, e.student_id, e.course_id, s.student_no, s.first_name, s.last_name, " +
                  "       sub.code, sub.name, c.section, c.academic_year, c.semester, " +
                  "       e.status, e.enrolled_at " +
                  "FROM enrollments e " +
                  "JOIN students s   ON s.id   = e.student_id " +
                  "JOIN courses c    ON c.id   = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "JOIN teachers t   ON t.id   = c.teacher_id " +
                  "JOIN users u      ON u.id   = t.user_id " +
                  "WHERE u.id = ? AND c.id = ? " +
                  "ORDER BY s.first_name, s.last_name";
        } else if (isTeacher) {
            // All teacher's courses
            sql = "SELECT e.id, e.student_id, e.course_id, s.student_no, s.first_name, s.last_name, " +
                  "       sub.code, sub.name, c.section, c.academic_year, c.semester, " +
                  "       e.status, e.enrolled_at " +
                  "FROM enrollments e " +
                  "JOIN students s   ON s.id   = e.student_id " +
                  "JOIN courses c    ON c.id   = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "JOIN teachers t   ON t.id   = c.teacher_id " +
                  "JOIN users u      ON u.id   = t.user_id " +
                  "WHERE u.id = ? " +
                  "ORDER BY c.academic_year DESC, c.semester, s.first_name, s.last_name";
        } else {
            sql = "SELECT e.id, e.student_id, e.course_id, s.student_no, s.first_name, s.last_name, " +
                  "       sub.code, sub.name, c.section, c.academic_year, c.semester, " +
                  "       e.status, e.enrolled_at " +
                  "FROM enrollments e " +
                  "JOIN students s   ON s.id   = e.student_id " +
                  "JOIN courses c    ON c.id   = e.course_id " +
                  "JOIN subjects sub ON sub.id = c.subject_id " +
                  "ORDER BY e.enrolled_at DESC";
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (isTeacher) {
                ps.setInt(1, scopedUser.getId());
                if (filterCourseId > 0) ps.setInt(2, filterCourseId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object[] row = {
                        rs.getInt("id"),
                        rs.getString("student_no"),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        rs.getString("code") + " — " + rs.getString("name"),
                        rs.getString("section"),
                        rs.getInt("academic_year"),
                        rs.getString("semester"),
                        rs.getString("status"),
                        rs.getTimestamp("enrolled_at") != null
                            ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(rs.getTimestamp("enrolled_at")) : "",
                        rs.getInt("student_id"),
                        rs.getInt("course_id")
                    };
                    allRows.add(row);
                    tableModel.addRow(row);
                }
            }
        } catch (SQLException ex) { showError("Failed to load enrollments: " + ex.getMessage()); }
    }

    private void populateForm() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        selectedEnrollmentId = (int) tableModel.getValueAt(row, 0);
        cbStatus.setSelectedItem(tableModel.getValueAt(row, 7));

        if (!isTeacher) {
            int studentId = (int) tableModel.getValueAt(row, 9);
            int courseId  = (int) tableModel.getValueAt(row, 10);

            // select matching student
            for (int i = 0; i < cbStudent.getItemCount(); i++) {
                if (cbStudent.getItemAt(i).id == studentId) {
                    cbStudent.setSelectedIndex(i);
                    break;
                }
            }
            // select matching course
            for (int i = 0; i < cbCourse.getItemCount(); i++) {
                if (cbCourse.getItemAt(i).id == courseId) {
                    cbCourse.setSelectedIndex(i);
                    break;
                }
            }
        }
        setStatus("", false);
    }

    private void enroll() {
        StudentItem si = (StudentItem) cbStudent.getSelectedItem();
        CourseItem  ci = (CourseItem)  cbCourse.getSelectedItem();
        if (si == null) { setStatus("Select a student.", true); return; }
        if (ci == null) { setStatus("Select a course.", true); return; }
        try {
            if (enrollmentDAO.existsDuplicate(si.id, ci.id, 0)) {
                setStatus("Student is already enrolled in this course.", true); return;
            }
            Enrollment e = new Enrollment();
            Student s = new Student(); s.setId(si.id); e.setStudent(s);
            Course  c = new Course();  c.setId(ci.id); e.setCourse(c);
            e.setStatus("active");
            enrollmentDAO.insert(e);
            loadTable(); clearForm();
            setStatus("Student enrolled successfully.", false);
        } catch (SQLException ex) { showError("Enroll failed: " + ex.getMessage()); }
    }

    private void updateStatus() {
        if (selectedEnrollmentId < 0) { setStatus("Select an enrollment first.", true); return; }
        String newStatus = (String) cbStatus.getSelectedItem();
        String sql = "UPDATE enrollments SET status=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, selectedEnrollmentId);
            ps.executeUpdate();
            loadTable(); clearForm();
            setStatus("Status updated to \"" + newStatus + "\".", false);
        } catch (SQLException ex) { showError("Update failed: " + ex.getMessage()); }
    }

    private void dropEnrollment() {
        if (selectedEnrollmentId < 0) { setStatus("Select an enrollment first.", true); return; }

        // Teacher: verify the enrollment belongs to one of their courses
        if (isTeacher && !enrollmentBelongsToTeacher(selectedEnrollmentId)) {
            setStatus("You can only drop enrollments in your own courses.", true); return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Drop this enrollment? All grades for this enrollment will also be deleted.",
            "Confirm Drop", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM grades WHERE enrollment_id=?")) {
                    ps.setInt(1, selectedEnrollmentId); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM enrollments WHERE id=?")) {
                    ps.setInt(1, selectedEnrollmentId); ps.executeUpdate();
                }
                conn.commit();
                loadTable(); clearForm();
                setStatus("Enrollment dropped.", false);
            } catch (SQLException ex) {
                conn.rollback();
                showError("Drop failed: " + ex.getMessage());
            }
        } catch (SQLException ex) { showError("Drop failed: " + ex.getMessage()); }
    }

    /** Verifies an enrollment belongs to one of the teacher's courses (DB-level check). */
    private boolean enrollmentBelongsToTeacher(int enrollmentId) {
        String sql =
            "SELECT COUNT(*) FROM enrollments e " +
            "JOIN courses c ON c.id = e.course_id " +
            "JOIN teachers t ON t.id = c.teacher_id " +
            "JOIN users u ON u.id = t.user_id " +
            "WHERE e.id = ? AND u.id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId);
            ps.setInt(2, scopedUser.getId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException ex) { return false; }
    }

    private void clearForm() {
        selectedEnrollmentId = -1;
        cbStatus.setSelectedIndex(0);
        table.clearSelection();
        setStatus("", false);
    }

    private void setStatus(String msg, boolean error) {
        lblStatus.setText(msg.isEmpty() ? " " : msg);
        lblStatus.setForeground(error ? ThemeManager.danger() : ThemeManager.SUCCESS);
    }

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.fontBold());
        l.setForeground(ThemeManager.text());
        return l;
    }

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

    private String str(Object o) { return o != null ? o.toString() : ""; }

    private static class StudentItem {
        int id; String label;
        StudentItem(int id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }

    private static class CourseItem {
        int id; String label;
        CourseItem(int id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }
}
