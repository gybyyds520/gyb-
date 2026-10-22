# -*- coding: utf-8 -*-
"""隔离修复回归验证"""
import json, urllib.request, urllib.error

BASE = "http://localhost:8080"

def login(u, p, lt):
    data = json.dumps({"username": u, "password": p, "loginType": lt}).encode()
    req = urllib.request.Request(BASE + "/api/auth/login", data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode()).get("data", {}).get("accessToken")

def call(method, path, token, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, method=method, data=data)
    req.add_header("Authorization", "Bearer " + token)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            raw = r.read()
            try:
                j = json.loads(raw.decode("utf-8", "replace").lstrip("\ufeff"))
                return j.get("code"), j.get("message", "")[:40], j.get("data")
            except Exception:
                return "HTTP200", raw[:60].decode("utf-8", "replace"), None
    except urllib.error.HTTPError as e:
        return f"HTTP{e.code}", e.read(100).decode("utf-8", "replace")[:60], None

print("=== 1) 家长待办（修复前 500，应恢复 200） ===")
PAR = login("2300000001P1", "123456", "PARENT")
c, m, d = call("GET", "/api/todo/list", PAR)
print(f"  家长 todo: code={c} msg={m} -> {'✅修复生效' if c == 200 else '❌仍异常'}")

print("\n=== 2) 教师数据范围（修复后应 36 左右） ===")
T004 = login("T004", "123456", "TEACHER")
c, m, d = call("GET", "/api/student/page?current=1&size=100", T004)
total = d.get("total") if isinstance(d, dict) else None
print(f"  T004 学生可见 total={total} -> {'✅收敛(36)' if total == 36 else '❌未收敛'}")

print("\n=== 3) 各角色核心功能回归 ===")
ADMIN = login("admin", "admin123", "TEACHER")
STU = login("2300000001", "123456", "STUDENT")

regress = [
    ("ADMIN", "GET", "/api/dashboard/overview", None, 200, "管理端总览"),
    ("ADMIN", "GET", "/api/monitor/overview", None, 200, "监察中心(admin专属)"),
    ("ADMIN", "GET", "/api/sys/users?page=1&size=5", None, 200, "系统用户(admin专属)"),
    ("T004", "GET", "/api/dashboard/overview", None, 200, "教师总览"),
    ("STU", "GET", "/api/portal/overview", None, 200, "学生门户"),
    ("PAR", "GET", "/api/parent/children", None, 200, "家长子女列表"),
    ("STU", "GET", "/api/parent/children/1", None, 403, "学生访问家长接口(应拒)"),
    ("PAR", "GET", "/api/parent/children/2", None, 403, "家长访问非绑定子女(应拒)"),
    ("STU", "GET", "/api/student/2", None, 403, "学生访问他人明细(应拒)"),
    ("STU", "GET", "/api/monitor/student/1", None, 403, "学生访问监察中心(应拒)"),
    ("PAR", "GET", "/api/profile/student?studentId=1", None, 403, "家长访问画像(应拒)"),
    ("STU", "POST", "/api/checkin?offeringId=1", None, 403, "学生发起签到(应拒)"),
    ("STU", "POST", "/api/profile/generate?studentId=1", None, 403, "学生生成画像(应拒)"),
]
toks = {"ADMIN": ADMIN, "T004": T004, "STU": STU, "PAR": PAR}
for who, method, path, body, expect, note in regress:
    c, m, _ = call(method, path, toks[who], body)
    ok = (c == expect) or (isinstance(c, int) and c == expect)
    print(f"  {'✅' if ok else '❌'} [{who}] {method} {path} -> code={c} (期望{expect}) {note}")

print("\n=== 4) 学院管理员范围（仍应为18人） ===")
T001 = login("T001", "123456", "TEACHER")
c, m, d = call("GET", "/api/student/page?current=1&size=100", T001)
total = d.get("total") if isinstance(d, dict) else None
print(f"  T001(学院管理员) 学生可见 total={total}")
