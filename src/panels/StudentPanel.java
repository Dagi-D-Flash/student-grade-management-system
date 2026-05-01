package panels;

import dao.StudentDAO;
import dao.UserDAO;
import models.Student;
import models.User;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

public class StudentPanel extends JPanel {

    private final StudentDAO studentDAO = new StudentDAO();
    private final UserDAO    userDAO    = new UserDAO();
    private final SimpleDateFormat sdf  = new SimpleDateFormat("yyyy-MM-dd");

    private JTable table;
    private DefaultTableModel tableModel;
    private final java.util.List<Object[]> allRows = new java.util.ArrayList<>();

    // Detail panel shown when a student is selected
    private JPanel detailPanel;
    private JLabel detailAvatar, detailName, detailId, detailGender, detailDob;
    private JLabel detailEmail, detailPhone, detailAddress, detailEnrolled;

    private JTextField tfFirstName, tfLastName, tfStudentNo, tfDob, tfPhone, tfAddress;
    private JTextField tfUsername, tfEmail, tfPassword;
    private JComboBox<String> cbGender;
    private JButton btnAdd, btnUpdate, btnDelete, btnClear;
    private JLabel lblStatus;

    private int selectedStudentId = -1;
    private int selectedUserId    = -1;

    public StudentPanel() {
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

        JLabel title = new JLabel("Student Management");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());
        outer.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        tfFirstName = field(); tfLastName  = field(); tfStudentNo = field();
        tfDob       = field(); tfPhone     = field(); tfAddress   = field();
        tfUsername  = field(); tfEmail     = field(); tfPassword  = field();
        cbGender    = new JComboBox<>(new String[]{"male", "female", "other"});

        row(form, g, 0, "First Name *",       tfFirstName,  "Last Name *",          tfLastName);
        row(form, g, 1, "Student No *",        tfStudentNo,  "Date of Birth (yyyy-MM-dd)", tfDob);
        row(form, g, 2, "Gender",              cbGender,     "Phone",                tfPhone);
        row(form, g, 3, "Address",             tfAddress,    "",                     new JLabel());
        row(form, g, 4, "Username *",          tfUsername,   "Email *",              tfEmail);
        row(form, g, 5, "Password (new only)", tfPassword,   "",                     new JLabel());

        lblStatus = new JLabel(" ");
        lblStatus.setFont(ThemeManager.fontSmall());
        g.gridx = 0; g.gridy = 6; g.gridwidth = 4; g.weightx = 1;
        form.add(lblStatus, g);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false);
        btnAdd    = ThemeManager.primaryButton("Add Student");
        btnUpdate = ThemeManager.primaryButton("Update");
        btnDelete = ThemeManager.dangerButton("Delete");
        btnClear  = ThemeManager.secondaryButton("Clear");
        btnRow.add(btnAdd); btnRow.add(btnUpdate); btnRow.add(btnDelete); btnRow.add(btnClear);

        g.gridy = 7; g.gridwidth = 4;
        form.add(btnRow, g);

        btnAdd.addActionListener(e    -> addStudent());
        btnUpdate.addActionListener(e -> updateStudent());
        btnDelete.addActionListener(e -> deleteStudent());
        btnClear.addActionListener(e  -> clearForm());

        outer.add(form, BorderLayout.CENTER);
        return outer;
    }

    private JSplitPane buildTablePanel() {
        String[] cols = {"ID", "Student No", "First Name", "Last Name", "Gender", "Phone", "DOB", "Username", "Email"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        ThemeManager.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { populateForm(); refreshDetailPanel(); }
        });

        JTextField tfSearch = searchField("Search by name, student no, username or email...");
        tfSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filterTable(tfSearch.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filterTable(tfSearch.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(tfSearch.getText()); }
        });

        JPanel tableWrapper = new JPanel(new BorderLayout(0, 4));
        tableWrapper.setBackground(ThemeManager.surface());
        tableWrapper.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.border()), "All Students"));
        tableWrapper.add(buildSearchBar(tfSearch), BorderLayout.NORTH);
        tableWrapper.add(new JScrollPane(table),   BorderLayout.CENTER);

        // Detail panel (right side)
        detailPanel = buildDetailPanel();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableWrapper, detailPanel);
        split.setDividerLocation(680);
        split.setResizeWeight(0.7);
        split.setBorder(null);
        return split;
    }

    private JPanel buildDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(ThemeManager.surface());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.border()),
            new javax.swing.border.EmptyBorder(16, 16, 16, 16)));

        // Placeholder
        JLabel placeholder = new JLabel("Select a student to view details", SwingConstants.CENTER);
        placeholder.setFont(ThemeManager.fontBody());
        placeholder.setForeground(ThemeManager.muted());
        panel.add(placeholder, BorderLayout.CENTER);
        return panel;
    }

    private void refreshDetailPanel() {
        int row = table.getSelectedRow();
        detailPanel.removeAll();
        detailPanel.setLayout(new BorderLayout());

        if (row < 0) {
            JLabel ph = new JLabel("Select a student to view details", SwingConstants.CENTER);
            ph.setFont(ThemeManager.fontBody());
            ph.setForeground(ThemeManager.muted());
            detailPanel.add(ph, BorderLayout.CENTER);
            detailPanel.revalidate(); detailPanel.repaint();
            return;
        }

        int sid = (int) tableModel.getValueAt(row, 0);
        Student s;
        try { s = studentDAO.getById(sid); }
        catch (Exception ex) { return; }
        if (s == null) return;

        // ── Load image ────────────────────────────────────────────────────────
        final int AV = 110;
        final java.awt.image.BufferedImage[] rawImg = {null};
        String photo = s.getProfilePhoto();
        if (photo != null && new java.io.File(photo).exists()) {
            try { rawImg[0] = javax.imageio.ImageIO.read(new java.io.File(photo)); }
            catch (Exception ignored) {}
        }
        final String firstName = s.getFirstName(), lastName = s.getLastName();

        // ── Avatar — direct-paint, no ImageIcon ───────────────────────────────
        JPanel avatarPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,   RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,       RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,  RenderingHints.VALUE_STROKE_PURE);
                int sz = Math.min(getWidth(), getHeight());
                int ox = (getWidth() - sz) / 2, oy = (getHeight() - sz) / 2;
                if (rawImg[0] != null) {
                    g2.drawImage(panels.StudentProfilePanel.makeCircle(rawImg[0], sz), ox, oy, null);
                } else {
                    g2.setColor(new Color(0x10a37f));
                    g2.fillOval(ox, oy, sz, sz);
                    g2.setColor(new Color(255, 255, 255, 60));
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawOval(ox + 2, oy + 2, sz - 4, sz - 4);
                    g2.setColor(Color.WHITE);
                    String ini = "";
                    if (firstName != null && !firstName.isEmpty()) ini += firstName.charAt(0);
                    if (lastName  != null && !lastName.isEmpty())  ini += lastName.charAt(0);
                    ini = ini.isEmpty() ? "?" : ini.toUpperCase();
                    g2.setFont(new Font("SansSerif", Font.BOLD, sz / 3));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(ini, ox + (sz - fm.stringWidth(ini)) / 2,
                                  oy + (sz - fm.getHeight()) / 2 + fm.getAscent());
                }
                g2.dispose();
            }
        };
        avatarPanel.setOpaque(false);
        avatarPanel.setPreferredSize(new Dimension(AV, AV));

        // ── Hero banner — avatar + name/ID stacked vertically, centered ─────
        Color c1 = ThemeManager.accent(), c2 = ThemeManager.accentH();
        JPanel hero = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, c1, getWidth(), getHeight(), c2));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        hero.setOpaque(false);
        hero.setBorder(new javax.swing.border.EmptyBorder(22, 16, 22, 16));

        avatarPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel nameLbl = new JLabel(s.getFirstName() + " " + s.getLastName(), SwingConstants.CENTER);
        nameLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
        nameLbl.setForeground(Color.WHITE);
        nameLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel idLbl = new JLabel(s.getStudentNo(), SwingConstants.CENTER);
        idLbl.setFont(ThemeManager.fontSmall());
        idLbl.setForeground(new Color(255, 255, 255, 190));
        idLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);
        col.add(avatarPanel);
        col.add(Box.createVerticalStrut(10));
        col.add(nameLbl);
        col.add(Box.createVerticalStrut(3));
        col.add(idLbl);

        hero.add(col, BorderLayout.CENTER);

        // ── Info grid ─────────────────────────────────────────────────────────
        String dob = s.getDateOfBirth() != null
            ? new java.text.SimpleDateFormat("MMM dd, yyyy").format(s.getDateOfBirth()) : "—";
        String enrolled = s.getEnrolledAt() != null
            ? new java.text.SimpleDateFormat("MMM dd, yyyy").format(s.getEnrolledAt()) : "—";
        String email = s.getUser() != null ? nvl(s.getUser().getEmail()) : "—";

        JPanel infoCard = new JPanel(new GridBagLayout());
        infoCard.setBackground(ThemeManager.surface());
        infoCard.setBorder(new javax.swing.border.EmptyBorder(14, 16, 14, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 6, 5, 6);
        gc.fill   = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        addDetailRow(infoCard, gc, 0, "\uD83D\uDCE7 Email",    email);
        addDetailRow(infoCard, gc, 1, "\uD83D\uDCDE Phone",    nvl(s.getPhone()));
        addDetailRow(infoCard, gc, 2, "\u26A7 Gender",         capitalize(s.getGender()));
        addDetailRow(infoCard, gc, 3, "\uD83C\uDF82 DOB",      dob);
        addDetailRow(infoCard, gc, 4, "\uD83D\uDCCD Address",  nvl(s.getAddress()));
        addDetailRow(infoCard, gc, 5, "\uD83D\uDCC5 Enrolled", enrolled);

        // Wrap hero + info in a single scrollable panel
        JPanel full = new JPanel(new BorderLayout());
        full.setBackground(ThemeManager.surface());
        full.add(hero,    BorderLayout.NORTH);
        full.add(infoCard, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(full);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ThemeManager.surface());
        scroll.getVerticalScrollBar().setUnitIncrement(12);

        detailPanel.add(scroll, BorderLayout.CENTER);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void addDetailRow(JPanel p, GridBagConstraints gc, int row, String label, String value) {
        gc.gridy = row;
        gc.gridx = 0; gc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(ThemeManager.fontBold());
        lbl.setForeground(ThemeManager.muted());
        p.add(lbl, gc);
        gc.gridx = 1; gc.weightx = 1;
        JLabel val = new JLabel(value);
        val.setFont(ThemeManager.fontBody());
        val.setForeground(ThemeManager.text());
        p.add(val, gc);
    }

    private static String nvl(String s) { return s != null && !s.isEmpty() ? s : "—"; }
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "—";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private void filterTable(String query) {
        tableModel.setRowCount(0);
        String q = query.trim().toLowerCase();
        for (Object[] row : allRows) {
            // cols: 0=ID,1=StudentNo,2=FirstName,3=LastName,4=Gender,5=Phone,6=DOB,7=Username,8=Email
            if (q.isEmpty()
                || str(row[1]).toLowerCase().contains(q)
                || str(row[2]).toLowerCase().contains(q)
                || str(row[3]).toLowerCase().contains(q)
                || (str(row[2]) + " " + str(row[3])).toLowerCase().contains(q)
                || str(row[7]).toLowerCase().contains(q)
                || str(row[8]).toLowerCase().contains(q)) {
                tableModel.addRow(row);
            }
        }
    }

    private void loadTable() {
        tableModel.setRowCount(0);
        allRows.clear();
        try {
            for (Student s : studentDAO.getAll()) {
                Object[] row = {
                    s.getId(), s.getStudentNo(), s.getFirstName(), s.getLastName(),
                    s.getGender(), s.getPhone(),
                    s.getDateOfBirth() != null ? sdf.format(s.getDateOfBirth()) : "",
                    s.getUser() != null ? s.getUser().getUsername() : "",
                    s.getUser() != null ? s.getUser().getEmail() : ""
                };
                allRows.add(row);
                tableModel.addRow(row);
            }
        } catch (SQLException ex) { showError("Failed to load students: " + ex.getMessage()); }
    }

    private void populateForm() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        selectedStudentId = (int) tableModel.getValueAt(row, 0);
        tfStudentNo.setText((String) tableModel.getValueAt(row, 1));
        tfFirstName.setText((String) tableModel.getValueAt(row, 2));
        tfLastName.setText((String)  tableModel.getValueAt(row, 3));
        cbGender.setSelectedItem(tableModel.getValueAt(row, 4));
        tfPhone.setText(str(tableModel.getValueAt(row, 5)));
        tfDob.setText(str(tableModel.getValueAt(row, 6)));
        tfUsername.setText(str(tableModel.getValueAt(row, 7)));
        tfEmail.setText(str(tableModel.getValueAt(row, 8)));
        tfPassword.setText("");
        tfAddress.setText("");

        try {
            Student s = studentDAO.getById(selectedStudentId);
            if (s != null) {
                tfAddress.setText(s.getAddress() != null ? s.getAddress() : "");
                selectedUserId = s.getUser() != null ? s.getUser().getId() : -1;
            }
        } catch (SQLException ex) { showError(ex.getMessage()); }
        setStatus("", false);
    }

    private void addStudent() {
        String err = validateForm(true);
        if (err != null) { setStatus(err, true); return; }
        try {
            if (userDAO.existsByUsername(tfUsername.getText().trim(), 0)) {
                setStatus("Username already exists.", true); return;
            }
            if (userDAO.existsByEmail(tfEmail.getText().trim(), 0)) {
                setStatus("Email already exists.", true); return;
            }
            if (studentDAO.existsByStudentNo(tfStudentNo.getText().trim(), 0)) {
                setStatus("Student number already exists.", true); return;
            }

            User u = new User();
            u.setUsername(tfUsername.getText().trim());
            u.setEmail(tfEmail.getText().trim());
            u.setPassword(tfPassword.getText().trim());
            u.setRole("student");
            u.setActive(true);
            userDAO.insert(u);

            Student s = buildStudent();
            s.setUser(u);
            studentDAO.insert(s);

            loadTable(); clearForm();
            setStatus("Student added successfully.", false);
        } catch (Exception ex) { showError("Add failed: " + ex.getMessage()); }
    }

    private void updateStudent() {
        if (selectedStudentId < 0) { setStatus("Select a student first.", true); return; }
        try {
            // Load original values for change detection
            Student orig = studentDAO.getById(selectedStudentId);
            User origUser = orig != null && orig.getUser() != null ? userDAO.getById(orig.getUser().getId()) : null;

            String newUsername = tfUsername.getText().trim();
            String newEmail    = tfEmail.getText().trim();
            String newPhone    = tfPhone.getText().trim();
            String newFirst    = tfFirstName.getText().trim();
            String newLast     = tfLastName.getText().trim();
            String newStudentNo= tfStudentNo.getText().trim();

            // Validate only required fields
            if (newFirst.isEmpty())     { setStatus("First name is required.", true); return; }
            if (newLast.isEmpty())      { setStatus("Last name is required.", true); return; }
            if (newStudentNo.isEmpty()) { setStatus("Student number is required.", true); return; }
            if (newUsername.isEmpty())  { setStatus("Username is required.", true); return; }
            if (newEmail.isEmpty())     { setStatus("Email is required.", true); return; }

            // Validate phone only if changed and non-empty
            if (!newPhone.isEmpty()) {
                if (!newPhone.matches("^(09\\d{8}|\\+2519\\d{8})$"))
                    { setStatus("Phone must be 09XXXXXXXX or +2519XXXXXXXX.", true); return; }
            }

            // Validate email only if changed
            boolean emailChanged = origUser == null || !newEmail.equals(origUser.getEmail());
            if (emailChanged) {
                if (!newEmail.matches("^[\\w.+\\-]+@[\\w\\-]+(\\.[\\w\\-]+)*(\\.[a-zA-Z]{2,})$"))
                    { setStatus("Invalid email format (e.g. user@gmail.com or user@domain.co.et).", true); return; }
                if (userDAO.existsByEmail(newEmail, selectedUserId))
                    { setStatus("Email already taken.", true); return; }
            }

            // Validate username only if changed
            boolean usernameChanged = origUser == null || !newUsername.equals(origUser.getUsername());
            if (usernameChanged) {
                if (!newUsername.matches("^[\\w.\\-@]+$"))
                    { setStatus("Username may only contain letters, numbers, dots, underscores, hyphens, or @.", true); return; }
                if (userDAO.existsByUsername(newUsername, selectedUserId))
                    { setStatus("Username already taken.", true); return; }
            }

            // Validate student no only if changed
            boolean studentNoChanged = orig == null || !newStudentNo.equals(orig.getStudentNo());
            if (studentNoChanged && studentDAO.existsByStudentNo(newStudentNo, selectedStudentId))
                { setStatus("Student number already taken.", true); return; }

            // Validate DOB only if non-empty
            String dob = tfDob.getText().trim();
            if (!dob.isEmpty()) {
                try { sdf.parse(dob); } catch (java.text.ParseException e)
                    { setStatus("Date of birth must be yyyy-MM-dd.", true); return; }
            }

            User u = userDAO.getById(selectedUserId);
            if (u == null) { setStatus("Associated user not found.", true); return; }
            u.setUsername(newUsername);
            u.setEmail(newEmail);
            if (!tfPassword.getText().trim().isEmpty())
                u.setPassword(tfPassword.getText().trim());
            userDAO.update(u);

            Student s = buildStudent();
            s.setId(selectedStudentId);
            s.setUser(u);
            studentDAO.update(s);

            loadTable(); clearForm();
            setStatus("Student updated successfully.", false);
        } catch (Exception ex) { showError("Update failed: " + ex.getMessage()); }
    }

    private void deleteStudent() {
        if (selectedStudentId < 0) { setStatus("Select a student first.", true); return; }
        String name = str(tableModel.getValueAt(table.getSelectedRow(), 2)) + " " +
                      str(tableModel.getValueAt(table.getSelectedRow(), 3));
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete student \"" + name + "\" and ALL their grades, enrollments and data?\nThis cannot be undone.",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try (Connection conn = util.DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // grades → enrollments → student → user (cascade-safe order)
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "DELETE g FROM grades g JOIN enrollments e ON e.id = g.enrollment_id WHERE e.student_id = ?")) {
                    ps.setInt(1, selectedStudentId); ps.executeUpdate();
                }
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM enrollments WHERE student_id = ?")) {
                    ps.setInt(1, selectedStudentId); ps.executeUpdate();
                }
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM students WHERE id = ?")) {
                    ps.setInt(1, selectedStudentId); ps.executeUpdate();
                }
                if (selectedUserId > 0) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM users WHERE id = ?")) {
                        ps.setInt(1, selectedUserId); ps.executeUpdate();
                    }
                }
                conn.commit();
                loadTable(); clearForm();
                setStatus("Student deleted.", false);
            } catch (SQLException ex) {
                conn.rollback();
                showError("Delete failed: " + ex.getMessage());
            }
        } catch (SQLException ex) { showError("Delete failed: " + ex.getMessage()); }
    }

    private Student buildStudent() throws ParseException {
        Student s = new Student();
        s.setFirstName(tfFirstName.getText().trim());
        s.setLastName(tfLastName.getText().trim());
        s.setStudentNo(tfStudentNo.getText().trim());
        s.setGender((String) cbGender.getSelectedItem());
        s.setPhone(tfPhone.getText().trim());
        s.setAddress(tfAddress.getText().trim());
        String dob = tfDob.getText().trim();
        s.setDateOfBirth(dob.isEmpty() ? null : sdf.parse(dob));
        s.setEnrolledAt(new java.util.Date());
        return s;
    }

    private String validateForm(boolean requirePassword) {
        if (tfFirstName.getText().trim().isEmpty()) return "First name is required.";
        if (tfLastName.getText().trim().isEmpty())  return "Last name is required.";
        if (tfStudentNo.getText().trim().isEmpty()) return "Student number is required.";
        if (tfUsername.getText().trim().isEmpty())  return "Username is required.";
        String uname = tfUsername.getText().trim();
        if (!uname.matches("^[\\w.\\-@]+$"))
            return "Username may only contain letters, numbers, dots, underscores, hyphens, or @.";
        String email = tfEmail.getText().trim();
        if (email.isEmpty()) return "Email is required.";
        if (!email.matches("^[\\w.+\\-]+@[\\w\\-]+(\\.[\\w\\-]+)*(\\.[a-zA-Z]{2,})$"))
            return "Invalid email format (e.g. user@gmail.com or user@domain.co.et).";
        String phone = tfPhone.getText().trim();
        if (!phone.isEmpty() && !phone.matches("^(09\\d{8}|\\+2519\\d{8})$"))
            return "Phone must be 09XXXXXXXX or +2519XXXXXXXX.";
        if (requirePassword && tfPassword.getText().trim().isEmpty())
            return "Password is required for new students.";
        String dob = tfDob.getText().trim();
        if (!dob.isEmpty()) {
            try { sdf.parse(dob); } catch (ParseException e) { return "Date of birth must be yyyy-MM-dd."; }
        }
        return null;
    }

    private void clearForm() {
        selectedStudentId = -1; selectedUserId = -1;
        tfFirstName.setText(""); tfLastName.setText(""); tfStudentNo.setText("");
        tfDob.setText(""); tfPhone.setText(""); tfAddress.setText("");
        tfUsername.setText(""); tfEmail.setText(""); tfPassword.setText("");
        cbGender.setSelectedIndex(0);
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
