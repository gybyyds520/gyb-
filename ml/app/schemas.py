"""请求/响应数据模型"""
from typing import Dict, List, Optional

from pydantic import BaseModel, Field


class FeatureRow(BaseModel):
    """单条样本特征（学生 × 学期）"""

    student_id: int
    term_id: Optional[int] = None
    # 学业成绩类
    avg_score: float = 0.0
    score_trend: float = 0.0
    score_std: float = 0.0
    min_score: float = 0.0
    fail_count: int = 0
    fail_rate: float = 0.0
    grade_point_avg: float = 0.0
    # 考勤类
    attendance_rate: float = 1.0
    late_count: int = 0
    absent_count: int = 0
    # 课堂表现类
    avg_participation: float = 0.0
    avg_homework: float = 0.0
    question_count: int = 0
    # 在线学习行为类
    study_minutes_avg: float = 0.0
    video_progress_avg: float = 0.0
    resource_view_count: int = 0
    forum_post_count: int = 0
    assignment_submit_rate: float = 0.0
    # 其他
    credit_total: float = 0.0
    course_count: int = 0

    label: Optional[int] = Field(default=None, ge=0, le=1)


class TrainRequest(BaseModel):
    model_type: str = "random_forest"
    dataset: List[FeatureRow] = Field(default_factory=list)
    test_size: float = 0.2


class TrainResponse(BaseModel):
    model_type: str
    version: str
    sample_count: int
    feature_count: int
    metrics: Dict[str, float]
    feature_importance: Dict[str, float]
    model_path: str


class PredictRequest(BaseModel):
    model_type: Optional[str] = None
    rows: List[FeatureRow] = Field(default_factory=list)


class PredictItem(BaseModel):
    student_id: int
    term_id: Optional[int] = None
    risk_label: int
    risk_probability: float
    risk_level: str


class PredictResponse(BaseModel):
    model_type: str
    version: str
    predictions: List[PredictItem]


class ModelInfo(BaseModel):
    model_type: str
    version: str
    trained_at: Optional[str] = None
    sample_count: int = 0
    metrics: Dict[str, float] = Field(default_factory=dict)


class HealthResponse(BaseModel):
    status: str
    models: List[str]
