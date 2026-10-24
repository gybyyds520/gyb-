"""通用下载器：支持重定向/超时/进度打印，用于 torch wheel 与 sd-turbo 模型下载"""
import os
import sys
import time

import requests


def download(url, dest, timeout=60):
    """流式下载到 dest，打印进度"""
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0"}
    if os.path.exists(dest) and os.path.getsize(dest) > 1024 * 1024:
        resume = os.path.getsize(dest)
        headers["Range"] = f"bytes={resume}-"
    else:
        resume = 0
    with requests.get(url, stream=True, timeout=timeout, headers=headers, allow_redirects=True) as r:
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
                        print(f"  {mb:.0f}/{total/1024/1024:.0f}MB {speed:.1f}MB/s", flush=True)
    print(f"OK {dest} {os.path.getsize(dest)/1024/1024:.0f}MB", flush=True)


def main():
    jobs = [
        ("https://download.pytorch.org/whl/cu128/torch-2.11.0%2Bcu128-cp313-cp313-win_amd64.whl",
         r"C:\Users\郭耀彬\Desktop\gybct\gybxx\ml\.sd_venv\torch-2.11.0+cu128-cp313-cp313-win_amd64.whl"),
        ("https://download.pytorch.org/whl/cu128/torchvision-0.26.0%2Bcu128-cp313-cp313-win_amd64.whl",
         r"C:\Users\郭耀彬\Desktop\gybct\gybxx\ml\.sd_venv\torchvision-0.26.0+cu128-cp313-cp313-win_amd64.whl"),
    ]
    for url, dest in jobs:
        print("download:", url.split("/")[-1], flush=True)
        try:
            download(url, dest)
        except Exception as e:  # noqa: BLE001
            print(f"FAIL {url}: {e}", flush=True)
            sys.exit(1)


if __name__ == "__main__":
    main()
