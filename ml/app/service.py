"""模型注册与推理服务"""
import json
import time
from pathlib import Path
from typing import Dict, List, Optional, Sequence

import joblib
import numpy as np

from app import features, train
from app.config import MODEL_DIR
from app.schemas import FeatureRow, ModelInfo, PredictItem

# 风险等级阈值（按概率从高到低判定）
LEVEL_THRESHOLDS = [
    ("RED", 0.85),
    ("ORANGE", 0.65),
    ("YELLOW", 0.40),
    ("BLUE", 0.0),
]


def risk_level(probability: float) -> str:
    """概率转风险等级：红(极高) 橙(高) 黄(中) 蓝(低)"""
    for level, threshold in LEVEL_THRESHOLDS:
        if probability >= threshold:
            return level
    return "BLUE"


class ModelRegistry:
    """模型文件的保存、加载与推理"""

    def __init__(self, model_dir: Path = MODEL_DIR):
        self.model_dir = Path(model_dir)
        self.model_dir.mkdir(parents=True, exist_ok=True)
        self._cache: Dict[str, object] = {}

    def _path(self, model_type: str, version: str) -> Path:
        return self.model_dir / f"{model_type}_{version}.joblib"

    def _meta_path(self, model_type: str, version: str) -> Path:
        return self.model_dir / f"{model_type}_{version}.meta.json"

    def save(self, model_type: str, version: str, model, metrics: dict,
             importance: dict, sample_count: int) -> str:
        """保存模型与元信息"""
        path = self._path(model_type, version)
        joblib.dump(model, path)
        meta = {
            "model_type": model_type,
            "version": version,
            "trained_at": time.strftime("%Y-%m-%d %H:%M:%S"),
            "sample_count": sample_count,
            "metrics": metrics,
            "feature_importance": importance,
            "features": features.FEATURE_NAMES,
        }
        self._meta_path(model_type, version).write_text(
            json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        self._cache[f"{model_type}_{version}"] = model
        return str(path)

    def latest_version(self, model_type: str) -> Optional[str]:
        """查找指定类型的最新版本"""
        metas = sorted(self.model_dir.glob(f"{model_type}_*.meta.json"), reverse=True)
        if not metas:
            return None
        # 文件名格式：{type}_{version}.meta.json
        return metas[0].name[len(model_type) + 1: -len(".meta.json")]

    def load(self, model_type: str, version: Optional[str] = None):
        """加载模型，优先命中缓存"""
        version = version or self.latest_version(model_type)
        if version is None:
            raise FileNotFoundError(f"模型 {model_type} 尚未训练")
        key = f"{model_type}_{version}"
        if key in self._cache:
            return self._cache[key]
        path = self._path(model_type, version)
        if not path.exists():
            raise FileNotFoundError(f"模型文件不存在：{path}")
        model = joblib.load(path)
        self._cache[key] = model
        return model

    def meta(self, model_type: str, version: Optional[str] = None) -> dict:
        """读取模型元信息"""
        version = version or self.latest_version(model_type)
        if version is None:
            return {}
        meta_path = self._meta_path(model_type, version)
        if not meta_path.exists():
            return {}
        return json.loads(meta_path.read_text(encoding="utf-8"))

    def list_models(self) -> List[ModelInfo]:
        """列出全部已训练模型"""
        result: List[ModelInfo] = []
        for meta_file in sorted(self.model_dir.glob("*.meta.json"), reverse=True):
            data = json.loads(meta_file.read_text(encoding="utf-8"))
            result.append(ModelInfo(
                model_type=data.get("model_type", ""),
                version=data.get("version", ""),
                trained_at=data.get("trained_at"),
                sample_count=data.get("sample_count", 0),
                metrics=data.get("metrics", {}),
            ))
        return result

    def predict(self, rows: Sequence[FeatureRow], model_type: str) -> List[PredictItem]:
        """批量推理"""
        model = self.load(model_type)
        X = features.build_matrix(rows)
        if hasattr(model, "predict_proba"):
            proba = np.asarray(model.predict_proba(X))[:, 1]
        else:
            proba = np.zeros(len(rows))
        labels = (proba >= 0.5).astype(int)

        return [
            PredictItem(
                student_id=row.student_id,
                term_id=row.term_id,
                risk_label=int(labels[i]),
                risk_probability=round(float(proba[i]), 4),
                risk_level=risk_level(float(proba[i])),
            )
            for i, row in enumerate(rows)
        ]


registry = ModelRegistry()
