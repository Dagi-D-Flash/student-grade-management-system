DROP DATABASE IF EXISTS gms_db;
CREATE DATABASE gms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE gms_db;


CREATE TABLE users (
    id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(100) NOT NULL UNIQUE,
    email      VARCHAR(150) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       ENUM('admin','teacher','student') NOT NULL DEFAULT 'student',
    is_active  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);


CREATE TABLE teachers (
    id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id     INT UNSIGNED NOT NULL UNIQUE,
    first_name  VARCHAR(50)  NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    employee_no VARCHAR(20)  NOT NULL UNIQUE,
    department  VARCHAR(100),
    phone       VARCHAR(20),
    hired_at    DATE,
    CONSTRAINT fk_teacher_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);


CREATE TABLE students (
    id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id       INT UNSIGNED NOT NULL UNIQUE,
    first_name    VARCHAR(50)  NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    student_no    VARCHAR(30)  NOT NULL UNIQUE,
    date_of_birth DATE,
    gender        ENUM('male','female','other'),
    address       TEXT,
    phone         VARCHAR(20),
    enrolled_at   DATE NOT NULL,
    CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);


CREATE TABLE subjects (
    id          INT UNSIGNED     AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(20)      NOT NULL UNIQUE,
    name        VARCHAR(150)     NOT NULL,
    description TEXT,
    credits     TINYINT UNSIGNED NOT NULL DEFAULT 3
);


CREATE TABLE courses (
    id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    subject_id    INT UNSIGNED NOT NULL,
    teacher_id    INT UNSIGNED NOT NULL,
    section       VARCHAR(20)  NOT NULL,
    academic_year YEAR         NOT NULL,
    semester      ENUM('1st','2nd','summer') NOT NULL,
    max_students  SMALLINT UNSIGNED DEFAULT 80,
    CONSTRAINT uq_course UNIQUE (subject_id, teacher_id, section, academic_year, semester),
    CONSTRAINT fk_course_subject FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES teachers(id) ON DELETE RESTRICT
);


CREATE TABLE course_grade_components (
    id             INT UNSIGNED   AUTO_INCREMENT PRIMARY KEY,
    course_id      INT UNSIGNED   NOT NULL,
    component_name VARCHAR(50)    NOT NULL,
    weight         DECIMAL(5,2)   NOT NULL,
    max_score      DECIMAL(6,2)   NOT NULL DEFAULT 100.00,
    created_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_component UNIQUE (course_id, component_name),
    CONSTRAINT fk_component_course FOREIGN KEY (course_id)
        REFERENCES courses(id) ON DELETE CASCADE
);


CREATE TABLE enrollments (
    id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    student_id  INT UNSIGNED NOT NULL,
    course_id   INT UNSIGNED NOT NULL,
    enrolled_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    status      ENUM('active','dropped','completed') NOT NULL DEFAULT 'active',
    CONSTRAINT uq_enrollment UNIQUE (student_id, course_id),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollment_course  FOREIGN KEY (course_id)  REFERENCES courses(id)  ON DELETE CASCADE
);


CREATE TABLE grades (
    id           INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enrollment_id INT UNSIGNED NOT NULL,
    component_id  INT UNSIGNED NOT NULL,
    grade_type    VARCHAR(50)  NOT NULL,
    score         DECIMAL(6,2) NOT NULL,
    remarks       VARCHAR(255),
    graded_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_grade UNIQUE (enrollment_id, component_id),
    CONSTRAINT fk_grade_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE CASCADE,
    CONSTRAINT fk_grade_component  FOREIGN KEY (component_id)  REFERENCES course_grade_components(id) ON DELETE CASCADE
);


CREATE TABLE schedules (
    id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    course_id   INT UNSIGNED NOT NULL,
    day_of_week ENUM('Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday') NOT NULL,
    start_time  TIME         NOT NULL,
    end_time    TIME         NOT NULL,
    room        VARCHAR(50),
    CONSTRAINT fk_schedule_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);


CREATE TABLE results (
    id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enrollment_id INT UNSIGNED NOT NULL UNIQUE,
    total_score   DECIMAL(6,2) NOT NULL,
    letter_grade  VARCHAR(3)   NOT NULL,
    grade_point   DECIMAL(4,2) NOT NULL,
    status        ENUM('pass','fail') NOT NULL,
    calculated_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_result_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE CASCADE
);


