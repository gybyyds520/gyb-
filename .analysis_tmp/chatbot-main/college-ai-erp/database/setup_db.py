import sqlite3

conn = sqlite3.connect("college.db")

cursor = conn.cursor()

# STUDENTS

cursor.execute("""
CREATE TABLE IF NOT EXISTS students (
    student_id INTEGER PRIMARY KEY,
    name TEXT,
    email TEXT UNIQUE,
    password TEXT,
    register_no TEXT,
    department TEXT,
    semester INTEGER,
    year INTEGER,
    phone TEXT,
    attendance REAL,
    total_classes INTEGER,
    cgpa REAL
)
""")

# MARKS

cursor.execute("""
CREATE TABLE IF NOT EXISTS marks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id INTEGER,
    subject TEXT,
    internal_marks INTEGER,
    assignment_marks INTEGER,
    semester_marks INTEGER,
    total INTEGER,
    grade TEXT
)
""")

# FACULTY

cursor.execute("""
CREATE TABLE IF NOT EXISTS faculty (
    faculty_id INTEGER PRIMARY KEY,
    name TEXT,
    email TEXT UNIQUE,
    password TEXT,
    department TEXT,
    subject TEXT,
    phone TEXT,
    experience INTEGER
)
""")

# EXAMS

cursor.execute("""
CREATE TABLE IF NOT EXISTS exams (
    exam_id INTEGER PRIMARY KEY,
    exam_name TEXT,
    date TEXT,
    semester INTEGER,
    department TEXT,
    hall TEXT
)
""")

# NOTIFICATIONS

cursor.execute("""
CREATE TABLE IF NOT EXISTS notifications (
    notification_id INTEGER PRIMARY KEY,
    title TEXT,
    message TEXT,
    date TEXT,
    target TEXT
)
""")

conn.commit()
conn.close()

print("Database Created Successfully!")