#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Trae Watch 配对脚本

从手机/PC 上的 Keva 文件提取凭证，生成配对载荷，两种交付方式：
  1. 终端打印二维码（手表扫码）
  2. 起一个局域网 HTTP 服务，手表输 6 位码换取

用法：
  # 从 zip（GameViewer 导出的 default.zip）生成
  python pair.py --zip "C:\\Program Files\\Netease\\GameViewer\\Download\\default.zip"

  # 从已解压目录
  python pair.py --dir ./keva

  # 只打印载荷，不起服务
  python pair.py --zip xx.zip --print-only

  # 指定监听地址与端口
  python pair.py --zip xx.zip --host 0.0.0.0 --port 8080

依赖：标准库即可；二维码需要 qrcode（可选，缺失时只打印文本载荷）
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import random
import re
import socket
import sys
import threading
import time
import zipfile
from http.server import BaseHTTPRequestHandler, HTTPServer

# ---------------------------------------------------------------------------
# Keva 提取（复用已验证的模式匹配逻辑）
# ---------------------------------------------------------------------------
JWT_RE = re.compile(rb"eyJ[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]{20,}")
JSON_RE = re.compile(rb'\{"[A-Za-z]')


def load_blk(path_or_dir: str) -> bytes:
    if path_or_dir.lower().endswith(".zip"):
        with zipfile.ZipFile(path_or_dir) as z:
            for n in z.namelist():
                if n.endswith("default.blk"):
                    return z.read(n)
        raise FileNotFoundError("zip 内未找到 default.blk")
    p = path_or_dir
    if os.path.isdir(p):
        p = os.path.join(p, "default.blk")
    if not os.path.exists(p):
        raise FileNotFoundError(f"未找到 {p}")
    return open(p, "rb").read()


def extract(blk: bytes) -> dict:
    out = {}

    # 用户信息 JSON
    for m in JSON_RE.finditer(blk):
        start = m.start()
        end = blk.find(b"\x00", start)
        if end == -1:
            end = min(start + 4000, len(blk))
        try:
            obj = json.loads(blk[start:end].decode("utf-8", "strict"))
            if isinstance(obj, dict) and ("userId" in obj or "screenName" in obj):
                out.update(obj)
                break
        except Exception:
            continue

    # JWT
    hits = JWT_RE.findall(blk)
    jwt = max((h.decode() for h in hits), key=len) if hits else ""
    if jwt:
        out["_jwt"] = jwt

    # refresh token 优先级：
    #   1) JWT 载荷里的 source_id —— 实测可用，是当前有效的 refresh token
    #   2) Keva 中形如 <base64>=.<16位hex> 的串
    rt = ""
    if jwt:
        rt = _refresh_from_jwt(jwt)
    if not rt:
        cands = [m.group().decode() for m in re.finditer(rb"[A-Za-z0-9+/]{40,}={0,2}\.[0-9a-f]{16}", blk)]
        if cands:
            rt = max(cands, key=len)
    if rt:
        out["_refresh_token"] = rt

    return out


def _refresh_from_jwt(jwt: str) -> str:
    try:
        p = jwt.split(".")[1]
        data = json.loads(base64.urlsafe_b64decode(p + "=" * (-len(p) % 4)))
        return (data.get("data") or {}).get("source_id", "") or ""
    except Exception:
        return ""


def build_payload(info: dict, bound_device_id: str) -> str:
    rt = info.get("_refresh_token", "")
    if not rt:
        raise SystemExit("未能从 Keva 中提取 refresh_token，请确认文件是否正确")
    obj = {
        "v": 1,
        "rt": rt,
        "did": bound_device_id,
        "uid": info.get("userId", ""),
        "name": info.get("screenName", ""),
        "tid": info.get("tenantId", ""),
        "exp": 0,
    }
    raw = json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    b64 = base64.urlsafe_b64encode(raw).decode().rstrip("=")
    return "TRAE1:" + b64


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


# ---------------------------------------------------------------------------
# 局域网配对服务
# ---------------------------------------------------------------------------
class PairHandler(BaseHTTPRequestHandler):
    payload = ""
    code = ""

    def log_message(self, fmt, *args):
        pass  # 静默，避免刷屏

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
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--zip", help="GameViewer 导出的 default.zip")
    src.add_argument("--dir", help="含 default.blk 的目录")
    ap.add_argument("--device-id", default="", help="BoundDeviceID（可选，留空则手表侧沿用）")
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--print-only", action="store_true", help="只打印载荷，不起服务")
    args = ap.parse_args()

    blk = load_blk(args.zip or args.dir)
    info = extract(blk)

    print("=== 从 Keva 提取 ===")
    print("用户:", info.get("screenName"), "/", info.get("userId"))
    print("手机:", info.get("phoneNumber"))
    print("租户:", info.get("tenantId"))
    print("refresh_token:", (info.get("_refresh_token") or "<未找到>")[:40])

    payload = build_payload(info, args.device_id)
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
