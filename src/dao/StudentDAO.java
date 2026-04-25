package dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import models.Student;
import models.User;
import util.DBConnection;

public class StudentDAO {
    public void insert(Student student) throws SQLException {
        String sql = "INSERT INTO students (user_id, first_name, last_name, student_no, date_of_birth, gender, address, phone, enrolled_at) VALUES (?,?,?,?,?,?,?,?,?)";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, 1);
        ) {
            ps.setInt(1, student.getUser().getId());
            ps.setString(2, student.getFirstName());
            ps.setString(3, student.getLastName());
            ps.setString(4, student.getStudentNo());
            ps.setDate(5, student.getDateOfBirth() != null ? new Date(student.getDateOfBirth().getTime()) : null);
            ps.setString(6, student.getGender());
            ps.setString(7, student.getAddress());
            ps.setString(8, student.getPhone());
            ps.setDate(9, student.getEnrolledAt() != null ? new Date(student.getEnrolledAt().getTime()) : new Date(System.currentTimeMillis()));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    student.setId(keys.getInt(1));
                }
            }
        }

    }

    public void update(Student student) throws SQLException {
        String sql = "UPDATE students SET user_id=?, first_name=?, last_name=?, student_no=?, date_of_birth=?, gender=?, address=?, phone=?, enrolled_at=? WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, student.getUser().getId());
            ps.setString(2, student.getFirstName());
            ps.setString(3, student.getLastName());
            ps.setString(4, student.getStudentNo());
            ps.setDate(5, student.getDateOfBirth() != null ? new Date(student.getDateOfBirth().getTime()) : null);
            ps.setString(6, student.getGender());
            ps.setString(7, student.getAddress());
            ps.setString(8, student.getPhone());
            ps.setDate(9, student.getEnrolledAt() != null ? new Date(student.getEnrolledAt().getTime()) : new Date(System.currentTimeMillis()));
            ps.setInt(10, student.getId());
            ps.executeUpdate();
        }

    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM students WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

    }

    public Student getById(int id) throws SQLException {
        String sql = "SELECT s.*, u.username, u.email, u.role, u.is_active, u.created_at AS u_created, u.updated_at AS u_updated FROM students s JOIN users u ON u.id = s.user_id WHERE s.id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Student var6 = this.mapRow(rs);
                    return var6;
                }
            }
        }

        return null;
    }

    public boolean existsByStudentNo(String studentNo, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM students WHERE student_no=? AND id<>?";

        boolean var7;
        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, studentNo);
            ps.setInt(2, excludeId);

            try (ResultSet rs = ps.executeQuery()) {
                var7 = rs.next() && rs.getInt(1) > 0;
            }
        }

        return var7;
    }

    public List<Student> getAll() throws SQLException {
        List<Student> list = new ArrayList();
        String sql = "SELECT s.*, u.username, u.email, u.role, u.is_active, u.created_at AS u_created, u.updated_at AS u_updated FROM students s JOIN users u ON u.id = s.user_id ORDER BY s.first_name, s.last_name";

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

    private Student mapRow(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setId(rs.getInt("id"));
        s.setFirstName(rs.getString("first_name"));
        s.setLastName(rs.getString("last_name"));
        s.setStudentNo(rs.getString("student_no"));
        s.setDateOfBirth(rs.getDate("date_of_birth"));
        s.setGender(rs.getString("gender"));
        s.setAddress(rs.getString("address"));
        s.setPhone(rs.getString("phone"));
        s.setEnrolledAt(rs.getDate("enrolled_at"));

        try {
            s.setProfilePhoto(rs.getString("profile_photo"));
        } catch (SQLException var4) {
        }

        User u = new User();
        u.setId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setRole(rs.getString("role"));
        u.setActive(rs.getBoolean("is_active"));
        s.setUser(u);
        return s;
    }

    public void updateProfile(int studentId, String phone, String profilePhoto) throws SQLException {
        String sql = "UPDATE students SET phone=?, profile_photo=? WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, phone);
            ps.setString(2, profilePhoto);
            ps.setInt(3, studentId);
            ps.executeUpdate();
        }

    }

    public void updateEmail(int userId, String email) throws SQLException {
        String sql = "UPDATE users SET email=? WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, email);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }

    }

    public void updatePassword(int userId, String newPassword) throws SQLException {
        String sql = "UPDATE users SET password=? WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, newPassword);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }

    }

    public boolean verifyPassword(int userId, String password) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE id=? AND password=?";

        boolean var7;
        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, userId);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                var7 = rs.next() && rs.getInt(1) > 0;
            }
        }

        return var7;
    }

    public Student getByUserId(int userId) throws SQLException {
        String sql = "SELECT s.*, u.username, u.email, u.role, u.is_active, u.created_at AS u_created, u.updated_at AS u_updated FROM students s JOIN users u ON u.id = s.user_id WHERE s.user_id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Student var6 = this.mapRow(rs);
                    return var6;
                }
            }
        }

        return null;
    }
}
