package util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Generates an HTML transcript and returns it as a String.
 * No external PDF library required — the TranscriptPanel renders it
 * in a JEditorPane and uses Java's built-in print dialog.
 */
public class TranscriptGenerator {

    public static String generateHtml(int studentId) throws SQLException {
        return generateHtml(studentId, ThemeManager.isDarkMode());
    }

    /**
     * Generates a transcript scoped to a specific teacher's assigned courses only.
     * Used when a teacher selects "All My Courses" — shows only the subjects
     * that teacher teaches, not the student's full academic record.
     */
    public static String generateTeacherTranscriptHtml(int studentId, int teacherUserId,
                                                        boolean dark) throws SQLException {
        StudentInfo info = fetchStudentInfo(studentId);
        if (info == null) throw new SQLException("Student ID " + studentId + " not found.");

        // Fetch only subjects from this teacher's courses
        List<SubjectRow> subjects = fetchSubjectsForTeacher(studentId, teacherUserId);

        // Compute scoped GPA (credit-weighted, teacher's courses only)
        double scopedGpa = -1;
        double totalPoints = 0; int totalCredits = 0;
        for (SubjectRow r : subjects) {
            totalPoints  += r.gradePoint * r.credits;
            totalCredits += r.credits;
        }
        if (totalCredits > 0) scopedGpa = totalPoints / totalCredits;

        // Theme colours
        String bodyBg    = dark ? "#0f172a" : "#ffffff";
        String bodyText  = dark ? "#e5e7eb" : "#111827";
        String surfaceBg = dark ? "#111827" : "#f9fafb";
        String altBg     = dark ? "#1f2937" : "#f3f4f6";
        String borderClr = dark ? "#374151" : "#e5e7eb";
        String mutedText = dark ? "#9ca3af" : "#6b7280";
        String labelText = dark ? "#d1d5db" : "#374151";
        String thBg      = dark ? "#1f2937" : "#374151";
        String sectionBg = dark ? "#0f172a"  : "#111827";
        String cgpaBg    = dark ? "#064e3b"  : "#d1fae5";
        String cgpaText  = dark ? "#6ee7b7"  : "#065f46";
        String gradeA    = dark ? "#6ee7b7"  : "#065f46";
        String gradeB    = dark ? "#93c5fd"  : "#1e40af";
        String gradeC    = dark ? "#fcd34d"  : "#92400e";
        String gradeD    = dark ? "#fb923c"  : "#9a3412";
        String gradeF    = dark ? "#fca5a5"  : "#991b1b";

        int subjectCount = subjects.size();
        int baseFontPx = subjectCount > 12 ? 7 : (subjectCount > 8 ? 8 : 9);
        int titlePx    = baseFontPx + 3;
        int thPad      = 2;
        int tdPad      = 1;
        int sectionPad = 2;
        int bodyMargin = 3;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<style>");
        sb.append("@page{size:A4 landscape;margin:0;}");
        sb.append("*{box-sizing:border-box;}");
        sb.append("body{font-family:Arial,sans-serif;font-size:").append(baseFontPx)
          .append("px;color:").append(bodyText)
          .append(";background:").append(bodyBg).append(";margin:0;}");
        sb.append("h1{font-size:").append(titlePx).append("px;text-align:center;color:#fff;background:#10a37f;padding:5px;margin:0;}");
        sb.append(".subtitle{text-align:center;font-size:").append(baseFontPx - 1)
          .append("px;color:#fff;background:#10a37f;padding:1px 0 4px 0;margin-bottom:4px;}");
        sb.append(".section-title{background:").append(sectionBg)
          .append(";color:#fff;font-size:").append(baseFontPx - 1)
          .append("px;font-weight:bold;padding:").append(sectionPad).append("px 6px;margin:4px 0 0 0;}");
        sb.append("table{width:100%;border-collapse:collapse;margin-bottom:2px;}");
        sb.append("th{background:").append(thBg)
          .append(";color:#fff;padding:").append(thPad).append("px 4px;text-align:left;font-size:")
          .append(baseFontPx - 1).append("px;}");
        sb.append("td{padding:").append(tdPad).append("px 4px;border-bottom:1px solid ").append(borderClr)
          .append(";font-size:").append(baseFontPx - 1).append("px;color:").append(bodyText).append(";}");
        sb.append("tr.alt{background:").append(altBg).append(";}");
        sb.append("tr{background:").append(surfaceBg).append(";}");
        sb.append(".info-label{font-weight:bold;width:90px;color:").append(labelText).append(";}");
        sb.append(".gpa-row td{background:").append(cgpaBg)
          .append(";color:").append(cgpaText).append(";font-weight:bold;}");
        sb.append(".grade-a{color:").append(gradeA).append(";font-weight:bold;}");
        sb.append(".grade-b{color:").append(gradeB).append(";font-weight:bold;}");
        sb.append(".grade-c{color:").append(gradeC).append(";font-weight:bold;}");
        sb.append(".grade-d{color:").append(gradeD).append(";font-weight:bold;}");
        sb.append(".grade-f{color:").append(gradeF).append(";font-weight:bold;}");
        sb.append(".footer{text-align:center;font-size:").append(baseFontPx - 2)
          .append("px;color:").append(mutedText)
          .append(";margin-top:4px;border-top:1px solid ").append(borderClr).append(";padding-top:3px;}");
        sb.append("</style></head><body>");

        sb.append("<h1>COURSE PERFORMANCE REPORT</h1>");
        sb.append("<div class='subtitle'>Grade Management System — Teacher's Assigned Courses</div>");

        // Student info
        sb.append("<div class='section-title'>Student Information</div>");
        sb.append("<table>");
        infoRow(sb, "Full Name",  info.firstName + " " + info.lastName, "Student No", info.studentNo);
        infoRow(sb, "Email",      nvl(info.email), "Gender", nvl(info.gender));
        String gpaStr  = scopedGpa < 0 ? "N/A" : String.format("%.2f / 4.0", scopedGpa);
        String gpaLtr  = scopedGpa < 0 ? "N/A" : GPACalculator.toLetterGrade(cgpaToPercent(scopedGpa));
        String standing = scopedGpa < 0 ? "N/A" : academicStanding(scopedGpa);
        infoRow(sb, "Courses GPA", gpaStr, "Overall Grade", gpaLtr);
        infoRow(sb, "Standing",    standing, "", "");
        sb.append("</table>");

        // Subject table
        sb.append("<div class='section-title'>Academic Record (Teacher's Courses Only)</div>");
        if (subjects.isEmpty()) {
            sb.append("<p style='color:#6b7280;padding:8px;'>No grade records found for this teacher's courses.</p>");
        } else {
            sb.append("<table><tr>");
            for (String h : new String[]{"Subject", "Year", "Semester", "Credits", "Avg %", "Grade Pt", "Letter", "Status"})
                sb.append("<th>").append(h).append("</th>");
            sb.append("</tr>");
            boolean alt = false;
            for (SubjectRow r : subjects) {
                sb.append(alt ? "<tr class='alt'>" : "<tr>");
                sb.append("<td>").append(esc(r.subjectName)).append("</td>");
                sb.append("<td style='text-align:center'>").append(r.academicYear).append("</td>");
                sb.append("<td style='text-align:center'>").append(esc(r.semester)).append("</td>");
                sb.append("<td style='text-align:center'>").append(r.credits).append("</td>");
                sb.append("<td style='text-align:center'>").append(String.format("%.1f", r.avg)).append("</td>");
                sb.append("<td style='text-align:center'>").append(String.format("%.2f", r.gradePoint)).append("</td>");
                sb.append("<td style='text-align:center' class='").append(gradeClass(r.letter)).append("'>").append(r.letter).append("</td>");
                sb.append("<td style='text-align:center'>").append(subjectStatus(r.avg)).append("</td>");
                sb.append("</tr>");
                alt = !alt;
            }
            // GPA summary row
            sb.append("<tr class='gpa-row'>");
            sb.append("<td colspan='4' style='text-align:right;font-weight:bold;'>Courses GPA</td>");
            sb.append("<td></td>");
            sb.append("<td style='text-align:center'>").append(scopedGpa < 0 ? "N/A" : String.format("%.2f", scopedGpa)).append("</td>");
            sb.append("<td style='text-align:center'>").append(gpaLtr).append("</td>");
            sb.append("<td>").append(standing).append("</td>");
            sb.append("</tr>");
            sb.append("</table>");
        }

        String date = new SimpleDateFormat("MMMM dd, yyyy HH:mm").format(new Date());
        sb.append("<div class='footer'>Generated on: ").append(date)
          .append(" &nbsp;|&nbsp; Course Performance Report — Grade Management System</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    /** Fetches subjects for a student scoped to a specific teacher's courses only. */
    private static List<SubjectRow> fetchSubjectsForTeacher(int studentId,
                                                             int teacherUserId) throws SQLException {
        List<SubjectRow> list = new ArrayList<>();
        String sql =
            "SELECT sub.name, sub.credits, c.academic_year, c.semester, " +
            "       SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM grades g " +
            "JOIN enrollments e  ON e.id   = g.enrollment_id " +
            "JOIN courses c      ON c.id   = e.course_id " +
            "JOIN subjects sub   ON sub.id = c.subject_id " +
            "JOIN teachers t     ON t.id   = c.teacher_id " +
            "JOIN users u        ON u.id   = t.user_id " +
            "JOIN course_grade_components cgc ON cgc.id = g.component_id AND cgc.course_id = c.id " +
            "WHERE e.student_id = ? AND u.id = ? " +
            "GROUP BY e.id, sub.name, sub.credits, c.academic_year, c.semester " +
            "ORDER BY c.academic_year, c.semester, sub.name";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, teacherUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SubjectRow r  = new SubjectRow();
                    r.subjectName = rs.getString("name");
                    r.credits     = rs.getInt("credits");
                    r.academicYear= rs.getInt("academic_year");
                    r.semester    = rs.getString("semester");
                    r.avg         = rs.getDouble("avg_pct");
                    r.gradePoint  = GPACalculator.toGradePoint(r.avg);
                    r.letter      = GPACalculator.toLetterGrade(r.avg);
                    list.add(r);
                }
            }
        }
        return list;
    }

    /**
     * Generates a detailed course-specific report for a teacher.
     * Shows student info + every grade component with its score for that course.
     */
    public static String generateCourseHtml(int studentId, int courseId,
                                             boolean dark) throws SQLException {
        StudentInfo info = fetchStudentInfo(studentId);
        if (info == null) throw new SQLException("Student ID " + studentId + " not found.");

        // Fetch course info
        String courseName = "", subjectCode = "", section = "", semester = "";
        int academicYear = 0;
        String sqlCourse =
            "SELECT sub.code, sub.name, c.section, c.academic_year, c.semester " +
            "FROM courses c JOIN subjects sub ON sub.id = c.subject_id WHERE c.id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlCourse)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    subjectCode  = rs.getString("code");
                    courseName   = rs.getString("name");
                    section      = rs.getString("section");
                    academicYear = rs.getInt("academic_year");
                    semester     = rs.getString("semester");
                }
            }
        }

        // Fetch grade components and student scores for this course
        List<ComponentScoreRow> components = new ArrayList<>();
        String sqlGrades =
            "SELECT cgc.component_name, cgc.weight, cgc.max_score, " +
            "       g.score, g.remarks " +
            "FROM course_grade_components cgc " +
            "LEFT JOIN grades g ON g.component_id = cgc.id " +
            "  AND g.enrollment_id = (" +
            "    SELECT id FROM enrollments WHERE student_id = ? AND course_id = ? LIMIT 1" +
            "  ) " +
            "WHERE cgc.course_id = ? " +
            "ORDER BY cgc.id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlGrades)) {
            ps.setInt(1, studentId);
            ps.setInt(2, courseId);
            ps.setInt(3, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ComponentScoreRow r = new ComponentScoreRow();
                    r.name     = rs.getString("component_name");
                    r.weight   = rs.getDouble("weight");
                    r.maxScore = rs.getDouble("max_score");
                    r.score    = rs.getDouble("score");
                    r.hasScore = !rs.wasNull();
                    r.remarks  = rs.getString("remarks");
                    components.add(r);
                }
            }
        }

        // Compute total weighted score
        double totalWeighted = 0;
        boolean anyScore = false;
        for (ComponentScoreRow r : components) {
            if (r.hasScore && r.maxScore > 0) {
                totalWeighted += (r.score / r.maxScore) * r.weight;
                anyScore = true;
            }
        }

        // Theme colours
        String bodyBg    = dark ? "#0f172a" : "#ffffff";
        String bodyText  = dark ? "#e5e7eb" : "#111827";
        String surfaceBg = dark ? "#111827" : "#f9fafb";
        String altBg     = dark ? "#1f2937" : "#f3f4f6";
        String borderClr = dark ? "#374151" : "#e5e7eb";
        String mutedText = dark ? "#9ca3af" : "#6b7280";
        String labelText = dark ? "#d1d5db" : "#374151";
        String thBg      = dark ? "#1f2937" : "#374151";
        String sectionBg = dark ? "#0f172a"  : "#111827";
        String totalBg   = dark ? "#064e3b"  : "#d1fae5";
        String totalText = dark ? "#6ee7b7"  : "#065f46";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<style>");
        sb.append("@page{size:A4 landscape;margin:0;}");
        sb.append("*{box-sizing:border-box;}");
        sb.append("body{font-family:Arial,sans-serif;font-size:10px;color:").append(bodyText)
          .append(";background:").append(bodyBg).append(";margin:0;}");
        sb.append("h1{font-size:13px;text-align:center;color:#fff;background:#10a37f;padding:5px;margin:0;}");
        sb.append(".subtitle{text-align:center;font-size:9px;color:#fff;background:#10a37f;padding:1px 0 4px 0;margin-bottom:6px;}");
        sb.append(".section-title{background:").append(sectionBg)
          .append(";color:#fff;font-size:9px;font-weight:bold;padding:3px 6px;margin:6px 0 0 0;}");
        sb.append("table{width:100%;border-collapse:collapse;margin-bottom:4px;}");
        sb.append("th{background:").append(thBg)
          .append(";color:#fff;padding:3px 5px;text-align:left;font-size:9px;}");
        sb.append("td{padding:3px 5px;border-bottom:1px solid ").append(borderClr)
          .append(";font-size:9px;color:").append(bodyText).append(";}");
        sb.append("tr.alt{background:").append(altBg).append(";}");
        sb.append("tr{background:").append(surfaceBg).append(";}");
        sb.append(".info-label{font-weight:bold;width:90px;color:").append(labelText).append(";}");
        sb.append(".total-row td{background:").append(totalBg)
          .append(";color:").append(totalText).append(";font-weight:bold;}");
        sb.append(".footer{text-align:center;font-size:8px;color:").append(mutedText)
          .append(";margin-top:6px;border-top:1px solid ").append(borderClr).append(";padding-top:3px;}");
        sb.append("</style></head><body>");

        sb.append("<h1>COURSE GRADE REPORT</h1>");
        sb.append("<div class='subtitle'>Grade Management System</div>");

        // Course info
        sb.append("<div class='section-title'>Course Information</div>");
        sb.append("<table>");
        infoRow(sb, "Subject Code", subjectCode, "Subject Name", courseName);
        infoRow(sb, "Section",      section,     "Academic Year", academicYear + " — " + semester);
        sb.append("</table>");

        // Student info
        sb.append("<div class='section-title'>Student Information</div>");
        sb.append("<table>");
        infoRow(sb, "Full Name",  info.firstName + " " + info.lastName, "Student No", info.studentNo);
        infoRow(sb, "Email",      nvl(info.email), "Gender", nvl(info.gender));
        sb.append("</table>");

        // Grade components
        sb.append("<div class='section-title'>Grade Breakdown</div>");
        sb.append("<table><tr>");
        for (String h : new String[]{"Component", "Weight %", "Max Score", "Score", "Weighted", "Letter"})
            sb.append("<th>").append(h).append("</th>");
        sb.append("</tr>");
        boolean alt = false;
        for (ComponentScoreRow r : components) {
            double weighted = (r.hasScore && r.maxScore > 0)
                ? (r.score / r.maxScore) * r.weight : 0;
            double pct = (r.hasScore && r.maxScore > 0)
                ? (r.score / r.maxScore) * 100 : 0;
            String letter = r.hasScore ? GPACalculator.toLetterGrade(pct) : "—";
            sb.append(alt ? "<tr class='alt'>" : "<tr>");
            sb.append("<td>").append(esc(r.name)).append("</td>");
            sb.append("<td style='text-align:center'>").append(String.format("%.0f%%", r.weight)).append("</td>");
            sb.append("<td style='text-align:center'>").append(formatScore(r.maxScore)).append("</td>");
            sb.append("<td style='text-align:center'>")
              .append(r.hasScore ? formatScore(r.score) : "—").append("</td>");
            sb.append("<td style='text-align:center'>")
              .append(r.hasScore ? String.format("%.2f", weighted) : "—").append("</td>");
            sb.append("<td style='text-align:center'>").append(letter).append("</td>");
            sb.append("</tr>");
            alt = !alt;
        }
        // Total row
        String totalLetter = anyScore ? GPACalculator.toLetterGrade(totalWeighted) : "—";
        double gp = anyScore ? GPACalculator.toGradePoint(totalWeighted) : 0;
        sb.append("<tr class='total-row'>");
        sb.append("<td colspan='3' style='text-align:right;font-weight:bold;'>Total</td>");
        sb.append("<td></td>");
        sb.append("<td style='text-align:center'>")
          .append(anyScore ? String.format("%.2f / 100", totalWeighted) : "—").append("</td>");
        sb.append("<td style='text-align:center'>").append(totalLetter).append("</td>");
        sb.append("</tr>");
        sb.append("</table>");

        // Summary
        sb.append("<div class='section-title'>Summary</div>");
        sb.append("<table>");
        infoRow(sb, "Total Score",  anyScore ? String.format("%.2f / 100", totalWeighted) : "N/A",
                    "Letter Grade", totalLetter);
        infoRow(sb, "Grade Point",  anyScore ? String.format("%.2f / 4.0", gp) : "N/A",
                    "Status",       anyScore ? subjectStatus(totalWeighted) : "N/A");
        sb.append("</table>");

        String date = new SimpleDateFormat("MMMM dd, yyyy HH:mm").format(new Date());
        sb.append("<div class='footer'>Generated on: ").append(date)
          .append(" &nbsp;|&nbsp; Course Grade Report — Grade Management System</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    /** Always call with dark=false for printing to paper. */
    public static String generateHtml(int studentId, boolean dark) throws SQLException {
        StudentInfo       info      = fetchStudentInfo(studentId);
        List<SubjectRow>  subjects  = fetchSubjects(studentId);
        List<SemesterRow> semesters = fetchSemesters(studentId);
        double cgpa  = GPACalculator.getCGPA(studentId);
        int    rank  = fetchRank(studentId);
        int    total = fetchTotalStudents();

        if (info == null) throw new SQLException("Student ID " + studentId + " not found.");
        String bodyBg    = dark ? "#0f172a" : "#ffffff";
        String bodyText  = dark ? "#e5e7eb" : "#111827";
        String surfaceBg = dark ? "#111827" : "#f9fafb";
        String altBg     = dark ? "#1f2937" : "#f3f4f6";
        String borderClr = dark ? "#374151" : "#e5e7eb";
        String mutedText = dark ? "#9ca3af" : "#6b7280";
        String labelText = dark ? "#d1d5db" : "#374151";
        String thBg      = dark ? "#1f2937" : "#374151";
        String sectionBg = dark ? "#0f172a"  : "#111827";
        String cgpaBg    = dark ? "#064e3b"  : "#d1fae5";
        String cgpaText  = dark ? "#6ee7b7"  : "#065f46";
        String gradeA    = dark ? "#6ee7b7"  : "#065f46";
        String gradeB    = dark ? "#93c5fd"  : "#1e40af";
        String gradeC    = dark ? "#fcd34d"  : "#92400e";
        String gradeD    = dark ? "#fb923c"  : "#9a3412";
        String gradeF    = dark ? "#fca5a5"  : "#991b1b";

        int subjectCount = subjects.size();
        // Dynamic font size: shrink proportionally if many subjects
        int baseFontPx = subjectCount > 12 ? 7 : (subjectCount > 8 ? 8 : 9);
        int titlePx    = baseFontPx + 3;
        int thPad      = 2;
        int tdPad      = 1;
        int sectionPad = 2;
        int bodyMargin = 3;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<style>");
        sb.append("@page{size:A4 landscape;margin:0;}");
        sb.append("*{box-sizing:border-box;}");
        sb.append("body{font-family:Arial,sans-serif;font-size:").append(baseFontPx)
          .append("px;color:").append(bodyText)
          .append(";background:").append(bodyBg).append(";margin:0;}");
        sb.append("h1{font-size:").append(titlePx).append("px;text-align:center;color:#fff;background:#10a37f;padding:3px;margin:0;}");
        sb.append(".subtitle{text-align:center;font-size:").append(baseFontPx - 1)
          .append("px;color:#fff;background:#10a37f;padding:1px 0 2px 0;margin-bottom:3px;}");
        sb.append(".section-title{background:").append(sectionBg)
          .append(";color:#fff;font-size:").append(baseFontPx - 1)
          .append("px;font-weight:bold;padding:").append(sectionPad).append("px 5px;margin:3px 0 0 0;}");
        sb.append("table{width:100%;border-collapse:collapse;margin-bottom:1px;}");
        sb.append("th{background:").append(thBg)
          .append(";color:#fff;padding:").append(thPad).append("px 3px;text-align:left;font-size:")
          .append(baseFontPx - 1).append("px;}");
        sb.append("td{padding:").append(tdPad).append("px 3px;border-bottom:1px solid ").append(borderClr)
          .append(";font-size:").append(baseFontPx - 1).append("px;color:").append(bodyText).append(";}");
        sb.append("tr.alt{background:").append(altBg).append(";}");
        sb.append("tr{background:").append(surfaceBg).append(";}");
        sb.append(".info-label{font-weight:bold;width:80px;color:").append(labelText).append(";}");
        sb.append(".cgpa-row td{background:").append(cgpaBg)
          .append(";color:").append(cgpaText).append(";font-weight:bold;}");
        sb.append(".grade-a{color:").append(gradeA).append(";font-weight:bold;}");
        sb.append(".grade-b{color:").append(gradeB).append(";font-weight:bold;}");
        sb.append(".grade-c{color:").append(gradeC).append(";font-weight:bold;}");
        sb.append(".grade-d{color:").append(gradeD).append(";font-weight:bold;}");
        sb.append(".grade-f{color:").append(gradeF).append(";font-weight:bold;}");
        sb.append(".footer{text-align:center;font-size:").append(baseFontPx - 2)
          .append("px;color:").append(mutedText)
          .append(";margin-top:3px;border-top:1px solid ").append(borderClr).append(";padding-top:2px;}");
        sb.append("</style></head><body>");

        // Header
        sb.append("<h1>OFFICIAL ACADEMIC TRANSCRIPT</h1>");
        sb.append("<div class='subtitle'>Grade Management System</div>");

        // Student info
        sb.append("<div class='section-title'>Student Information</div>");
        sb.append("<table>");
        infoRow(sb, "Full Name",     info.firstName + " " + info.lastName,
                    "Student No",    info.studentNo);
        infoRow(sb, "Email",         nvl(info.email),
                    "Gender",        nvl(info.gender));
        infoRow(sb, "Date of Birth", nvl(info.dob),
                    "Enrolled",      nvl(info.enrolledAt));
        infoRow(sb, "Phone",         nvl(info.phone),
                    "Address",       nvl(info.address));
        String cgpaStr  = cgpa < 0 ? "N/A" : String.format("%.2f / 4.0", cgpa);
        String rankStr  = rank < 0 ? "N/A" : rank + " of " + total;
        String standing = cgpa < 0 ? "N/A" : academicStanding(cgpa);
        String letter   = cgpa < 0 ? "N/A" : GPACalculator.toLetterGrade(cgpaToPercent(cgpa));
        infoRow(sb, "CGPA",          cgpaStr,
                    "Overall Grade", letter);
        infoRow(sb, "Class Rank",    rankStr,
                    "Standing",      standing);
        sb.append("</table>");

        // Subject table
        sb.append("<div class='section-title'>Academic Record by Subject</div>");
        if (subjects.isEmpty()) {
            sb.append("<p style='color:#6b7280;padding:8px;'>No grade records found.</p>");
        } else {
            sb.append("<table><tr>");
            for (String h : new String[]{"Subject", "Year", "Semester", "Credits", "Avg %", "Grade Pt", "Letter", "Status"})
                sb.append("<th>").append(h).append("</th>");
            sb.append("</tr>");
            boolean alt = false;
            for (SubjectRow r : subjects) {
                sb.append(alt ? "<tr class='alt'>" : "<tr>");
                sb.append("<td>").append(esc(r.subjectName)).append("</td>");
                sb.append("<td style='text-align:center'>").append(r.academicYear).append("</td>");
                sb.append("<td style='text-align:center'>").append(esc(r.semester)).append("</td>");
                sb.append("<td style='text-align:center'>").append(r.credits).append("</td>");
                sb.append("<td style='text-align:center'>").append(String.format("%.1f", r.avg)).append("</td>");
                sb.append("<td style='text-align:center'>").append(String.format("%.2f", r.gradePoint)).append("</td>");
                sb.append("<td style='text-align:center' class='").append(gradeClass(r.letter)).append("'>").append(r.letter).append("</td>");
                sb.append("<td style='text-align:center'>").append(subjectStatus(r.avg)).append("</td>");
                sb.append("</tr>");
                alt = !alt;
            }
            sb.append("</table>");
        }

        // Semester GPA table
        sb.append("<div class='section-title'>Semester GPA Summary</div>");
        if (semesters.isEmpty()) {
            sb.append("<p style='color:#6b7280;padding:8px;'>No semester data found.</p>");
        } else {
            sb.append("<table><tr>");
            for (String h : new String[]{"Academic Year", "Semester", "Courses", "Credits", "GPA", "Letter", "Standing"})
                sb.append("<th>").append(h).append("</th>");
            sb.append("</tr>");
            boolean alt = false;
            for (SemesterRow r : semesters) {
                sb.append(alt ? "<tr class='alt'>" : "<tr>");
                sb.append("<td style='text-align:center'>").append(r.academicYear).append("</td>");
                sb.append("<td style='text-align:center'>").append(esc(r.semester)).append("</td>");
                sb.append("<td style='text-align:center'>").append(r.courseCount).append("</td>");
                sb.append("<td style='text-align:center'>").append(r.credits).append("</td>");
                sb.append("<td style='text-align:center'>").append(String.format("%.2f", r.gpa)).append("</td>");
                sb.append("<td style='text-align:center' class='").append(gradeClass(GPACalculator.toLetterGrade(cgpaToPercent(r.gpa)))).append("'>")
                  .append(GPACalculator.toLetterGrade(cgpaToPercent(r.gpa))).append("</td>");
                sb.append("<td>").append(academicStanding(r.gpa)).append("</td>");
                sb.append("</tr>");
                alt = !alt;
            }
            // CGPA summary row
            sb.append("<tr class='cgpa-row'>");
            sb.append("<td colspan='4' style='text-align:right;font-weight:bold;'>Cumulative GPA (CGPA)</td>");
            sb.append("<td style='text-align:center'>").append(cgpa < 0 ? "N/A" : String.format("%.2f", cgpa)).append("</td>");
            sb.append("<td style='text-align:center'>").append(letter).append("</td>");
            sb.append("<td>").append(standing).append("</td>");
            sb.append("</tr>");
            sb.append("</table>");
        }

        // Footer
        String date = new SimpleDateFormat("MMMM dd, yyyy HH:mm").format(new Date());
        sb.append("<div class='footer'>Generated on: ").append(date)
          .append(" &nbsp;|&nbsp; Official transcript — Grade Management System</div>");

        sb.append("</body></html>");
        return sb.toString();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void infoRow(StringBuilder sb, String l1, String v1, String l2, String v2) {
        sb.append("<tr><td class='info-label'>").append(l1).append("</td>")
          .append("<td>").append(esc(v1)).append("</td>")
          .append("<td class='info-label'>").append(l2).append("</td>")
          .append("<td>").append(esc(v2)).append("</td></tr>");
    }

    private static String gradeClass(String letter) {
        if (letter == null || letter.isEmpty()) return "";
        switch (letter.charAt(0)) {
            case 'A': return "grade-a";
            case 'B': return "grade-b";
            case 'C': return "grade-c";
            case 'D': return "grade-d";
            case 'F': return "grade-f";
            default:  return "";
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String nvl(String s) { return s != null ? s : "—"; }

    private static String subjectStatus(double avg) {
        if (avg >= 90) return "Distinction";
        if (avg >= 75) return "Very Good";
        if (avg >= 60) return "Good";
        if (avg >  50) return "Pass";
        if (avg >= 40) return "Probation";
        return "Fail";
    }

    private static String academicStanding(double gpa) {
        if (gpa >= 3.75) return "Distinction";
        if (gpa >= 3.5)  return "Very Good";
        if (gpa >= 3.0)  return "Good";
        if (gpa >  2.0)  return "Pass";
        if (gpa >= 1.0)  return "Probation";
        return "Fail";
    }

    private static double cgpaToPercent(double cgpa) {
        if (cgpa >= 4.0)  return 90;
        if (cgpa >= 3.75) return 85;
        if (cgpa >= 3.5)  return 80;
        if (cgpa >= 3.0)  return 75;
        if (cgpa >= 2.75) return 70;
        if (cgpa >= 2.5)  return 65;
        if (cgpa >= 2.0)  return 60;
        if (cgpa >= 1.75) return 55;
        if (cgpa >= 1.0)  return 50;
        return 45;
    }

    // ── DB fetch methods ──────────────────────────────────────────────────────

    private static StudentInfo fetchStudentInfo(int studentId) throws SQLException {
        String sql =
            "SELECT s.first_name, s.last_name, s.student_no, s.gender, s.phone, s.address, " +
            "       s.date_of_birth, s.enrolled_at, u.email " +
            "FROM students s JOIN users u ON u.id = s.user_id WHERE s.id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StudentInfo i = new StudentInfo();
                    i.firstName  = rs.getString("first_name");
                    i.lastName   = rs.getString("last_name");
                    i.studentNo  = rs.getString("student_no");
                    i.gender     = rs.getString("gender");
                    i.phone      = rs.getString("phone");
                    i.address    = rs.getString("address");
                    i.email      = rs.getString("email");
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy");
                    i.dob        = rs.getDate("date_of_birth") != null
                        ? sdf.format(rs.getDate("date_of_birth")) : null;
                    i.enrolledAt = rs.getDate("enrolled_at") != null
                        ? sdf.format(rs.getDate("enrolled_at")) : null;
                    return i;
                }
            }
        }
        return null;
    }

    private static List<SubjectRow> fetchSubjects(int studentId) throws SQLException {
        List<SubjectRow> list = new ArrayList<>();
        String sql =
            "SELECT sub.name, sub.credits, c.academic_year, c.semester, " +
            "       SUM((g.score / cgc.max_score) * cgc.weight) AS avg_pct " +
            "FROM grades g " +
            "JOIN course_grade_components cgc ON cgc.id = g.component_id " +
            "JOIN enrollments e  ON e.id   = g.enrollment_id " +
            "JOIN courses c      ON c.id   = e.course_id " +
            "JOIN subjects sub   ON sub.id = c.subject_id " +
            "WHERE e.student_id = ? " +
            "GROUP BY e.id, sub.name, sub.credits, c.academic_year, c.semester " +
            "ORDER BY c.academic_year, c.semester, sub.name";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SubjectRow r  = new SubjectRow();
                    r.subjectName = rs.getString("name");
                    r.credits     = rs.getInt("credits");
                    r.academicYear= rs.getInt("academic_year");
                    r.semester    = rs.getString("semester");
                    r.avg         = rs.getDouble("avg_pct");
                    r.gradePoint  = GPACalculator.toGradePoint(r.avg);
                    r.letter      = GPACalculator.toLetterGrade(r.avg);
                    list.add(r);
                }
            }
        }
        return list;
    }

    private static List<SemesterRow> fetchSemesters(int studentId) throws SQLException {
        List<GPACalculator.SemesterGPA> raw = GPACalculator.getSemesterGPAs(studentId);
        List<SemesterRow> list = new ArrayList<>();
        for (GPACalculator.SemesterGPA s : raw) {
            SemesterRow r  = new SemesterRow();
            r.academicYear = s.academicYear;
            r.semester     = s.semester;
            r.courseCount  = s.courseCount;
            r.credits      = s.totalCredits;
            r.gpa          = s.gpa;
            list.add(r);
        }
        return list;
    }

    private static int fetchRank(int studentId) throws SQLException {
        String sql =
            "SELECT DISTINCT s.id FROM students s " +
            "JOIN enrollments e ON e.student_id = s.id " +
            "JOIN grades g ON g.enrollment_id = e.id";
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getInt("id"));
        }
        List<double[]> gpas = new ArrayList<>();
        for (int id : ids) {
            double g = GPACalculator.getCGPA(id);
            gpas.add(new double[]{id, g < 0 ? -1 : g});
        }
        gpas.sort((a, b) -> Double.compare(b[1], a[1]));
        for (int i = 0; i < gpas.size(); i++) {
            if ((int) gpas.get(i)[0] == studentId) return i + 1;
        }
        return -1;
    }

    private static int fetchTotalStudents() throws SQLException {
        String sql = "SELECT COUNT(*) FROM students";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    // ── inner classes ─────────────────────────────────────────────────────────

    private static class StudentInfo {
        String firstName, lastName, studentNo, gender, phone, address, email, dob, enrolledAt;
    }

    private static class SubjectRow {
        String subjectName, semester, letter;
        int    credits, academicYear;
        double avg, gradePoint;
    }

    private static class SemesterRow {
        int    academicYear, courseCount, credits;
        String semester;
        double gpa;
    }

    private static class ComponentScoreRow {
        String name, remarks;
        double weight, maxScore, score;
        boolean hasScore;
    }

    private static String formatScore(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.2f", v);
    }
}
