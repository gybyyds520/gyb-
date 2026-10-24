import sqlite3

conn = sqlite3.connect("college.db")
cursor = conn.cursor()

# STUDENT

cursor.execute("""
INSERT OR REPLACE INTO students
(student_id,name,email,password,register_no,department,semester,year,phone,attendance,total_classes,cgpa)
VALUES
(1,'Mahesh','mahesh@college.edu','123456','23CSE001','CSE',5,3,'9876543210',82,100,8.4)
""")

# FACULTY

cursor.execute("""
INSERT OR REPLACE INTO faculty
(faculty_id,name,email,password,department,subject,phone,experience)
VALUES
(1,'Kumar','kumar@college.edu','faculty123','CSE','Artificial Intelligence','9876500000',8)
""")

# MARKS

cursor.execute("""
INSERT INTO marks
(student_id,subject,internal_marks,assignment_marks,semester_marks,total,grade)
VALUES
(1,'Artificial Intelligence',25,10,55,90,'A')
""")

cursor.execute("""
INSERT INTO marks
(student_id,subject,internal_marks,assignment_marks,semester_marks,total,grade)
VALUES
(1,'DBMS',22,9,50,81,'A')
""")

# EXAMS

cursor.execute("""
INSERT OR REPLACE INTO exams
(exam_id,exam_name,date,semester,department,hall)
VALUES
(1,'Semester Examination','2026-06-15',5,'CSE','Hall-A')
""")

# NOTIFICATIONS

cursor.execute("""
INSERT OR REPLACE INTO notifications
(notification_id,title,message,date,target)
VALUES
(1,'Internal Exam','Internal exam starts next week','2026-06-01','students')
""")

conn.commit()
conn.close()

print("Sample Data Loaded!")