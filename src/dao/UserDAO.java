//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import models.User;
import util.DBConnection;

public class UserDAO {
    public void insert(User user) throws SQLException {
        String sql = "INSERT INTO users (username, email, password, role, is_active) VALUES (?,?,?,?,?)";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, 1);
        ) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getRole());
            ps.setBoolean(5, user.isActive());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getInt(1));
                }
            }
        }

    }

    public void update(User user) throws SQLException {
        String sql = "UPDATE users SET username=?, email=?, password=?, role=?, is_active=? WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getRole());
            ps.setBoolean(5, user.isActive());
            ps.setInt(6, user.getId());
            ps.executeUpdate();
        }

    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM users WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

    }

    public User getById(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User var6 = mapRow(rs);
                    return var6;
                }
            }
        }

        return null;
    }

    public boolean existsByUsername(String username, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE username=? AND id<>?";

        boolean var7;
        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, username);
            ps.setInt(2, excludeId);

            try (ResultSet rs = ps.executeQuery()) {
                var7 = rs.next() && rs.getInt(1) > 0;
            }
        }

        return var7;
    }

    public boolean existsByEmail(String email, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE email=? AND id<>?";

        boolean var7;
        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, email);
            ps.setInt(2, excludeId);

            try (ResultSet rs = ps.executeQuery()) {
                var7 = rs.next() && rs.getInt(1) > 0;
            }
        }

        return var7;
    }

    public List<User> getAll() throws SQLException {
        List<User> list = new ArrayList();
        String sql = "SELECT * FROM users ORDER BY username";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
        ) {
            while(rs.next()) {
                list.add(mapRow(rs));
            }
        }

        return list;
    }

    public List<User> getUnassigned(String role) throws SQLException {
        List<User> list = new ArrayList();
        String table = "student".equals(role) ? "students" : "teachers";
        String sql = "SELECT * FROM users WHERE role=? AND id NOT IN (SELECT user_id FROM " + table + ") ORDER BY username";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, role);

            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }

        return list;
    }

    static User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setPassword(rs.getString("password"));
        u.setRole(rs.getString("role"));
        u.setActive(rs.getBoolean("is_active"));
        u.setCreatedAt(rs.getTimestamp("created_at"));
        u.setUpdatedAt(rs.getTimestamp("updated_at"));
        return u;
    }
}
