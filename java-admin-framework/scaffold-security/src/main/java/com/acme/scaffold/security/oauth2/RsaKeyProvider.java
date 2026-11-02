package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.security.jwt.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * OAuth2 授权服务器 RSA 密钥提供方（RS256 签名 + JWKS 公钥暴露）。
 *
 * <p>优先读取外部固定私钥（{@code app.security.oauth2.private-key}，Base64 编码的 PKCS#8 DER），
 * 未配置则生成内存临时密钥（重启失效，并打印告警，与 {@code JWT_SECRET} 缺失告警同口径）。
 * 临时密钥仅用于本地开发/测试；生产必须配置固定私钥，否则已签发令牌无法跨重启校验、JWKS kid 也会变化。</p>
 */
@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);
    private static final int KEY_SIZE = 2048;

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    public RsaKeyProvider(JwtProperties properties) {
        this.keyId = StringUtils.hasText(properties.getOauth2KeyId())
                ? properties.getOauth2KeyId() : "oauth2-rs256";
        if (StringUtils.hasText(properties.getOauth2PrivateKey())) {
            this.privateKey = loadPrivateKey(properties.getOauth2PrivateKey());
            this.publicKey = derivePublicKey(this.privateKey);
        } else {
            KeyPair kp = generateKeyPair();
            this.privateKey = (RSAPrivateKey) kp.getPrivate();
            this.publicKey = (RSAPublicKey) kp.getPublic();
            log.warn("app.security.oauth2.private-key 未配置，已生成内存临时 RSA 密钥（重启后失效，JWKS kid 变化）。"
                    + " 生产环境请配置固定私钥（Base64 PKCS#8 DER）以保证已签发令牌可跨重启校验。");
        }
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public String getKeyId() {
        return keyId;
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(KEY_SIZE);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("生成 RSA 密钥对失败", e);
        }
    }

    private static RSAPrivateKey loadPrivateKey(String b64) {
        try {
            byte[] der = Base64.getDecoder().decode(b64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("解析 OAuth2 RSA 私钥失败（须为 Base64 PKCS#8 DER）", e);
        }
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateKey priv) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            RSAPublicKeySpec spec;
            if (priv instanceof RSAPrivateCrtKey crt) {
                spec = new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent());
            } else {
                throw new IllegalStateException("RSA 私钥须为 CRT 形式以推导公钥");
            }
            return (RSAPublicKey) kf.generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("由 RSA 私钥推导公钥失败", e);
        }
    }
}
