# -*- coding: utf-8 -*-
import sqlite3, json

DB = r"C:\Users\郭耀彬\Desktop\gybct\gybxx\.analysis_tmp\chatbot-main\college-ai-erp\database\college.db"
c = sqlite3.connect(DB)
tables = [r[0] for r in c.execute("SELECT name FROM sqlite_master WHERE type='table'")]
print("表:", tables)
for t in tables:
    n = c.execute(f"SELECT COUNT(*) FROM [{t}]").fetchone()[0]
    cols = [r[1] for r in c.execute(f"PRAGMA table_info([{t}])")]
    print(f"  {t}: {n} 行, 列={cols}")
print("--- notifications 数据 ---")
try:
    for r in c.execute("SELECT * FROM notifications"):
        print(r)
except Exception as e:
    print("notifications 读取失败:", e)
print("--- students 数据 ---")
for r in c.execute("SELECT student_id,name,email,password,department,semester,cgpa FROM students"):
    print(r)
print("--- marks 数据 ---")
for r in c.execute("SELECT * FROM marks"):
    print(r)
c.close()
