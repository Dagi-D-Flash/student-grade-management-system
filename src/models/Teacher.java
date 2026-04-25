package models;

import java.util.Date;

public class Teacher {
    private int id;
    private User user;
    private String firstName;
    private String lastName;
    private String employeeNo;
    private String department;
    private String phone;
    private Date hiredAt;

    public Teacher() {}

    public Teacher(int id, User user, String firstName, String lastName, String employeeNo, String department, String phone, Date hiredAt) {
        this.id = id;
        this.user = user;
        this.firstName = firstName;
        this.lastName = lastName;
        this.employeeNo = employeeNo;
        this.department = department;
        this.phone = phone;
        this.hiredAt = hiredAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Date getHiredAt() { return hiredAt; }
    public void setHiredAt(Date hiredAt) { this.hiredAt = hiredAt; }
}
