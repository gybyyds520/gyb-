"""特征工程测试"""
import numpy as np
import pytest

from app import features
from app.schemas import FeatureRow


def make_row(**kwargs) -> FeatureRow:
    """构造默认样本"""
    base = dict(student_id=1, term_id=1, avg_score=75.0, attendance_rate=0.9)
    base.update(kwargs)
    return FeatureRow(**base)


def test_feature_names_length():
    """特征总数应为基础特征 + 派生特征"""
    assert len(features.FEATURE_NAMES) == len(features.BASE_FEATURES) + len(features.DERIVED_FEATURES)


def test_build_matrix_shape():
    """矩阵形状应为 (样本数, 特征数)"""
    rows = [make_row(student_id=i) for i in range(5)]
    matrix = features.build_matrix(rows)
    assert matrix.shape == (5, len(features.FEATURE_NAMES))


def test_empty_rows():
    """空输入应返回空矩阵"""
    assert features.build_matrix([]).shape[0] == 0
    assert features.extract_labels([]).tolist() == []


def test_extract_labels_default_zero():
    """未标注样本的标签默认为 0"""
    rows = [make_row(student_id=1), make_row(student_id=2, label=1)]
    assert features.extract_labels(rows).tolist() == [0, 1]


def test_engagement_score_bounded():
    """全部满分时投入度应接近 100"""
    row = make_row(attendance_rate=1.0, video_progress_avg=100,
                   avg_participation=10, assignment_submit_rate=1.0)
    data = features.row_to_dict(row)
    assert 99.0 <= data["engagement_score"] <= 100.0


def test_engagement_score_zero():
    """全部为零时投入度为 0"""
    row = make_row(attendance_rate=0.0, video_progress_avg=0,
                   avg_participation=0, assignment_submit_rate=0.0)
    assert features.row_to_dict(row)["engagement_score"] == 0.0


@pytest.mark.parametrize("attendance,expected_min", [
    (1.0, 40.0),
    (0.5, 20.0),
    (0.0, 0.0),
])
def test_engagement_includes_attendance(attendance, expected_min):
    """投入度中出勤占比应为 40%"""
    row = make_row(attendance_rate=attendance)
    assert features.row_to_dict(row)["engagement_score"] >= expected_min - 0.001


@pytest.mark.parametrize("avg_score,fail_rate,attendance", [
    (90.0, 0.0, 1.0),
    (55.0, 0.5, 0.6),
    (30.0, 1.0, 0.2),
])
def test_risk_index_monotonic(avg_score, fail_rate, attendance):
    """成绩越低、不及格率越高、出勤越差，风险指数越大"""
    low = features.row_to_dict(make_row(avg_score=avg_score, fail_rate=fail_rate,
                                        attendance_rate=attendance))["risk_index"]
    high = features.row_to_dict(make_row(avg_score=avg_score - 10, fail_rate=fail_rate + 0.2,
                                         attendance_rate=max(0.0, attendance - 0.2)))["risk_index"]
    assert high >= low


def test_study_efficiency():
    """单位学分学习时长 = 学习时长 / 学分"""
    row = make_row(study_minutes_avg=200.0, credit_total=20.0)
    assert features.row_to_dict(row)["study_efficiency"] == pytest.approx(10.0)


def test_clip_features_bounds():
    """越界特征应被裁剪到合法区间"""
    import pandas as pd
    df = pd.DataFrame([{"avg_score": 150.0, "attendance_rate": 2.0, "avg_homework": -10.0}])
    clipped = features.clip_features(df)
    assert clipped["avg_score"][0] == 100.0
    assert clipped["attendance_rate"][0] == 1.0
    assert clipped["avg_homework"][0] == 0.0


def test_missing_columns_filled():
    """缺失特征列应补 0"""
    import pandas as pd
    df = pd.DataFrame([{"avg_score": 80.0}])
    built = features.build_dataframe([make_row(avg_score=80.0)])
    assert built.shape[1] == len(features.FEATURE_NAMES)
    assert not built.isna().any().any()
    assert df is not None


def test_matrix_dtype_float():
    """矩阵应为浮点类型"""
    assert features.build_matrix([make_row()]).dtype == np.float64
