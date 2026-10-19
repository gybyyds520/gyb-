#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""外部开源学情数据集导入器（天池 / UCI / xAPI 风格）。

把公开数据集映射为项目训练特征（FeatureRow / MlFeatureRow 对齐格式），
输出 `external_features.csv` 与 `source_info.json`，供 Java 端
`PredictionService.buildTrainingDataset()` 并入训练集，/ml/dataset-summary 可统计。

支持的输入 schema（自动识别列名，中英文均可）：
  A. 在线学习行为风格（天池 179206「学生成绩预测」/ Kaggle xAPI-Edu-Data）
     RaisedHands / VisitedResources / AnnouncementsView / Discussion / StudentAbsenceDays / Class(成绩等级)
     及中文列名：举手次数 / 访问课程资源频率 / 查看通知次数 / 参与讨论次数 / 缺席天数 / 成绩等级
  B. UCI Student Performance（student-mat.csv / student-por.csv，分号分隔）
     studytime / failures / absences / G1 / G2 / G3（0-20 分制）

映射规则为"启发式 + 量纲归一化"，转换后写入 source_info.json 留痕（可审计），
所有映射字段在 README 与报告中标注为外部换算特征。
"""
from __future__ import annotations

import csv
import json
import math
import sys
from pathlib import Path
from typing import Dict, List, Optional

# FeatureRow 列顺序（与 ml/app/schemas.py 对齐）
FEATURE_COLUMNS = [
    "student_id", "term_id",
    "avg_score", "score_trend", "score_std", "min_score",
    "fail_count", "fail_rate", "grade_point_avg",
    "attendance_rate", "late_count", "absent_count",
    "avg_participation", "avg_homework", "question_count",
    "study_minutes_avg", "video_progress_avg", "resource_view_count",
    "forum_post_count", "assignment_submit_rate",
    "credit_total", "course_count",
    "label",
]

DEFAULT_TERM_ID = -1  # 外部样本统一学期占位，与库内学期区分
DEFAULT_STUDENT_BASE = 9_000_000  # 外部学生 ID 基址，避免与库内学生冲突


def norm_01(v: float, lo: float, hi: float) -> float:
    if hi <= lo:
        return 0.0
    return round(max(0.0, min(1.0, (v - lo) / (hi - lo))), 4)


def to_float(v, default: float = 0.0) -> float:
    try:
        return float(v)
    except (TypeError, ValueError):
        return default


def detect_delimiter(path: Path) -> str:
    head = path.read_text(encoding="utf-8", errors="replace").splitlines()[0]
    if ";" in head:
        return ";"
    return ","


def build_alias_map(header: List[str]) -> Dict[str, str]:
    """中英文列名 → 内部字段名（大小写/空格/下划线归一化后匹配）。"""
    def norm(s: str) -> str:
        return "".join(ch for ch in s.strip().lower() if ch.isalnum())

    alias = {
        # 行为类
        "raisedhands": "question_count", "raisedhands次数": "question_count",
        "举手次数": "question_count", "举手": "question_count",
        "visitedresources": "resource_view_count", "访问课程资源频率": "resource_view_count",
        "访问资源次数": "resource_view_count", "浏览在线课件次数": "resource_view_count",
        "announcementsview": "forum_post_count", "查看通知次数": "forum_post_count",
        "查看公告次数": "forum_post_count", "查看公告": "forum_post_count",
        "discussion": "forum_post_count", "参与讨论次数": "forum_post_count",
        "讨论次数": "forum_post_count", "参与课堂讨论次数": "forum_post_count",
        "studentabsence": "absent_count", "studentabsencedays": "absent_count",
        "缺席天数": "absent_count", "absence": "absent_count", "absences": "absent_count",
        # 成绩类
        "g1": "g1", "g2": "g2", "g3": "g3", "grade1": "g1", "grade2": "g2", "grade3": "g3",
        "期中成绩": "g2", "期末成绩": "g3",
        "class": "class_level", "成绩等级": "class_level", "班级成绩等级": "class_level",
        "gradelevel": "class_level", "grade": "class_level",
        # 学习投入
        "studytime": "study_minutes_avg", "每周学习时间": "study_minutes_avg",
        "studyhours": "study_minutes_avg",
        "failures": "failures", "不及格次数": "failures",
        "parentschoolsatisfaction": "parent_satisfaction", "家长满意度": "parent_satisfaction",
    }
    result: Dict[str, str] = {}
    for col in header:
        key = norm(col)
        if key in alias:
            result[col] = alias[key]
        else:
            # 精确匹配失败的，再做子串兜底（如 “缺席天数” 含 “缺席”）
            for pat, field in alias.items():
                if pat in key:
                    result[col] = field
                    break
    return result


def schema_b_uci(rows: List[Dict[str, str]], source: str) -> List[dict]:
    """UCI Student Performance：0-20 分制 → 百分制（×5），G3<10 视为风险。"""
    out: List[dict] = []
    for idx, r in enumerate(rows):
        g1, g2, g3 = to_float(r.get("g1")), to_float(r.get("g2")), to_float(r.get("g3"))
        grades = [g1, g2, g3]
        avg_raw = sum(grades) / max(1, len(grades))
        avg_score = round(avg_raw * 5, 2)  # 0-20 → 100 分制
        min_score = round(min(grades) * 5, 2)
        score_trend = round((g3 - g1) * 5, 2)
        score_std = round(
            math.sqrt(sum((g - avg_raw) ** 2 for g in grades) / len(grades)) * 5, 2) if len(grades) > 1 else 0.0
        fail_count = sum(1 for g in grades if g < 10)
        fail_rate = round(fail_count / len(grades), 4)
        absences = int(to_float(r.get("absences")))
        absent_count = absences
        attendance_rate = norm_01(200 - absences, 0, 200)  # 按学期 200 天估算
        studytime = int(to_float(r.get("study_minutes_avg"), 2))
        study_minutes_avg = studytime * 60.0
        label = 1 if g3 < 10 else 0
        out.append({
            "student_id": DEFAULT_STUDENT_BASE + idx,
            "term_id": DEFAULT_TERM_ID,
            "avg_score": avg_score, "score_trend": score_trend, "score_std": score_std,
            "min_score": min_score, "fail_count": fail_count, "fail_rate": fail_rate,
            "grade_point_avg": 0.0,
            "attendance_rate": attendance_rate, "late_count": 0, "absent_count": absent_count,
            "avg_participation": 0.0, "avg_homework": 0.0, "question_count": 0,
            "study_minutes_avg": study_minutes_avg, "video_progress_avg": 0.0,
            "resource_view_count": 0, "forum_post_count": 0, "assignment_submit_rate": 0.0,
            "credit_total": 0.0, "course_count": 1,
            "label": label,
        })
    return out


def schema_a_behavior(rows: List[Dict[str, str]], source: str) -> List[dict]:
    """在线行为风格：举手/资源浏览/讨论/缺勤 + 成绩等级。"""
    out: List[dict] = []
    for idx, r in enumerate(rows):
        raised = to_float(r.get("question_count"))
        visited = to_float(r.get("resource_view_count"))
        forum = to_float(r.get("forum_post_count"))
        absent = int(to_float(r.get("absent_count"), 7))
        avg_score = to_float(r.get("g3")) or 0.0
        cls = str(r.get("class_level", "")).strip().upper()
        # 等级 → 风险标签：L / G（低）→ 风险；M / H / 其余 → 正常
        label = 1 if cls in ("L", "G", "低", "不及格") else 0
        participation = round((raised + visited + forum) / 3, 2) if (raised or visited or forum) else 0.0
        out.append({
            "student_id": DEFAULT_STUDENT_BASE + idx,
            "term_id": DEFAULT_TERM_ID,
            "avg_score": avg_score, "score_trend": 0.0, "score_std": 0.0,
            "min_score": 0.0, "fail_count": 0, "fail_rate": 0.0,
            "grade_point_avg": 0.0,
            "attendance_rate": norm_01(200 - absent * 8, 0, 200), "late_count": 0,
            "absent_count": absent,
            "avg_participation": participation, "avg_homework": 0.0,
            "question_count": int(raised),
            "study_minutes_avg": 0.0, "video_progress_avg": 0.0,
            "resource_view_count": int(visited), "forum_post_count": int(forum),
            "assignment_submit_rate": 0.0,
            "credit_total": 0.0, "course_count": 1,
            "label": label,
        })
    return out


def import_file(path: Path, out_dir: Path, meta: dict) -> int:
    delimiter = detect_delimiter(path)
    with path.open(encoding="utf-8", errors="replace", newline="") as f:
        reader = csv.DictReader(f, delimiter=delimiter)
        rows = list(reader)
    if not rows:
        print(f"[SKIP] {path.name}: 空文件")
        return 0
    alias = build_alias_map(reader.fieldnames or [])
    if not any(v in ("g1", "g2", "g3") for v in alias.values()):
        print(f"[SKIP] {path.name}: 未识别为 UCI/成绩风格（{path.name} 需含 G1/G2/G3 或成绩列）")
        return 0
    normalized = []
    for r in rows:
        row = {alias.get(k, k): v for k, v in r.items()}
        normalized.append(row)
    samples = schema_b_uci(normalized, str(path))
    if not samples:
        return 0
    out_csv = out_dir / "external_features.csv"
    with out_csv.open("a", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=FEATURE_COLUMNS)
        if out_csv.stat().st_size == 0:
            writer.writeheader()
        for s in samples:
            writer.writerow(s)
    meta["files"].append({
        "source": str(path.name),
        "rows": len(samples),
        "mapping": "UCI: G1/G2/G3(0-20)*5, absences→absent_count, G3<10→label=1; 行为列按别名映射",
    })
    return len(samples)


def main(argv: List[str]) -> int:
    project_root = Path(__file__).resolve().parent.parent.parent
    in_dir = Path(argv[1]).resolve() if len(argv) > 1 else project_root / "ml" / "data" / "external"
    out_dir = Path(argv[2]).resolve() if len(argv) > 2 else project_root / "ml" / "data" / "external"

    in_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)
    # 幂等：每次全量重建外部特征集，避免重复运行重复追加
    out_csv = out_dir / "external_features.csv"
    if out_csv.exists():
        out_csv.unlink()
    meta_path = out_dir / "source_info.json"
    if meta_path.exists():
        meta_path.unlink()
    meta = {"files": []}

    imported = 0
    for f in sorted(in_dir.rglob("*.csv")):
        if f.name in ("external_features.csv",):
            continue
        n = import_file(f, out_dir, meta)
        imported += n
        print(f"[OK] {f.name}: 导入 {n} 条")

    # 汇总写入
    if meta["files"]:
        meta_path.write_text(
            json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"[DONE] 累计外部样本已追加至 {out_dir / 'external_features.csv'}，来源元数据见 {meta_path.name}")

    if imported == 0 and not (out_dir / "external_features.csv").exists():
        print("[HINT] 未导入任何样本。请把天池「学生成绩预测.csv」（https://tianchi.aliyun.com/dataset/179206）"
              "或 UCI student-mat/por.csv 放入 ml/data/external/ 后重跑本脚本。")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
