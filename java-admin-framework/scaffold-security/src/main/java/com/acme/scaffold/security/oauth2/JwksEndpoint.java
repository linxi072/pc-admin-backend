package com.acme.scaffold.security.oauth2;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JWKS 端点（{@code GET /oauth2/jwks}）：暴露 OAuth2 RS256 公钥，供资源方/外部系统
 * 拉取并校验令牌签名。JWK 手工拼装（Base64URL 无填充），零额外第三方依赖。
 */
@RestController
@RequestMapping("/oauth2")
public class JwksEndpoint {

    private final RsaKeyProvider keys;

    public JwksEndpoint(RsaKeyProvider keys) {
        this.keys = keys;
    }

    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        RSAPublicKey pub = keys.getPublicKey();
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keys.getKeyId());
        jwk.put("n", base64UrlUnsigned(pub.getModulus()));
        jwk.put("e", base64UrlUnsigned(pub.getPublicExponent()));
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("keys", List.of(jwk));
        return set;
    }

    /** 将 BigInteger 编码为无符号 Base64URL（去除 BigInteger 可能添加的符号位前导 0x00）。 */
    private static String base64UrlUnsigned(BigInteger v) {
        byte[] bytes = v.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
