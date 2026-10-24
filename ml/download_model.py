"""sd-turbo 模型下载（hf-mirror.com，断点续传）"""
import os
import sys
import time

import requests

BASE = "https://hf-mirror.com/stabilityai/sd-turbo/resolve/main/"
OUT = r"C:\Users\郭耀彬\Desktop\gybct\gybxx\ml\sd-models\sd-turbo"

FILES = [
    "model_index.json",
    "scheduler/scheduler_config.json",
    "text_encoder/config.json",
    "text_encoder/model.safetensors",
    "tokenizer/merges.txt",
    "tokenizer/special_tokens_map.json",
    "tokenizer/tokenizer_config.json",
    "tokenizer/vocab.json",
    "unet/config.json",
    "unet/diffusion_pytorch_model.fp16.safetensors",
    "vae/config.json",
    "vae/diffusion_pytorch_model.safetensors",
    "feature_extractor/preprocessor_config.json",
]


def download(url, dest):
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0"}
    if os.path.exists(dest) and os.path.getsize(dest) > 1024 * 1024:
        resume = os.path.getsize(dest)
        headers["Range"] = f"bytes={resume}-"
    else:
        resume = 0
    with requests.get(url, stream=True, timeout=60, headers=headers, allow_redirects=True) as r:
        r.raise_for_status()
        total = int(r.headers.get("Content-Length", 0)) + resume
        mode = "ab" if resume else "wb"
        done = resume
        t0 = time.time()
        with open(dest, mode) as f:
            for chunk in r.iter_content(chunk_size=1024 * 1024):
                if chunk:
                    f.write(chunk)
                    done += len(chunk)
                    if total and done % (20 * 1024 * 1024) < 1024 * 1024:
                        mb = done / 1024 / 1024
                        speed = done / 1024 / 1024 / max(time.time() - t0, 0.01)
                        print(f"    {mb:.0f}/{total/1024/1024:.0f}MB {speed:.1f}MB/s", flush=True)
    size_mb = os.path.getsize(dest) / 1024 / 1024
    print(f"OK {dest} {size_mb:.0f}MB", flush=True)
    return size_mb


def main():
    for f in FILES:
        dest = os.path.join(OUT, f)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        print("download:", f, flush=True)
        try:
            download(BASE + f, dest)
        except Exception as e:  # noqa: BLE001
            print(f"FAIL {f}: {e}", flush=True)
            sys.exit(1)


if __name__ == "__main__":
    main()
