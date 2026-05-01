package panels;

import dao.TeacherDAO;
import dao.UserDAO;
import models.Teacher;
import models.User;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;

public class TeacherPanel extends JPanel {

    private final TeacherDAO teacherDAO = new TeacherDAO();
    private final UserDAO    userDAO    = new UserDAO();
    private final SimpleDateFormat sdf  = new SimpleDateFormat("yyyy-MM-dd");

    private JTable table;
    private DefaultTableModel tableModel;
    private final java.util.List<Object[]> allRows = new java.util.ArrayList<>();

    private JTextField tfFirstName, tfLastName, tfEmployeeNo, tfDepartment, tfPhone, tfHiredAt;
    private JTextField tfUsername, tfEmail, tfPassword;
    private JButton btnAdd, btnUpdate, btnDelete, btnClear;
    private JLabel lblStatus;

    private int selectedTeacherId = -1;
    private int selectedUserId    = -1;

    public TeacherPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());
        add(buildFormPanel(),  BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        loadTable();
    }

    private JPanel buildFormPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 6));
        outer.setBackground(ThemeManager.surface());
        outer.setBorder(ThemeManager.cardBorder());

        JLabel title = new JLabel("Teacher Management");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());
        outer.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        tfFirstName  = field(); tfLastName   = field(); tfEmployeeNo = field();
        tfDepartment = field(); tfPhone      = field(); tfHiredAt    = field();
        tfUsername   = field(); tfEmail      = field(); tfPassword   = field();

        row(form, g, 0, "First Name *",        tfFirstName,  "Last Name *",             tfLastName);
        row(form, g, 1, "Employee No *",        tfEmployeeNo, "Department",              tfDepartment);
        row(form, g, 2, "Phone",                tfPhone,      "Hired At (yyyy-MM-dd)",   tfHiredAt);
        row(form, g, 3, "Username *",           tfUsername,   "Email *",                 tfEmail);
        row(form, g, 4, "Password (new only)",  tfPassword,   "",                        new JLabel());

        lblStatus = new JLabel(" ");
        lblStatus.setFont(ThemeManager.fontSmall());
        g.gridx = 0; g.gridy = 5; g.gridwidth = 4; g.weightx = 1;
        form.add(lblStatus, g);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false);
        btnAdd    = ThemeManager.primaryButton("Add Teacher");
        btnUpdate = ThemeManager.primaryButton("Update");
        btnDelete = ThemeManager.dangerButton("Delete");
        btnClear  = ThemeManager.secondaryButton("Clear");
        btnRow.add(btnAdd); btnRow.add(btnUpdate); btnRow.add(btnDelete); btnRow.add(btnClear);

        g.gridy = 6; g.gridwidth = 4;
        form.add(btnRow, g);

        btnAdd.addActionListener(e    -> addTeacher());
        btnUpdate.addActionListener(e -> updateTeacher());
        btnDelete.addActionListener(e -> deleteTeacher());
        btnClear.addActionListener(e  -> clearForm());

        outer.add(form, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildTablePanel() {
        String[] cols = {"ID", "Employee No", "First Name", "Last Name", "Department", "Phone", "Hired At", "Username", "Email"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        ThemeManager.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) populateForm();
        });

        JTextField tfSearch = searchField("Search by name, employee no, department or email...");
        tfSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filterTable(tfSearch.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filterTable(tfSearch.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(tfSearch.getText()); }
        });

        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setBackground(ThemeManager.surface());
        wrapper.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.border()), "All Teachers"));
        wrapper.add(buildSearchBar(tfSearch), BorderLayout.NORTH);
        wrapper.add(new JScrollPane(table),   BorderLayout.CENTER);
        return wrapper;
    }

    private void filterTable(String query) {
        tableModel.setRowCount(0);
        String q = query.trim().toLowerCase();
        for (Object[] row : allRows) {
            // cols: 0=ID,1=EmpNo,2=FirstName,3=LastName,4=Dept,5=Phone,6=HiredAt,7=Username,8=Email
            if (q.isEmpty()
                || str(row[1]).toLowerCase().contains(q)
                || str(row[2]).toLowerCase().contains(q)
                || str(row[3]).toLowerCase().contains(q)
                || (str(row[2]) + " " + str(row[3])).toLowerCase().contains(q)
                || str(row[4]).toLowerCase().contains(q)
                || str(row[8]).toLowerCase().contains(q)) {
                tableModel.addRow(row);
            }
        }
    }

    private void loadTable() {
        tableModel.setRowCount(0);
        allRows.clear();
        try {
            for (Teacher t : teacherDAO.getAll()) {
                Object[] row = {
                    t.getId(), t.getEmployeeNo(), t.getFirstName(), t.getLastName(),
                    t.getDepartment(), t.getPhone(),
                    t.getHiredAt() != null ? sdf.format(t.getHiredAt()) : "",
                    t.getUser() != null ? t.getUser().getUsername() : "",
                    t.getUser() != null ? t.getUser().getEmail() : ""
                };
                allRows.add(row);
                tableModel.addRow(row);
            }
        } catch (SQLException ex) { showError("Failed to load teachers: " + ex.getMessage()); }
    }

    private void populateForm() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        selectedTeacherId = (int) tableModel.getValueAt(row, 0);
        tfEmployeeNo.setText(str(tableModel.getValueAt(row, 1)));
        tfFirstName.setText(str(tableModel.getValueAt(row, 2)));
        tfLastName.setText(str(tableModel.getValueAt(row, 3)));
        tfDepartment.setText(str(tableModel.getValueAt(row, 4)));
        tfPhone.setText(str(tableModel.getValueAt(row, 5)));
        tfHiredAt.setText(str(tableModel.getValueAt(row, 6)));
        tfUsername.setText(str(tableModel.getValueAt(row, 7)));
        tfEmail.setText(str(tableModel.getValueAt(row, 8)));
        tfPassword.setText("");

        try {
            Teacher t = teacherDAO.getById(selectedTeacherId);
            if (t != null) selectedUserId = t.getUser() != null ? t.getUser().getId() : -1;
        } catch (SQLException ex) { showError(ex.getMessage()); }
        setStatus("", false);
    }

    private void addTeacher() {
        String err = validateForm(true);
        if (err != null) { setStatus(err, true); return; }
        try {
            if (userDAO.existsByUsername(tfUsername.getText().trim(), 0)) {
                setStatus("Username already exists.", true); return;
            }
            if (userDAO.existsByEmail(tfEmail.getText().trim(), 0)) {
                setStatus("Email already exists.", true); return;
            }
            if (teacherDAO.existsByEmployeeNo(tfEmployeeNo.getText().trim(), 0)) {
                setStatus("Employee number already exists.", true); return;
            }

            User u = new User();
            u.setUsername(tfUsername.getText().trim());
            u.setEmail(tfEmail.getText().trim());
            u.setPassword(tfPassword.getText().trim());
            u.setRole("teacher");
            u.setActive(true);
            userDAO.insert(u);

            Teacher t = buildTeacher();
            t.setUser(u);
            teacherDAO.insert(t);

            loadTable(); clearForm();
            setStatus("Teacher added successfully.", false);
        } catch (Exception ex) { showError("Add failed: " + ex.getMessage()); }
    }

    private void updateTeacher() {
        if (selectedTeacherId < 0) { setStatus("Select a teacher first.", true); return; }
        String err = validateForm(false);
        if (err != null) { setStatus(err, true); return; }
        try {
            if (userDAO.existsByUsername(tfUsername.getText().trim(), selectedUserId)) {
                setStatus("Username already taken.", true); return;
            }
            if (userDAO.existsByEmail(tfEmail.getText().trim(), selectedUserId)) {
                setStatus("Email already taken.", true); return;
            }
            if (teacherDAO.existsByEmployeeNo(tfEmployeeNo.getText().trim(), selectedTeacherId)) {
                setStatus("Employee number already taken.", true); return;
            }

            User u = userDAO.getById(selectedUserId);
            if (u == null) { setStatus("Associated user not found.", true); return; }
            u.setUsername(tfUsername.getText().trim());
            u.setEmail(tfEmail.getText().trim());
            if (!tfPassword.getText().trim().isEmpty())
                u.setPassword(tfPassword.getText().trim());
            userDAO.update(u);

            Teacher t = buildTeacher();
            t.setId(selectedTeacherId);
            t.setUser(u);
            teacherDAO.update(t);

            loadTable(); clearForm();
            setStatus("Teacher updated successfully.", false);
        } catch (Exception ex) { showError("Update failed: " + ex.getMessage()); }
    }

    private void deleteTeacher() {
        if (selectedTeacherId < 0) { setStatus("Select a teacher first.", true); return; }
        String name = str(tableModel.getValueAt(table.getSelectedRow(), 2)) + " " +
                      str(tableModel.getValueAt(table.getSelectedRow(), 3));
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete teacher \"" + name + "\" and ALL their courses, enrollments and grades?\nThis cannot be undone.",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try (Connection conn = util.DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // grades → enrollments → courses → teacher → user
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "DELETE g FROM grades g JOIN enrollments e ON e.id = g.enrollment_id " +
                        "JOIN courses c ON c.id = e.course_id WHERE c.teacher_id = ?")) {
                    ps.setInt(1, selectedTeacherId); ps.executeUpdate();
                }
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "DELETE e FROM enrollments e JOIN courses c ON c.id = e.course_id WHERE c.teacher_id = ?")) {
                    ps.setInt(1, selectedTeacherId); ps.executeUpdate();
                }
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM courses WHERE teacher_id = ?")) {
                    ps.setInt(1, selectedTeacherId); ps.executeUpdate();
                }
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM teachers WHERE id = ?")) {
                    ps.setInt(1, selectedTeacherId); ps.executeUpdate();
                }
                if (selectedUserId > 0) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM users WHERE id = ?")) {
                        ps.setInt(1, selectedUserId); ps.executeUpdate();
                    }
                }
                conn.commit();
                loadTable(); clearForm();
                setStatus("Teacher deleted.", false);
            } catch (SQLException ex) {
                conn.rollback();
                showError("Delete failed: " + ex.getMessage());
            }
        } catch (SQLException ex) { showError("Delete failed: " + ex.getMessage()); }
    }

    private Teacher buildTeacher() throws ParseException {
        Teacher t = new Teacher();
        t.setFirstName(tfFirstName.getText().trim());
        t.setLastName(tfLastName.getText().trim());
        t.setEmployeeNo(tfEmployeeNo.getText().trim());
        t.setDepartment(tfDepartment.getText().trim());
        t.setPhone(tfPhone.getText().trim());
        String hired = tfHiredAt.getText().trim();
        t.setHiredAt(hired.isEmpty() ? null : sdf.parse(hired));
        return t;
    }

    private String validateForm(boolean requirePassword) {
        if (tfFirstName.getText().trim().isEmpty())  return "First name is required.";
        if (tfLastName.getText().trim().isEmpty())   return "Last name is required.";
        if (tfEmployeeNo.getText().trim().isEmpty()) return "Employee number is required.";
        if (tfUsername.getText().trim().isEmpty())   return "Username is required.";
        if (tfEmail.getText().trim().isEmpty())      return "Email is required.";
        if (!tfEmail.getText().trim().matches("^[\\w.+\\-]+@[\\w\\-]+\\.[a-zA-Z]{2,}$"))
            return "Invalid email format.";
        if (requirePassword && tfPassword.getText().trim().isEmpty())
            return "Password is required for new teachers.";
        String hired = tfHiredAt.getText().trim();
        if (!hired.isEmpty()) {
            try { sdf.parse(hired); } catch (ParseException e) { return "Hired date must be yyyy-MM-dd."; }
        }
        return null;
    }

    private void clearForm() {
        selectedTeacherId = -1; selectedUserId = -1;
        tfFirstName.setText(""); tfLastName.setText(""); tfEmployeeNo.setText("");
        tfDepartment.setText(""); tfPhone.setText(""); tfHiredAt.setText("");
        tfUsername.setText(""); tfEmail.setText(""); tfPassword.setText("");
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

    private void row(JPanel p, GridBagConstraints g, int row,
                     String l1, JComponent c1, String l2, JComponent c2) {
        g.gridy = row; g.gridwidth = 1; g.weightx = 0;
        g.gridx = 0; p.add(lbl(l1), g);
        g.gridx = 1; g.weightx = 0.4; p.add(c1, g);
        g.gridx = 2; g.weightx = 0; p.add(lbl(l2), g);
        g.gridx = 3; g.weightx = 0.4; p.add(c2, g);
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
}
