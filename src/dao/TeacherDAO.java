package dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import models.Teacher;
import models.User;
import util.DBConnection;

public class TeacherDAO {
    public void insert(Teacher teacher) throws SQLException {
        String sql = "INSERT INTO teachers (user_id, first_name, last_name, employee_no, department, phone, hired_at) VALUES (?,?,?,?,?,?,?)";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, 1);
        ) {
            ps.setInt(1, teacher.getUser().getId());
            ps.setString(2, teacher.getFirstName());
            ps.setString(3, teacher.getLastName());
            ps.setString(4, teacher.getEmployeeNo());
            ps.setString(5, teacher.getDepartment());
            ps.setString(6, teacher.getPhone());
            ps.setDate(7, teacher.getHiredAt() != null ? new Date(teacher.getHiredAt().getTime()) : null);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    teacher.setId(keys.getInt(1));
                }
            }
        }

    }

    public void update(Teacher teacher) throws SQLException {
        String sql = "UPDATE teachers SET user_id=?, first_name=?, last_name=?, employee_no=?, department=?, phone=?, hired_at=? WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, teacher.getUser().getId());
            ps.setString(2, teacher.getFirstName());
            ps.setString(3, teacher.getLastName());
            ps.setString(4, teacher.getEmployeeNo());
            ps.setString(5, teacher.getDepartment());
            ps.setString(6, teacher.getPhone());
            ps.setDate(7, teacher.getHiredAt() != null ? new Date(teacher.getHiredAt().getTime()) : null);
            ps.setInt(8, teacher.getId());
            ps.executeUpdate();
        }

    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM teachers WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

    }

    public Teacher getById(int id) throws SQLException {
        String sql = "SELECT t.*, u.username, u.email, u.role, u.is_active, u.created_at AS u_created, u.updated_at AS u_updated FROM teachers t JOIN users u ON u.id = t.user_id WHERE t.id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Teacher var6 = this.mapRow(rs);
                    return var6;
                }
            }
        }

        return null;
    }

    public boolean existsByEmployeeNo(String employeeNo, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM teachers WHERE employee_no=? AND id<>?";

        boolean var7;
        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, employeeNo);
            ps.setInt(2, excludeId);

            try (ResultSet rs = ps.executeQuery()) {
                var7 = rs.next() && rs.getInt(1) > 0;
            }
        }

        return var7;
    }

    public List<Teacher> getAll() throws SQLException {
        List<Teacher> list = new ArrayList();
        String sql = "SELECT t.*, u.username, u.email, u.role, u.is_active, u.created_at AS u_created, u.updated_at AS u_updated FROM teachers t JOIN users u ON u.id = t.user_id ORDER BY t.first_name, t.last_name";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
        ) {
            while(rs.next()) {
                list.add(this.mapRow(rs));
            }
        }

        return list;
    }

    private Teacher mapRow(ResultSet rs) throws SQLException {
        Teacher t = new Teacher();
        t.setId(rs.getInt("id"));
        t.setFirstName(rs.getString("first_name"));
        t.setLastName(rs.getString("last_name"));
        t.setEmployeeNo(rs.getString("employee_no"));
        t.setDepartment(rs.getString("department"));
        t.setPhone(rs.getString("phone"));
        t.setHiredAt(rs.getDate("hired_at"));
        User u = new User();
        u.setId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setRole(rs.getString("role"));
        u.setActive(rs.getBoolean("is_active"));
        t.setUser(u);
        return t;
    }
}
