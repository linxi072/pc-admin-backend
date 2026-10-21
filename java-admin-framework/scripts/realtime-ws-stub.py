#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
realtime-ws-stub.py — 实时推送 WebSocket 端点本地 stub（零依赖，仅用标准库）

作用：在没有真实后端、或沙箱无 MySQL/无法启动 Spring Boot 的场景下，镜像
java-admin-framework 的 WebSocket 契约，便于：
  - 离线完成「真实跨进程 WS 联调」：本 stub 与 realtime-ws-probe.py 经真实 TCP socket
    握手、收发真实 RFC 6455 帧（非内存 mock）；
  - 与前端 src/api/realtime.js 共享同一套语义（后端侧镜像）。

镜像的后端契约（源码依据）：
  - 端点：GET /ws?token=<JWT>（WebSocketConfig.registerWebSocketHandlers）；
  - 握手鉴权：token 缺失/无效 -> 拒绝握手（HTTP 403，对应 RealtimeHandshakeInterceptor
    返回 false；Origin 为空时 WebSocketOriginPolicy 放行，故本 stub 同样不校验 Origin）；
  - 心跳：客户端文本帧 "ping" -> 服务端文本帧 "pong"（RealtimeWebSocketHandler.handleTextMessage）；
  - 推送信封：{"type": "UNREAD_MESSAGE"|"TODO_REMINDER", "payload": {...}, "ts": <epochMillis>}
    （RealtimeMessage + RealtimeNotificationPayload）。

用法：
  python3 scripts/realtime-ws-stub.py                    # 监听 8090
  python3 scripts/realtime-ws-stub.py --port 9099 --valid-token my-jwt
  python3 scripts/realtime-ws-probe.py --expect-push yes # 另开终端执行探针
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import struct
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

# 镜像 RealtimeMessage + RealtimeNotificationPayload 的推送信封
PUSH_TYPE = "UNREAD_MESSAGE"
PUSH_PAYLOAD = {
    "title": "系统公告",
    "content": "您有一条新的系统公告，请及时查收",
    "msgType": "ANNOUNCEMENT",
    "extra": {"stub": True},
}


def encode_text_frame(payload: str) -> bytes:
    """服务端 -> 客户端帧（不掩码，RFC 6455 §5.1）。"""
    data = payload.encode("utf-8")
    header = bytearray([0x81])  # FIN + opcode 1 (text)
    n = len(data)
    if n < 126:
        header.append(n)
    elif n < 1 << 16:
        header.append(126)
        header.extend(struct.pack(">H", n))
    else:
        header.append(127)
        header.extend(struct.pack(">Q", n))
    return bytes(header) + data


def decode_client_frame(sock) -> tuple[int, bytes] | None:
    """读取一个客户端帧（必须掩码，RFC 6455 §5.3）；连接结束返回 None。"""
    def recv_exact(count: int) -> bytes | None:
        buf = b""
        while len(buf) < count:
            chunk = sock.recv(count - len(buf))
            if not chunk:
                return None
            buf += chunk
        return buf

    head = recv_exact(2)
    if head is None:
        return None
    fin_opcode = head[0]
    mask_len = head[1]
    masked = bool(mask_len & 0x80)
    length = mask_len & 0x7F
    if length == 126:
        ext = recv_exact(2)
        if ext is None:
            return None
        length = struct.unpack(">H", ext)[0]
    elif length == 127:
        ext = recv_exact(8)
        if ext is None:
            return None
        length = struct.unpack(">Q", ext)[0]
    mask_key = recv_exact(4) if masked else b""
    if masked and mask_key is None:
        return None
    payload = recv_exact(length) if length else b""
    if payload is None:
        return None
    if masked:
        payload = bytes(b ^ mask_key[i % 4] for i, b in enumerate(payload))
    return fin_opcode & 0x0F, payload


class WsHandler(BaseHTTPRequestHandler):
    valid_token = "stub-jwt"
    push_delay_ms = 150

    def log_message(self, fmt, *args):  # 静音默认访问日志，保持探针输出干净
        pass

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path != "/ws":
            self.send_error(404, "not found")
            return
        token = (parse_qs(parsed.query).get("token") or [None])[0]
        if token != self.valid_token:
            # 镜像 RealtimeHandshakeInterceptor：握手被拒 -> 403（带统一包络便于排查）
            body = json.dumps({
                "code": "COMMON_403",
                "message": "握手被拒绝：token 缺失或无效",
                "data": None,
            }, ensure_ascii=False).encode("utf-8")
            self.send_response(403)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if (self.headers.get("Upgrade") or "").lower() != "websocket":
            self.send_error(400, "expected websocket upgrade")
            return

        key = self.headers.get("Sec-WebSocket-Key", "")
        accept = base64.b64encode(hashlib.sha1((key + WS_GUID).encode()).digest()).decode()
        self.send_response(101)
        self.send_header("Upgrade", "websocket")
        self.send_header("Connection", "Upgrade")
        self.send_header("Sec-WebSocket-Accept", accept)
        self.end_headers()
        self.close_connection = True

        stop = threading.Event()

        def push_worker():
            # 连接建立后延迟推送一帧，镜像「业务事件触发推送」的时序
            if stop.wait(self.push_delay_ms / 1000.0):
                return
            msg = {"type": PUSH_TYPE, "payload": PUSH_PAYLOAD, "ts": int(time.time() * 1000)}
            try:
                self.wfile.write(encode_text_frame(json.dumps(msg, ensure_ascii=False)))
                self.wfile.flush()
            except OSError:
                pass

        threading.Thread(target=push_worker, daemon=True).start()
        try:
            while True:
                frame = decode_client_frame(self.connection)
                if frame is None:
                    break
                opcode, payload = frame
                if opcode == 8:  # close
                    break
                if opcode == 1 and payload.decode("utf-8", "replace") in ("ping", '"ping"'):
                    # 镜像 RealtimeWebSocketHandler：ping -> pong
                    self.wfile.write(encode_text_frame("pong"))
                    self.wfile.flush()
        except OSError:
            pass
        finally:
            stop.set()


def main() -> int:
    ap = argparse.ArgumentParser(description="实时推送 WebSocket 本地 stub（镜像后端契约）")
    ap.add_argument("--port", type=int, default=8090)
    ap.add_argument("--valid-token", default="stub-jwt", help="接受握手的 token 值（其余一律 403）")
    ap.add_argument("--push-delay-ms", type=int, default=150, help="连接建立后延迟多少毫秒推送测试帧")
    args = ap.parse_args()

    WsHandler.valid_token = args.valid_token
    WsHandler.push_delay_ms = max(0, args.push_delay_ms)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), WsHandler)
    print(f"WS stub 监听 ws://127.0.0.1:{args.port}/ws （有效 token={args.valid_token!r}，"
          f"{args.push_delay_ms}ms 后推送 {PUSH_TYPE} 帧）")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
