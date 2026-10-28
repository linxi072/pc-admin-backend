// java-admin-framework 后端性能压测（k6 版本，用于 CI / 生产预发环境）
// 运行：k6 run -e BASE=http://localhost:8080 -e USER=admin -e PASSWORD=admin123 perf.k6.js
// 需要 k6（https://k6.io），本沙箱未预装，故此脚本作为可交付的 CI 资产，由用户在具备 k6 的环境执行。

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

const BASE = __ENV.BASE || "http://localhost:8080";
const USER = __ENV.USER || "admin";
const PASSWORD = __ENV.PASSWORD || "admin123";

// 错误率指标
const errorRate = new Rate("errors");

// 登录取令牌（每 VU 复用）
function loginToken() {
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ username: USER, password: PASSWORD }),
    { headers: { "Content-Type": "application/json", "X-Forwarded-For": `10.${Math.floor(Math.random()*254)}.${Math.floor(Math.random()*254)}.${Math.floor(Math.random()*254)}` } }
  );
  const body = res.json();
  return body && body.data ? body.data.accessToken : null;
}

export const options = {
  scenarios: {
    // TC-P1 匿名探针：高并发吞吐基线
    ping: {
      executor: "constant-vus",
      vus: 50,
      duration: "1m",
      exec: "ping",
    },
    // TC-P3 登录鉴权：中等并发，口令哈希开销较大
    login: {
      executor: "constant-vus",
      vus: 20,
      duration: "1m",
      exec: "login",
    },
    // TC-P2 鉴权读接口
    read: {
      executor: "constant-vus",
      vus: 20,
      duration: "1m",
      exec: "read",
      startTime: "5s",
    },
  },
  thresholds: {
    // 预期性能指标（可在此处强制 SLA 门禁）
    "http_req_duration{page:ping}": ["p(95)<20"],
    "http_req_duration{page:read}": ["p(95)<150"],
    "http_req_duration{page:login}": ["p(95)<400"],
    errors: ["rate<0.01"],
  },
};

export function ping() {
  const res = http.get(`${BASE}/api/connectivity/ping`, {
    tags: { page: "ping" },
    headers: { "X-Forwarded-For": `10.${Math.floor(Math.random()*254)}.${Math.floor(Math.random()*254)}.${Math.floor(Math.random()*254)}` },
  });
  const ok = check(res, { "ping 200": (r) => r.status === 200 });
  errorRate.add(!ok);
  sleep(0.01);
}

export function login() {
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ username: USER, password: PASSWORD }),
    {
      tags: { page: "login" },
      headers: { "Content-Type": "application/json", "X-Forwarded-For": `10.${Math.floor(Math.random()*254)}.${Math.floor(Math.random()*254)}.${Math.floor(Math.random()*254)}` },
    }
  );
  const ok = check(res, { "login 200": (r) => r.status === 200 });
  errorRate.add(!ok);
  sleep(0.05);
}

export function read() {
  const token = loginToken();
  if (!token) {
    errorRate.add(true);
    return;
  }
  const res = http.get(`${BASE}/api/system/menus/tree`, {
    tags: { page: "read" },
    headers: { Authorization: `Bearer ${token}`, "X-Forwarded-For": `10.${Math.floor(Math.random()*254)}.${Math.floor(Math.random()*254)}.${Math.floor(Math.random()*254)}` },
  });
  const ok = check(res, { "menus 200": (r) => r.status === 200 });
  errorRate.add(!ok);
  sleep(0.05);
}
