package dao;

import models.GradeComponent;
import util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GradeComponentDAO {

    public void insert(GradeComponent c) throws SQLException {
        String sql = "INSERT INTO course_grade_components (course_id,component_name,weight,max_score) VALUES (?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getCourseId());
            ps.setString(2, c.getComponentName());
            ps.setDouble(3, c.getWeight());
            ps.setDouble(4, c.getMaxScore());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) c.setId(keys.getInt(1));
            }
        }
    }

    public void update(GradeComponent c) throws SQLException {
        String sql = "UPDATE course_grade_components SET component_name=?,weight=?,max_score=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getComponentName());
            ps.setDouble(2, c.getWeight());
            ps.setDouble(3, c.getMaxScore());
            ps.setInt(4, c.getId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM course_grade_components WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public List<GradeComponent> getByCourseId(int courseId) throws SQLException {
        List<GradeComponent> list = new ArrayList<>();
        String sql = "SELECT * FROM course_grade_components WHERE course_id=? ORDER BY id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public double getTotalWeight(int courseId, int excludeId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(weight),0) FROM course_grade_components WHERE course_id=? AND id<>?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            ps.setInt(2, excludeId < 0 ? -1 : excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0;
    }

    public boolean existsByName(int courseId, String name, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM course_grade_components WHERE course_id=? AND component_name=? AND id<>?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            ps.setString(2, name);
            ps.setInt(3, excludeId < 0 ? -1 : excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private GradeComponent mapRow(ResultSet rs) throws SQLException {
        return new GradeComponent(
            rs.getInt("id"),
            rs.getInt("course_id"),
            rs.getString("component_name"),
            rs.getDouble("weight"),
            rs.getDouble("max_score")
        );
    }
}
