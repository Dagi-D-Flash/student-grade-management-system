package models;

import java.util.Date;

public class Grade {
    private int    id;
    private Enrollment enrollment;
    private int    componentId;
    private String gradeType;
    private double score;
    private double maxScore;
    private double weight;
    private String remarks;
    private Date   gradedAt;

    public Grade() {}

    public int    getId()                    { return id; }
    public void   setId(int id)              { this.id = id; }

    public Enrollment getEnrollment()                    { return enrollment; }
    public void       setEnrollment(Enrollment e)        { this.enrollment = e; }

    public int    getComponentId()               { return componentId; }
    public void   setComponentId(int cid)        { this.componentId = cid; }

    public String getGradeType()                 { return gradeType; }
    public void   setGradeType(String gt)        { this.gradeType = gt; }

    public double getScore()                     { return score; }
    public void   setScore(double score)         { this.score = score; }

    public double getMaxScore()                  { return maxScore; }
    public void   setMaxScore(double ms)         { this.maxScore = ms; }

    public double getWeight()                    { return weight; }
    public void   setWeight(double w)            { this.weight = w; }

    public String getRemarks()                   { return remarks; }
    public void   setRemarks(String r)           { this.remarks = r; }

    public Date   getGradedAt()                  { return gradedAt; }
    public void   setGradedAt(Date d)            { this.gradedAt = d; }
}
