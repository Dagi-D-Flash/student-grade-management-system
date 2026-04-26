package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import models.Subject;
import util.DBConnection;

public class SubjectDAO {
    public void insert(Subject subject) throws SQLException {
        String sql = "INSERT INTO subjects (code, name, description, credits) VALUES (?,?,?,?)";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, 1);
        ) {
            ps.setString(1, subject.getCode().toUpperCase());
            ps.setString(2, subject.getName());
            ps.setString(3, subject.getDescription());
            ps.setInt(4, subject.getCredits());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    subject.setId(keys.getInt(1));
                }
            }
        }

    }

    public void update(Subject subject) throws SQLException {
        String sql = "UPDATE subjects SET code=?, name=?, description=?, credits=? WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, subject.getCode().toUpperCase());
            ps.setString(2, subject.getName());
            ps.setString(3, subject.getDescription());
            ps.setInt(4, subject.getCredits());
            ps.setInt(5, subject.getId());
            ps.executeUpdate();
        }

    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM subjects WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

    }

    public Subject getById(int id) throws SQLException {
        String sql = "SELECT * FROM subjects WHERE id=?";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Subject var6 = mapRow(rs);
                    return var6;
                }
            }
        }

        return null;
    }

    public boolean existsByCode(String code, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM subjects WHERE code=? AND id<>?";

        boolean var7;
        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            ps.setString(1, code.toUpperCase());
            ps.setInt(2, excludeId);

            try (ResultSet rs = ps.executeQuery()) {
                var7 = rs.next() && rs.getInt(1) > 0;
            }
        }

        return var7;
    }

    public List<Subject> getAll() throws SQLException {
        List<Subject> list = new ArrayList();
        String sql = "SELECT * FROM subjects ORDER BY code";

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

    static Subject mapRow(ResultSet rs) throws SQLException {
        Subject s = new Subject();
        s.setId(rs.getInt("id"));
        s.setCode(rs.getString("code"));
        s.setName(rs.getString("name"));
        s.setDescription(rs.getString("description"));
        s.setCredits(rs.getInt("credits"));
        return s;
    }
}
