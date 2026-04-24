package models;

public class Course {
    private int id;
    private Subject subject;
    private Teacher teacher;
    private String section;
    private int academicYear;
    private String semester;
    private int maxStudents;

    public Course() {}

    public Course(int id, Subject subject, Teacher teacher, String section, int academicYear, String semester, int maxStudents) {
        this.id = id;
        this.subject = subject;
        this.teacher = teacher;
        this.section = section;
        this.academicYear = academicYear;
        this.semester = semester;
        this.maxStudents = maxStudents;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }

    public Teacher getTeacher() { return teacher; }
    public void setTeacher(Teacher teacher) { this.teacher = teacher; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public int getAcademicYear() { return academicYear; }
    public void setAcademicYear(int academicYear) { this.academicYear = academicYear; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public int getMaxStudents() { return maxStudents; }
    public void setMaxStudents(int maxStudents) { this.maxStudents = maxStudents; }
}
