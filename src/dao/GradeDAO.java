package dao;

import models.Enrollment;
import models.Grade;
import util.DBConnection;

import java.sql.*;

import java.util.ArrayList;
import java.util.List;

public class GradeDAO {

    public void insert(Grade grade) throws SQLException {
        String sql = "INSERT INTO grades (enrollment_id,component_id,grade_type,score,remarks) VALUES (?,?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, grade.getEnrollment().getId());
            ps.setInt(2, grade.getComponentId());
            ps.setString(3, grade.getGradeType());
            ps.setDouble(4, grade.getScore());
            ps.setString(5, grade.getRemarks());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) grade.setId(keys.getInt(1));
            }
        }
    }

    public void update(Grade grade) throws SQLException {
        String sql = "UPDATE grades SET score=?,remarks=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, grade.getScore());
            ps.setString(2, grade.getRemarks());
            ps.setInt(3, grade.getId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM grades WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public void deleteByEnrollmentAndComponent(int enrollmentId, int componentId) throws SQLException {
        String sql = "DELETE FROM grades WHERE enrollment_id=? AND component_id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId);
            ps.setInt(2, componentId);
            ps.executeUpdate();
        }
    }

    public Grade getByEnrollmentAndComponent(int enrollmentId, int componentId) throws SQLException {
        String sql = "SELECT * FROM grades WHERE enrollment_id=? AND component_id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId);
            ps.setInt(2, componentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Grade> getByEnrollmentId(int enrollmentId) throws SQLException {
        List<Grade> list = new ArrayList<>();
        String sql = "SELECT g.*, cgc.component_name, cgc.weight, cgc.max_score " +
                     "FROM grades g JOIN course_grade_components cgc ON cgc.id=g.component_id " +
                     "WHERE g.enrollment_id=? ORDER BY cgc.id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enrollmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRowFull(rs));
            }
        }
        return list;
    }

    private Grade mapRow(ResultSet rs) throws SQLException {
        Grade g = new Grade();
        g.setId(rs.getInt("id"));
        g.setComponentId(rs.getInt("component_id"));
        g.setGradeType(rs.getString("grade_type"));
        g.setScore(rs.getDouble("score"));
        g.setRemarks(rs.getString("remarks"));
        g.setGradedAt(rs.getTimestamp("graded_at"));
        Enrollment e = new Enrollment();
        e.setId(rs.getInt("enrollment_id"));
        g.setEnrollment(e);
        return g;
    }

    private Grade mapRowFull(ResultSet rs) throws SQLException {
        Grade g = mapRow(rs);
        g.setMaxScore(rs.getDouble("max_score"));
        g.setWeight(rs.getDouble("weight"));
        return g;
    }
}
