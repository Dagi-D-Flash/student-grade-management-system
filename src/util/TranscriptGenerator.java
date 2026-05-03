package util;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TranscriptGenerator {

    private static final DeviceRgb HEADER_BG  = new DeviceRgb(13,  110, 253);
    private static final DeviceRgb SECTION_BG = new DeviceRgb(33,  37,  43);
    private static final DeviceRgb ROW_ALT    = new DeviceRgb(245, 246, 250);
    private static final DeviceRgb BORDER_CLR = new DeviceRgb(200, 200, 200);
    private static final DeviceRgb GREEN      = new DeviceRgb(25,  135, 84);
    private static final DeviceRgb RED        = new DeviceRgb(220, 53,  69);

    private static final SolidBorder THIN_BORDER = new SolidBorder(BORDER_CLR, 0.5f);

    public static void generate(int studentId, String outputPath) throws IOException, SQLException {
        StudentInfo       info      = fetchStudentInfo(studentId);
        List<SubjectRow>  subjects  = fetchSubjects(studentId);
        List<SemesterRow> semesters = fetchSemesters(studentId);
        double cgpa  = GPACalculator.getCGPA(studentId);
        int    rank  = fetchRank(studentId);
        int    total = fetchTotalStudents();

        PdfWriter   writer = new PdfWriter(outputPath);
        PdfDocument pdf    = new PdfDocument(writer);
        Document    doc    = new Document(pdf, PageSize.A4);
        doc.setMargins(36, 36, 36, 36);

        PdfFont bold    = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        addHeader(doc, bold, regular);
        addStudentInfo(doc, bold, regular, info, cgpa, rank, total);
        addSectionTitle(doc, bold, "Academic Record by Subject");
        addSubjectTable(doc, bold, regular, subjects);
        addSectionTitle(doc, bold, "Semester GPA Summary");
        addSemesterTable(doc, bold, regular, semesters, cgpa);
        addFooter(doc, regular);

        doc.close();
    }

    private static void addHeader(Document doc, PdfFont bold, PdfFont regular) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1}))
            .setWidth(UnitValue.createPercentValue(100));
        Cell title = new Cell()
            .add(new Paragraph("OFFICIAL ACADEMIC TRANSCRIPT")
                .setFont(bold).setFontSize(18).setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER))
            .add(new Paragraph("Grade Management System")
                .setFont(regular).setFontSize(11).setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(HEADER_BG)
            .setPadding(16)
            .setBorder(Border.NO_BORDER);
        header.addCell(title);
        doc.add(header);
        doc.add(new Paragraph("\n").setFontSize(4));
    }

    private static void addStudentInfo(Document doc, PdfFont bold, PdfFont regular,
                                       StudentInfo info, double cgpa, int rank, int total) {
        addSectionTitle(doc, bold, "Student Information");
        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
            .setWidth(UnitValue.createPercentValue(100));

        addInfoRow(table, bold, regular, "Full Name",     info.firstName + " " + info.lastName,
                                         "Student No",    info.studentNo);
        addInfoRow(table, bold, regular, "Email",         info.email,
                                         "Gender",        info.gender != null ? info.gender : "—");
        addInfoRow(table, bold, regular, "Date of Birth", info.dob != null ? info.dob : "—",
                                         "Enrolled",      info.enrolledAt != null ? info.enrolledAt : "—");
        addInfoRow(table, bold, regular, "Phone",         info.phone != null ? info.phone : "—",
                                         "Address",       info.address != null ? info.address : "—");

        String cgpaStr  = cgpa < 0 ? "N/A" : String.format("%.2f / 4.0", cgpa);
        String rankStr  = rank < 0 ? "N/A" : rank + " of " + total;
        String standing = cgpa < 0 ? "N/A" : academicStanding(cgpa);
        String letter   = cgpa < 0 ? "N/A" : GPACalculator.toLetterGrade(cgpaToPercent(cgpa));

        addInfoRow(table, bold, regular, "CGPA",          cgpaStr,
                                         "Overall Grade", letter);
        addInfoRow(table, bold, regular, "Rank",          rankStr,
                                         "Standing",      standing);
        doc.add(table);
        doc.add(new Paragraph("\n").setFontSize(6));
    }

    private static void addSubjectTable(Document doc, PdfFont bold, PdfFont regular,
                                        List<SubjectRow> rows) {
        float[] widths = {3f, 1.2f, 1.2f, 1.2f, 1f, 1f, 1f, 1.5f};
        Table table = new Table(UnitValue.createPercentArray(widths))
            .setWidth(UnitValue.createPercentValue(100));

        for (String h : new String[]{"Subject", "Year", "Semester", "Credits", "Avg %", "Grade Pt", "Letter", "Status"}) {
            table.addHeaderCell(new Cell()
                .add(new Paragraph(h).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(SECTION_BG)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(5)
                .setBorder(THIN_BORDER));
        }

        boolean alt = false;
        for (SubjectRow r : rows) {
            DeviceRgb bg          = alt ? ROW_ALT : new DeviceRgb(255, 255, 255);
            DeviceRgb letterColor = r.avg < 60 ? RED : (r.avg >= 80 ? GREEN : new DeviceRgb(33, 37, 43));

            addDataCell(table, r.subjectName,                       regular, 9, bg, TextAlignment.LEFT);
            addDataCell(table, String.valueOf(r.academicYear),      regular, 9, bg, TextAlignment.CENTER);
            addDataCell(table, r.semester,                          regular, 9, bg, TextAlignment.CENTER);
            addDataCell(table, String.valueOf(r.credits),           regular, 9, bg, TextAlignment.CENTER);
            addDataCell(table, String.format("%.1f", r.avg),       regular, 9, bg, TextAlignment.CENTER);
            addDataCell(table, String.format("%.1f", r.gradePoint),regular, 9, bg, TextAlignment.CENTER);
            table.addCell(new Cell()
                .add(new Paragraph(r.letter).setFont(bold).setFontSize(9).setFontColor(letterColor))
                .setBackgroundColor(bg).setTextAlignment(TextAlignment.CENTER)
                .setPadding(4).setBorder(THIN_BORDER));
            addDataCell(table, subjectStatus(r.avg), regular, 9, bg, TextAlignment.CENTER);
            alt = !alt;
        }
        doc.add(table);
        doc.add(new Paragraph("\n").setFontSize(6));
    }

    private static void addSemesterTable(Document doc, PdfFont bold, PdfFont regular,
                                         List<SemesterRow> rows, double cgpa) {
        float[] widths = {1.5f, 1.5f, 1f, 1f, 1f, 1.5f, 2f};
        Table table = new Table(UnitValue.createPercentArray(widths))
            .setWidth(UnitValue.createPercentValue(100));

        for (String h : new String[]{"Academic Year", "Semester", "Courses", "Credits", "GPA", "Letter", "Standing"}) {
            table.addHeaderCell(new Cell()
                .add(new Paragraph(h).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(SECTION_BG)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(5)
                .setBorder(THIN_BORDER));
        }

        boolean alt = false;
        for (SemesterRow r : rows) {
            DeviceRgb bg = alt ? ROW_ALT : new DeviceRgb(255, 255, 255);
            addDataCell(table, String.valueOf(r.academicYear),                          regular, 9, bg, TextAlignment.CENTER);
            addDataCell(table, r.semester,                                              regular, 9, bg, TextAlignment.CENTER);
            addDataCell(table, String.valueOf(r.courseCount),                           regular, 9, bg, TextAlignment.CENTER);
            addDataCell(table, String.valueOf(r.credits),                               regular, 9, bg, TextAlignment.CENTER);
            addDataCell(table, String.format("%.2f", r.gpa),                           regular, 9, bg, TextAlignment.CENTER);
            addDataCell(table, GPACalculator.toLetterGrade(cgpaToPercent(r.gpa)),      bold,    9, bg, TextAlignment.CENTER);
            addDataCell(table, academicStanding(r.gpa),                                regular, 9, bg, TextAlignment.LEFT);
            alt = !alt;
        }

        if (!rows.isEmpty()) {
            DeviceRgb cgpaBg  = new DeviceRgb(230, 240, 255);
            String    cgpaStr = cgpa < 0 ? "N/A" : String.format("%.2f", cgpa);
            DeviceRgb cgpaClr = cgpa >= 3.0 ? GREEN : (cgpa < 2.0 ? RED : new DeviceRgb(13, 110, 253));

            table.addCell(new Cell(1, 4)
                .add(new Paragraph("Cumulative GPA (CGPA)").setFont(bold).setFontSize(9))
                .setBackgroundColor(cgpaBg).setTextAlignment(TextAlignment.RIGHT)
                .setPadding(5).setBorder(THIN_BORDER));
            table.addCell(new Cell()
                .add(new Paragraph(cgpaStr).setFont(bold).setFontSize(11).setFontColor(cgpaClr))
                .setBackgroundColor(cgpaBg).setTextAlignment(TextAlignment.CENTER)
                .setPadding(5).setBorder(THIN_BORDER));
            table.addCell(new Cell()
                .add(new Paragraph(cgpa < 0 ? "N/A" : GPACalculator.toLetterGrade(cgpaToPercent(cgpa)))
                    .setFont(bold).setFontSize(11))
                .setBackgroundColor(cgpaBg).setTextAlignment(TextAlignment.CENTER)
                .setPadding(5).setBorder(THIN_BORDER));
            table.addCell(new Cell()
                .add(new Paragraph(cgpa < 0 ? "N/A" : academicStanding(cgpa)).setFont(bold).setFontSize(9))
                .setBackgroundColor(cgpaBg).setTextAlignment(TextAlignment.LEFT)
                .setPadding(5).setBorder(THIN_BORDER));
        }
        doc.add(table);
        doc.add(new Paragraph("\n").setFontSize(6));
    }

    private static void addSectionTitle(Document doc, PdfFont bold, String text) {
        doc.add(new Paragraph(text)
            .setFont(bold).setFontSize(11)
            .setFontColor(ColorConstants.WHITE)
            .setBackgroundColor(SECTION_BG)
            .setPaddingLeft(10).setPaddingTop(6).setPaddingBottom(6)
            .setMarginBottom(0));
    }

    private static void addFooter(Document doc, PdfFont regular) {
        doc.add(new Paragraph("\n").setFontSize(4));
        String date = new SimpleDateFormat("MMMM dd, yyyy HH:mm").format(new Date());
        doc.add(new Paragraph("Generated on: " + date + "  |  Official transcript — Grade Management System.")
            .setFont(regular).setFontSize(8)
            .setFontColor(new DeviceRgb(120, 120, 120))
            .setTextAlignment(TextAlignment.CENTER)
            .setBorderTop(new SolidBorder(BORDER_CLR, 1))
            .setPaddingTop(6));
    }

    private static void addInfoRow(Table table, PdfFont bold, PdfFont regular,
                                   String l1, String v1, String l2, String v2) {
        table.addCell(new Cell().add(new Paragraph(l1).setFont(bold).setFontSize(9))
            .setPadding(5).setBorder(THIN_BORDER).setBackgroundColor(ROW_ALT));
        table.addCell(new Cell().add(new Paragraph(v1).setFont(regular).setFontSize(9))
            .setPadding(5).setBorder(THIN_BORDER));
        table.addCell(new Cell().add(new Paragraph(l2).setFont(bold).setFontSize(9))
            .setPadding(5).setBorder(THIN_BORDER).setBackgroundColor(ROW_ALT));
        table.addCell(new Cell().add(new Paragraph(v2).setFont(regular).setFontSize(9))
            .setPadding(5).setBorder(THIN_BORDER));
    }

    private static void addDataCell(Table table, String text, PdfFont font, float size,
                                    DeviceRgb bg, TextAlignment align) {
        table.addCell(new Cell()
            .add(new Paragraph(text).setFont(font).setFontSize(size))
            .setBackgroundColor(bg).setTextAlignment(align)
            .setPadding(4).setBorder(THIN_BORDER));
    }

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
                    i.dob        = rs.getDate("date_of_birth") != null
                        ? new SimpleDateFormat("MMM dd, yyyy").format(rs.getDate("date_of_birth")) : null;
                    i.enrolledAt = rs.getDate("enrolled_at") != null
                        ? new SimpleDateFormat("MMM dd, yyyy").format(rs.getDate("enrolled_at")) : null;
                    return i;
                }
            }
        }
        return new StudentInfo();
    }

    private static List<SubjectRow> fetchSubjects(int studentId) throws SQLException {
        List<SubjectRow> list = new ArrayList<>();
        String sql =
            "SELECT sub.name, sub.credits, c.academic_year, c.semester, " +
            "       SUM(g.score/g.max_score*100*g.weight)/SUM(g.weight) AS avg_pct " +
            "FROM grades g " +
            "JOIN enrollments e  ON e.id   = g.enrollment_id " +
            "JOIN courses c      ON c.id   = e.course_id " +
            "JOIN subjects sub   ON sub.id = c.subject_id " +
            "WHERE e.student_id = ? AND g.max_score > 0 " +
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
            "JOIN enrollments e ON e.student_id = s.id";
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getInt("id"));
        }
        List<double[]> gpas = new ArrayList<>();
        for (int id : ids) gpas.add(new double[]{id, GPACalculator.getCGPA(id)});
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

    private static String subjectStatus(double avg) {
        if (avg >= 90) return "Excellent";
        if (avg >= 80) return "Good";
        if (avg >= 70) return "Average";
        if (avg >= 60) return "Passing";
        return "Failing";
    }

    private static String academicStanding(double gpa) {
        if (gpa >= 3.7) return "Summa Cum Laude";
        if (gpa >= 3.5) return "Magna Cum Laude";
        if (gpa >= 3.0) return "Cum Laude";
        if (gpa >= 2.0) return "Good Standing";
        if (gpa >= 1.0) return "Academic Probation";
        return "Academic Warning";
    }

    private static double cgpaToPercent(double cgpa) {
        if (cgpa >= 4.0) return 97;
        if (cgpa >= 3.7) return 90;
        if (cgpa >= 3.3) return 87;
        if (cgpa >= 3.0) return 83;
        if (cgpa >= 2.7) return 80;
        if (cgpa >= 2.3) return 77;
        if (cgpa >= 2.0) return 73;
        if (cgpa >= 1.7) return 70;
        if (cgpa >= 1.3) return 67;
        if (cgpa >= 1.0) return 63;
        if (cgpa >= 0.7) return 60;
        return 0;
    }

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
}
