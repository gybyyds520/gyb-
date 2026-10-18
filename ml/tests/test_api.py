"""API 接口测试"""
import pytest
from fastapi.testclient import TestClient

from app import train
from app.main import app
from app.schemas import FeatureRow

client = TestClient(app)


def sample_rows(n: int = 40) -> list:
    """构造训练样本"""
    rows = []
    for i in range(n):
        risk = i % 2 == 0
        rows.append(dict(
            student_id=i + 1,
            term_id=1,
            avg_score=50.0 if risk else 85.0,
            attendance_rate=0.6 if risk else 0.95,
            fail_count=2 if risk else 0,
            fail_rate=0.4 if risk else 0.0,
            avg_homework=55.0 if risk else 88.0,
            study_minutes_avg=30.0 if risk else 190.0,
            video_progress_avg=35.0 if risk else 92.0,
            assignment_submit_rate=0.5 if risk else 0.96,
            credit_total=20.0,
            course_count=8,
            label=int(risk),
        ))
    return rows


def test_health():
    """健康检查应返回 UP"""
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "UP"


@pytest.mark.parametrize("model_type", ["logistic_regression", "random_forest", "xgboost"])
def test_train_and_predict_flow(model_type):
    """训练后应立即可预测"""
    train_resp = client.post("/ml/train", json={
        "model_type": model_type,
        "dataset": sample_rows(60),
        "test_size": 0.25,
    })
    assert train_resp.status_code == 200, train_resp.text
    body = train_resp.json()
    assert body["model_type"] == model_type
    assert body["sample_count"] == 60
    assert "accuracy" in body["metrics"]
    assert len(body["feature_importance"]) > 0

    pred_resp = client.post("/ml/predict", json={
        "model_type": model_type,
        "rows": sample_rows(6),
    })
    assert pred_resp.status_code == 200, pred_resp.text
    preds = pred_resp.json()["predictions"]
    assert len(preds) == 6
    for p in preds:
        assert 0.0 <= p["risk_probability"] <= 1.0
        assert p["risk_level"] in {"BLUE", "YELLOW", "ORANGE", "RED"}


def test_train_empty_dataset():
    """空数据集应返回 400"""
    resp = client.post("/ml/train", json={"model_type": "random_forest", "dataset": []})
    assert resp.status_code == 400


@pytest.mark.parametrize("bad_model", ["svm", "knn", ""])
def test_train_unsupported_model(bad_model):
    """不支持的模型应返回 400"""
    resp = client.post("/ml/train", json={"model_type": bad_model, "dataset": sample_rows(20)})
    assert resp.status_code == 400


def test_predict_without_model(tmp_path, monkeypatch):
    """未训练模型预测应返回 404"""
    from app.service import registry
    monkeypatch.setattr(registry, "model_dir", tmp_path)
    resp = client.post("/ml/predict", json={
        "model_type": "random_forest",
        "rows": sample_rows(3),
    })
    assert resp.status_code == 404


def test_predict_empty_rows():
    """空预测数据应返回 400"""
    resp = client.post("/ml/predict", json={"model_type": "random_forest", "rows": []})
    assert resp.status_code == 400


def test_list_models():
    """训练后模型列表应非空"""
    client.post("/ml/train", json={"model_type": "random_forest", "dataset": sample_rows(40)})
    resp = client.get("/ml/models")
    assert resp.status_code == 200
    assert len(resp.json()) >= 1


def test_feature_importance_endpoint():
    """特征重要性接口应返回字典"""
    client.post("/ml/train", json={"model_type": "random_forest", "dataset": sample_rows(40)})
    resp = client.get("/ml/models/random_forest/importance")
    assert resp.status_code == 200
    assert isinstance(resp.json()["feature_importance"], dict)


def test_feature_importance_not_found():
    """不存在的模型应返回 404"""
    resp = client.get("/ml/models/no_such_model/importance")
    assert resp.status_code == 404


@pytest.mark.parametrize("payload_label", [0, 1])
def test_label_validation(payload_label):
    """标签只能为 0 或 1"""
    row = dict(student_id=1, avg_score=70.0, label=payload_label)
    parsed = FeatureRow(**row)
    assert parsed.label == payload_label


def test_label_out_of_range():
    """越界标签应校验失败"""
    with pytest.raises(Exception):
        FeatureRow(student_id=1, label=5)
