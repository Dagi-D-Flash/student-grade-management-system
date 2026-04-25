package models;

public class GradeComponent {
    private int    id;
    private int    courseId;
    private String componentName;
    private double weight;
    private double maxScore;

    public GradeComponent() {}

    public GradeComponent(int id, int courseId, String componentName, double weight, double maxScore) {
        this.id            = id;
        this.courseId      = courseId;
        this.componentName = componentName;
        this.weight        = weight;
        this.maxScore      = maxScore;
    }

    public int    getId()            { return id; }
    public void   setId(int id)      { this.id = id; }

    public int    getCourseId()               { return courseId; }
    public void   setCourseId(int courseId)   { this.courseId = courseId; }

    public String getComponentName()                      { return componentName; }
    public void   setComponentName(String componentName)  { this.componentName = componentName; }

    public double getWeight()             { return weight; }
    public void   setWeight(double w)     { this.weight = w; }

    public double getMaxScore()           { return maxScore; }
    public void   setMaxScore(double ms)  { this.maxScore = ms; }

    @Override public String toString() { return componentName + " (" + (int)weight + "%)"; }
}
