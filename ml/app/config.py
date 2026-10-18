"""全局配置"""
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
MODEL_DIR = Path(os.getenv("MODEL_DIR", BASE_DIR / "models"))
MODEL_DIR.mkdir(parents=True, exist_ok=True)

RANDOM_SEED = 42
TEST_SIZE = 0.2
DEFAULT_MODEL = "random_forest"

SUPPORTED_MODELS = ["logistic_regression", "random_forest", "xgboost"]
