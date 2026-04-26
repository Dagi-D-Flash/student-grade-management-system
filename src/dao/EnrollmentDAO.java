package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import models.Course;
import models.Enrollment;
import models.Student;
import util.DBConnection;

public class EnrollmentDAO {
    public void insert(Enrollment enrollment) throws SQLException {
        String sql = "INSERT INTO enrollments (student_id, course_id, status) VALUES (?,?,?)";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, 1);
        ) {
            ps.setInt(1, enrollment.getStudent().getId());
            ps.setInt(2, enrollment.getCourse().getId());
            ps.setString(3, enrollment.getStatus() != null ? enrollment.getStatus() : "active");
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    enrollment.setId(keys.getInt(1));
                }
            }
        }

    }

    public void update(Enrollment enrollment) throws SQLException {
        String sql = "UPDATE enrollments SET student_id=?, course_id=?, status=? WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, enrollment.getStudent().getId());
            ps.setInt(2, enrollment.getCourse().getId());
            ps.setString(3, enrollment.getStatus());
            ps.setInt(4, enrollment.getId());
            ps.executeUpdate();
        }

    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM enrollments WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

    }

    public Enrollment getById(int id) throws SQLException {
        String sql = "SELECT e.id, e.status, e.enrolled_at, e.student_id, e.course_id FROM enrollments e WHERE e.id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Enrollment var6 = this.mapRowLight(rs);
                    return var6;
                }
            }
        }

        return null;
    }

    public boolean existsDuplicate(int studentId, int courseId, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM enrollments WHERE student_id=? AND course_id=? AND id<>?";

        boolean var8;
        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, studentId);
            ps.setInt(2, courseId);
            ps.setInt(3, excludeId);

            try (ResultSet rs = ps.executeQuery()) {
                var8 = rs.next() && rs.getInt(1) > 0;
            }
        }

        return var8;
    }

    public List<Enrollment> getAll() throws SQLException {
        List<Enrollment> list = new ArrayList();
        String sql = "SELECT e.id, e.status, e.enrolled_at, e.student_id, e.course_id FROM enrollments e ORDER BY e.id";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
        ) {
            while(rs.next()) {
                list.add(this.mapRowLight(rs));
            }
        }

        return list;
    }

    private Enrollment mapRowLight(ResultSet rs) throws SQLException {
        Enrollment e = new Enrollment();
        e.setId(rs.getInt("id"));
        e.setStatus(rs.getString("status"));
        e.setEnrolledAt(rs.getTimestamp("enrolled_at"));
        Student s = new Student();
        s.setId(rs.getInt("student_id"));
        e.setStudent(s);
        Course c = new Course();
        c.setId(rs.getInt("course_id"));
        e.setCourse(c);
        return e;
    }
}
