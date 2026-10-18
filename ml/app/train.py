"""模型训练与评估：逻辑回归 / 随机森林 / XGBoost"""
from typing import Dict, List, Sequence, Tuple

import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (accuracy_score, f1_score, precision_score,
                             recall_score, roc_auc_score)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from app import features
from app.schemas import FeatureRow

try:  # XGBoost 为可选依赖，缺失时自动降级
    from xgboost import XGBClassifier
    XGBOOST_AVAILABLE = True
except ImportError:  # pragma: no cover
    XGBClassifier = None
    XGBOOST_AVAILABLE = False


class UnsupportedModelError(ValueError):
    """不支持的模型类型"""


class ConstantPredictor:
    """常量预测器：训练数据只有单一类别时的兜底模型"""

    def __init__(self, value: int):
        self.value = int(value)

    def fit(self, X, y=None):
        return self

    def predict(self, X):
        return np.full(len(X), self.value, dtype=int)

    def predict_proba(self, X):
        p = float(self.value)
        return np.tile(np.array([1.0 - p, p]), (len(X), 1))


def build_estimator(model_type: str):
    """构造指定类型的估计器"""
    if model_type == "logistic_regression":
        return Pipeline([
            ("scaler", StandardScaler()),
            ("clf", LogisticRegression(max_iter=1000, random_state=42)),
        ])
    if model_type == "random_forest":
        return RandomForestClassifier(
            n_estimators=200, max_depth=8, min_samples_leaf=2,
            random_state=42, n_jobs=-1,
        )
    if model_type == "xgboost":
        if not XGBOOST_AVAILABLE:
            raise UnsupportedModelError(
                "xgboost 未安装，请使用 logistic_regression 或 random_forest"
            )
        return XGBClassifier(
            n_estimators=200, max_depth=5, learning_rate=0.1,
            subsample=0.9, colsample_bytree=0.9,
            random_state=42, eval_metric="logloss", n_jobs=-1,
        )
    raise UnsupportedModelError(f"不支持的模型类型：{model_type}")


def evaluate(model, X_test: np.ndarray, y_test: np.ndarray) -> Dict[str, float]:
    """计算分类指标"""
    y_pred = model.predict(X_test)
    y_prob = model.predict_proba(X_test)[:, 1] if hasattr(model, "predict_proba") else y_pred

    metrics = {
        "accuracy": round(float(accuracy_score(y_test, y_pred)), 4),
        "precision": round(float(precision_score(y_test, y_pred, zero_division=0)), 4),
        "recall": round(float(recall_score(y_test, y_pred, zero_division=0)), 4),
        "f1": round(float(f1_score(y_test, y_pred, zero_division=0)), 4),
    }
    # 只有一个类别时 AUC 无定义
    if len(np.unique(y_test)) > 1:
        metrics["roc_auc"] = round(float(roc_auc_score(y_test, y_prob)), 4)
    else:
        metrics["roc_auc"] = 0.0
    return metrics


def feature_importance(model, names: List[str]) -> Dict[str, float]:
    """提取特征重要性（按贡献降序）"""
    if hasattr(model, "feature_importances_"):
        values = model.feature_importances_
    elif hasattr(model, "coef_"):
        values = np.abs(model.coef_[0])
    elif hasattr(model, "named_steps") and hasattr(model.named_steps.get("clf"), "coef_"):
        values = np.abs(model.named_steps["clf"].coef_[0])
    else:
        return {}
    pairs = sorted(zip(names, values), key=lambda x: float(x[1]), reverse=True)
    return {k: round(float(v), 4) for k, v in pairs}


def train_and_evaluate(
    dataset: Sequence[FeatureRow],
    model_type: str,
    test_size: float = 0.2,
) -> Tuple[object, Dict[str, float], Dict[str, float], int]:
    """训练并返回 (模型, 指标, 特征重要性, 样本数)"""
    if not dataset:
        raise ValueError("训练数据集为空")

    X = features.build_matrix(dataset)
    y = features.extract_labels(dataset)

    # 训练数据只有单一类别时，逻辑回归等求解器无法拟合，使用常量预测器兜底
    if len(np.unique(y)) == 1:
        fallback = ConstantPredictor(y[0]).fit(X, y)
        metrics = evaluate(fallback, X, y)
        return fallback, metrics, {}, len(dataset)

    stratify = y if len(np.unique(y)) > 1 else None
    if len(dataset) >= 5 and 0 < test_size < 1:
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=test_size, random_state=42, stratify=stratify
        )
    else:
        # 样本过少时全量训练并在同一批上评估
        X_train, X_test, y_train, y_test = X, X, y, y

    model = build_estimator(model_type)
    model.fit(X_train, y_train)

    metrics = evaluate(model, X_test, y_test)
    importance = feature_importance(model, features.FEATURE_NAMES)
    return model, metrics, importance, len(dataset)


def available_models() -> List[str]:
    """当前环境可用的模型类型"""
    models = ["logistic_regression", "random_forest"]
    if XGBOOST_AVAILABLE:
        models.append("xgboost")
    return models
