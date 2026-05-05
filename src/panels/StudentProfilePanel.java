package panels;

import dao.StudentDAO;
import models.Student;
import models.User;
import util.GPACalculator;
import util.ThemeManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class StudentProfilePanel extends JPanel {

    private static final int AVATAR_DISPLAY = 140;
    private static final int AVATAR_STORE   = 800;   // store at 800px for ultra-HD display
    private static final String PHOTO_DIR   = "profile_photos";
    private final User       user;
    private final StudentDAO dao = new StudentDAO();
    private Student          student;

    private JLabel     avatarLabel;   // kept for admin panel compat
    private JPanel     avatarPanel;   // custom-painted for student profile
    private BufferedImage avatarImage; // raw image — painted directly, no pre-scaling
    private File       pendingPhotoFile;
    private JTextField tfEmail, tfPhone;
    private JButton    btnSave, btnChangePass;
    private JLabel     lblStatus;
    private String     origEmail, origPhone;
    public StudentProfilePanel(User user) {
        this.user = user;
        setLayout(new BorderLayout());
        setBackground(ThemeManager.bg());
        reload();
    }
    // ── Load / reload ─────────────────────────────────────────────────────────

    private void reload() {
        removeAll();
        try { student = dao.getByUserId(user.getId()); }
        catch (Exception ex) {
            add(errLabel("Failed to load profile: " + ex.getMessage()), BorderLayout.CENTER);
            return;
        }
        if (student == null) {
            add(errLabel("No student record found."), BorderLayout.CENTER);
            return;
        }
        origEmail = nvl2(student.getUser().getEmail());
        origPhone = nvl2(student.getPhone());

        JPanel content = buildContent();
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ThemeManager.bg());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        revalidate(); repaint();
    }

    // ── Main layout: hero banner + two-column body ────────────────────────────

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(ThemeManager.bg());
        root.setBorder(new EmptyBorder(20, 20, 20, 20));

        root.add(buildHeroBanner(), BorderLayout.NORTH);

        // Two-column body
        JPanel body = new JPanel(new GridBagLayout());
        body.setBackground(ThemeManager.bg());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.insets = new Insets(0, 0, 0, 10);
        gc.gridy = 0; gc.weighty = 1;

        // Left column: avatar card + stats + actions
        gc.gridx = 0; gc.weightx = 0.32;
        body.add(buildLeftColumn(), gc);

        // Right column: info fields
        gc.gridx = 1; gc.weightx = 0.68; gc.insets = new Insets(0, 0, 0, 0);
        body.add(buildRightColumn(), gc);

        root.add(body, BorderLayout.CENTER);
        return root;
    }

    // ── Hero banner ───────────────────────────────────────────────────────────

    private JPanel buildHeroBanner() {
        Color c1 = ThemeManager.accent();
        Color c2 = ThemeManager.accentH();
        JPanel hero = new JPanel(new BorderLayout(16, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, c1, getWidth(), getHeight(), c2));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
            }
        };
        hero.setOpaque(false);
        hero.setBorder(new EmptyBorder(20, 24, 20, 24));
        hero.setPreferredSize(new Dimension(0, 90));

        String fullName = student.getFirstName() + " " + student.getLastName();
        JLabel nameLbl = new JLabel(fullName);
        nameLbl.setFont(new Font("SansSerif", Font.BOLD, 20));
        nameLbl.setForeground(Color.WHITE);

        JLabel subLbl = new JLabel(student.getStudentNo() + "  •  Student");
        subLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subLbl.setForeground(new Color(255, 255, 255, 200));

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        text.add(nameLbl);
        text.add(Box.createVerticalStrut(4));
        text.add(subLbl);

        // Enrollment year badge on the right
        String enrollYear = student.getEnrolledAt() != null
            ? new java.text.SimpleDateFormat("yyyy").format(student.getEnrolledAt())
            : "—";
        JLabel badge = new JLabel("Enrolled  " + enrollYear, SwingConstants.CENTER);
        badge.setFont(new Font("SansSerif", Font.BOLD, 13));
        badge.setForeground(Color.WHITE);
        badge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 255, 255, 80), 1),
            new EmptyBorder(6, 14, 6, 14)));

        hero.add(text,  BorderLayout.CENTER);
        hero.add(badge, BorderLayout.EAST);
        return hero;
    }

    // ── Left column ───────────────────────────────────────────────────────────

    private JPanel buildLeftColumn() {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBackground(ThemeManager.bg());

        col.add(buildAvatarCard());
        col.add(Box.createVerticalStrut(12));
        col.add(buildStatsCard());
        col.add(Box.createVerticalStrut(12));
        col.add(buildActionsCard());
        col.add(Box.createVerticalGlue());
        return col;
    }

    private JPanel buildAvatarCard() {
        JPanel card = card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        // Custom panel that paints the image directly at full quality
        avatarPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,   RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
                int s = Math.min(getWidth(), getHeight());
                int ox = (getWidth()  - s) / 2;
                int oy = (getHeight() - s) / 2;
                if (avatarImage != null) {
                    BufferedImage circle = makeCircle(avatarImage, s);
                    g2.drawImage(circle, ox, oy, null);
                } else {
                    // Initials fallback — draw directly
                    g2.setColor(new Color(0x10a37f));
                    g2.fillOval(ox, oy, s, s);
                    g2.setColor(Color.WHITE);
                    String ini = getInitials();
                    g2.setFont(new Font("SansSerif", Font.BOLD, s / 3));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(ini,
                        ox + (s - fm.stringWidth(ini)) / 2,
                        oy + (s - fm.getHeight()) / 2 + fm.getAscent());
                }
                g2.dispose();
            }
            private String getInitials() {
                String f = student != null ? student.getFirstName() : "?";
                String l = student != null ? student.getLastName()  : "";
                String ini = "";
                if (f != null && !f.isEmpty()) ini += f.charAt(0);
                if (l != null && !l.isEmpty()) ini += l.charAt(0);
                return ini.isEmpty() ? "?" : ini.toUpperCase();
            }
        };
        avatarPanel.setOpaque(false);
        avatarPanel.setPreferredSize(new Dimension(AVATAR_DISPLAY, AVATAR_DISPLAY));
        avatarPanel.setMaximumSize(new Dimension(AVATAR_DISPLAY, AVATAR_DISPLAY));
        avatarPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        avatarPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        avatarPanel.setToolTipText("Click to change photo");
        avatarPanel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { uploadPhoto(); }
        });

        refreshAvatar();

        String fullName = student.getFirstName() + " " + student.getLastName();
        JLabel nameLbl = new JLabel(fullName, SwingConstants.CENTER);
        nameLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        nameLbl.setForeground(ThemeManager.text());
        nameLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel idLbl = new JLabel(student.getStudentNo(), SwingConstants.CENTER);
        idLbl.setFont(ThemeManager.fontSmall());
        idLbl.setForeground(ThemeManager.muted());
        idLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint = new JLabel("📷 click to change", SwingConstants.CENTER);
        hint.setFont(new Font("SansSerif", Font.PLAIN, 10));
        hint.setForeground(ThemeManager.muted());
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(Box.createVerticalStrut(8));
        card.add(avatarPanel);
        card.add(Box.createVerticalStrut(10));
        card.add(nameLbl);
        card.add(Box.createVerticalStrut(3));
        card.add(idLbl);
        card.add(Box.createVerticalStrut(6));
        card.add(hint);
        card.add(Box.createVerticalStrut(8));
        return card;
    }

    private JPanel buildStatsCard() {
        // GPA
        double cgpa = -1;
        try { cgpa = util.GPACalculator.getCGPA(student.getId()); } catch (Exception ignored) {}
        String cgpaStr = cgpa >= 0 ? String.format("%.2f", cgpa) : "N/A";
        Color  cgpaCol = cgpa >= 0 ? ThemeManager.gpaColor(cgpa) : ThemeManager.muted();

        // Year level derived from enrolled_at
        String yearLevel = "—";
        if (student.getEnrolledAt() != null) {
            long ms   = System.currentTimeMillis() - student.getEnrolledAt().getTime();
            int  years = (int)(ms / (1000L * 60 * 60 * 24 * 365));
            yearLevel = "Year " + Math.min(years + 1, 5);
        }

        JPanel card = card();
        card.setLayout(new GridLayout(1, 2, 1, 0));
        card.add(miniStat("GPA",        cgpaStr,   cgpaCol));
        card.add(miniStat("Year Level", yearLevel, ThemeManager.gradeBFg()));
        return card;
    }

    private JPanel miniStat(String label, String value, Color color) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setBackground(ThemeManager.elevated());
        p.setBorder(new EmptyBorder(10, 8, 10, 8));
        JLabel v = new JLabel(value, SwingConstants.CENTER);
        v.setFont(new Font("SansSerif", Font.BOLD, 20));
        v.setForeground(color);
        JLabel l = new JLabel(label, SwingConstants.CENTER);
        l.setFont(ThemeManager.fontSmall());
        l.setForeground(ThemeManager.muted());
        p.add(v, BorderLayout.CENTER);
        p.add(l, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildActionsCard() {
        JPanel card = card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        btnSave = ThemeManager.primaryButton("Save Changes");
        btnSave.setEnabled(false);
        btnSave.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnSave.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JButton btnUpload = ThemeManager.secondaryButton("📷  Upload Photo");
        btnUpload.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnUpload.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        btnChangePass = ThemeManager.secondaryButton("🔒  Change Password");
        btnChangePass.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnChangePass.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        lblStatus = new JLabel(" ", SwingConstants.CENTER);
        lblStatus.setFont(ThemeManager.fontSmall());
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);

        btnSave.addActionListener(e      -> saveChanges());
        btnUpload.addActionListener(e    -> uploadPhoto());
        btnChangePass.addActionListener(e -> changePassword());

        card.add(btnSave);
        card.add(Box.createVerticalStrut(8));
        card.add(btnUpload);
        card.add(Box.createVerticalStrut(8));
        card.add(btnChangePass);
        card.add(Box.createVerticalStrut(10));
        card.add(lblStatus);
        return card;
    }

    // ── Right column: info fields ─────────────────────────────────────────────

    private JPanel buildRightColumn() {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBackground(ThemeManager.bg());

        col.add(buildReadOnlySection());
        col.add(Box.createVerticalStrut(12));
        col.add(buildEditableSection());
        col.add(Box.createVerticalGlue());
        return col;
    }

    private JPanel buildReadOnlySection() {
        JPanel card = card();
        card.setLayout(new BorderLayout(0, 10));

        JLabel title = sectionTitle("Personal Information");
        card.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 6, 5, 6);
        gc.fill   = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        String dob = student.getDateOfBirth() != null
            ? new java.text.SimpleDateFormat("MMM dd, yyyy").format(student.getDateOfBirth()) : "—";
        String enrolled = student.getEnrolledAt() != null
            ? new java.text.SimpleDateFormat("MMM dd, yyyy").format(student.getEnrolledAt()) : "—";

        addInfoRow(grid, gc, 0, "Full Name",    student.getFirstName() + " " + student.getLastName());
        addInfoRow(grid, gc, 1, "Student No",   student.getStudentNo());
        addInfoRow(grid, gc, 2, "Gender",       capitalize(student.getGender()));
        addInfoRow(grid, gc, 3, "Date of Birth",dob);
        addInfoRow(grid, gc, 4, "Address",      nvl(student.getAddress()));
        addInfoRow(grid, gc, 5, "Enrolled",     enrolled);

        card.add(grid, BorderLayout.CENTER);

        JLabel note = new JLabel("These fields can only be changed by an administrator.");
        note.setFont(ThemeManager.fontSmall());
        note.setForeground(ThemeManager.muted());
        card.add(note, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildEditableSection() {
        JPanel card = card();
        card.setLayout(new BorderLayout(0, 10));

        JLabel title = sectionTitle("Contact Details  (editable)");
        card.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill   = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        tfEmail = editField(origEmail);
        tfPhone = editField(origPhone);

        javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { updateSaveState(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { updateSaveState(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateSaveState(); }
        };
        tfEmail.getDocument().addDocumentListener(dl);
        tfPhone.getDocument().addDocumentListener(dl);

        gc.gridy = 0; gc.gridx = 0; gc.weightx = 0;
        grid.add(fieldLabel("Email"), gc);
        gc.gridx = 1; gc.weightx = 1;
        grid.add(tfEmail, gc);

        gc.gridy = 1; gc.gridx = 0; gc.weightx = 0;
        grid.add(fieldLabel("Phone"), gc);
        gc.gridx = 1; gc.weightx = 1;
        grid.add(tfPhone, gc);

        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private void uploadPhoto() {
        ui.PhotoEditorDialog dialog = new ui.PhotoEditorDialog(
            SwingUtilities.getWindowAncestor(this),
            PHOTO_DIR,
            "student_" + student.getId());

        // Pre-load current photo so the editor opens with it already shown
        String currentPath = pendingPhotoFile != null ? pendingPhotoFile.getPath()
                           : (student.getProfilePhoto() != null ? student.getProfilePhoto() : null);
        if (currentPath != null && new File(currentPath).exists()) {
            dialog.preloadImage(new File(currentPath));
        }

        dialog.setVisible(true);

        if (dialog.isConfirmed() && dialog.getSavedFile() != null) {
            pendingPhotoFile = dialog.getSavedFile();
            refreshAvatar();
            updateSaveState();
            setStatus("Photo ready — click Save Changes to apply.", false);
        }
    }

    private void saveChanges() {
        String newEmail = tfEmail.getText().trim();
        String newPhone = tfPhone.getText().trim();
        if (newEmail.isEmpty() || !newEmail.contains("@")) {
            setStatus("Enter a valid email.", true); return;
        }
        try {
            dao.updateProfile(student.getId(), newPhone,
                pendingPhotoFile != null ? pendingPhotoFile.getPath() : student.getProfilePhoto());
            dao.updateEmail(user.getId(), newEmail);
            origEmail = newEmail; origPhone = newPhone;
            pendingPhotoFile = null;
            student.setPhone(newPhone);
            student.getUser().setEmail(newEmail);
            btnSave.setEnabled(false);
            setStatus("Saved successfully.", false);
        } catch (Exception ex) {
            setStatus("Save failed: " + ex.getMessage(), true);
        }
    }

    private void changePassword() {
        JPasswordField cur = new JPasswordField(20);
        JPasswordField np  = new JPasswordField(20);
        JPasswordField cf  = new JPasswordField(20);
        JPanel p = new JPanel(new GridLayout(3, 2, 8, 8));
        p.add(new JLabel("Current:")); p.add(cur);
        p.add(new JLabel("New:"));     p.add(np);
        p.add(new JLabel("Confirm:")); p.add(cf);
        if (JOptionPane.showConfirmDialog(this, p, "Change Password",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String cs = new String(cur.getPassword()), ns = new String(np.getPassword()), cfs = new String(cf.getPassword());
        if (cs.isEmpty() || ns.isEmpty()) { setStatus("Passwords cannot be empty.", true); return; }
        if (!ns.equals(cfs))              { setStatus("Passwords do not match.", true); return; }
        if (ns.length() < 6)              { setStatus("Min 6 characters.", true); return; }
        try {
            if (!dao.verifyPassword(user.getId(), cs)) { setStatus("Wrong current password.", true); return; }
            dao.updatePassword(user.getId(), ns);
            setStatus("Password changed.", false);
        } catch (Exception ex) { setStatus("Failed: " + ex.getMessage(), true); }
    }

    private void updateSaveState() {
        btnSave.setEnabled(!tfEmail.getText().trim().equals(origEmail)
                        || !tfPhone.getText().trim().equals(origPhone)
                        || pendingPhotoFile != null);
    }

    // ── Avatar ────────────────────────────────────────────────────────────────

    private void refreshAvatar() {
        String path = pendingPhotoFile != null ? pendingPhotoFile.getPath()
                    : (student != null ? student.getProfilePhoto() : null);
        if (path != null && new File(path).exists()) {
            try {
                avatarImage = ImageIO.read(new File(path));
            } catch (IOException ignored) {
                avatarImage = null;
            }
        } else {
            avatarImage = null;
        }
        if (avatarPanel != null) avatarPanel.repaint();
        // Keep avatarLabel in sync for admin panel usage
        if (avatarLabel != null) {
            if (avatarImage != null) {
                avatarLabel.setIcon(new ImageIcon(makeCircle(avatarImage, AVATAR_DISPLAY)));
            } else {
                avatarLabel.setIcon(new ImageIcon(initialsAvatar(
                    student != null ? student.getFirstName() : "?",
                    student != null ? student.getLastName()  : "", AVATAR_DISPLAY)));
            }
            avatarLabel.setText(null);
        }
    }

    // ── Public static helpers (used by StudentPanel admin view too) ───────────

    /**
     * Renders src into a size×size circle using an off-screen alpha mask.
     * Includes a clean antialiased border ring drawn inside the buffer.
     */
    public static BufferedImage makeCircle(BufferedImage src, int size) {
        BufferedImage scaled = progressiveScale(src, size);

        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,   RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);

        // 1. Draw circle mask
        g2.setColor(Color.WHITE);
        g2.fillOval(0, 0, size, size);

        // 2. Composite image using SrcIn
        g2.setComposite(AlphaComposite.SrcIn);
        g2.drawImage(scaled, 0, 0, size, size, null);

        // 3. Draw border ring on top (SrcOver so it's always visible)
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(new Color(0, 0, 0, 55));   // subtle dark ring
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawOval(1, 1, size - 2, size - 2);

        g2.dispose();
        return out;
    }

    /** Progressive bilinear halving then final BICUBIC — avoids single-pass blur. */
    private static BufferedImage progressiveScale(BufferedImage src, int target) {
        // Square-crop first
        int min = Math.min(src.getWidth(), src.getHeight());
        if (src.getWidth() != src.getHeight()) {
            int ox = (src.getWidth()  - min) / 2;
            int oy = (src.getHeight() - min) / 2;
            src = src.getSubimage(ox, oy, min, min);
        }
        BufferedImage cur = src;
        int w = cur.getWidth();
        while (w > target * 2) {
            w /= 2;
            BufferedImage tmp = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = tmp.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(cur, 0, 0, w, w, null);
            g.dispose();
            cur = tmp;
        }
        // Final BICUBIC pass
        BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(cur, 0, 0, target, target, null);
        g.dispose();
        return out;
    }

    public static BufferedImage initialsAvatar(String first, String last, int size) {
        String ini = "";
        if (first != null && !first.isEmpty()) ini += first.charAt(0);
        if (last  != null && !last.isEmpty())  ini += last.charAt(0);
        if (ini.isEmpty()) ini = "?";
        ini = ini.toUpperCase();
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0x10a37f));
        g2.fillOval(0, 0, size, size);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, size / 3));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(ini, (size - fm.stringWidth(ini)) / 2,
                      (size - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
        return img;
    }

    private static BufferedImage resizeToSquare(BufferedImage src, int size) {
        int min = Math.min(src.getWidth(), src.getHeight());
        int ox  = (src.getWidth()  - min) / 2;
        int oy  = (src.getHeight() - min) / 2;
        BufferedImage cropped = src.getSubimage(ox, oy, min, min);
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(cropped, 0, 0, size, size, null);
        g2.dispose();
        return out;
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(ThemeManager.surface());
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.border()),
            new EmptyBorder(14, 16, 14, 16)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return p;
    }

    private JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.fontHeader());
        l.setForeground(ThemeManager.text());
        l.setBorder(new EmptyBorder(0, 0, 6, 0));
        return l;
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.fontBold());
        l.setForeground(ThemeManager.muted());
        l.setPreferredSize(new Dimension(90, 24));
        return l;
    }

    private void addInfoRow(JPanel p, GridBagConstraints gc, int row, String label, String value) {
        gc.gridy = row;
        gc.gridx = 0; gc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(ThemeManager.fontBold());
        lbl.setForeground(ThemeManager.muted());
        lbl.setPreferredSize(new Dimension(110, 24));
        p.add(lbl, gc);
        gc.gridx = 1; gc.weightx = 1;
        JLabel val = new JLabel(value != null ? value : "—");
        val.setFont(ThemeManager.fontBody());
        val.setForeground(ThemeManager.text());
        p.add(val, gc);
    }

    private JTextField editField(String value) {
        JTextField tf = new JTextField(value != null ? value : "");
        tf.setFont(ThemeManager.fontBody());
        tf.setBackground(ThemeManager.elevated());
        tf.setForeground(ThemeManager.text());
        tf.setCaretColor(ThemeManager.text());
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.border()),
            new EmptyBorder(5, 10, 5, 10)));
        return tf;
    }

    private void setStatus(String msg, boolean error) {
        lblStatus.setText(msg);
        lblStatus.setForeground(error ? ThemeManager.danger() : ThemeManager.SUCCESS);
    }

    private JLabel errLabel(String msg) {
        JLabel l = new JLabel(msg, SwingConstants.CENTER);
        l.setForeground(ThemeManager.danger());
        return l;
    }

    private static String nvl(String s)  { return s != null && !s.isEmpty() ? s : "—"; }
    private static String nvl2(String s) { return s != null ? s : ""; }
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "—";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
