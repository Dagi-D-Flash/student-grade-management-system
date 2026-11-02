# Student Grade Management System

A desktop-based Student Grade Management System built with **Java Swing** and **MySQL**. The application provides separate interfaces for administrators, teachers, and students to manage academic records, courses, enrollments, grades, and student performance.

## Overview

The system is designed to organize academic information in one application, with role-specific dashboards and tools for managing and viewing student records.

## Features

### Administrator
- Admin dashboard with academic statistics and charts.
- Student and teacher record management.
- Subject and course management.
- Course enrollment management.
- Academic performance visualizations.
- Leaderboards and student performance monitoring.

### Teacher
- Teacher dashboard and course overview.
- Student search and enrollment management.
- Course grade-component configuration.
- Grade entry and updating.
- Grade weighting and score calculations.
- Student performance analysis.
- Transcript generation and export.

### Student
- Personal dashboard and profile management.
- Profile photo editing.
- Enrolled course overview.
- Grade and subject performance tracking.
- Semester GPA and cumulative GPA (CGPA) views.
- Academic performance charts.
- Leaderboard and academic transcript access.

## Technology Stack

| Technology | Purpose |
|---|---|
| Java | Application logic |
| Java Swing | Desktop user interface |
| MySQL | Relational database |
| JDBC | Database connectivity |
| JFreeChart | Academic charts and visualizations |
| FlatLaf | Modern Swing appearance and themes |

## Application Structure

The source code is organized into the following main packages:

```text
student-grade-management-system/
│
├── sql/
│   └── database.sql
│
└── src/
    ├── AdminDashboard.java
    ├── LoginUI.java
    ├── StudentDashboard.java
    ├── TeacherDashboard.java
    │
    ├── dao/
    │   ├── CourseDAO.java
    │   ├── EnrollmentDAO.java
    │   ├── GradeComponentDAO.java
    │   ├── GradeDAO.java
    │   ├── StudentDAO.java
    │   ├── SubjectDAO.java
    │   ├── TeacherDAO.java
    │   └── UserDAO.java
    │
    ├── models/
    │   ├── Course.java
    │   ├── Enrollment.java
    │   ├── Grade.java
    │   ├── GradeComponent.java
    │   ├── Student.java
    │   ├── Subject.java
    │   ├── Teacher.java
    │   └── User.java
    │
    ├── panels/
    │   ├── AdminChartsPanel.java
    │   ├── ChartsPanel.java
    │   ├── CoursePanel.java
    │   ├── EnrollmentPanel.java
    │   ├── GPAPanel.java
    │   ├── LeaderboardPanel.java
    │   ├── PerformanceAnalysisPanel.java
    │   ├── SchedulePanel.java
    │   ├── StudentCoursesPanel.java
    │   ├── StudentEnrollmentPanel.java
    │   ├── StudentGradePanel.java
    │   ├── StudentPanel.java
    │   ├── StudentProfilePanel.java
    │   ├── StudentSearchPanel.java
    │   ├── SubjectPanel.java
    │   ├── TeacherAnalysisPanel.java
    │   ├── TeacherCoursesPanel.java
    │   ├── TeacherGradePanel.java
    │   ├── TeacherPanel.java
    │   └── TranscriptPanel.java
    │
    ├── ui/
    │   ├── CollapsibleSidebar.java
    │   └── PhotoEditorDialog.java
    │
    └── util/
        ├── DBConnection.java
        ├── GPACalculator.java
        ├── GradeStructureService.java
        ├── ThemeManager.java
        └── TranscriptGenerator.java
```

## Database Design

The MySQL database is named `gms_db`.

It contains the following main tables:

- `users` — user accounts and roles.
- `students` — student information.
- `teachers` — teacher information.
- `subjects` — academic subjects.
- `courses` — courses assigned to teachers.
- `course_grade_components` — assessment components and their weights.
- `enrollments` — student course enrollments.
- `grades` — scores recorded for assessment components.
- `schedules` — course schedules.
- `results` — enrollment-level academic results.

The database uses primary keys, foreign keys, unique constraints, and relational associations to maintain data consistency.

## Getting Started

### Prerequisites

- Java Development Kit (JDK).
- MySQL Server.
- A Java IDE such as IntelliJ IDEA, Eclipse, or NetBeans.
- MySQL Connector/J.
- The external libraries required by the application, including FlatLaf and JFreeChart.

### 1. Clone the repository

```bash
git clone https://github.com/Dagi-D-Flash/student-grade-management-system.git
```

### 2. Set up the database

Open MySQL Workbench or your preferred MySQL client and import:

```text
sql/database.sql
```

**Important:** The supplied SQL script drops and recreates `gms_db`. Do not run it against a database containing information you need to preserve.

### 3. Configure the database connection

Open:

```text
src/util/DBConnection.java
```

Configure the MySQL connection URL, username, and password for your local environment.

Do not commit real database credentials to the repository.

### 4. Configure dependencies

Add the required external Java libraries to the project classpath.

The repository currently does not include a Maven `pom.xml` or Gradle build file, so dependency setup must be done through your IDE or by adding a build configuration.

### 5. Run the application

Open the project in your Java IDE and run:

```text
src/LoginUI.java
```

The login screen is the application's entry point.

## Architecture

The application follows a layered organization:

- **UI layer:** Swing dashboards, panels, forms, tables, and dialogs.
- **Model layer:** Java classes representing users and academic entities.
- **DAO layer:** JDBC-based database operations.
- **Utility layer:** Database connection management, GPA calculations, grade-structure updates, theme management, and transcript generation.
- **Database layer:** MySQL tables and relationships.

## Important Notes

This repository represents a student software project and should not be considered production-ready.

Before using it with real student information:

- Replace plaintext password handling with secure password hashing.
- Move database credentials outside the source code.
- Remove real student records and personal photos from the public repository.
- Review access control and database permissions.
- Verify GPA and grading rules against the institution's official academic regulations.
- Add a reproducible build configuration and document the required dependency versions.



Software Engineering Student | Java Developer

GitHub: [@Dagi-D-Flash](https://github.com/Dagi-D-Flash)
