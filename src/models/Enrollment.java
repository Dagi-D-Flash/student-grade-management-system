package models;

import java.util.Date;

public class Enrollment {
    private int id;
    private Student student;
    private Course course;
    private Date enrolledAt;
    private String status;

    public Enrollment() {}

    public Enrollment(int id, Student student, Course course, Date enrolledAt, String status) {
        this.id = id;
        this.student = student;
        this.course = course;
        this.enrolledAt = enrolledAt;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public Date getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(Date enrolledAt) { this.enrolledAt = enrolledAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
