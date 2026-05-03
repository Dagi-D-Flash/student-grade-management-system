package panels;

import dao.GradeComponentDAO;
import dao.GradeDAO;
import models.Enrollment;
import models.Grade;
import models.GradeComponent;
import models.User;
import util.DBConnection;
import util.GradeStructureService;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enter Grades tab — full CRUD per student per course.
 *
 * Layout:
 *   TOP    : course selector + refresh
 *   LEFT   : student list (searchable) with their current total %
 *   RIGHT  : grade form — one row per component, pre-filled from DB
 *            Buttons: Save All | Delete All Grades | Delete Selected Component Grade
 */
public class TeacherGradePanel extends JPanel {

    private final GradeDAO          gradeDAO     = new GradeDAO();
    private final GradeComponentDAO componentDAO = new GradeComponentDAO();
    private final User teacher;

    // ── top bar ───────────────────────────────────────────────────────────────
    private JComboBox<CourseItem> cbCourse;
    private JLabel hintLabel;
    private JTabbedPane tabs;

    // ── structure tab ─────────────────────────────────────────────────────────
    private JTable structTable;
    private DefaultTableModel structModel;
    private JTextField tfCompName, tfWeight, tfMaxScore;
    private JLabel lblTotalWeight, lblStructStatus;
    private JButton btnAddComp, btnUpdateComp, btnDeleteComp, btnClearComp;
    private int selectedCompId = -1;

    // ── grades tab — student list ─────────────────────────────────────────────
    private JTable studentTable;
    private DefaultTableModel studentModel;
    private final List<Object[]> allStudentRows = new ArrayList<>();

    // ── grades tab — grade form ───────────────────────────────────────────────
    private JPanel gradeFormPanel;          // holds the dynamic component rows
    private JScrollPane gradeFormScroll;
    private JLabel lblSelectedStudent;
    private JLabel lblGradeStatus;
    private JButton btnSaveAll, btnDeleteAll;

    // component id → {scoreField, remarksField, deleteBtn, gradeId-holder}
    private final Map<Integer, ComponentRow> componentRows = new LinkedHashMap<>();

    private int selectedEnrollmentId = -1;
    private int currentCourseId      = -1;

    // ── constructor ───────────────────────────────────────────────────────────

    public TeacherGradePanel(User teacher) {
        this.teacher = teacher;
        setLayout(new BorderLayout(8, 8));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());

        add(buildTopBar(), BorderLayout.NORTH);

        tabs = new JTabbedPane();
        tabs.setFont(ThemeManager.fontBold());
        tabs.addTab("\uD83D\uDCCB  Grade Structure", buildStructureTab());
        tabs.addTab("\u270F\uFE0F  Enter Grades",    buildGradesTab());
        add(tabs, BorderLayout.CENTER);

        loadCourses();
    }

    public void setSidebarExpanded(boolean expanded) {
        if (hintLabel != null) hintLabel.setVisible(expanded);
    }

    /**
     * Switches to the "Enter Grades" tab and selects the student matching
     * the given student number. Called from My Students on double-click.
     */
    public void selectStudentByNo(String studentNo) {
        // Switch to the grades tab (index 1)
        if (tabs != null) tabs.setSelectedIndex(1);
        // Find and select the matching row in the student list
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < studentModel.getRowCount(); i++) {
                if (studentNo.equals(studentModel.getValueAt(i, 1))) {
                    studentTable.setRowSelectionInterval(i, i);
                    studentTable.scrollRectToVisible(studentTable.getCellRect(i, 0, true));
                    return;
                }
            }
            // If not visible (filtered), search in allStudentRows and reload
            for (Object[] row : allStudentRows) {
                if (studentNo.equals(row[1])) {
                    // Clear search filter and re-select
                    studentModel.setRowCount(0);
                    for (Object[] r : allStudentRows) studentModel.addRow(r);
                    for (int i = 0; i < studentModel.getRowCount(); i++) {
                        if (studentNo.equals(studentModel.getValueAt(i, 1))) {
                            studentTable.setRowSelectionInterval(i, i);
                            studentTable.scrollRectToVisible(studentTable.getCellRect(i, 0, true));
                            return;
                        }
                    }
                }
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TOP BAR
    // ═════════════════════════════════════════════════════════════════════════

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(ThemeManager.cardBorder());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        left.setOpaque(false);

        JLabel lbl = new JLabel("Course:");
        lbl.setFont(ThemeManager.fontBold());
        lbl.setForeground(ThemeManager.text());
        left.add(lbl);

        cbCourse = new JComboBox<>();
        cbCourse.setPreferredSize(new Dimension(420, 30));
        cbCourse.addActionListener(e -> onCourseChanged());
        left.add(cbCourse);

        JButton btnRefresh = ThemeManager.secondaryButton("\u21BB Refresh");
        btnRefresh.addActionListener(e -> loadCourses());
        left.add(btnRefresh);

        hintLabel = new JLabel("\u2190 Collapse sidebar for more space");
        hintLabel.setFont(ThemeManager.fontSmall());
        hintLabel.setForeground(ThemeManager.muted());

        bar.add(left,      BorderLayout.WEST);
        bar.add(hintLabel, BorderLayout.EAST);
        return bar;
    }

    private void onCourseChanged() {
        CourseItem ci = (CourseItem) cbCourse.getSelectedItem();
        currentCourseId      = ci != null ? ci.id : -1;
        selectedEnrollmentId = -1;
        loadStructure();
        loadStudentList();
        clearGradeForm("Select a student from the list to view or enter grades.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // STRUCTURE TAB  (unchanged logic, kept intact)
    // ═════════════════════════════════════════════════════════════════════════

    private JPanel buildStructureTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(ThemeManager.bg());
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel info = new JLabel(
            "<html><b>Define grade components for this course.</b> " +
            "Total weight MUST equal exactly 100%. " +
            "All enrolled students share the same structure.</html>");
        info.setFont(ThemeManager.fontSmall());
        info.setForeground(ThemeManager.muted());
        info.setBorder(new EmptyBorder(0, 0, 8, 0));
        panel.add(info, BorderLayout.NORTH);

        structModel = new DefaultTableModel(
                new String[]{"ID", "Component Name", "Weight (%)", "Max Score"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        structTable = new JTable(structModel);
        ThemeManager.styleTable(structTable);
        structTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        structTable.getColumnModel().getColumn(0).setMaxWidth(50);
        structTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onComponentSelected();
        });

        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.setBackground(ThemeManager.surface());
        tableWrapper.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ThemeManager.border()), "Current Grade Structure"));
        tableWrapper.add(new JScrollPane(structTable));

        JPanel form = new JPanel(new BorderLayout(10, 6));
        form.setBackground(ThemeManager.surface());
        form.setBorder(ThemeManager.cardBorder());

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill   = GridBagConstraints.HORIZONTAL;

        tfCompName = styledField(); tfWeight = styledField(); tfMaxScore = styledField();
        lblTotalWeight  = new JLabel("Total weight: 0 / 100");
        lblTotalWeight.setFont(ThemeManager.fontBold());
        lblStructStatus = new JLabel(" ");
        lblStructStatus.setFont(ThemeManager.fontSmall());

        g.gridy = 0; g.gridwidth = 1; g.weightx = 0;
        g.gridx = 0; fields.add(lbl("Component Name *"), g);
        g.gridx = 1; g.weightx = 0.35; fields.add(tfCompName, g);
        g.gridx = 2; g.weightx = 0;    fields.add(lbl("Weight % *"), g);
        g.gridx = 3; g.weightx = 0.15; fields.add(tfWeight, g);
        g.gridx = 4; g.weightx = 0;    fields.add(lbl("Max Score *"), g);
        g.gridx = 5; g.weightx = 0.15; fields.add(tfMaxScore, g);
        g.gridx = 6; g.weightx = 0.2;  fields.add(lblTotalWeight, g);

        g.gridy = 1; g.gridx = 0; g.gridwidth = 7; g.weightx = 1;
        fields.add(lblStructStatus, g);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        btnAddComp    = ThemeManager.primaryButton("Add Component");
        btnUpdateComp = ThemeManager.primaryButton("Update");
        btnDeleteComp = ThemeManager.dangerButton("Delete");
        btnClearComp  = ThemeManager.secondaryButton("Clear");
        btnRow.add(btnAddComp); btnRow.add(btnUpdateComp);
        btnRow.add(btnDeleteComp); btnRow.add(btnClearComp);

        btnAddComp.addActionListener(e    -> addComponent());
        btnUpdateComp.addActionListener(e -> updateComponent());
        btnDeleteComp.addActionListener(e -> deleteComponent());
        btnClearComp.addActionListener(e  -> clearCompForm());

        form.add(fields, BorderLayout.CENTER);
        form.add(btnRow, BorderLayout.EAST);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableWrapper, form);
        split.setDividerLocation(260);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private void loadStructure() {
        structModel.setRowCount(0);
        if (currentCourseId < 0) return;
        try {
            List<GradeComponent> list = componentDAO.getByCourseId(currentCourseId);
            double total = 0;
            for (GradeComponent c : list) {
                structModel.addRow(new Object[]{c.getId(), c.getComponentName(), c.getWeight(), c.getMaxScore()});
                total += c.getWeight();
            }
            updateTotalWeightLabel(total);
        } catch (SQLException ex) { System.err.println("loadStructure: " + ex.getMessage()); }
    }

    private void onComponentSelected() {
        int row = structTable.getSelectedRow();
        if (row < 0) return;
        selectedCompId = (int) structModel.getValueAt(row, 0);
        tfCompName.setText(structModel.getValueAt(row, 1).toString());
        tfWeight.setText(structModel.getValueAt(row, 2).toString());
        tfMaxScore.setText(structModel.getValueAt(row, 3).toString());
        setStructStatus("", false);
    }

    private void addComponent() {
        if (currentCourseId < 0) { setStructStatus("Select a course first.", true); return; }
        String err = validateCompForm(false);
        if (err != null) { setStructStatus(err, true); return; }
        try {
            componentDAO.insert(buildComponent());
            loadStructure(); clearCompForm();
            setStructStatus("Component added.", false);
        } catch (SQLException ex) { showError(ex.getMessage()); }
    }

    private void updateComponent() {
        if (selectedCompId < 0) { setStructStatus("Select a component first.", true); return; }
        String err = validateCompForm(true);
        if (err != null) { setStructStatus(err, true); return; }
        try {
            GradeComponent updated = buildComponent();
            updated.setId(selectedCompId);

            // Delegate entirely to the service — one atomic transaction
            GradeStructureService.UpdateResult result =
                GradeStructureService.updateComponent(updated);

            if (!result.success) {
                setStructStatus(result.message, true);
                return;
            }

            // Refresh everything instantly
            loadStructure();
            clearCompForm();
            loadStudentList();                               // refresh Total% for all students
            if (selectedEnrollmentId >= 0) loadGradeForm(); // refresh open student's scores

            setStructStatus(result.message, false);

        } catch (SQLException ex) { showError(ex.getMessage()); }
    }

    private void deleteComponent() {
        if (selectedCompId < 0) { setStructStatus("Select a component first.", true); return; }
        int ok = JOptionPane.showConfirmDialog(this,
                "Delete component \"" + tfCompName.getText() + "\"?\n" +
                "All student grades for this component will also be deleted.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        try {
            componentDAO.delete(selectedCompId);
            loadStructure(); clearCompForm();
            setStructStatus("Component deleted.", false);
        } catch (SQLException ex) { showError(ex.getMessage()); }
    }

    private GradeComponent buildComponent() {
        GradeComponent c = new GradeComponent();
        c.setCourseId(currentCourseId);
        c.setComponentName(tfCompName.getText().trim());
        c.setWeight(parseDouble(tfWeight.getText()));
        c.setMaxScore(parseDouble(tfMaxScore.getText()));
        return c;
    }

    private String validateCompForm(boolean isUpdate) {
        String name = tfCompName.getText().trim();
        String wStr = tfWeight.getText().trim();
        String msStr = tfMaxScore.getText().trim();
        if (name.isEmpty())  return "Component name is required.";
        if (wStr.isEmpty())  return "Weight is required.";
        if (msStr.isEmpty()) return "Max score is required.";
        double w  = parseDouble(wStr);
        double ms = parseDouble(msStr);
        if (w <= 0 || w > 100) return "Weight must be between 0.01 and 100.";
        if (ms <= 0)           return "Max score must be greater than 0.";
        if (ms > 100)          return "Max score cannot exceed 100.";
        try {
            if (componentDAO.existsByName(currentCourseId, name, isUpdate ? selectedCompId : -1))
                return "A component named \"" + name + "\" already exists for this course.";
            double existing  = componentDAO.getTotalWeight(currentCourseId, isUpdate ? selectedCompId : -1);
            double projected = existing + w;
            if (projected > 100)
                return String.format("Total weight would be %.1f%% (exceeds 100%%). Available: %.1f%%.",
                        projected, 100 - existing);
        } catch (SQLException ex) { return "DB error: " + ex.getMessage(); }
        return null;
    }

    private void updateTotalWeightLabel(double total) {
        lblTotalWeight.setText(String.format("Total weight: %.0f / 100", total));
        lblTotalWeight.setForeground(Math.abs(total - 100) < 0.01 ? ThemeManager.SUCCESS : ThemeManager.WARNING);
    }

    private void clearCompForm() {
        selectedCompId = -1;
        tfCompName.setText(""); tfWeight.setText(""); tfMaxScore.setText("");
        structTable.clearSelection();
        setStructStatus("", false);
    }

    private void setStructStatus(String msg, boolean error) {
        lblStructStatus.setText(msg.isEmpty() ? " " : msg);
        lblStructStatus.setForeground(error ? ThemeManager.danger() : ThemeManager.SUCCESS);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GRADES TAB  — split: student list (left) | grade form (right)
    // ═════════════════════════════════════════════════════════════════════════

    private JPanel buildGradesTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(ThemeManager.bg());
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildStudentListPanel(), buildGradeFormPanel());
        split.setDividerLocation(340);
        split.setResizeWeight(0.3);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    // ── Left: student list ────────────────────────────────────────────────────

    private JPanel buildStudentListPanel() {
        // Columns: [0] enrollId (hidden), [1] studentNo, [2] name, [3] total%, [4] status
        studentModel = new DefaultTableModel(
                new String[]{"#", "Student No", "Student Name", "Total %", "Status"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        studentTable = new JTable(studentModel);
        ThemeManager.styleTable(studentTable);
        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studentTable.setRowHeight(24);

        // hide internal enroll-id column
        studentTable.getColumnModel().getColumn(0).setMinWidth(0);
        studentTable.getColumnModel().getColumn(0).setMaxWidth(0);
        studentTable.getColumnModel().getColumn(0).setWidth(0);

        // colour the Total% column
        studentTable.getColumnModel().getColumn(3).setCellRenderer(totalPctRenderer());

        studentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onStudentSelected();
        });

        // search bar
        JTextField tfSearch = new JTextField();
        tfSearch.setBackground(ThemeManager.elevated());
        tfSearch.setForeground(ThemeManager.text());
        tfSearch.setCaretColor(ThemeManager.text());
        tfSearch.setFont(ThemeManager.fontBody());
        tfSearch.putClientProperty("JTextField.placeholderText", "Search by name or student ID...");
        tfSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filterStudents(tfSearch.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filterStudents(tfSearch.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterStudents(tfSearch.getText()); }
        });

        JPanel searchBar = new JPanel(new BorderLayout(4, 0));
        searchBar.setOpaque(false);
        searchBar.setBorder(new EmptyBorder(0, 0, 4, 0));
        JLabel icon = new JLabel("\uD83D\uDD0D");
        icon.setFont(new Font("SansSerif", Font.PLAIN, 14));
        searchBar.add(icon,     BorderLayout.WEST);
        searchBar.add(tfSearch, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ThemeManager.surface());
        wrapper.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ThemeManager.border()), "Enrolled Students"));
        wrapper.add(searchBar,                     BorderLayout.NORTH);
        wrapper.add(new JScrollPane(studentTable), BorderLayout.CENTER);
        return wrapper;
    }

    // ── Right: grade form ─────────────────────────────────────────────────────

    private JPanel buildGradeFormPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 8));
        outer.setBackground(ThemeManager.surface());
        outer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ThemeManager.border()), "Grade Entry"));

        // header: student name + status label
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(6, 10, 4, 10));

        lblSelectedStudent = new JLabel("No student selected");
        lblSelectedStudent.setFont(ThemeManager.fontBold());
        lblSelectedStudent.setForeground(ThemeManager.text());

        lblGradeStatus = new JLabel(" ");
        lblGradeStatus.setFont(ThemeManager.fontSmall());
        lblGradeStatus.setForeground(ThemeManager.muted());

        header.add(lblSelectedStudent, BorderLayout.WEST);
        header.add(lblGradeStatus,     BorderLayout.EAST);

        // scrollable form area — rebuilt dynamically per student
        gradeFormPanel = new JPanel();
        gradeFormPanel.setLayout(new BoxLayout(gradeFormPanel, BoxLayout.Y_AXIS));
        gradeFormPanel.setBackground(ThemeManager.bg());
        gradeFormPanel.setBorder(new EmptyBorder(8, 10, 8, 10));

        gradeFormScroll = new JScrollPane(gradeFormPanel);
        gradeFormScroll.setBorder(null);
        gradeFormScroll.getViewport().setBackground(ThemeManager.bg());

        // bottom action bar
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(0, 0, 4, 4));

        btnSaveAll  = ThemeManager.primaryButton("\uD83D\uDCBE  Save All Grades");
        btnDeleteAll = ThemeManager.dangerButton("\uD83D\uDDD1  Delete All Grades");

        btnSaveAll.addActionListener(e   -> saveAllGrades());
        btnDeleteAll.addActionListener(e -> deleteAllGrades());

        bottom.add(btnDeleteAll);
        bottom.add(btnSaveAll);

        outer.add(header,        BorderLayout.NORTH);
        outer.add(gradeFormScroll, BorderLayout.CENTER);
        outer.add(bottom,        BorderLayout.SOUTH);

        // show placeholder message
        showFormPlaceholder("Select a student from the list to view or enter grades.");
        return outer;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // STUDENT LIST HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private void loadStudentList() {
        studentModel.setRowCount(0);
        allStudentRows.clear();
        if (currentCourseId < 0) return;

        // Join grades through course_grade_components to ensure we only sum
        // grades that belong to THIS course — not grades from other courses
        // that happen to share the same enrollment_id in the seed data.
        String sql =
            "SELECT e.id AS enroll_id, s.student_no, " +
            "       s.first_name, s.last_name, e.status, " +
            "       SUM((g.score / cgc.max_score) * cgc.weight) AS total_pct " +
            "FROM enrollments e " +
            "JOIN students s ON s.id = e.student_id " +
            "LEFT JOIN grades g ON g.enrollment_id = e.id " +
            "LEFT JOIN course_grade_components cgc " +
            "       ON cgc.id = g.component_id AND cgc.course_id = e.course_id " +
            "WHERE e.course_id = ? " +
            "GROUP BY e.id, s.student_no, s.first_name, s.last_name, e.status " +
            "ORDER BY s.first_name, s.last_name";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, currentCourseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double total      = rs.getDouble("total_pct");
                    boolean hasGrades = !rs.wasNull();
                    String fullName   = rs.getString("first_name") + " " + rs.getString("last_name");
                    Object[] row = {
                        rs.getInt("enroll_id"),
                        rs.getString("student_no"),
                        fullName,
                        hasGrades ? String.format("%.1f%%", total) : "—",
                        rs.getString("status")
                    };
                    allStudentRows.add(row);
                    studentModel.addRow(row);
                }
            }
        } catch (SQLException ex) { System.err.println("loadStudentList: " + ex.getMessage()); }
    }

    private void filterStudents(String query) {
        // Preserve the currently selected enrollment so selection survives filtering
        int prevEnrollId = selectedEnrollmentId;

        studentModel.setRowCount(0);
        String q = query.trim().toLowerCase();
        for (Object[] row : allStudentRows) {
            String no   = row[1].toString().toLowerCase();
            String name = row[2].toString().toLowerCase();
            if (q.isEmpty() || no.contains(q) || name.contains(q))
                studentModel.addRow(row);
        }

        // Re-select the previously selected student if still visible
        if (prevEnrollId >= 0) {
            for (int i = 0; i < studentModel.getRowCount(); i++) {
                if (((Number) studentModel.getValueAt(i, 0)).intValue() == prevEnrollId) {
                    studentTable.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
    }

    private void onStudentSelected() {
        int row = studentTable.getSelectedRow();
        if (row < 0) {
            selectedEnrollmentId = -1;
            clearGradeForm("Select a student from the list to view or enter grades.");
            return;
        }
        selectedEnrollmentId = (int) studentModel.getValueAt(row, 0);
        String studentName   = studentModel.getValueAt(row, 2).toString();
        String studentNo     = studentModel.getValueAt(row, 1).toString();
        lblSelectedStudent.setText(studentName + "  (" + studentNo + ")");
        loadGradeForm();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GRADE FORM — build / load / clear
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Rebuilds the grade form for the selected student.
     * Each component gets its own row with:
     *   Label | Score field (pre-filled from DB) | /MaxScore | Weighted% | Letter | Remarks | Delete btn
     */
    private void loadGradeForm() {
        gradeFormPanel.removeAll();
        componentRows.clear();

        if (selectedEnrollmentId < 0 || currentCourseId < 0) {
            showFormPlaceholder("Select a student to view grades.");
            return;
        }

        List<GradeComponent> components;
        Map<Integer, Grade> gradeMap = new LinkedHashMap<>();

        try {
            components = componentDAO.getByCourseId(currentCourseId);
            if (components.isEmpty()) {
                showFormPlaceholder("No grade components defined for this course.\nGo to the Grade Structure tab to add components.");
                return;
            }

            // Fetch grades for this enrollment that belong to THIS course's components only.
            // This is the critical join — without it, grades seeded for other courses
            // (same enrollment_id but different component_id) pollute the map.
            String sql =
                "SELECT g.id, g.enrollment_id, g.component_id, g.grade_type, " +
                "       g.score, g.remarks, g.graded_at " +
                "FROM grades g " +
                "JOIN course_grade_components cgc ON cgc.id = g.component_id " +
                "WHERE g.enrollment_id = ? AND cgc.course_id = ?";

            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, selectedEnrollmentId);
                ps.setInt(2, currentCourseId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Grade g = new Grade();
                        g.setId(rs.getInt("id"));
                        g.setComponentId(rs.getInt("component_id"));
                        g.setGradeType(rs.getString("grade_type"));
                        g.setScore(rs.getDouble("score"));
                        g.setRemarks(rs.getString("remarks"));
                        g.setGradedAt(rs.getTimestamp("graded_at"));
                        Enrollment enr = new Enrollment();
                        enr.setId(rs.getInt("enrollment_id"));
                        g.setEnrollment(enr);
                        gradeMap.put(g.getComponentId(), g);
                    }
                }
            }

        } catch (SQLException ex) {
            showFormPlaceholder("Error loading grades: " + ex.getMessage());
            return;
        }

        // ── column header row ─────────────────────────────────────────────────
        JPanel headerRow = formRow(true);
        headerRow.add(headerCell("Component",  160));
        headerRow.add(headerCell("Score",       80));
        headerRow.add(headerCell("/ Max",       55));
        headerRow.add(headerCell("Weighted %",  80));
        headerRow.add(headerCell("Letter",      55));
        headerRow.add(headerCell("Remarks",    140));
        headerRow.add(headerCell("",            80)); // delete button column
        gradeFormPanel.add(headerRow);
        gradeFormPanel.add(Box.createVerticalStrut(2));

        // ── one row per component ─────────────────────────────────────────────
        for (GradeComponent comp : components) {
            // Look up from the pre-fetched map — no extra DB call per row
            Grade existing = gradeMap.get(comp.getId());

            // Score field — pre-filled with REAL score from DB, or empty if no grade yet
            JTextField tfScore = inputField(80);
            if (existing != null) {
                // Show the actual saved score, not 0
                tfScore.setText(formatScore(existing.getScore()));
            } else {
                tfScore.setText("");   // blank = no grade entered yet
                tfScore.putClientProperty("JTextField.placeholderText", "0");
            }

            JTextField tfRemarks = inputField(140);
            if (existing != null && existing.getRemarks() != null)
                tfRemarks.setText(existing.getRemarks());

            // computed display labels (update live as score is typed)
            JLabel lblWeighted = valueLabel(existing != null
                    ? String.format("%.2f", calcWeighted(existing.getScore(), comp)) : "—");
            JLabel lblLetter   = letterLabel(existing != null
                    ? toLetterGrade(calcPct(existing.getScore(), comp)) : "—");

            // wire live recalc
            final GradeComponent compFinal = comp;
            tfScore.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e)  { recalcLabels(tfScore, compFinal, lblWeighted, lblLetter); }
                public void removeUpdate(javax.swing.event.DocumentEvent e)  { recalcLabels(tfScore, compFinal, lblWeighted, lblLetter); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { recalcLabels(tfScore, compFinal, lblWeighted, lblLetter); }
            });

            // delete button for this single component
            JButton btnDel = ThemeManager.dangerButton("Delete");
            btnDel.setFont(ThemeManager.fontSmall());
            btnDel.setPreferredSize(new Dimension(72, 26));
            final int gradeId = existing != null ? existing.getId() : -1;
            btnDel.addActionListener(e -> deleteSingleGrade(comp, gradeId));
            if (existing == null) {
                btnDel.setEnabled(false);
                btnDel.setToolTipText("No grade saved yet");
            }

            // store for save
            componentRows.put(comp.getId(), new ComponentRow(comp, tfScore, tfRemarks, btnDel, gradeId));

            // build the visual row
            JPanel row = formRow(false);
            row.add(compLabel(comp.getComponentName() + "  (max " + formatScore(comp.getMaxScore()) + ")", 160));
            row.add(tfScore);
            row.add(valueLabel("/ " + formatScore(comp.getMaxScore()), 55));
            row.add(lblWeighted);
            row.add(lblLetter);
            row.add(tfRemarks);
            row.add(btnDel);
            gradeFormPanel.add(row);
            gradeFormPanel.add(Box.createVerticalStrut(4));
        }

        // ── total row ─────────────────────────────────────────────────────────
        gradeFormPanel.add(Box.createVerticalStrut(6));
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(ThemeManager.border());
        gradeFormPanel.add(sep);
        gradeFormPanel.add(Box.createVerticalStrut(4));

        JPanel totalRow = formRow(false);
        JLabel totalLbl = new JLabel("Total Score:");
        totalLbl.setFont(ThemeManager.fontBold());
        totalLbl.setForeground(ThemeManager.text());
        totalLbl.setPreferredSize(new Dimension(160, 26));

        double savedTotal = 0;
        boolean anyGrade  = false;
        for (ComponentRow cr : componentRows.values()) {
            if (cr.gradeId > 0) {
                String txt = cr.scoreField.getText().trim();
                if (!txt.isEmpty()) { savedTotal += calcWeighted(parseDouble(txt), cr.component); anyGrade = true; }
            }
        }
        JLabel totalVal = new JLabel(anyGrade ? String.format("%.2f / 100", savedTotal) : "—");
        totalVal.setFont(new Font("SansSerif", Font.BOLD, 14));
        totalVal.setForeground(anyGrade ? ThemeManager.accent() : ThemeManager.muted());

        totalRow.add(totalLbl);
        totalRow.add(totalVal);
        gradeFormPanel.add(totalRow);

        gradeFormPanel.revalidate();
        gradeFormPanel.repaint();
        setGradeStatus(" ", false);
    }

    private void showFormPlaceholder(String message) {
        gradeFormPanel.removeAll();
        componentRows.clear();
        JLabel lbl = new JLabel("<html><center>" + message.replace("\n", "<br>") + "</center></html>");
        lbl.setFont(ThemeManager.fontBody());
        lbl.setForeground(ThemeManager.muted());
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        gradeFormPanel.add(Box.createVerticalGlue());
        gradeFormPanel.add(lbl);
        gradeFormPanel.add(Box.createVerticalGlue());
        gradeFormPanel.revalidate();
        gradeFormPanel.repaint();
        if (lblSelectedStudent != null) lblSelectedStudent.setText("No student selected");
        setGradeStatus(" ", false);
    }

    private void clearGradeForm(String message) {
        selectedEnrollmentId = -1;
        showFormPlaceholder(message);
    }

    // ── live recalc ───────────────────────────────────────────────────────────

    private void recalcLabels(JTextField tfScore, GradeComponent comp,
                               JLabel lblWeighted, JLabel lblLetter) {
        String txt = tfScore.getText().trim();
        if (txt.isEmpty()) {
            lblWeighted.setText("—");
            lblLetter.setText("—");
            lblLetter.setForeground(ThemeManager.muted());
            return;
        }
        double score    = parseDouble(txt);
        double pct      = calcPct(score, comp);
        double weighted = calcWeighted(score, comp);
        lblWeighted.setText(String.format("%.2f", weighted));
        String letter = toLetterGrade(pct);
        lblLetter.setText(letter);
        lblLetter.setForeground(letterColor(letter));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CRUD ACTIONS
    // ═════════════════════════════════════════════════════════════════════════

    private void saveAllGrades() {
        if (selectedEnrollmentId < 0) { setGradeStatus("Select a student first.", true); return; }
        if (componentRows.isEmpty())  { setGradeStatus("No grade components loaded.", true); return; }

        // Validate all scores first
        for (ComponentRow cr : componentRows.values()) {
            String txt = cr.scoreField.getText().trim();
            if (txt.isEmpty()) continue;   // blank = skip (don't save 0 unless explicitly typed)
            double score = parseDouble(txt);
            if (score < 0) {
                setGradeStatus("Score cannot be negative for: " + cr.component.getComponentName(), true);
                return;
            }
            if (score > cr.component.getMaxScore()) {
                setGradeStatus(String.format("Score %.1f exceeds max %.1f for: %s",
                        score, cr.component.getMaxScore(), cr.component.getComponentName()), true);
                return;
            }
        }

        try {
            int saved = 0;
            for (ComponentRow cr : componentRows.values()) {
                String txt     = cr.scoreField.getText().trim();
                String remarks = cr.remarksField.getText().trim();

                if (txt.isEmpty()) continue;   // skip blank fields
                double score = parseDouble(txt);

                Grade existing = gradeDAO.getByEnrollmentAndComponent(
                        selectedEnrollmentId, cr.component.getId());

                if (existing != null) {
                    // UPDATE
                    existing.setScore(score);
                    existing.setRemarks(remarks);
                    gradeDAO.update(existing);
                } else {
                    // INSERT
                    Grade g = new Grade();
                    Enrollment enr = new Enrollment();
                    enr.setId(selectedEnrollmentId);
                    g.setEnrollment(enr);
                    g.setComponentId(cr.component.getId());
                    g.setGradeType(cr.component.getComponentName());
                    g.setScore(score);
                    g.setRemarks(remarks);
                    gradeDAO.insert(g);
                }
                saved++;
            }

            // Reload form from DB so delete buttons activate and totals update
            loadGradeForm();
            refreshStudentTotalInList();
            setGradeStatus(saved + " grade(s) saved successfully.", false);

        } catch (SQLException ex) {
            showError("Save failed: " + ex.getMessage());
        }
    }

    private void deleteSingleGrade(GradeComponent comp, int gradeId) {
        if (gradeId < 0) { setGradeStatus("No saved grade for this component.", true); return; }
        int ok = JOptionPane.showConfirmDialog(this,
                "Delete the saved grade for \"" + comp.getComponentName() + "\"?\n" +
                "The field will be cleared and the record removed from the database.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        try {
            gradeDAO.delete(gradeId);
            loadGradeForm();
            refreshStudentTotalInList();
            setGradeStatus("Grade for \"" + comp.getComponentName() + "\" deleted.", false);
        } catch (SQLException ex) { showError("Delete failed: " + ex.getMessage()); }
    }

    private void deleteAllGrades() {
        if (selectedEnrollmentId < 0) { setGradeStatus("Select a student first.", true); return; }
        boolean anyGrade = componentRows.values().stream().anyMatch(cr -> cr.gradeId > 0);
        if (!anyGrade) { setGradeStatus("No saved grades to delete for this student.", true); return; }

        int ok = JOptionPane.showConfirmDialog(this,
                "Delete ALL saved grades for this student in this course?\n" +
                "This cannot be undone.",
                "Confirm Delete All", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        try {
            for (ComponentRow cr : componentRows.values()) {
                if (cr.gradeId > 0) gradeDAO.delete(cr.gradeId);
            }
            loadGradeForm();
            refreshStudentTotalInList();
            setGradeStatus("All grades deleted for this student.", false);
        } catch (SQLException ex) { showError("Delete failed: " + ex.getMessage()); }
    }

    /** Refreshes the Total% column in the student list for the currently selected row. */
    private void refreshStudentTotalInList() {
        int selRow = studentTable.getSelectedRow();
        if (selRow < 0) return;
        String sql =
            "SELECT SUM((g.score / cgc.max_score) * cgc.weight) AS total_pct " +
            "FROM grades g " +
            "JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "WHERE g.enrollment_id = ? AND cgc.course_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, selectedEnrollmentId);
            ps.setInt(2, currentCourseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double total  = rs.getDouble("total_pct");
                    boolean has   = !rs.wasNull();
                    String display = has ? String.format("%.1f%%", total) : "—";
                    studentModel.setValueAt(display, selRow, 3);
                    for (Object[] row : allStudentRows) {
                        if (((Number) row[0]).intValue() == selectedEnrollmentId) {
                            row[3] = display; break;
                        }
                    }
                }
            }
        } catch (SQLException ex) { System.err.println("refreshTotal: " + ex.getMessage()); }
    }

    private void setGradeStatus(String msg, boolean error) {
        if (lblGradeStatus == null) return;
        lblGradeStatus.setText(msg.isEmpty() ? " " : msg);
        lblGradeStatus.setForeground(error ? ThemeManager.danger() : ThemeManager.SUCCESS);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // COURSE COMBO
    // ═════════════════════════════════════════════════════════════════════════

    private void loadCourses() {
        CourseItem selected = (CourseItem) cbCourse.getSelectedItem();
        cbCourse.removeAllItems();
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
            ps.setInt(1, teacher.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String label = rs.getString("code") + " \u2014 " + rs.getString("name")
                            + "  [" + rs.getString("section") + "]  "
                            + rs.getInt("academic_year") + " " + rs.getString("semester");
                    CourseItem item = new CourseItem(rs.getInt("id"), label);
                    cbCourse.addItem(item);
                    if (selected != null && item.id == selected.id)
                        cbCourse.setSelectedItem(item);
                }
            }
        } catch (SQLException ex) { System.err.println("loadCourses: " + ex.getMessage()); }
        if (cbCourse.getItemCount() > 0) onCourseChanged();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UI HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private JPanel formRow(boolean isHeader) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row.setOpaque(true);
        row.setBackground(isHeader ? ThemeManager.elevated() : ThemeManager.surface());
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, isHeader ? 28 : 34));
        row.setBorder(new EmptyBorder(isHeader ? 2 : 1, 4, isHeader ? 2 : 1, 4));
        return row;
    }

    private JLabel headerCell(String text, int width) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.fontBold());
        l.setForeground(ThemeManager.muted());
        l.setPreferredSize(new Dimension(width, 22));
        return l;
    }

    private JLabel compLabel(String text, int width) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.fontBold());
        l.setForeground(ThemeManager.text());
        l.setPreferredSize(new Dimension(width, 26));
        return l;
    }

    private JLabel valueLabel(String text) { return valueLabel(text, 80); }
    private JLabel valueLabel(String text, int width) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.fontBody());
        l.setForeground(ThemeManager.muted());
        l.setPreferredSize(new Dimension(width, 26));
        return l;
    }

    private JLabel letterLabel(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(ThemeManager.fontBold());
        l.setForeground(letterColor(text));
        l.setPreferredSize(new Dimension(55, 26));
        return l;
    }

    private JTextField inputField(int width) {
        JTextField tf = new JTextField();
        tf.setBackground(ThemeManager.elevated());
        tf.setForeground(ThemeManager.text());
        tf.setCaretColor(ThemeManager.text());
        tf.setFont(ThemeManager.fontBody());
        tf.setPreferredSize(new Dimension(width, 26));
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.border()),
                new EmptyBorder(2, 6, 2, 6)));
        return tf;
    }

    private DefaultTableCellRenderer totalPctRenderer() {
        return new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                setHorizontalAlignment(SwingConstants.CENTER);
                String s = v != null ? v.toString() : "—";
                if (!sel && !s.equals("—")) {
                    double val = parseDouble(s.replace("%", ""));
                    if      (val >= 75) { setBackground(ThemeManager.gradeABg()); setForeground(ThemeManager.gradeAFg()); }
                    else if (val >= 60) { setBackground(ThemeManager.gradeBBg()); setForeground(ThemeManager.gradeBFg()); }
                    else if (val >= 50) { setBackground(ThemeManager.gradeCBg()); setForeground(ThemeManager.gradeCFg()); }
                    else                { setBackground(ThemeManager.gradeFBg()); setForeground(ThemeManager.gradeFFg()); }
                }
                return this;
            }
        };
    }

    // ═════════════════════════════════════════════════════════════════════════
    // MATH / GRADE UTILITIES
    // ═════════════════════════════════════════════════════════════════════════

    private double calcPct(double score, GradeComponent comp) {
        return comp.getMaxScore() > 0 ? (score / comp.getMaxScore()) * 100.0 : 0;
    }

    private double calcWeighted(double score, GradeComponent comp) {
        return calcPct(score, comp) * comp.getWeight() / 100.0;
    }

    private String toLetterGrade(double pct) {
        if (pct >= 90) return "A+";
        if (pct >= 85) return "A";
        if (pct >= 80) return "A-";
        if (pct >= 75) return "B+";
        if (pct >= 70) return "B";
        if (pct >= 65) return "B-";
        if (pct >= 60) return "C+";
        if (pct >= 55) return "C";
        if (pct >= 50) return "C-";
        if (pct >= 45) return "D";
        return "F";
    }

    private Color letterColor(String letter) {
        if (letter == null || letter.equals("—")) return ThemeManager.muted();
        switch (letter.charAt(0)) {
            case 'A': return ThemeManager.gradeAFg();
            case 'B': return ThemeManager.gradeBFg();
            case 'C': return ThemeManager.gradeCFg();
            case 'D': return ThemeManager.gradeDFg();
            case 'F': return ThemeManager.gradeFFg();
            default:  return ThemeManager.muted();
        }
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0.0; }
    }

    private String formatScore(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    private JTextField styledField() {
        JTextField tf = new JTextField(10);
        tf.setBackground(ThemeManager.elevated());
        tf.setForeground(ThemeManager.text());
        tf.setCaretColor(ThemeManager.text());
        tf.setFont(ThemeManager.fontBody());
        return tf;
    }

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.fontBold());
        l.setForeground(ThemeManager.muted());
        return l;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═════════════════════════════════════════════════════════════════════════

    /** Holds the UI controls for one grade component row in the form. */
    private static class ComponentRow {
        final GradeComponent component;
        final JTextField     scoreField;
        final JTextField     remarksField;
        final JButton        deleteBtn;
        final int            gradeId;   // -1 if not yet saved in DB

        ComponentRow(GradeComponent component, JTextField scoreField,
                     JTextField remarksField, JButton deleteBtn, int gradeId) {
            this.component    = component;
            this.scoreField   = scoreField;
            this.remarksField = remarksField;
            this.deleteBtn    = deleteBtn;
            this.gradeId      = gradeId;
        }
    }

    private static class CourseItem {
        final int id; final String label;
        CourseItem(int id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }
}
