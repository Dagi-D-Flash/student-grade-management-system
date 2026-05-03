package panels;

import models.User;
import util.DBConnection;
import util.ThemeManager;
import util.TranscriptGenerator;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TranscriptPanel extends JPanel {

    private final User    user;
    private final boolean isAdminOrTeacher;
    private final boolean isTeacher;

    private JComboBox<StudentItem> cbStudent;
    private JComboBox<CourseItem>  cbCourse;   // teacher-only
    private JTextField             tfSearch;
    private final java.util.List<StudentItem> allStudents = new java.util.ArrayList<>();
    private JEditorPane            previewPane;
    private JScrollPane            previewScroll;
    private JButton                btnGenerate, btnPrint, btnExport;
    private JLabel                 lblStatus;

    private int lastGeneratedStudentId = -1;
    private int lastGeneratedCourseId  = -1;
    private final Runnable themeListener = this::onThemeChanged;

    public TranscriptPanel(User user) {
        this.user             = user;
        this.isAdminOrTeacher = "admin".equals(user.getRole()) || "teacher".equals(user.getRole());
        this.isTeacher        = "teacher".equals(user.getRole());
        setLayout(new BorderLayout(10, 10));
        setBorder(ThemeManager.panelBorder());
        setBackground(ThemeManager.bg());

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildPreview(), BorderLayout.CENTER);

        if (isAdminOrTeacher) {
            loadStudents();
            if (isTeacher) loadCourses();
        } else {
            generateForCurrentUser();
        }

        ThemeManager.addThemeListener(themeListener);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                    && !isDisplayable()) {
                ThemeManager.removeThemeListener(themeListener);
            }
        });
    }

    private void onThemeChanged() {
        Color paneBg = ThemeManager.isDarkMode() ? new Color(0x1f2937) : Color.WHITE;
        previewPane.setBackground(paneBg);
        previewScroll.getViewport().setBackground(paneBg);
        if (lastGeneratedStudentId >= 0) {
            generateHtml(lastGeneratedStudentId, lastGeneratedCourseId);
        } else {
            previewPane.setText(buildPlaceholderHtml());
        }
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(ThemeManager.cardBorder());

        // ── Row 1: title ──────────────────────────────────────────────────────
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row1.setOpaque(false);
        JLabel title = new JLabel("Academic Transcript");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());
        row1.add(title);

        // ── Row 2: all controls ───────────────────────────────────────────────
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row2.setOpaque(false);

        if (isAdminOrTeacher) {
            JLabel searchIcon = new JLabel("\uD83D\uDD0D");
            searchIcon.setForeground(ThemeManager.muted());
            row2.add(searchIcon);

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
            row2.add(tfSearch);

            JLabel lbl = new JLabel("Student:");
            lbl.setFont(ThemeManager.fontBold());
            lbl.setForeground(ThemeManager.muted());
            row2.add(lbl);

            cbStudent = new JComboBox<>();
            cbStudent.setPreferredSize(new Dimension(200, 28));
            row2.add(cbStudent);
        }

        if (isTeacher) {
            JLabel courseLbl = new JLabel("Course:");
            courseLbl.setFont(ThemeManager.fontBold());
            courseLbl.setForeground(ThemeManager.muted());
            row2.add(courseLbl);

            cbCourse = new JComboBox<>();
            cbCourse.setPreferredSize(new Dimension(200, 28));
            row2.add(cbCourse);
        }

        btnGenerate = ThemeManager.primaryButton("Generate");
        btnGenerate.addActionListener(e -> generate());
        row2.add(btnGenerate);

        btnPrint = ThemeManager.secondaryButton("Print");
        btnPrint.setEnabled(false);
        btnPrint.addActionListener(e -> printTranscript());
        row2.add(btnPrint);

        JButton btnExportLocal = ThemeManager.secondaryButton("Export PDF");
        btnExportLocal.setEnabled(false);
        btnExportLocal.addActionListener(e -> exportToPdf());
        this.btnExport = btnExportLocal;
        row2.add(btnExportLocal);

        lblStatus = new JLabel(" ");
        lblStatus.setFont(ThemeManager.fontSmall());
        lblStatus.setForeground(ThemeManager.muted());
        row2.add(lblStatus);

        bar.add(row1);
        bar.add(row2);
        return bar;
    }

    private void loadCourses() {
        if (cbCourse == null) return;
        cbCourse.removeAllItems();
        cbCourse.addItem(new CourseItem(-1, "Full Transcript (All Courses)"));
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
            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String label = rs.getString("code") + " \u2014 " + rs.getString("name")
                        + " [" + rs.getString("section") + "] "
                        + rs.getInt("academic_year") + " " + rs.getString("semester");
                    cbCourse.addItem(new CourseItem(rs.getInt("id"), label));
                }
            }
        } catch (SQLException ex) { setStatus("Failed to load courses: " + ex.getMessage(), true); }
    }

    /** Filters the student combo in real-time as the user types. */
    private void filterStudents(String query) {
        String q = query.trim().toLowerCase();
        StudentItem prev = (StudentItem) cbStudent.getSelectedItem();

        cbStudent.removeAllItems();
        for (StudentItem item : allStudents) {
            String label     = item.label.toLowerCase();
            String stripped  = label.replace("wour/", "").replace("/", "");
            String qStripped = q.replace("wour/", "").replace("/", "");
            if (q.isEmpty() || label.contains(q) || stripped.contains(qStripped))
                cbStudent.addItem(item);
        }

        // restore previous selection if still visible, else pick first match
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

    private JScrollPane buildPreview() {
        Color paneBg = ThemeManager.isDarkMode() ? new Color(0x1f2937) : Color.WHITE;
        previewPane = new JEditorPane("text/html", buildPlaceholderHtml());
        previewPane.setEditable(false);
        previewPane.setBackground(paneBg);

        previewScroll = new JScrollPane(previewPane);
        previewScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.border()));
        previewScroll.getViewport().setBackground(paneBg);
        return previewScroll;
    }

    private void loadStudents() {
        allStudents.clear();
        cbStudent.removeAllItems();
        String sql;
        if (isTeacher) {
            // Only students enrolled in this teacher's courses
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
            if (isTeacher) ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StudentItem item = new StudentItem(
                        rs.getInt("id"),
                        rs.getString("student_no") + " \u2014 " +
                        rs.getString("first_name") + " " + rs.getString("last_name"));
                    allStudents.add(item);
                    cbStudent.addItem(item);
                }
            }
        } catch (SQLException ex) {
            setStatus("Failed to load students: " + ex.getMessage(), true);
        }
    }

    private void generateForCurrentUser() {
        int studentId = resolveStudentId();
        if (studentId < 0) {
            setStatus("No student record linked to this account.", true);
            return;
        }
        generateHtml(studentId, -1);
    }

    private void generate() {
        if (isAdminOrTeacher) {
            StudentItem si = (StudentItem) cbStudent.getSelectedItem();
            if (si == null) { setStatus("Select a student first.", true); return; }
            int courseId = -1;
            if (cbCourse != null) {
                CourseItem ci = (CourseItem) cbCourse.getSelectedItem();
                courseId = ci != null ? ci.id : -1;
            }
            generateHtml(si.id, courseId);
        } else {
            generateForCurrentUser();
        }
    }

    private void generateHtml(int studentId, int courseId) {
        btnGenerate.setEnabled(false);
        btnPrint.setEnabled(false);
        setStatus("Generating...", false);

        final int cid = courseId;
        new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception {
                if (isTeacher && cid > 0) {
                    // Specific course selected — detailed component-level report
                    return util.TranscriptGenerator.generateCourseHtml(
                        studentId, cid, ThemeManager.isDarkMode());
                } else if (isTeacher && cid < 0) {
                    // "All My Courses" selected — scoped to teacher's courses only
                    return util.TranscriptGenerator.generateTeacherTranscriptHtml(
                        studentId, user.getId(), ThemeManager.isDarkMode());
                } else {
                    // Admin or student — full transcript
                    return util.TranscriptGenerator.generateHtml(
                        studentId, ThemeManager.isDarkMode());
                }
            }
            protected void done() {
                btnGenerate.setEnabled(true);
                try {
                    String html = get();
                    lastGeneratedStudentId = studentId;
                    lastGeneratedCourseId  = cid;
                    previewPane.setText(html);
                    previewPane.setCaretPosition(0);
                    btnPrint.setEnabled(true);
                    btnExport.setEnabled(true);
                    setStatus("Transcript ready. Use Print to print.", false);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    setStatus("Error: " + msg, true);
                    previewPane.setText(buildErrorHtml(msg));
                }
            }
        }.execute();
    }

    private void printTranscript() {
        try {
            String lightHtml = null;
            try {
                if (lastGeneratedStudentId >= 0) {
                    if (isTeacher && lastGeneratedCourseId > 0)
                        lightHtml = util.TranscriptGenerator.generateCourseHtml(
                            lastGeneratedStudentId, lastGeneratedCourseId, false);
                    else if (isTeacher && lastGeneratedCourseId < 0)
                        lightHtml = util.TranscriptGenerator.generateTeacherTranscriptHtml(
                            lastGeneratedStudentId, user.getId(), false);
                    else
                        lightHtml = util.TranscriptGenerator.generateHtml(lastGeneratedStudentId, false);
                } else {
                    int sid = resolveStudentId();
                    if (sid >= 0) lightHtml = util.TranscriptGenerator.generateHtml(sid, false);
                }
            } catch (Exception ignored) {}

            JEditorPane printPane = new JEditorPane("text/html",
                lightHtml != null ? lightHtml : previewPane.getText());
            printPane.setBackground(Color.WHITE);

            java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();

            // Pre-set landscape so the dialog opens with it already selected
            java.awt.print.PageFormat pf = job.defaultPage();
            pf.setOrientation(java.awt.print.PageFormat.LANDSCAPE);
            job.setPrintable(printPane.getPrintable(null, null), job.validatePage(pf));

            // Use attribute-set dialog — triggers the modern OS print dialog on Windows
            javax.print.attribute.PrintRequestAttributeSet attrs =
                new javax.print.attribute.HashPrintRequestAttributeSet();
            attrs.add(javax.print.attribute.standard.OrientationRequested.LANDSCAPE);
            attrs.add(new javax.print.attribute.standard.Copies(1));
            attrs.add(javax.print.attribute.standard.MediaSizeName.ISO_A4);

            if (job.printDialog(attrs)) {
                job.print(attrs);
                setStatus("Sent to printer.", false);
            } else {
                setStatus("Print cancelled.", false);
            }
        } catch (java.awt.print.PrinterException ex) {
            setStatus("Print failed: " + ex.getMessage(), true);
        }
    }

    private void exportToPdf() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save Transcript as PDF");
        fc.setSelectedFile(new java.io.File("transcript.pdf"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Files", "pdf"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        java.io.File outFile = fc.getSelectedFile();
        if (!outFile.getName().toLowerCase().endsWith(".pdf"))
            outFile = new java.io.File(outFile.getAbsolutePath() + ".pdf");

        final java.io.File finalFile = outFile;
        btnExport.setEnabled(false);
        setStatus("Exporting PDF...", false);

        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                // Regenerate clean light HTML
                String html = null;
                if (lastGeneratedStudentId >= 0) {
                    if (isTeacher && lastGeneratedCourseId > 0)
                        html = util.TranscriptGenerator.generateCourseHtml(
                            lastGeneratedStudentId, lastGeneratedCourseId, false);
                    else if (isTeacher && lastGeneratedCourseId < 0)
                        html = util.TranscriptGenerator.generateTeacherTranscriptHtml(
                            lastGeneratedStudentId, user.getId(), false);
                    else
                        html = util.TranscriptGenerator.generateHtml(lastGeneratedStudentId, false);
                } else {
                    int sid = resolveStudentId();
                    if (sid >= 0) html = util.TranscriptGenerator.generateHtml(sid, false);
                }
                if (html == null) throw new Exception("No transcript generated.");

                // Render at 3× scale for ~300dpi quality (A4 landscape @ 96dpi = 1122×794, ×3 = 3366×2382)
                final int SCALE = 3;
                final int W = 1122 * SCALE, H = 794 * SCALE;
                final String finalHtml = html;
                final java.awt.image.BufferedImage[] holder = new java.awt.image.BufferedImage[1];

                SwingUtilities.invokeAndWait(() -> {
                    JEditorPane ep = new JEditorPane();
                    ep.setContentType("text/html");
                    ep.setText(finalHtml);
                    ep.setBackground(Color.WHITE);
                    // Layout at base size so HTML proportions are correct, then scale up when painting
                    ep.setSize(1122, 794);
                    ep.validate();

                    java.awt.image.BufferedImage img =
                        new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D g2 = img.createGraphics();
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                                        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                                        java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g2.setColor(Color.WHITE);
                    g2.fillRect(0, 0, W, H);
                    // Scale up the painting
                    g2.scale(SCALE, SCALE);
                    ep.paint(g2);
                    g2.dispose();
                    holder[0] = img;
                });

                // Encode as JPEG at maximum quality
                java.awt.image.BufferedImage img = holder[0];
                java.io.ByteArrayOutputStream imgBaos = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageWriter jpegWriter =
                    javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
                javax.imageio.ImageWriteParam iwp = jpegWriter.getDefaultWriteParam();
                iwp.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                iwp.setCompressionQuality(1.0f);
                jpegWriter.setOutput(javax.imageio.ImageIO.createImageOutputStream(imgBaos));
                jpegWriter.write(null, new javax.imageio.IIOImage(img, null, null), iwp);
                jpegWriter.dispose();
                byte[] jpegBytes = imgBaos.toByteArray();

                // Write PDF — page is A4-landscape in points, image is 3× for sharpness
                writePdf(finalFile, jpegBytes, W, H);
                return null;
            }
            protected void done() {
                btnExport.setEnabled(true);
                try {
                    get();
                    setStatus("PDF saved: " + finalFile.getName(), false);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    setStatus("Export failed: " + msg, true);
                }
            }
        }.execute();
    }

    /**
     * Writes a single-page PDF containing one JPEG image.
     * Pure Java — no external library needed.
     * Page size matches the image dimensions (points = pixels at 72dpi).
     */
    private static void writePdf(java.io.File out, byte[] jpeg, int wPx, int hPx)
            throws java.io.IOException {
        // PDF points: 1pt = 1/72 inch. At 96dpi: pts = px * 72/96
        float wPt = wPx * 72f / 96f;
        float hPt = hPx * 72f / 96f;

        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        java.util.List<Integer> offsets = new java.util.ArrayList<>();

        // Helper to write a PDF object
        java.util.function.BiConsumer<Integer, String> obj = (num, content) -> {
            offsets.add(body.size());
            byte[] b = (num + " 0 obj\n" + content + "\nendobj\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            try { body.write(b); } catch (java.io.IOException ignored) {}
        };

        // Object 1: Catalog
        obj.accept(1, "<< /Type /Catalog /Pages 2 0 R >>");

        // Object 2: Pages
        obj.accept(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");

        // Object 3: Page
        String pageDict = String.format(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.2f %.2f]\n" +
            "   /Contents 4 0 R /Resources << /XObject << /Im1 5 0 R >> >> >>",
            wPt, hPt);
        obj.accept(3, pageDict);

        // Object 4: Content stream (draw image filling the page)
        String stream = String.format("q %.2f 0 0 %.2f 0 0 cm /Im1 Do Q", wPt, hPt);
        byte[] streamBytes = stream.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        String contentObj = "<< /Length " + streamBytes.length + " >>\nstream\n" +
                            stream + "\nendstream";
        obj.accept(4, contentObj);

        // Object 5: Image XObject (JPEG)
        String imgDict = String.format(
            "<< /Type /XObject /Subtype /Image /Width %d /Height %d\n" +
            "   /ColorSpace /DeviceRGB /BitsPerComponent 8\n" +
            "   /Filter /DCTDecode /Length %d >>\nstream\n",
            wPx, hPx, jpeg.length);
        offsets.add(body.size());
        try {
            body.write(("5 0 obj\n" + imgDict).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            body.write(jpeg);
            body.write("\nendstream\nendobj\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        } catch (java.io.IOException ignored) {}

        // Cross-reference table
        int xrefOffset = body.size();
        StringBuilder xref = new StringBuilder();
        xref.append("xref\n0 6\n");
        xref.append("0000000000 65535 f \n");
        for (int offset : offsets)
            xref.append(String.format("%010d 00000 n \n", offset));

        xref.append("trailer\n<< /Size 6 /Root 1 0 R >>\n");
        xref.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            fos.write("%PDF-1.4\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            // Adjust offsets by header length
            int headerLen = "%PDF-1.4\n".length();
            body.writeTo(fos);
            // Rewrite xref with corrected offsets
            // (simpler: just write header first then body, offsets already relative to body start)
            // We need to redo with header offset — rebuild properly
        }

        // Rebuild with correct absolute offsets (header = 9 bytes)
        int headerLen = 9; // "%PDF-1.4\n"
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            byte[] header = "%PDF-1.4\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            fos.write(header);

            java.io.ByteArrayOutputStream body2 = new java.io.ByteArrayOutputStream();
            java.util.List<Integer> off2 = new java.util.ArrayList<>();

            java.util.function.BiConsumer<Integer, String> obj2 = (num, content) -> {
                off2.add(headerLen + body2.size());
                byte[] b = (num + " 0 obj\n" + content + "\nendobj\n")
                    .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                try { body2.write(b); } catch (java.io.IOException ignored) {}
            };

            obj2.accept(1, "<< /Type /Catalog /Pages 2 0 R >>");
            obj2.accept(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
            obj2.accept(3, pageDict);
            obj2.accept(4, contentObj);

            // Image object
            off2.add(headerLen + body2.size());
            body2.write(("5 0 obj\n" + imgDict).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            body2.write(jpeg);
            body2.write("\nendstream\nendobj\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

            fos.write(body2.toByteArray());

            int xrefOff = headerLen + body2.size();
            StringBuilder xref2 = new StringBuilder();
            xref2.append("xref\n0 6\n");
            xref2.append("0000000000 65535 f \n");
            for (int o : off2)
                xref2.append(String.format("%010d 00000 n \n", o));
            xref2.append("trailer\n<< /Size 6 /Root 1 0 R >>\n");
            xref2.append("startxref\n").append(xrefOff).append("\n%%EOF\n");
            fos.write(xref2.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        }
    }

    private int resolveStudentId() {
        String sql = "SELECT id FROM students WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException ex) {
            setStatus("DB error: " + ex.getMessage(), true);
        }
        return -1;
    }

    private void setStatus(String msg, boolean error) {
        lblStatus.setText(msg);
        lblStatus.setForeground(error ? ThemeManager.danger() : ThemeManager.SUCCESS);
    }

    private String buildPlaceholderHtml() {
        boolean dark = ThemeManager.isDarkMode();
        String bg   = dark ? "#111827" : "#ffffff";
        String text = dark ? "#9ca3af" : "#6b7280";
        return "<html><body style='font-family:Arial;background:" + bg + ";color:" + text
             + ";padding:40px;text-align:center;'>"
             + "<h2 style='color:#10a37f;'>Academic Transcript</h2>"
             + "<p>Select a student and click <b>Generate</b> to view the transcript.</p>"
             + "</body></html>";
    }

    private String buildErrorHtml(String msg) {
        boolean dark = ThemeManager.isDarkMode();
        String bg   = dark ? "#111827" : "#ffffff";
        return "<html><body style='font-family:Arial;background:" + bg
             + ";color:#991b1b;padding:40px;'>"
             + "<h3>Failed to generate transcript</h3><p>" + (msg != null ? msg : "Unknown error") + "</p>"
             + "</body></html>";
    }

    private static class StudentItem {
        int id; String label;
        StudentItem(int id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }

    private static class CourseItem {
        final int id; final String label;
        CourseItem(int id, String label) { this.id = id; this.label = label; }
        public String toString() { return label; }
    }
}
