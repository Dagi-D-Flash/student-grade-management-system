package panels;

import models.User;
import util.DBConnection;
import util.TranscriptGenerator;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TranscriptPanel extends JPanel {

    private final User user;
    private final boolean isAdmin;

    private JComboBox<StudentItem> cbStudent;
    private JTextField tfOutputPath;
    private JButton btnBrowse, btnGenerate;
    private JTextArea logArea;

    public TranscriptPanel(User user) {
        this.user    = user;
        this.isAdmin = "admin".equals(user.getRole()) || "teacher".equals(user.getRole());
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 246, 250));

        add(buildHeader(),  BorderLayout.NORTH);
        add(buildForm(),    BorderLayout.CENTER);
        add(buildLog(),     BorderLayout.SOUTH);

        if (isAdmin) loadStudents();
        else         prefillStudent();
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220)),
            BorderFactory.createEmptyBorder(14, 18, 14, 18)
        ));
        JLabel title = new JLabel("Generate PDF Transcript");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        JLabel sub = new JLabel("Exports a full academic transcript including grades, GPA, and rank.");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sub.setForeground(new Color(100, 100, 100));
        JPanel inner = new JPanel(new GridLayout(2, 1, 0, 2));
        inner.setOpaque(false);
        inner.add(title); inner.add(sub);
        p.add(inner, BorderLayout.WEST);
        return p;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220)),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 6, 8, 6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        int row = 0;

        if (isAdmin) {
            g.gridx = 0; g.gridy = row; g.weightx = 0;
            form.add(label("Student:"), g);
            cbStudent = new JComboBox<>();
            cbStudent.setPreferredSize(new Dimension(320, 30));
            g.gridx = 1; g.weightx = 1; g.gridwidth = 2;
            form.add(cbStudent, g);
            g.gridwidth = 1;
            row++;
        }

        g.gridx = 0; g.gridy = row; g.weightx = 0;
        form.add(label("Save to:"), g);

        tfOutputPath = new JTextField(System.getProperty("user.home") + File.separator + "transcript.pdf");
        g.gridx = 1; g.weightx = 1;
        form.add(tfOutputPath, g);

        btnBrowse = new JButton("Browse...");
        btnBrowse.setFocusPainted(false);
        btnBrowse.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        g.gridx = 2; g.weightx = 0;
        form.add(btnBrowse, g);
        row++;

        btnGenerate = new JButton("Generate PDF Transcript");
        btnGenerate.setBackground(new Color(13, 110, 253));
        btnGenerate.setForeground(Color.WHITE);
        btnGenerate.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnGenerate.setFocusPainted(false);
        btnGenerate.setBorderPainted(false);
        btnGenerate.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnGenerate.setPreferredSize(new Dimension(240, 36));
        g.gridx = 0; g.gridy = row; g.gridwidth = 3; g.weightx = 0;
        g.anchor = GridBagConstraints.CENTER;
        form.add(btnGenerate, g);

        btnBrowse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File(tfOutputPath.getText()));
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Files", "pdf"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                String path = fc.getSelectedFile().getAbsolutePath();
                if (!path.toLowerCase().endsWith(".pdf")) path += ".pdf";
                tfOutputPath.setText(path);
            }
        });

        btnGenerate.addActionListener(e -> generatePDF());

        return form;
    }

    private JPanel buildLog() {
        logArea = new JTextArea(6, 0);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(33, 37, 43));
        logArea.setForeground(new Color(200, 255, 200));
        logArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Log"));
        p.add(new JScrollPane(logArea));
        return p;
    }

    private void generatePDF() {
        String path = tfOutputPath.getText().trim();
        if (path.isEmpty()) {
            log("ERROR: Please specify an output file path.");
            return;
        }

        int studentId = resolveStudentId();
        if (studentId < 0) {
            log("ERROR: Could not resolve student. Please select a valid student.");
            return;
        }

        btnGenerate.setEnabled(false);
        log("Generating transcript for student ID: " + studentId + " ...");

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                TranscriptGenerator.generate(studentId, path);
                return null;
            }
            @Override
            protected void done() {
                btnGenerate.setEnabled(true);
                try {
                    get();
                    log("SUCCESS: Transcript saved to: " + path);
                    int open = JOptionPane.showConfirmDialog(TranscriptPanel.this,
                        "Transcript generated successfully.\nOpen file location?",
                        "Done", JOptionPane.YES_NO_OPTION);
                    if (open == JOptionPane.YES_OPTION) {
                        try { Desktop.getDesktop().open(new File(path).getParentFile()); }
                        catch (Exception ex) { log("Could not open folder: " + ex.getMessage()); }
                    }
                } catch (Exception ex) {
                    log("ERROR: " + ex.getCause().getMessage());
                }
            }
        }.execute();
    }

    private int resolveStudentId() {
        if (isAdmin) {
            StudentItem si = (StudentItem) cbStudent.getSelectedItem();
            return si != null ? si.id : -1;
        }
        String sql = "SELECT id FROM students WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException ex) { log("DB error: " + ex.getMessage()); }
        return -1;
    }

    private void loadStudents() {
        cbStudent.removeAllItems();
        String sql = "SELECT id, student_no, first_name, last_name FROM students ORDER BY first_name";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cbStudent.addItem(new StudentItem(rs.getInt("id"),
                    rs.getString("student_no") + " - " +
                    rs.getString("first_name") + " " + rs.getString("last_name")));
            }
        } catch (SQLException ex) { log("DB error: " + ex.getMessage()); }
    }

    private void prefillStudent() {
        log("Ready to generate transcript for: " + user.getUsername());
    }

    private void log(String msg) {
        logArea.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + "] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        return l;
    }

    private static class StudentItem {
        int id; String label;
        StudentItem(int id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }
}
