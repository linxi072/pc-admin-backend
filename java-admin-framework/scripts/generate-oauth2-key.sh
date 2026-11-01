#!/usr/bin/env bash
#
# 生成 OAuth2 授权服务器 RS256 固定密钥对。
#
# 用法：
#   bash scripts/generate-oauth2-key.sh
#
# 输出：
#   - APP_OAUTH2_PRIVATE_KEY=<Base64 PKCS#8 DER>   注入环境变量 / 密钥管理（切勿提交进仓库）
#   - 公钥 JWK（仅公开部分，可安全分发；用于离线核对 .well-known/jwks.json 或前置发布）
#
# 约定：私钥须通过 APP_OAUTH2_PRIVATE_KEY 环境变量注入，application.yml 仅引用
#       ${APP_OAUTH2_PRIVATE_KEY:}，禁止在配置文件中写入明文私钥。
set -euo pipefail

if ! command -v openssl >/dev/null 2>&1; then
  echo "错误：未找到 openssl，请先安装 OpenSSL。" >&2
  exit 1
fi

echo ">> 生成 RSA 2048 密钥对（PKCS#8）..."
PEM=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048)

# Base64(PKCS#8 DER) 私钥
PRIVATE_B64=$(printf '%s' "$PEM" | openssl pkey -outform DER | base64 | tr -d '\n')
echo
echo "APP_OAUTH2_PRIVATE_KEY=${PRIVATE_B64}"

# 公钥 JWK（best-effort，依赖 python3 cryptography；运行时 JWKS 端点也会自动推导）
PUB_PEM=$(printf '%s' "$PEM" | openssl rsa -pubout 2>/dev/null)
if python3 -c 'import cryptography' >/dev/null 2>&1; then
  echo
  echo "----- 公钥 JWK（kid=oauth2-rs256，可安全分发） -----"
  printf '%s' "$PUB_PEM" | python3 - <<'PY'
import sys, base64, json
from cryptography.hazmat.primitives import serialization

pub = serialization.load_pem_public_key(sys.stdin.buffer.read())
nums = pub.public_numbers()


def b64url_uint(v: int) -> str:
    length = (v.bit_length() + 7) // 8
    return base64.urlsafe_b64encode(v.to_bytes(length, 'big')).rstrip(b'=').decode()


jwk = {
    "kty": "RSA", "use": "sig", "alg": "RS256", "kid": "oauth2-rs256",
    "n": b64url_uint(nums.n), "e": b64url_uint(nums.e),
}
print(json.dumps(jwk, indent=2))
PY
else
  echo
  echo "（提示：未检测到 python3 cryptography，跳过 JWK 打印；运行时 JWKS 端点会自动从私钥推导公钥。）"
fi
