import sqlite3

conn = sqlite3.connect("../database/college.db")

try:
    conn.execute("""
    ALTER TABLE notifications
    ADD COLUMN student_id INTEGER
    """)

    conn.commit()
    print("student_id column added successfully!")

except Exception as e:
    print("Error:", e)

conn.close()