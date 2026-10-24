"""成长激励卡图像服务 —— FastAPI 入口（独立进程，默认端口 8001）

基于 Stable Diffusion Turbo（sd-turbo）文生图，为学情系统生成
个性化激励卡背景图。模型首次调用时懒加载（约 20-30 秒），
之后 512x512 单张约 2-5 秒（RTX 4060 8G 实测）。

启动：.sd_venv\\Scripts\\python run_sd.py --port 8001
"""
import base64
import io
import os
import threading
import time

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

MODEL_PATH = os.environ.get(
    "SD_MODEL_PATH",
    os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "sd-models", "sd-turbo"),
)
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

app = FastAPI(title="成长激励卡图像服务", description="Stable Diffusion Turbo 文生图", version="1.0.0")

_pipe = None
_pipe_lock = threading.Lock()


class GenerateRequest(BaseModel):
    prompt: str
    negative_prompt: str = "text, words, letters, numbers, watermark, signature, logo, low quality, blurry, deformed"
    width: int = 512
    height: int = 512


class GenerateResponse(BaseModel):
    image_base64: str
    elapsed: float
    device: str


def get_pipe():
    """懒加载扩散模型（线程安全）"""
    global _pipe
    if _pipe is None:
        with _pipe_lock:
            if _pipe is None:
                from diffusers import AutoPipelineForText2Image

                pipe = AutoPipelineForText2Image.from_pretrained(
                    MODEL_PATH,
                    torch_dtype=torch.float16,
                    safety_checker=None,
                    requires_safety_checker=False,
                )
                pipe.to(DEVICE)
                # 低显存优化
                try:
                    pipe.enable_attention_slicing()
                except Exception:
                    pass
                _pipe = pipe
    return _pipe


@app.get("/health")
def health():
    return {"status": "UP", "device": DEVICE, "model_loaded": _pipe is not None}


@app.post("/sd/generate", response_model=GenerateResponse)
def generate(req: GenerateRequest):
    if not req.prompt or not req.prompt.strip():
        raise HTTPException(status_code=400, detail="prompt 不能为空")
    t0 = time.time()
    try:
        pipe = get_pipe()
        image = pipe(
            prompt=req.prompt,
            negative_prompt=req.negative_prompt,
            width=req.width,
            height=req.height,
            num_inference_steps=4,
            guidance_scale=0.0,
        ).images[0]
    except Exception as e:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=f"图像生成失败: {e}") from e
    buf = io.BytesIO()
    image.save(buf, format="PNG")
    return GenerateResponse(
        image_base64=base64.b64encode(buf.getvalue()).decode("ascii"),
        elapsed=round(time.time() - t0, 2),
        device=DEVICE,
    )
