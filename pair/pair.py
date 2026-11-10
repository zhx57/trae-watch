#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Trae Watch 配对脚本

从手机/PC 上的 Keva 文件提取凭证，生成手表配对载荷，三种交付方式：
  1. 终端打印二维码（手表扫码）
  2. 起一个局域网 HTTP 服务，手表输 6 位码换取
  3. 只打印载荷，手动粘贴

重要：refresh_token 是严格轮换制，每次 ExchangeToken 都会作废旧值。
本脚本**不再自己交换 token**，而是复用 trae_cred.py 的凭证中心：
  - 已有有效 access_token 时完全不碰网络
  - 首次使用会从 Keva 导入，之后一直复用同一份

用法：
  # 从 zip（GameViewer 导出的 default.zip）导入并生成载荷
  python pair.py --zip "C:\\Program Files\\Netease\\GameViewer\\Download\\default.zip"

  # 从已解压目录
  python pair.py --dir ./keva

  # 只打印载荷，不起服务
  python pair.py --print-only

  # 起服务（默认 8080）
  python pair.py --host 0.0.0.0 --port 8080
"""
from __future__ import annotations

import argparse
import json
import os
import random
import socket
import sys
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import trae_cred  # noqa: E402


def local_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def print_qr(text: str) -> None:
    try:
        import qrcode  # type: ignore
    except ImportError:
        print("（未安装 qrcode，跳过二维码。可执行: pip install qrcode）")
        return
    qr = qrcode.QRCode(border=1)
    qr.add_data(text)
    qr.make(fit=True)
    qr.print_ascii(invert=True)


class PairHandler(BaseHTTPRequestHandler):
    payload = ""
    code = ""

    def log_message(self, fmt, *args):
        pass

    def do_POST(self):
        if self.path != "/pair":
            self.send_error(404)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length) or b"{}")
        except Exception:
            self.send_error(400)
            return
        if str(body.get("code", "")) != PairHandler.code:
            self._json({"error": "invalid code"}, 403)
            return
        self._json({"payload": PairHandler.payload}, 200)
        print(f"\n[配对成功] 已向 {self.client_address[0]} 下发凭证")

    def _json(self, obj: dict, status: int):
        data = json.dumps(obj).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def serve(host: str, port: int, payload: str) -> None:
    PairHandler.payload = payload
    PairHandler.code = "".join(random.choices("0123456789", k=6))
    ip = local_ip()
    print("\n" + "=" * 46)
    print("  在手表上打开 Trae Watch → 输入配对码")
    print(f"  IP   : {ip}")
    print(f"  端口 : {port}")
    print(f"  配对码: {PairHandler.code}")
    print("=" * 46)
    print("  Ctrl+C 退出\n")
    srv = HTTPServer((host, port), PairHandler)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止。")


def main() -> int:
    ap = argparse.ArgumentParser(description="Trae Watch 配对")
    src = ap.add_mutually_exclusive_group()
    src.add_argument("--zip", help="GameViewer 导出的 default.zip（首次导入用）")
    src.add_argument("--dir", help="含 default.blk 的目录（首次导入用）")
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--print-only", action="store_true", help="只打印载荷，不起服务")
    args = ap.parse_args()

    # 只有在显式给了 Keva 来源时才导入；日常使用直接复用已保存的凭证
    if args.zip or args.dir:
        trae_cred.do_import(args.zip or args.dir)

    cred = trae_cred.load_cred()
    if not cred.get("refresh_token"):
        print("没有可用凭证。首次使用请指定 Keva 来源：")
        print('  python pair.py --zip "C:\\path\\default.zip"')
        return 1

    payload = trae_cred.build_payload(cred)
    print("=== 凭证 ===")
    print("用户:", cred.get("screen_name"), "/", cred.get("user_id"))
    print("租户:", cred.get("tenant_id"))
    print("refresh_token:", (cred.get("refresh_token") or "")[:40] + "...")
    print("\n=== 配对载荷 ===")
    print(payload)

    if args.print_only:
        print("\n=== 二维码 ===")
        print_qr(payload)
        return 0

    serve(args.host, args.port, payload)
    return 0


if __name__ == "__main__":
    sys.exit(main())
