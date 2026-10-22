# -*- coding: utf-8 -*-
"""诊断：T004 可见学生数 = 接口返回 vs SQL 推导"""
import json
import urllib.request

BASE = "http://localhost:8080"


def login(u, p, lt):
    data = json.dumps({"username": u, "password": p, "loginType": lt}).encode()
    req = urllib.request.Request(BASE + "/api/auth/login", data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as r:
        d = json.loads(r.read().decode())
    return d.get("data", {}).get("accessToken"), d.get("data", {})


def get_json(path, token):
    req = urllib.request.Request(BASE + path)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.loads(r.read().decode("utf-8", "replace"))


tok, meta = login("T004", "123456", "TEACHER")
print("T004 登录 meta: userId=%s userType=%s roles=%s teacherId=%s" % (
    meta.get("userId"), meta.get("userType"), meta.get("roles"), meta.get("teacherId")))

j = get_json("/api/student/page?current=1&size=200", tok)
d = j["data"]
recs = d.get("records", [])
print("接口返回: total=%s 本页条数=%s" % (d.get("total"), len(recs)))
if recs:
    print("首条字段:", sorted(recs[0].keys()))
    key = "id" if "id" in recs[0] else "studentId"
    ids = sorted({r.get(key) for r in recs})
    print("去重%s数=%s" % (key, len(ids)))
    print("列表:", ids[:60])

# 对照1：家长接口范围
j2 = get_json("/api/warning/page?current=1&size=1", tok)
print("T004 预警 total=%s" % j2["data"].get("total"))
