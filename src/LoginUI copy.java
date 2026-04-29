import models.User;
import util.DBConnection;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginUI extends JFrame {

    public static void main(String[] args) {
        ThemeManager.applyStartupTheme();
        SwingUtilities.invokeLater(LoginUI::new);
    }

    private JTextField     usernameField;
    private JPasswordField passwordField;
    private JButton        loginButton;

    public LoginUI() {
        setTitle("GMS — Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(440, 520);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.bg());
        root.putClientProperty("theme-role", "bg");
        root.add(buildBrand(),  BorderLayout.NORTH);
        root.add(buildForm(),   BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);

        loginButton.addActionListener(e -> handleLogin());
        passwordField.addActionListener(e -> handleLogin());

        setVisible(true);
    }

    private JPanel buildBrand() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(ThemeManager.surface());
        p.setBorder(new EmptyBorder(36, 40, 28, 40));

        JLabel icon = new JLabel("\uD83C\uDF93", SwingConstants.CENTER);
        icon.setFont(new Font("SansSerif", Font.PLAIN, 42));
        icon.setForeground(ThemeManager.accent());
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Grade Management System", SwingConstants.CENTER);
        title.setFont(ThemeManager.fontTitle());
        title.setForeground(ThemeManager.text());
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Sign in to your account", SwingConstants.CENTER);
        sub.setFont(ThemeManager.fontSmall());
        sub.setForeground(ThemeManager.muted());
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(icon);
        p.add(Box.createVerticalStrut(10));
        p.add(title);
        p.add(Box.createVerticalStrut(4));
        p.add(sub);
        return p;
    }

    private JPanel buildForm() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(ThemeManager.bg());
        p.setBorder(new EmptyBorder(24, 40, 24, 40));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(6, 0, 6, 0);
        g.weightx = 1;

        JLabel lblUser = new JLabel("Username");
        lblUser.setFont(ThemeManager.fontBold());
        lblUser.setForeground(ThemeManager.text());
        g.gridx = 0; g.gridy = 0;
        p.add(lblUser, g);

        usernameField = new JTextField();
        usernameField.setPreferredSize(new Dimension(0, 38));
        usernameField.setBackground(ThemeManager.elevated());
        usernameField.setForeground(ThemeManager.text());
        usernameField.setCaretColor(ThemeManager.text());
        usernameField.setFont(ThemeManager.fontBody());
        usernameField.putClientProperty("theme-role", "field");
        g.gridy = 1;
        p.add(usernameField, g);

        JLabel lblPass = new JLabel("Password");
        lblPass.setFont(ThemeManager.fontBold());
        lblPass.setForeground(ThemeManager.text());
        g.gridy = 2;
        p.add(lblPass, g);

        passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(0, 38));
        passwordField.setBackground(ThemeManager.elevated());
        passwordField.setForeground(ThemeManager.text());
        passwordField.setCaretColor(ThemeManager.text());
        passwordField.setFont(ThemeManager.fontBody());
        passwordField.putClientProperty("theme-role", "field");
        g.gridy = 3;
        p.add(passwordField, g);

        loginButton = ThemeManager.primaryButton("Sign In");
        loginButton.setPreferredSize(new Dimension(0, 42));
        loginButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.gridy = 4;
        g.insets = new Insets(16, 0, 6, 0);
        p.add(loginButton, g);

        return p;
    }

    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(ThemeManager.bg());
        p.setBorder(new EmptyBorder(0, 40, 20, 40));

        JLabel lbl = new JLabel("Grade Management System v1.0");
        lbl.setFont(ThemeManager.fontSmall());
        lbl.setForeground(ThemeManager.muted());

        JToggleButton toggle = ThemeManager.createToggleButton();

        p.add(lbl,    BorderLayout.WEST);
        p.add(toggle, BorderLayout.EAST);
        return p;
    }

    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter username and password.",
                "Login", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setUsername(rs.getString("username"));
                    user.setRole(rs.getString("role"));
                    dispose();
                    switch (user.getRole()) {
                        case "admin":   new AdminDashboard(user);   break;
                        case "teacher": new TeacherDashboard(user); break;
                        default:        new StudentDashboard(user); break;
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid username or password.",
                        "Login Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
