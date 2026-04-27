package util;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GPACalculator {

    public static class SemesterGPA {
        public final int    academicYear;
        public final String semester;
        public final double gpa;
        public final int    totalCredits;
        public final int    courseCount;

        public SemesterGPA(int academicYear, String semester, double gpa, int totalCredits, int courseCount) {
            this.academicYear = academicYear;
            this.semester     = semester;
            this.gpa          = gpa;
            this.totalCredits = totalCredits;
            this.courseCount  = courseCount;
        }
    }

    public static List<SemesterGPA> getSemesterGPAs(int studentId) throws SQLException {
        List<SemesterGPA> result = new ArrayList<>();
        String sql =
            "SELECT c.academic_year, c.semester, sub.credits, " +
            "       SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM enrollments e " +
            "JOIN courses c    ON c.id   = e.course_id " +
            "JOIN subjects sub ON sub.id = c.subject_id " +
            "JOIN grades g     ON g.enrollment_id = e.id " +
            "JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "WHERE e.student_id = ? " +
            "GROUP BY e.id, c.academic_year, c.semester, sub.credits " +
            "ORDER BY c.academic_year, c.semester";

        List<int[]>    creditsList = new ArrayList<>();
        List<double[]> gpList      = new ArrayList<>();
        List<String>   yearSem     = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getInt("academic_year") + "|" + rs.getString("semester");
                    int    credits = rs.getInt("credits");
                    double gp      = toGradePoint(rs.getDouble("avg_pct"));

                    int idx = yearSem.indexOf(key);
                    if (idx < 0) {
                        yearSem.add(key);
                        creditsList.add(new int[]{credits});
                        gpList.add(new double[]{gp * credits});
                    } else {
                        creditsList.get(idx)[0] += credits;
                        gpList.get(idx)[0]       += gp * credits;
                    }
                }
            }
        }

        String[] semGpaCountSql = {
            "SELECT c.academic_year, c.semester, COUNT(DISTINCT e.id) AS cnt " +
            "FROM enrollments e " +
            "JOIN courses c ON c.id = e.course_id " +
            "WHERE e.student_id = ? " +
            "GROUP BY c.academic_year, c.semester " +
            "ORDER BY c.academic_year, c.semester"
        };

        List<int[]> counts = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(semGpaCountSql[0])) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getInt("academic_year") + "|" + rs.getString("semester");
                    int idx = yearSem.indexOf(key);
                    if (idx >= 0) {
                        while (counts.size() <= idx) counts.add(new int[]{0});
                        counts.get(idx)[0] = rs.getInt("cnt");
                    }
                }
            }
        }

        for (int i = 0; i < yearSem.size(); i++) {
            String[] parts = yearSem.get(i).split("\\|");
            int    credits = creditsList.get(i)[0];
            double gpa     = credits > 0 ? gpList.get(i)[0] / credits : 0.0;
            int    cnt     = (i < counts.size()) ? counts.get(i)[0] : 0;
            result.add(new SemesterGPA(Integer.parseInt(parts[0]), parts[1], gpa, credits, cnt));
        }
        return result;
    }

    public static double getCGPA(int studentId) throws SQLException {
        String sql =
            "SELECT sub.credits, " +
            "       SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM enrollments e " +
            "JOIN courses c    ON c.id   = e.course_id " +
            "JOIN subjects sub ON sub.id = c.subject_id " +
            "JOIN grades g     ON g.enrollment_id = e.id " +
            "JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "WHERE e.student_id = ? " +
            "GROUP BY e.id, sub.credits";

        double totalPoints = 0;
        int    totalCredits = 0;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int    credits = rs.getInt("credits");
                    double gp      = toGradePoint(rs.getDouble("avg_pct"));
                    totalPoints  += gp * credits;
                    totalCredits += credits;
                }
            }
        }
        return totalCredits > 0 ? totalPoints / totalCredits : -1;
    }

    public static double toGradePoint(double pct) {
        if (pct >= 90) return 4.0;
        if (pct >= 85) return 4.0;
        if (pct >= 80) return 3.75;
        if (pct >= 75) return 3.5;
        if (pct >= 70) return 3.0;
        if (pct >= 65) return 2.75;
        if (pct >= 60) return 2.5;
        if (pct >= 55) return 2.0;
        if (pct >= 50) return 1.75;
        if (pct >= 45) return 1.0;
        return 0.0;
    }

    public static String toLetterGrade(double pct) {
        if (pct >= 90) return "A+";
        if (pct >= 85) return "A";
        if (pct >= 80) return "A-";
        if (pct >= 75) return "B+";
        if (pct >= 70) return "B";
        if (pct >= 65) return "B-";
        if (pct >= 60) return "C+";
        if (pct >= 55) return "C";
        if (pct >= 50) return "C-";
        if (pct >= 45) return "D";
        return "F";
    }

    public static Color gpaColor(double gpa) {
        return ThemeManager.gpaColor(gpa);
    }
}
