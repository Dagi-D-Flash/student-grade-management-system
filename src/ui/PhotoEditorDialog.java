package ui;

import util.ThemeManager;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Modal dialog for selecting, previewing, and editing a profile photo
 * before saving. Follows strict single-pass processing:
 * 1. Load original (unchanged)
 * 2. Show preview
 * 3. User adjusts size + crop
 * 4. ONE final resize/crop pass on the original
 * 5. Save to disk at high quality
 */
public class PhotoEditorDialog extends JDialog {

    // ── Result ────────────────────────────────────────────────────────────────
    private File savedFile; // non-null if user confirmed
    private boolean confirmed = false;

    // ── State ─────────────────────────────────────────────────────────────────
    private BufferedImage original; // never modified
    private File sourceFile;

    // ── UI ────────────────────────────────────────────────────────────────────
    private JLabel previewLabel;
    private JPanel previewCircle; // custom-painted, no ImageIcon
    private BufferedImage previewImage; // raw processed image for direct painting
    private JSlider sizeSlider;
    private JSpinner sizeSpinner;
    private JLabel lblDimensions;
    private JButton btnChoose, btnApply, btnCancel;
    private JLabel lblStatus;

    // Crop drag state
    private int cropX, cropY, cropSize; // in original-image coordinates
    private boolean hasCrop = false;
    private Point dragStart;
    private CropOverlayPanel cropPanel;

    private static final int MIN_SIZE = 100;
    private static final int MAX_SIZE = 800;
    private static final int DEF_SIZE = 400;

    private final String outputDir;
    private final String filePrefix;

    public PhotoEditorDialog(Window owner, String outputDir, String filePrefix) {
        super(owner, "Edit Profile Photo", ModalityType.APPLICATION_MODAL);
        this.outputDir = outputDir;
        this.filePrefix = filePrefix;
        buildUI();
        pack();
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(owner);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isConfirmed() {
        return confirmed;
    }

    public File getSavedFile() {
        return savedFile;
    }

    /** Pre-loads an existing image into the editor before the dialog is shown. */
    public void preloadImage(File f) {
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null)
                return;
            original = img;
            sourceFile = f;
            hasCrop = false;
            // Schedule UI update for after the dialog is shown
            SwingUtilities.invokeLater(() -> {
                if (cropPanel != null)
                    cropPanel.repaint();
                updateCirclePreview();
                if (btnApply != null)
                    btnApply.setEnabled(true);
                setStatus("Current photo loaded. Choose a new one or adjust and save.", false);
            });
        } catch (IOException ignored) {
        }
    }

    // ── UI Construction ───────────────────────────────────────────────────────

    private void buildUI() {
        getContentPane().setBackground(ThemeManager.bg());
        setLayout(new BorderLayout(0, 0));

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(new EmptyBorder(0, 8, 0, 8));

        btnChoose = ThemeManager.primaryButton("📂  Choose Image");
        btnChoose.addActionListener(e -> chooseImage());

        JLabel title = new JLabel("Profile Photo Editor");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());

        bar.add(title);
        bar.add(Box.createHorizontalStrut(20));
        bar.add(btnChoose);
        return bar;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(ThemeManager.bg());
        center.setBorder(new EmptyBorder(12, 16, 8, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.insets = new Insets(0, 0, 0, 12);

        // Left: crop area
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 0.65;
        gc.weighty = 1;
        center.add(buildCropPanel(), gc);

        // Right: controls + circle preview
        gc.gridx = 1;
        gc.weightx = 0.35;
        gc.insets = new Insets(0, 0, 0, 0);
        center.add(buildControlsPanel(), gc);

        return center;
    }

    private JPanel buildCropPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ThemeManager.surface());
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.border()),
                new EmptyBorder(8, 8, 8, 8)));

        JLabel header = new JLabel("Image Preview  (drag to select crop area)");
        header.setFont(ThemeManager.fontBold());
        header.setForeground(ThemeManager.muted());
        header.setBorder(new EmptyBorder(0, 0, 6, 0));
        wrapper.add(header, BorderLayout.NORTH);

        cropPanel = new CropOverlayPanel();
        cropPanel.setPreferredSize(new Dimension(480, 380));
        cropPanel.setBackground(ThemeManager.elevated());
        wrapper.add(cropPanel, BorderLayout.CENTER);

        JLabel hint = new JLabel("Drag a square on the image to crop. Leave empty for center crop.");
        hint.setFont(ThemeManager.fontSmall());
        hint.setForeground(ThemeManager.muted());
        hint.setBorder(new EmptyBorder(4, 0, 0, 0));
        wrapper.add(hint, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildControlsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ThemeManager.surface());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.border()),
                new EmptyBorder(14, 14, 14, 14)));

        // Circle preview
        JLabel circleHeader = new JLabel("Circle Preview");
        circleHeader.setFont(ThemeManager.fontBold());
        circleHeader.setForeground(ThemeManager.muted());
        circleHeader.setAlignmentX(Component.CENTER_ALIGNMENT);

        previewCircle = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                int s = Math.min(getWidth(), getHeight());
                int ox = (getWidth() - s) / 2, oy = (getHeight() - s) / 2;
                if (previewImage != null) {
                    BufferedImage circle = makeCircleMask(previewImage, s);
                    g2.drawImage(circle, ox, oy, null);
                } else {
                    g2.setColor(ThemeManager.elevated());
                    g2.fillOval(ox, oy, s, s);
                    g2.setColor(ThemeManager.muted());
                    g2.setFont(ThemeManager.fontSmall());
                    String t = "No image";
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(t, ox + (s - fm.stringWidth(t)) / 2,
                            oy + s / 2 + fm.getAscent() / 2);
                }
                g2.dispose();
            }
        };
        previewCircle.setOpaque(false);
        previewCircle.setPreferredSize(new Dimension(160, 160));
        previewCircle.setMaximumSize(new Dimension(160, 160));
        previewCircle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Size controls
        JLabel sizeHeader = new JLabel("Output Size (px)");
        sizeHeader.setFont(ThemeManager.fontBold());
        sizeHeader.setForeground(ThemeManager.muted());
        sizeHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

        sizeSlider = new JSlider(MIN_SIZE, MAX_SIZE, DEF_SIZE);
        sizeSlider.setOpaque(false);
        sizeSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        sizeSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        SpinnerNumberModel spinModel = new SpinnerNumberModel(DEF_SIZE, MIN_SIZE, MAX_SIZE, 10);
        sizeSpinner = new JSpinner(spinModel);
        sizeSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        sizeSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblDimensions = new JLabel("Output: " + DEF_SIZE + " × " + DEF_SIZE + " px");
        lblDimensions.setFont(ThemeManager.fontSmall());
        lblDimensions.setForeground(ThemeManager.muted());
        lblDimensions.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Sync slider ↔ spinner
        sizeSlider.addChangeListener(e -> {
            int v = sizeSlider.getValue();
            sizeSpinner.setValue(v);
            lblDimensions.setText("Output: " + v + " × " + v + " px");
            updateCirclePreview();
        });
        sizeSpinner.addChangeListener(e -> {
            int v = (int) sizeSpinner.getValue();
            sizeSlider.setValue(v);
            lblDimensions.setText("Output: " + v + " × " + v + " px");
            updateCirclePreview();
        });

        // Reset crop button
        JButton btnResetCrop = ThemeManager.secondaryButton("Reset Crop");
        btnResetCrop.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnResetCrop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btnResetCrop.addActionListener(e -> {
            hasCrop = false;
            cropPanel.repaint();
            updateCirclePreview();
        });

        panel.add(circleHeader);
        panel.add(Box.createVerticalStrut(6));
        panel.add(previewCircle);
        panel.add(Box.createVerticalStrut(16));
        panel.add(sizeHeader);
        panel.add(Box.createVerticalStrut(4));
        panel.add(sizeSlider);
        panel.add(Box.createVerticalStrut(4));
        panel.add(sizeSpinner);
        panel.add(Box.createVerticalStrut(4));
        panel.add(lblDimensions);
        panel.add(Box.createVerticalStrut(12));
        panel.add(btnResetCrop);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildBottom() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(ThemeManager.surface());
        bar.setBorder(new EmptyBorder(8, 16, 8, 16));

        lblStatus = new JLabel(" ");
        lblStatus.setFont(ThemeManager.fontSmall());
        lblStatus.setForeground(ThemeManager.muted());

        btnApply = ThemeManager.primaryButton("✔  Apply & Save Photo");
        btnCancel = ThemeManager.secondaryButton("Cancel");
        btnApply.setEnabled(false);

        btnApply.addActionListener(e -> applyAndSave());
        btnCancel.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(btnCancel);
        btns.add(btnApply);

        bar.add(lblStatus, BorderLayout.WEST);
        bar.add(btns, BorderLayout.EAST);
        return bar;
    }

    // ── Image selection ───────────────────────────────────────────────────────

    private void chooseImage() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select Profile Photo");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Images (JPG, PNG, BMP, GIF)", "jpg", "jpeg", "png", "bmp", "gif"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;

        File f = fc.getSelectedFile();
        String name = f.getName().toLowerCase();
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg")
                && !name.endsWith(".png") && !name.endsWith(".bmp")
                && !name.endsWith(".gif")) {
            showErr("Unsupported format. Use JPG, PNG, BMP, or GIF.");
            return;
        }

        // Guard against very large files (>20MB)
        if (f.length() > 20 * 1024 * 1024) {
            showErr("Image is too large (max 20 MB).");
            return;
        }

        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                showErr("Cannot read image file.");
                return;
            }
            original = img;
            sourceFile = f;
            hasCrop = false;
            cropPanel.repaint();
            updateCirclePreview();
            btnApply.setEnabled(true);
            setStatus("Image loaded: " + img.getWidth() + " × " + img.getHeight() + " px", false);
        } catch (IOException ex) {
            showErr("Failed to load image: " + ex.getMessage());
        }
    }

    // ── Preview update ────────────────────────────────────────────────────────

    private void updateCirclePreview() {
        if (original == null)
            return;
        int outSize = (int) sizeSpinner.getValue();
        // Run the exact same pipeline as applyAndSave — preview IS the final result
        previewImage = processImage(outSize);
        previewCircle.repaint();
    }

    // ── Core processing (called ONCE on save) ─────────────────────────────────

    /**
     * Applies crop (if set) then resizes to outSize × outSize.
     * Single-pass: operates on the original image only.
     */
    private BufferedImage processImage(int outSize) {
        BufferedImage src = original;

        // Step 1: crop (from original coordinates)
        if (hasCrop && cropSize > 0) {
            int cx = Math.max(0, Math.min(cropX, src.getWidth() - cropSize));
            int cy = Math.max(0, Math.min(cropY, src.getHeight() - cropSize));
            int cs = Math.min(cropSize, Math.min(src.getWidth() - cx, src.getHeight() - cy));
            src = src.getSubimage(cx, cy, cs, cs);
        } else {
            // Center crop to square
            int min = Math.min(src.getWidth(), src.getHeight());
            int ox = (src.getWidth() - min) / 2;
            int oy = (src.getHeight() - min) / 2;
            src = src.getSubimage(ox, oy, min, min);
        }

        // Step 2: single resize to output size
        return scaleHQ(src, outSize, outSize);
    }

    private static BufferedImage scaleHQ(BufferedImage src, int w, int h) {
        // Progressive downscale to avoid BICUBIC blur on large-to-small passes
        BufferedImage current = src;
        int cw = src.getWidth(), ch = src.getHeight();
        while (cw > w * 2 || ch > h * 2) {
            cw = Math.max(cw / 2, w);
            ch = Math.max(ch / 2, h);
            BufferedImage tmp = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = tmp.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(current, 0, 0, cw, ch, null);
            g.dispose();
            current = tmp;
        }
        // Final pass with BICUBIC
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(current, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }

    private static BufferedImage toCircle(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillOval(0, 0, size, size);
        g2.setComposite(AlphaComposite.SrcIn);
        g2.drawImage(src, 0, 0, size, size, null);
        g2.dispose();
        return out;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void applyAndSave() {
        if (original == null)
            return;
        int outSize = (int) sizeSpinner.getValue();
        setStatus("Processing...", false);

        SwingWorker<File, Void> worker = new SwingWorker<>() {
            protected File doInBackground() throws Exception {
                BufferedImage processed = processImage(outSize);

                File dir = new File(outputDir);
                if (!dir.exists())
                    dir.mkdirs();

                // Prefer PNG for lossless quality; use JPEG only if source was JPEG
                String srcName = sourceFile.getName().toLowerCase();
                boolean useJpeg = srcName.endsWith(".jpg") || srcName.endsWith(".jpeg");
                String ext = useJpeg ? "jpg" : "png";
                File dest = new File(dir, filePrefix + "." + ext);

                if (useJpeg) {
                    // JPEG at quality 0.97 — near-lossless
                    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.97f);
                    writer.setOutput(ImageIO.createImageOutputStream(dest));
                    // Convert ARGB → RGB for JPEG
                    BufferedImage rgb = new BufferedImage(processed.getWidth(), processed.getHeight(),
                            BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = rgb.createGraphics();
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                    g.drawImage(processed, 0, 0, null);
                    g.dispose();
                    writer.write(null, new javax.imageio.IIOImage(rgb, null, null), param);
                    writer.dispose();
                } else {
                    // PNG — lossless
                    ImageIO.write(processed, "png", dest);
                }
                return dest;
            }

            protected void done() {
                try {
                    savedFile = get();
                    confirmed = true;
                    setStatus("Saved: " + savedFile.getName(), false);
                    dispose();
                } catch (Exception ex) {
                    showErr("Save failed: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    // ── Crop overlay panel ────────────────────────────────────────────────────

    private class CropOverlayPanel extends JPanel {
        // Display-space crop rect (for drawing)
        private int dCropX, dCropY, dCropSize;

        CropOverlayPanel() {
            setOpaque(true);
            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    dragStart = e.getPoint();
                }

                public void mouseDragged(MouseEvent e) {
                    onDrag(e.getPoint());
                }

                public void mouseReleased(MouseEvent e) {
                    onDrag(e.getPoint());
                    updateCirclePreview();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private void onDrag(Point p) {
            if (original == null || dragStart == null)
                return;
            int x1 = Math.min(dragStart.x, p.x);
            int y1 = Math.min(dragStart.y, p.y);
            int x2 = Math.max(dragStart.x, p.x);
            int y2 = Math.max(dragStart.y, p.y);
            int size = Math.min(x2 - x1, y2 - y1); // square
            if (size < 10)
                return;

            // Clamp to image display bounds
            Rectangle imgRect = getImageRect();
            x1 = Math.max(x1, imgRect.x);
            y1 = Math.max(y1, imgRect.y);
            size = Math.min(size, Math.min(imgRect.x + imgRect.width - x1,
                    imgRect.y + imgRect.height - y1));
            dCropX = x1;
            dCropY = y1;
            dCropSize = size;

            // Convert display coords → original image coords
            double scaleX = (double) original.getWidth() / imgRect.width;
            double scaleY = (double) original.getHeight() / imgRect.height;
            cropX = (int) ((x1 - imgRect.x) * scaleX);
            cropY = (int) ((y1 - imgRect.y) * scaleY);
            cropSize = (int) (size * Math.min(scaleX, scaleY));
            hasCrop = true;
            repaint();
        }

        /** Returns the rectangle where the image is drawn inside this panel. */
        private Rectangle getImageRect() {
            if (original == null)
                return new Rectangle(0, 0, getWidth(), getHeight());
            double pw = getWidth(), ph = getHeight();
            double iw = original.getWidth(), ih = original.getHeight();
            double scale = Math.min(pw / iw, ph / ih);
            int w = (int) (iw * scale), h = (int) (ih * scale);
            int x = (getWidth() - w) / 2;
            int y = (getHeight() - h) / 2;
            return new Rectangle(x, y, w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (original == null) {
                g2.setColor(ThemeManager.muted());
                g2.setFont(ThemeManager.fontBody());
                String t = "Click \"Choose Image\" to load a photo";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, getHeight() / 2);
                g2.dispose();
                return;
            }

            Rectangle r = getImageRect();
            g2.drawImage(original, r.x, r.y, r.width, r.height, null);

            if (hasCrop && dCropSize > 0) {
                // Dim outside crop
                g2.setColor(new Color(0, 0, 0, 100));
                g2.fillRect(r.x, r.y, r.width, r.height);
                // Clear crop area
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                g2.drawImage(original,
                        dCropX, dCropY, dCropX + dCropSize, dCropY + dCropSize,
                        cropX, cropY, cropX + cropSize, cropY + cropSize, null);
                // Crop border
                g2.setColor(new Color(0x10a37f));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(dCropX, dCropY, dCropSize, dCropSize);
                // Corner handles
                int h = 8;
                g2.setColor(Color.WHITE);
                g2.fillRect(dCropX - h / 2, dCropY - h / 2, h, h);
                g2.fillRect(dCropX + dCropSize - h / 2, dCropY - h / 2, h, h);
                g2.fillRect(dCropX - h / 2, dCropY + dCropSize - h / 2, h, h);
                g2.fillRect(dCropX + dCropSize - h / 2, dCropY + dCropSize - h / 2, h, h);
            }
            g2.dispose();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Off-screen circle mask — fillOval + SrcIn for clean antialiased boundary. */
    private static BufferedImage makeCircleMask(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setColor(Color.WHITE);
        g2.fillOval(0, 0, size, size);
        g2.setComposite(AlphaComposite.SrcIn);
        g2.drawImage(src, 0, 0, size, size, null);
        // Border ring baked in
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(new Color(0, 0, 0, 55));
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawOval(1, 1, size - 2, size - 2);
        g2.dispose();
        return out;
    }

    private void setStatus(String msg, boolean error) {
        lblStatus.setText(msg);
        lblStatus.setForeground(error ? ThemeManager.danger() : ThemeManager.SUCCESS);
    }

    private void showErr(String msg) {
        setStatus(msg, true);
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
