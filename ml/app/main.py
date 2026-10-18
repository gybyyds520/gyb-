"""学业风险预测服务 —— FastAPI 入口"""
import time
from typing import List

from fastapi import FastAPI, HTTPException

from app import train
from app.config import DEFAULT_MODEL
from app.schemas import (HealthResponse, ModelInfo, PredictRequest,
                         PredictResponse, TrainRequest, TrainResponse)
from app.service import registry

app = FastAPI(
    title="学业风险预测服务",
    description="基于逻辑回归 / 随机森林 / XGBoost 的学情预测推理服务",
    version="1.0.0",
)


@app.get("/health", response_model=HealthResponse)
def health():
    """健康检查"""
    models = [f"{m.model_type}:{m.version}" for m in registry.list_models()]
    return HealthResponse(status="UP", models=models)


@app.get("/ml/models", response_model=List[ModelInfo])
def list_models():
    """列出已训练模型"""
    return registry.list_models()


@app.post("/ml/train", response_model=TrainResponse)
def train_model(req: TrainRequest):
    """训练模型并持久化"""
    model_type = req.model_type if req.model_type is not None else DEFAULT_MODEL
    if model_type not in train.available_models():
        raise HTTPException(status_code=400, detail=f"模型不可用：{model_type}")
    if not req.dataset:
        raise HTTPException(status_code=400, detail="训练数据集为空")

    try:
        model, metrics, importance, count = train.train_and_evaluate(
            req.dataset, model_type, req.test_size
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    version = time.strftime("%Y%m%d%H%M%S")
    path = registry.save(model_type, version, model, metrics, importance, count)

    return TrainResponse(
        model_type=model_type,
        version=version,
        sample_count=count,
        feature_count=len(importance) or len(train.features.FEATURE_NAMES),
        metrics=metrics,
        feature_importance=importance,
        model_path=path,
    )


@app.post("/ml/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    """批量预测学业风险"""
    model_type = req.model_type if req.model_type is not None else DEFAULT_MODEL
    if model_type not in train.available_models():
        raise HTTPException(status_code=400, detail=f"模型不可用：{model_type}")
    if not req.rows:
        raise HTTPException(status_code=400, detail="预测数据为空")
    try:
        items = registry.predict(req.rows, model_type)
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))

    version = registry.latest_version(model_type) or "unknown"
    return PredictResponse(model_type=model_type, version=version, predictions=items)


@app.get("/ml/models/{model_type}/importance")
def importance(model_type: str):
    """查看指定模型的特征重要性"""
    meta = registry.meta(model_type)
    if not meta:
        raise HTTPException(status_code=404, detail=f"模型 {model_type} 不存在")
    return {"model_type": model_type, "feature_importance": meta.get("feature_importance", {})}
