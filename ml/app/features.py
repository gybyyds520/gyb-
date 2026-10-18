"""特征工程：特征定义、矩阵构建与派生特征计算"""
from typing import List, Sequence

import numpy as np
import pandas as pd

from app.schemas import FeatureRow

# 参与建模的原始特征
BASE_FEATURES: List[str] = [
    "avg_score",
    "score_trend",
    "score_std",
    "min_score",
    "fail_count",
    "fail_rate",
    "grade_point_avg",
    "attendance_rate",
    "late_count",
    "absent_count",
    "avg_participation",
    "avg_homework",
    "question_count",
    "study_minutes_avg",
    "video_progress_avg",
    "resource_view_count",
    "forum_post_count",
    "assignment_submit_rate",
    "credit_total",
    "course_count",
]

# 派生特征：由基础特征组合而成，用于增强模型对综合风险的刻画
DERIVED_FEATURES: List[str] = [
    "engagement_score",  # 综合投入度
    "risk_index",        # 风险指数
    "study_efficiency",  # 单位学分学习时长
]

FEATURE_NAMES: List[str] = BASE_FEATURES + DERIVED_FEATURES


def row_to_dict(row: FeatureRow) -> dict:
    """将单条样本转为特征字典"""
    data = row.model_dump(exclude={"student_id", "term_id", "label"})
    participation = max(0.0, min(10.0, data.get("avg_participation", 0.0)))
    homework = max(0.0, min(100.0, data.get("avg_homework", 0.0)))
    attendance = max(0.0, min(1.0, data.get("attendance_rate", 0.0)))
    video = max(0.0, min(100.0, data.get("video_progress_avg", 0.0)))
    submit = max(0.0, min(1.0, data.get("assignment_submit_rate", 0.0)))

    # 综合投入度：出勤 40% + 视频完成度 20% + 课堂参与 20% + 作业提交率 20%
    engagement = (attendance * 0.4 + video / 100 * 0.2
                  + participation / 10 * 0.2 + submit * 0.2) * 100

    # 风险指数：不及格率与成绩水平共同决定，越大越危险
    risk = (data.get("fail_rate", 0.0) * 50
            + max(0.0, 60 - data.get("avg_score", 0.0)) * 0.5
            + max(0.0, 1 - attendance) * 30)

    credit = max(1.0, data.get("credit_total", 0.0))
    efficiency = data.get("study_minutes_avg", 0.0) / credit

    data["engagement_score"] = round(engagement, 4)
    data["risk_index"] = round(risk, 4)
    data["study_efficiency"] = round(efficiency, 4)
    return data


def build_dataframe(rows: Sequence[FeatureRow]) -> pd.DataFrame:
    """构建特征矩阵"""
    records = [row_to_dict(r) for r in rows]
    df = pd.DataFrame(records)
    # 补齐缺失列，保证训练/推理特征一致
    for col in FEATURE_NAMES:
        if col not in df.columns:
            df[col] = 0.0
    return df[FEATURE_NAMES].astype(float)


def build_matrix(rows: Sequence[FeatureRow]) -> np.ndarray:
    """构建 numpy 特征矩阵"""
    return build_dataframe(rows).to_numpy(dtype=float)


def extract_labels(rows: Sequence[FeatureRow]) -> np.ndarray:
    """提取标签，缺省为 0"""
    return np.array([r.label if r.label is not None else 0 for r in rows], dtype=int)


def clip_features(df: pd.DataFrame) -> pd.DataFrame:
    """对越界特征做裁剪，保证推理与训练分布一致"""
    bounds = {
        "avg_score": (0, 100),
        "min_score": (0, 100),
        "avg_homework": (0, 100),
        "video_progress_avg": (0, 100),
        "attendance_rate": (0, 1),
        "assignment_submit_rate": (0, 1),
        "fail_rate": (0, 1),
        "avg_participation": (0, 10),
    }
    for col, (low, high) in bounds.items():
        if col in df.columns:
            df[col] = df[col].clip(low, high)
    return df.fillna(0.0)
