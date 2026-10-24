"""SD 图像服务启动脚本：python run_sd.py [--port 8001]"""
import argparse

import uvicorn

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="成长激励卡图像服务（Stable Diffusion Turbo）")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8001)
    parser.add_argument("--reload", action="store_true")
    args = parser.parse_args()

    uvicorn.run("app.sd_service:app", host=args.host, port=args.port, reload=args.reload)
