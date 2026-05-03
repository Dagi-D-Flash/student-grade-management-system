package panels;

import dao.EnrollmentDAO;
import dao.GradeDAO;
import models.Enrollment;
import models.Grade;
import models.User;
import util.DBConnection;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
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
    
    // Bulk operations and auto-save components
    private JButton btnBulkSave, btnImportCSV, btnExportCSV, btnApplyToAll;
    private JCheckBox chkAutoSave;
    private Timer autoSaveTimer;
    private boolean hasUnsavedChanges = false;

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
        initializeAutoSave();
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
        
        // Add bulk operations buttons
        btnBulkSave = styledBtn("Bulk Save", new Color(255, 193, 7));
        btnBulkSave.setForeground(Color.BLACK);
        btnImportCSV = styledBtn("Import CSV", new Color(111, 66, 193));
        btnExportCSV = styledBtn("Export CSV", new Color(32, 201, 151));
        btnApplyToAll = styledBtn("Apply to All", new Color(253, 126, 20));
        btnApplyToAll.setForeground(Color.BLACK);
        
        btnBulkSave.addActionListener(e -> bulkSaveGrades());
        btnImportCSV.addActionListener(e -> importGradesFromCSV());
        btnExportCSV.addActionListener(e -> exportGradesToCSV());
        btnApplyToAll.addActionListener(e -> applyGradeToAllStudents());
        
        bar.add(Box.createHorizontalStrut(20));
        bar.add(btnBulkSave);
        bar.add(btnImportCSV);
        bar.add(btnExportCSV);
        bar.add(btnApplyToAll);
        
        // Auto-save checkbox
        chkAutoSave = new JCheckBox("Auto-save");
        chkAutoSave.setBackground(Color.WHITE);
        chkAutoSave.addActionListener(e -> toggleAutoSave());
        bar.add(Box.createHorizontalStrut(20));
        bar.add(chkAutoSave);
        
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
        
        // Add auto-save listeners
        tfScore.getDocument().addDocumentListener(new SimpleDocListener(this::markUnsavedChanges));
        tfMaxScore.getDocument().addDocumentListener(new SimpleDocListener(this::markUnsavedChanges));
        tfWeight.getDocument().addDocumentListener(new SimpleDocListener(this::markUnsavedChanges));
        tfRemarks.getDocument().addDocumentListener(new SimpleDocListener(this::markUnsavedChanges));

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
        hasUnsavedChanges = false;
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

    // ========== BULK OPERATIONS AND AUTO-SAVE METHODS ==========
    
    private void initializeAutoSave() {
        autoSaveTimer = new Timer(5000, e -> { // Auto-save every 5 seconds
            if (hasUnsavedChanges && chkAutoSave.isSelected()) {
                autoSaveGrade();
            }
        });
        autoSaveTimer.setRepeats(true);
    }
    
    private void toggleAutoSave() {
        if (chkAutoSave.isSelected()) {
            autoSaveTimer.start();
            JOptionPane.showMessageDialog(this, "Auto-save enabled (every 5 seconds)", "Auto-save", JOptionPane.INFORMATION_MESSAGE);
        } else {
            autoSaveTimer.stop();
        }
    }
    
    private void markUnsavedChanges() {
        hasUnsavedChanges = true;
    }
    
    private void autoSaveGrade() {
        if (selectedEnrollmentId < 0 || !validateGradeInput()) {
            return;
        }
        
        try {
            Grade g = buildGradeFromForm();
            Enrollment e = new Enrollment(); 
            e.setId(selectedEnrollmentId);
            g.setEnrollment(e);
            
            if (selectedGradeId > 0) {
                g.setId(selectedGradeId);
                gradeDAO.update(g);
            } else {
                gradeDAO.insert(g);
            }
            
            hasUnsavedChanges = false;
            loadGrades();
            
            // Show brief auto-save indicator
            JLabel autoSaveLabel = new JLabel("Auto-saved ✓");
            autoSaveLabel.setForeground(new Color(25, 135, 84));
            autoSaveLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));
            
        } catch (Exception ex) {
            // Silent fail for auto-save to avoid interrupting user
            System.err.println("Auto-save failed: " + ex.getMessage());
        }
    }
    
    private boolean validateGradeInput() {
        String scoreText = tfScore.getText().trim();
        String maxScoreText = tfMaxScore.getText().trim();
        String weightText = tfWeight.getText().trim();
        
        if (scoreText.isEmpty() || maxScoreText.isEmpty() || weightText.isEmpty()) {
            return false;
        }
        
        try {
            double score = Double.parseDouble(scoreText);
            double maxScore = Double.parseDouble(maxScoreText);
            double weight = Double.parseDouble(weightText);
            
            return score >= 0 && maxScore > 0 && weight >= 0 && weight <= 1.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private void bulkSaveGrades() {
        if (selectedEnrollmentId < 0) {
            JOptionPane.showMessageDialog(this, "Select a student first.", "Bulk Save", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String input = JOptionPane.showInputDialog(this, 
            "Enter grades in format: type,score,maxScore,weight,remarks (one per line)\n" +
            "Example:\nquiz,85,100,0.1,Good work\nassignment,92,100,0.2,Excellent", 
            "Bulk Grade Entry", JOptionPane.PLAIN_MESSAGE);
            
        if (input == null || input.trim().isEmpty()) return;
        
        String[] lines = input.trim().split("\n");
        List<Grade> gradesToSave = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            
            String[] parts = line.split(",");
            if (parts.length < 4) {
                errors.add("Line " + (i + 1) + ": Invalid format");
                continue;
            }
            
            try {
                Grade g = new Grade();
                g.setGradeType(parts[0].trim());
                g.setScore(Double.parseDouble(parts[1].trim()));
                g.setMaxScore(Double.parseDouble(parts[2].trim()));
                g.setWeight(Double.parseDouble(parts[3].trim()));
                g.setRemarks(parts.length > 4 ? parts[4].trim() : "");
                
                Enrollment e = new Enrollment();
                e.setId(selectedEnrollmentId);
                g.setEnrollment(e);
                
                gradesToSave.add(g);
            } catch (NumberFormatException ex) {
                errors.add("Line " + (i + 1) + ": Invalid number format");
            }
        }
        
        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Errors found:\n" + String.join("\n", errors), 
                "Bulk Save Errors", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            for (Grade g : gradesToSave) {
                gradeDAO.insert(g);
            }
            JOptionPane.showMessageDialog(this, "Successfully saved " + gradesToSave.size() + " grades!", 
                "Bulk Save Complete", JOptionPane.INFORMATION_MESSAGE);
            loadGrades();
            clearForm();
        } catch (Exception ex) {
            showError(ex);
        }
    }
    
    private void importGradesFromCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
        
        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File file = fileChooser.getSelectedFile();
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNum = 0;
            
            // Skip header line
            br.readLine();
            lineNum++;
            
            while ((line = br.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split(",");
                if (parts.length < 6) {
                    errors.add("Line " + lineNum + ": Missing columns");
                    continue;
                }
                
                try {
                    String studentNo = parts[0].trim();
                    String gradeType = parts[1].trim();
                    double score = Double.parseDouble(parts[2].trim());
                    double maxScore = Double.parseDouble(parts[3].trim());
                    double weight = Double.parseDouble(parts[4].trim());
                    String remarks = parts[5].trim();
                    
                    // Find enrollment ID by student number
                    int enrollmentId = findEnrollmentByStudentNo(studentNo);
                    if (enrollmentId < 0) {
                        errors.add("Line " + lineNum + ": Student not found: " + studentNo);
                        continue;
                    }
                    
                    Grade g = new Grade();
                    g.setGradeType(gradeType);
                    g.setScore(score);
                    g.setMaxScore(maxScore);
                    g.setWeight(weight);
                    g.setRemarks(remarks);
                    
                    Enrollment e = new Enrollment();
                    e.setId(enrollmentId);
                    g.setEnrollment(e);
                    
                    gradeDAO.insert(g);
                    successCount++;
                    
                } catch (NumberFormatException ex) {
                    errors.add("Line " + lineNum + ": Invalid number format");
                } catch (Exception ex) {
                    errors.add("Line " + lineNum + ": " + ex.getMessage());
                }
            }
            
            String message = "Import complete!\nSuccessfully imported: " + successCount + " grades";
            if (!errors.isEmpty()) {
                message += "\nErrors: " + errors.size() + " (see details below)\n\n" + 
                          String.join("\n", errors.subList(0, Math.min(errors.size(), 10)));
                if (errors.size() > 10) {
                    message += "\n... and " + (errors.size() - 10) + " more errors";
                }
            }
            
            JOptionPane.showMessageDialog(this, message, "Import Results", 
                errors.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            
            loadGrades();
            
        } catch (IOException ex) {
            showError(ex);
        }
    }
    
    private void exportGradesToCSV() {
        CourseItem ci = (CourseItem) cbCourse.getSelectedItem();
        if (ci == null) {
            JOptionPane.showMessageDialog(this, "Select a course first.", "Export", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
        fileChooser.setSelectedFile(new File("grades_export.csv"));
        
        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File file = fileChooser.getSelectedFile();
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Student No,Student Name,Grade Type,Score,Max Score,Weight,Weighted %,Letter Grade,Remarks");
            
            String sql = "SELECT s.student_no, s.first_name, s.last_name, g.grade_type, " +
                        "g.score, g.max_score, g.weight, g.remarks " +
                        "FROM enrollments e " +
                        "JOIN students s ON s.id = e.student_id " +
                        "LEFT JOIN grades g ON g.enrollment_id = e.id " +
                        "WHERE e.course_id = ? " +
                        "ORDER BY s.student_no, g.grade_type";
                        
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, ci.id);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String studentNo = rs.getString("student_no");
                        String studentName = rs.getString("first_name") + " " + rs.getString("last_name");
                        String gradeType = rs.getString("grade_type");
                        
                        if (gradeType != null) { // Has grades
                            double score = rs.getDouble("score");
                            double maxScore = rs.getDouble("max_score");
                            double weight = rs.getDouble("weight");
                            String remarks = rs.getString("remarks");
                            
                            double weighted = maxScore > 0 ? (score / maxScore) * 100 * weight : 0;
                            double pct = maxScore > 0 ? (score / maxScore) * 100 : 0;
                            String letterGrade = toLetterGrade(pct);
                            
                            pw.printf("%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s,%s%n",
                                studentNo, studentName, gradeType, score, maxScore, weight, 
                                weighted, letterGrade, remarks != null ? remarks : "");
                        } else { // No grades yet
                            pw.printf("%s,%s,,,,,,,,%n", studentNo, studentName);
                        }
                    }
                }
            }
            
            JOptionPane.showMessageDialog(this, "Grades exported successfully to:\n" + file.getAbsolutePath(), 
                "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                
        } catch (Exception ex) {
            showError(ex);
        }
    }
    
    private void applyGradeToAllStudents() {
        if (!validateGradeInput()) {
            JOptionPane.showMessageDialog(this, "Please fill in valid grade information first.", 
                "Apply to All", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        CourseItem ci = (CourseItem) cbCourse.getSelectedItem();
        if (ci == null) return;
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Apply current grade to ALL students in this course?\n" +
            "Type: " + cbGradeType.getSelectedItem() + "\n" +
            "Score: " + tfScore.getText() + "/" + tfMaxScore.getText() + "\n" +
            "Weight: " + tfWeight.getText(), 
            "Confirm Apply to All", JOptionPane.YES_NO_OPTION);
            
        if (confirm != JOptionPane.YES_OPTION) return;
        
        try {
            String sql = "SELECT id FROM enrollments WHERE course_id = ?";
            List<Integer> enrollmentIds = new ArrayList<>();
            
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, ci.id);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        enrollmentIds.add(rs.getInt("id"));
                    }
                }
            }
            
            int successCount = 0;
            for (int enrollmentId : enrollmentIds) {
                try {
                    Grade g = buildGradeFromForm();
                    Enrollment e = new Enrollment();
                    e.setId(enrollmentId);
                    g.setEnrollment(e);
                    
                    gradeDAO.insert(g);
                    successCount++;
                } catch (Exception ex) {
                    System.err.println("Failed to save grade for enrollment " + enrollmentId + ": " + ex.getMessage());
                }
            }
            
            JOptionPane.showMessageDialog(this, 
                "Applied grade to " + successCount + " out of " + enrollmentIds.size() + " students.", 
                "Apply Complete", JOptionPane.INFORMATION_MESSAGE);
            
            loadGrades();
            clearForm();
            
        } catch (Exception ex) {
            showError(ex);
        }
    }
    
    private int findEnrollmentByStudentNo(String studentNo) {
        CourseItem ci = (CourseItem) cbCourse.getSelectedItem();
        if (ci == null) return -1;
        
        String sql = "SELECT e.id FROM enrollments e " +
                    "JOIN students s ON s.id = e.student_id " +
                    "WHERE e.course_id = ? AND s.student_no = ?";
                    
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ci.id);
            ps.setString(2, studentNo);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException ex) {
            System.err.println("Error finding enrollment: " + ex.getMessage());
        }
        
        return -1;
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
