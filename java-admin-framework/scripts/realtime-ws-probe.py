#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
realtime-ws-probe.py — 实时推送 WebSocket 联通探针（零依赖，仅用标准库，手写 RFC 6455 最小客户端）

在「用户本机、后端已启动」或配合本地 stub 的环境下运行，经真实 TCP socket 验证：
  1) 握手：GET /ws?token=<JWT> -> 101，且 Sec-WebSocket-Accept 校验通过；
  2) 心跳：客户端文本帧 "ping" -> 服务端文本帧 "pong"（真实帧往返，非内存 mock）；
  3) 推送信封：{"type": "UNREAD_MESSAGE"|"TODO_REMINDER", "payload": {...}, "ts": <epochMillis>}；
  4) 鉴权：缺 token / 无效 token -> 拒绝握手（非 101，真实后端为 403）。

镜像的后端契约（源码依据）：
  - WebSocketConfig：端点 /ws，握手挂 RealtimeHandshakeInterceptor；
  - RealtimeHandshakeInterceptor：token 缺失/无效 -> beforeHandshake 返回 false；
  - RealtimeWebSocketHandler：文本 "ping" -> 回应 "pong"；
  - RealtimeMessage / RealtimeNotificationPayload：推送信封字段；
  - WebSocketOriginPolicy：Origin 为空放行，故本探针不发送 Origin 头。

用法：
  # 离线自证（配合 stub，真实跨进程）：
  python3 scripts/realtime-ws-stub.py &
  python3 scripts/realtime-ws-probe.py --expect-push yes
  # 指向真实后端（业务事件才会推送，默认不断言推送帧）：
  python3 scripts/realtime-ws-probe.py --base-url ws://localhost:8080 --token <JWT>

说明：与 connectivity-probe.py 同一风格——不可达时打印清晰失败而非崩溃；退出码 0/1。
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import socket
import struct
import sys
import time
from urllib.parse import urlparse

WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
WS_PATH = "/ws"
PUSH_TYPES = {"UNREAD_MESSAGE", "TODO_REMINDER"}


class WsConnection:
    """服务端->客户端帧读取器（帧不掩码，RFC 6455 §5.1）。"""

    def __init__(self, sock: socket.socket, seed: bytes = b""):
        self.sock = sock
        self.buf = bytearray(seed)

    def _need(self, n: int, deadline: float) -> None:
        while len(self.buf) < n:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError("等待帧数据超时")
            self.sock.settimeout(remaining)
            chunk = self.sock.recv(65536)
            if not chunk:
                raise ConnectionError("对端关闭连接")
            self.buf += chunk

    def read_frame(self, deadline: float) -> tuple[int, bytes]:
        self._need(2, deadline)
        opcode = self.buf[0] & 0x0F
        length = self.buf[1] & 0x7F
        idx = 2
        if length == 126:
            self._need(4, deadline)
            length = struct.unpack(">H", self.buf[2:4])[0]
            idx = 4
        elif length == 127:
            self._need(10, deadline)
            length = struct.unpack(">Q", self.buf[2:10])[0]
            idx = 10
        self._need(idx + length, deadline)
        payload = bytes(self.buf[idx:idx + length])
        del self.buf[:idx + length]
        return opcode, payload

    def send_text(self, text: str) -> None:
        data = text.encode("utf-8")
        header = bytearray([0x81])  # FIN + opcode 1
        n = len(data)
        if n < 126:
            header.append(0x80 | n)  # 客户端帧必须掩码
        elif n < 1 << 16:
            header.append(0x80 | 126)
            header.extend(struct.pack(">H", n))
        else:
            header.append(0x80 | 127)
            header.extend(struct.pack(">Q", n))
        mask = os.urandom(4)
        header.extend(mask)
        masked = bytes(b ^ mask[i % 4] for i, b in enumerate(data))
        self.sock.sendall(bytes(header) + masked)

    def close(self) -> None:
        try:
            self.sock.close()
        except OSError:
            pass


def handshake(host: str, port: int, token: str | None) -> tuple[WsConnection | None, int, dict, str]:
    """发起 WS 握手；返回 (连接或 None, HTTP 状态码, 响应头, 附加说明)。"""
    key = base64.b64encode(os.urandom(16)).decode("ascii")
    query = f"{WS_PATH}?token={token}" if token else WS_PATH
    req = (
        f"GET {query} HTTP/1.1\r\n"
        f"Host: {host}:{port}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "\r\n"
    )
    try:
        sock = socket.create_connection((host, port), timeout=5)
    except OSError as e:
        # 不可达时清晰失败而非裸抛（沙箱/未启动场景）
        return None, 0, {}, f"无法连接 {host}:{port}: {e}"
    sock.sendall(req.encode("ascii"))
    buf = b""
    while b"\r\n\r\n" not in buf:
        chunk = sock.recv(4096)
        if not chunk:
            break
        buf += chunk
    head, _, rest = buf.partition(b"\r\n\r\n")
    lines = head.split(b"\r\n")
    try:
        status = int(lines[0].split(b" ")[1])
    except (IndexError, ValueError):
        sock.close()
        return None, 0, {}, f"无法解析状态行: {lines[0][:80]!r}"
    headers = {}
    for line in lines[1:]:
        k, _, v = line.partition(b":")
        headers[k.decode("latin-1").strip().lower()] = v.decode("latin-1").strip()

    if status != 101:
        sock.close()
        return None, status, headers, "握手未升级"

    expect = base64.b64encode(hashlib.sha1((key + WS_GUID).encode("ascii")).digest()).decode("ascii")
    accept = headers.get("sec-websocket-accept", "")
    conn = WsConnection(sock, rest)
    return conn, status, headers, ("Sec-WebSocket-Accept 校验通过" if accept == expect
                                   else f"Accept 不匹配: 期望 {expect} 实际 {accept}")


def parse_base(base_url: str) -> tuple[str, int, str]:
    u = urlparse(base_url if "://" in base_url else f"ws://{base_url}")
    host = u.hostname or "127.0.0.1"
    port = u.port or (443 if u.scheme == "wss" else 80)
    path = u.path if u.path and u.path != "/" else WS_PATH
    return host, port, path


def main() -> int:
    ap = argparse.ArgumentParser(description="实时推送 WebSocket 联通探针")
    ap.add_argument("--base-url", default="ws://127.0.0.1:8090", help="WS 基址（默认本地 stub）")
    ap.add_argument("--token", default="stub-jwt", help="握手 token（默认 stub 的 stub-jwt；真实后端传 JWT）")
    ap.add_argument("--expect-push", choices=["yes", "no"], default="no",
                    help="是否断言推送帧：stub 模式传 yes；真实后端仅在业务事件时推送，传 no")
    ap.add_argument("--timeout-ms", type=int, default=5000, help="帧等待超时（毫秒）")
    args = ap.parse_args()

    host, port, path = parse_base(args.base_url)
    frame_deadline = time.monotonic() + args.timeout_ms / 1000.0
    print(f"WS 联通探针 -> {args.base_url}（路径 {path}，帧等待超时 {args.timeout_ms}ms，推送断言: {args.expect_push}）\n")

    failures = 0
    skips = 0

    def report(case: str, ok: bool | None, msg: str) -> None:
        nonlocal failures, skips
        if ok is None:
            skips += 1
            tag = "SKIP"
        else:
            failures += 0 if ok else 1
            tag = "PASS" if ok else "FAIL"
        print(f"[{tag}] {case}: {msg}")

    # ---------- WS-01 握手 + WS-02 心跳 + WS-03 推送（同一连接，顺序无关） ----------
    conn, status, headers, note = handshake(host, port, args.token)
    if conn is None:
        report("WS-01 握手（有效 token）", False, f"HTTP {status} {note}" if status else note)
        # 握手失败则后续用例均无连接可测，直接收尾
        report("WS-02 心跳往返", False, "无可用连接（握手失败）")
        if args.expect_push == "yes":
            report("WS-03 推送信封", False, "无可用连接（握手失败）")
        else:
            report("WS-03 推送信封", None, "未启用 --expect-push（真实后端仅在业务事件时推送，跳过）")
        print(f"\n{failures} 项检查未通过。")
        return 1
    report("WS-01 握手（有效 token）", True, f"HTTP 101，{note}")

    pong_at = None
    push_ok = None
    push_msg = "未收到推送帧"
    io_err = None
    try:
        conn.send_text("ping")
        while True:
            try:
                opcode, payload = conn.read_frame(frame_deadline)
            except (TimeoutError, socket.timeout):
                # 注意：Python <=3.9 的 socket.timeout 不是 TimeoutError 子类（3.10 起才是别名），须分别捕获
                break
            except OSError as e:
                io_err = f"连接读取失败: {e}"
                break
            if opcode == 8:
                break
            if opcode != 1:
                continue
            text = payload.decode("utf-8", "replace")
            if text in ("pong", '"pong"'):
                pong_at = time.monotonic()
                continue
            try:
                msg = json.loads(text)
            except json.JSONDecodeError:
                continue
            if isinstance(msg, dict) and msg.get("type") in PUSH_TYPES:
                payload_obj = msg.get("payload")
                ts = msg.get("ts")
                if isinstance(payload_obj, dict) and isinstance(ts, int):
                    push_ok = True
                    push_msg = f"type={msg.get('type')}, payload 字段={sorted(payload_obj)}, ts={ts}"
                else:
                    push_ok = False
                    push_msg = f"信封字段不完整: {text[:200]}"
    except OSError as e:
        io_err = f"连接错误: {e}"
    finally:
        conn.close()

    if pong_at is not None:
        report("WS-02 心跳往返", True, f"{(pong_at - (frame_deadline - args.timeout_ms / 1000.0)) * 1000:.0f}ms 量级内收到 pong")
    else:
        report("WS-02 心跳往返", False, io_err or "未收到 pong 帧")

    if args.expect_push == "yes":
        report("WS-03 推送信封", push_ok, push_msg if push_ok else (io_err or push_msg))
    else:
        report("WS-03 推送信封", None, "未启用 --expect-push（真实后端仅在业务事件时推送，跳过）")

    # ---------- WS-04 / WS-05 握手鉴权拒绝（独立连接） ----------
    _, st_no, _, note_no = handshake(host, port, None)
    # status==0 表示根本连不上（与「拒绝握手」是两回事），不可判为通过
    report("WS-04 缺 token 拒绝握手", st_no != 0 and st_no != 101,
           (f"HTTP {st_no}（{note_no}）" if st_no else note_no))

    _, st_bad, _, note_bad = handshake(host, port, args.token + "-tampered")
    report("WS-05 无效 token 拒绝握手", st_bad != 0 and st_bad != 101,
           (f"HTTP {st_bad}（{note_bad}）" if st_bad else note_bad))

    passed = 5 - failures - skips
    print()
    if failures == 0:
        print(f"全部 WS 联通检查通过（{passed} 通过 / {failures} 失败 / {skips} 跳过）。")
        return 0
    print(f"{failures} 项检查未通过，请查看上方详情。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
