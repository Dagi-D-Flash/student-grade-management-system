
package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import models.Course;
import models.Subject;
import models.Teacher;
import util.DBConnection;

public class CourseDAO {
    public void insert(Course course) throws SQLException {
        String sql = "INSERT INTO courses (subject_id, teacher_id, section, academic_year, semester, max_students) VALUES (?,?,?,?,?,?)";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, 1);
        ) {
            ps.setInt(1, course.getSubject().getId());
            ps.setInt(2, course.getTeacher().getId());
            ps.setString(3, course.getSection());
            ps.setInt(4, course.getAcademicYear());
            ps.setString(5, course.getSemester());
            ps.setInt(6, course.getMaxStudents());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    course.setId(keys.getInt(1));
                }
            }
        }

    }

    public void update(Course course) throws SQLException {
        String sql = "UPDATE courses SET subject_id=?, teacher_id=?, section=?, academic_year=?, semester=?, max_students=? WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, course.getSubject().getId());
            ps.setInt(2, course.getTeacher().getId());
            ps.setString(3, course.getSection());
            ps.setInt(4, course.getAcademicYear());
            ps.setString(5, course.getSemester());
            ps.setInt(6, course.getMaxStudents());
            ps.setInt(7, course.getId());
            ps.executeUpdate();
        }

    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM courses WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

    }

    public Course getById(int id) throws SQLException {
        String sql = "SELECT c.*, s.code AS sub_code, s.name AS sub_name, s.description AS sub_desc, s.credits, t.first_name AS t_first, t.last_name AS t_last, t.employee_no, t.department, t.phone AS t_phone, t.user_id AS t_user_id FROM courses c JOIN subjects s ON s.id = c.subject_id JOIN teachers t ON t.id = c.teacher_id WHERE c.id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Course var6 = this.mapRow(rs);
                    return var6;
                }
            }
        }

        return null;
    }

    public boolean existsDuplicate(int subjectId, int teacherId, String section, int year, String semester, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM courses WHERE subject_id=? AND teacher_id=? AND section=? AND academic_year=? AND semester=? AND id<>?";

        boolean var11;
        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, subjectId);
            ps.setInt(2, teacherId);
            ps.setString(3, section);
            ps.setInt(4, year);
            ps.setString(5, semester);
            ps.setInt(6, excludeId);

            try (ResultSet rs = ps.executeQuery()) {
                var11 = rs.next() && rs.getInt(1) > 0;
            }
        }

        return var11;
    }

    public List<Course> getAll() throws SQLException {
        List<Course> list = new ArrayList();
        String sql = "SELECT c.*, s.code AS sub_code, s.name AS sub_name, s.description AS sub_desc, s.credits, t.first_name AS t_first, t.last_name AS t_last, t.employee_no, t.department, t.phone AS t_phone, t.user_id AS t_user_id FROM courses c JOIN subjects s ON s.id = c.subject_id JOIN teachers t ON t.id = c.teacher_id ORDER BY c.academic_year DESC, c.semester, s.code";

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

    private Course mapRow(ResultSet rs) throws SQLException {
        Course c = new Course();
        c.setId(rs.getInt("id"));
        c.setSection(rs.getString("section"));
        c.setAcademicYear(rs.getInt("academic_year"));
        c.setSemester(rs.getString("semester"));
        c.setMaxStudents(rs.getInt("max_students"));
        Subject sub = new Subject();
        sub.setId(rs.getInt("subject_id"));
        sub.setCode(rs.getString("sub_code"));
        sub.setName(rs.getString("sub_name"));
        sub.setDescription(rs.getString("sub_desc"));
        sub.setCredits(rs.getInt("credits"));
        c.setSubject(sub);
        Teacher t = new Teacher();
        t.setId(rs.getInt("teacher_id"));
        t.setFirstName(rs.getString("t_first"));
        t.setLastName(rs.getString("t_last"));
        t.setEmployeeNo(rs.getString("employee_no"));
        t.setDepartment(rs.getString("department"));
        t.setPhone(rs.getString("t_phone"));
        c.setTeacher(t);
        return c;
    }
}
