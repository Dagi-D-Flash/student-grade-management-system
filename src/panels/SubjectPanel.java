package panels;

import dao.SubjectDAO;
import models.Subject;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;

public class SubjectPanel extends JPanel {

    private final SubjectDAO subjectDAO = new SubjectDAO();

    private JTable table;
    private DefaultTableModel tableModel;
    private final java.util.List<Object[]> allRows = new java.util.ArrayList<>();

    private JTextField tfCode, tfName, tfDescription, tfCredits;
    private JButton btnAdd, btnUpdate, btnDelete, btnClear;
    private JLabel lblStatus;

    private int selectedId = -1;

    public SubjectPanel() {
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

        JLabel title = new JLabel("Subject Management");
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());
        outer.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        tfCode        = field(); tfName        = field();
        tfDescription = field(); tfCredits     = field();

        g.gridy = 0; g.gridwidth = 1; g.weightx = 0;
        g.gridx = 0; form.add(lbl("Code *"), g);
        g.gridx = 1; g.weightx = 0.3; form.add(tfCode, g);
        g.gridx = 2; g.weightx = 0; form.add(lbl("Name *"), g);
        g.gridx = 3; g.weightx = 0.5; form.add(tfName, g);

        g.gridy = 1; g.weightx = 0;
        g.gridx = 0; form.add(lbl("Description"), g);
        g.gridx = 1; g.weightx = 0.5; g.gridwidth = 3; form.add(tfDescription, g);

        g.gridy = 2; g.gridwidth = 1; g.weightx = 0;
        g.gridx = 0; form.add(lbl("Credits *"), g);
        g.gridx = 1; g.weightx = 0.2; form.add(tfCredits, g);

        lblStatus = new JLabel(" ");
        lblStatus.setFont(ThemeManager.fontSmall());
        g.gridx = 0; g.gridy = 3; g.gridwidth = 4; g.weightx = 1;
        form.add(lblStatus, g);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false);
        btnAdd    = ThemeManager.primaryButton("Add Subject");
        btnUpdate = ThemeManager.primaryButton("Update");
        btnDelete = ThemeManager.dangerButton("Delete");
        btnClear  = ThemeManager.secondaryButton("Clear");
        btnRow.add(btnAdd); btnRow.add(btnUpdate); btnRow.add(btnDelete); btnRow.add(btnClear);

        g.gridy = 4; g.gridwidth = 4;
        form.add(btnRow, g);

        btnAdd.addActionListener(e    -> addSubject());
        btnUpdate.addActionListener(e -> updateSubject());
        btnDelete.addActionListener(e -> deleteSubject());
        btnClear.addActionListener(e  -> clearForm());

        outer.add(form, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildTablePanel() {
        String[] cols = {"ID", "Code", "Name", "Description", "Credits"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        ThemeManager.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) populateForm();
        });

        JTextField tfSearch = searchField("Search by code, name or description...");
        tfSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filterTable(tfSearch.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filterTable(tfSearch.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(tfSearch.getText()); }
        });

        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setBackground(ThemeManager.surface());
        wrapper.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.border()), "All Subjects"));
        wrapper.add(buildSearchBar(tfSearch), BorderLayout.NORTH);
        wrapper.add(new JScrollPane(table),   BorderLayout.CENTER);
        return wrapper;
    }

    private void filterTable(String query) {
        tableModel.setRowCount(0);
        String q = query.trim().toLowerCase();
        for (Object[] row : allRows) {
            // cols: 0=ID,1=Code,2=Name,3=Description,4=Credits
            if (q.isEmpty()
                || str(row[1]).toLowerCase().contains(q)
                || str(row[2]).toLowerCase().contains(q)
                || str(row[3]).toLowerCase().contains(q)) {
                tableModel.addRow(row);
            }
        }
    }

    private void loadTable() {
        tableModel.setRowCount(0);
        allRows.clear();
        try {
            for (Subject s : subjectDAO.getAll()) {
                Object[] row = {s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getCredits()};
                allRows.add(row);
                tableModel.addRow(row);
            }
        } catch (SQLException ex) { showError("Failed to load subjects: " + ex.getMessage()); }
    }

    private void populateForm() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        selectedId = (int) tableModel.getValueAt(row, 0);
        tfCode.setText(str(tableModel.getValueAt(row, 1)));
        tfName.setText(str(tableModel.getValueAt(row, 2)));
        tfDescription.setText(str(tableModel.getValueAt(row, 3)));
        tfCredits.setText(str(tableModel.getValueAt(row, 4)));
        setStatus("", false);
    }

    private void addSubject() {
        String err = validateForm();
        if (err != null) { setStatus(err, true); return; }
        try {
            if (subjectDAO.existsByCode(tfCode.getText().trim(), 0)) {
                setStatus("Subject code already exists.", true); return;
            }
            subjectDAO.insert(buildSubject());
            loadTable(); clearForm();
            setStatus("Subject added.", false);
        } catch (Exception ex) { showError("Add failed: " + ex.getMessage()); }
    }

    private void updateSubject() {
        if (selectedId < 0) { setStatus("Select a subject first.", true); return; }
        String err = validateForm();
        if (err != null) { setStatus(err, true); return; }
        try {
            if (subjectDAO.existsByCode(tfCode.getText().trim(), selectedId)) {
                setStatus("Subject code already taken.", true); return;
            }
            Subject s = buildSubject();
            s.setId(selectedId);
            subjectDAO.update(s);
            loadTable(); clearForm();
            setStatus("Subject updated.", false);
        } catch (Exception ex) { showError("Update failed: " + ex.getMessage()); }
    }

    private void deleteSubject() {
        if (selectedId < 0) { setStatus("Select a subject first.", true); return; }
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete subject \"" + tfName.getText() + "\"?\nThis will also delete related courses.",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            subjectDAO.delete(selectedId);
            loadTable(); clearForm();
            setStatus("Subject deleted.", false);
        } catch (SQLException ex) { showError("Delete failed: " + ex.getMessage()); }
    }

    private Subject buildSubject() {
        Subject s = new Subject();
        s.setCode(tfCode.getText().trim().toUpperCase());
        s.setName(tfName.getText().trim());
        s.setDescription(tfDescription.getText().trim());
        s.setCredits(Integer.parseInt(tfCredits.getText().trim()));
        return s;
    }

    private String validateForm() {
        if (tfCode.getText().trim().isEmpty()) return "Code is required.";
        if (tfName.getText().trim().isEmpty()) return "Name is required.";
        String cr = tfCredits.getText().trim();
        if (cr.isEmpty()) return "Credits is required.";
        try {
            int c = Integer.parseInt(cr);
            if (c < 1 || c > 10) return "Credits must be between 1 and 10.";
        } catch (NumberFormatException e) { return "Credits must be a number."; }
        return null;
    }

    private void clearForm() {
        selectedId = -1;
        tfCode.setText(""); tfName.setText(""); tfDescription.setText(""); tfCredits.setText("");
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
}
