"""风险等级与模型注册测试"""
import pytest

from app.schemas import FeatureRow
from app.service import ModelRegistry, risk_level


class DummyPredictor:
    """可序列化的桩模型：输出固定正类概率"""

    def predict(self, X):
        return [1] * len(X)

    def predict_proba(self, X):
        return [[0.1, 0.9]] * len(X)


class PlainModel:
    """可序列化但不具备推理能力的桩模型"""

    pass


@pytest.mark.parametrize("probability,expected", [
    (0.95, "RED"),
    (0.85, "RED"),
    (0.8499, "ORANGE"),
    (0.70, "ORANGE"),
    (0.65, "ORANGE"),
    (0.6499, "YELLOW"),
    (0.45, "YELLOW"),
    (0.40, "YELLOW"),
    (0.3999, "BLUE"),
    (0.10, "BLUE"),
    (0.0, "BLUE"),
])
def test_risk_level_boundaries(probability, expected):
    """风险等级边界判定"""
    assert risk_level(probability) == expected


def test_thresholds_descending():
    """阈值应按降序排列"""
    from app.service import LEVEL_THRESHOLDS
    values = [t for _, t in LEVEL_THRESHOLDS]
    assert values == sorted(values, reverse=True)


def test_registry_latest_version(tmp_path):
    """保存后应能取到最新版本"""
    reg = ModelRegistry(tmp_path)
    assert reg.latest_version("random_forest") is None

    reg.save("random_forest", "v1", DummyPredictor(), {"accuracy": 0.9}, {"avg_score": 0.5}, 10)
    assert reg.latest_version("random_forest") == "v1"

    loaded = reg.load("random_forest")
    assert isinstance(loaded, DummyPredictor)


def test_registry_predict(tmp_path):
    """推理应返回概率与等级"""
    reg = ModelRegistry(tmp_path)
    reg.save("logistic_regression", "v1", DummyPredictor(), {"accuracy": 0.8}, {}, 5)

    rows = [FeatureRow(student_id=1, avg_score=50.0), FeatureRow(student_id=2, avg_score=90.0)]
    items = reg.predict(rows, "logistic_regression")

    assert len(items) == 2
    assert items[0].risk_label == 1
    assert items[0].risk_probability == pytest.approx(0.9)
    assert items[0].risk_level == "RED"
    assert [i.student_id for i in items] == [1, 2]


def test_registry_load_missing(tmp_path):
    """未训练模型加载应抛异常"""
    reg = ModelRegistry(tmp_path)
    with pytest.raises(FileNotFoundError):
        reg.load("random_forest")


def test_registry_list_models(tmp_path):
    """应能列出已训练模型"""
    reg = ModelRegistry(tmp_path)
    reg.save("random_forest", "v1", PlainModel(), {"accuracy": 0.9}, {}, 10)
    reg.save("xgboost", "v2", PlainModel(), {"accuracy": 0.95}, {}, 10)

    models = reg.list_models()
    assert len(models) == 2
    assert {m.model_type for m in models} == {"random_forest", "xgboost"}


def test_meta_persisted(tmp_path):
    """元信息应持久化到磁盘"""
    reg = ModelRegistry(tmp_path)
    reg.save("xgboost", "v9", PlainModel(), {"f1": 0.77}, {"avg_score": 0.3}, 42)

    meta = reg.meta("xgboost")
    assert meta["metrics"]["f1"] == 0.77
    assert meta["sample_count"] == 42
    assert meta["version"] == "v9"


def test_registry_cache_hit(tmp_path):
    """重复加载应命中缓存返回同一实例"""
    reg = ModelRegistry(tmp_path)
    reg.save("random_forest", "v1", DummyPredictor(), {}, {}, 3)
    assert reg.load("random_forest") is reg.load("random_forest")
