"""模型训练与评估测试"""
import numpy as np
import pytest

from app import train
from app.schemas import FeatureRow

rng = np.random.default_rng(42)


def make_dataset(n: int = 60, seed: int = 0) -> list:
    """构造可用数据集：低成绩低出勤 → 高风险"""
    r = np.random.default_rng(seed)
    rows = []
    for i in range(n):
        risk = r.random() < 0.5
        avg = float(r.normal(52 if risk else 82, 5))
        attendance = float(r.normal(0.6 if risk else 0.95, 0.05))
        rows.append(FeatureRow(
            student_id=i + 1,
            term_id=1,
            avg_score=avg,
            score_std=float(abs(r.normal(8, 2))),
            min_score=avg - 10,
            fail_count=int(risk) * 2,
            fail_rate=0.4 if risk else 0.0,
            grade_point_avg=max(0.0, (avg - 50) / 10),
            attendance_rate=min(1.0, max(0.0, attendance)),
            late_count=int(r.integers(0, 5)),
            absent_count=int(risk) * 3,
            avg_participation=3.0 if risk else 8.0,
            avg_homework=55.0 if risk else 85.0,
            study_minutes_avg=30.0 if risk else 180.0,
            video_progress_avg=30.0 if risk else 90.0,
            assignment_submit_rate=0.5 if risk else 0.95,
            credit_total=20.0,
            course_count=8,
            label=int(risk),
        ))
    return rows


@pytest.mark.parametrize("model_type", ["logistic_regression", "random_forest", "xgboost"])
def test_train_supported_models(model_type):
    """三种模型均应可训练并输出指标"""
    dataset = make_dataset(80, seed=1)
    model, metrics, importance, count = train.train_and_evaluate(dataset, model_type, 0.25)

    assert count == 80
    assert 0.0 <= metrics["accuracy"] <= 1.0
    assert 0.0 <= metrics["precision"] <= 1.0
    assert 0.0 <= metrics["recall"] <= 1.0
    assert 0.0 <= metrics["f1"] <= 1.0
    assert len(importance) > 0
    assert model is not None


def test_train_accuracy_reasonable():
    """在可分数据上准确率应较高"""
    dataset = make_dataset(200, seed=3)
    _, metrics, _, _ = train.train_and_evaluate(dataset, "random_forest", 0.25)
    assert metrics["accuracy"] >= 0.8


def test_train_empty_dataset():
    """空数据集应报错"""
    with pytest.raises(ValueError):
        train.train_and_evaluate([], "random_forest")


def test_unsupported_model():
    """不支持的模型类型应报错"""
    with pytest.raises(train.UnsupportedModelError):
        train.train_and_evaluate(make_dataset(20), "svm")


def test_single_class_dataset():
    """单一类别数据集不应因 AUC 计算崩溃"""
    rows = []
    for i in range(20):
        rows.append(FeatureRow(student_id=i + 1, avg_score=80.0, label=1))
    _, metrics, _, _ = train.train_and_evaluate(rows, "logistic_regression", 0.2)
    assert metrics["roc_auc"] == 0.0


def test_small_dataset_no_split():
    """样本过少时应在全量上训练"""
    rows = [FeatureRow(student_id=1, avg_score=50.0, label=1),
            FeatureRow(student_id=2, avg_score=85.0, label=0),
            FeatureRow(student_id=3, avg_score=40.0, label=1)]
    _, metrics, _, count = train.train_and_evaluate(rows, "random_forest", 0.2)
    assert count == 3
    assert metrics["accuracy"] >= 0.0


@pytest.mark.parametrize("model_type", ["logistic_regression", "random_forest", "xgboost"])
def test_available_models_contains(model_type):
    """可用模型列表应包含三种模型"""
    assert model_type in train.available_models()


def test_feature_importance_sum():
    """树模型特征重要性之和应接近 1"""
    _, _, importance, _ = train.train_and_evaluate(make_dataset(120, seed=7), "random_forest", 0.2)
    total = sum(importance.values())
    assert 0.9 <= total <= 1.1
