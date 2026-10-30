package com.acme.scaffold.security.secret;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 外部密钥源配置。前缀 {@code secret}。默认 provider=local（沿用环境变量 / 随机临时密钥，离线可用）。
 */
@ConfigurationProperties(prefix = "secret")
public class SecretProperties {

    /** 密钥源：local（默认）| vault。 */
    private String provider = "local";
    private final Vault vault = new Vault();
    private final Local local = new Local();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Vault getVault() {
        return vault;
    }

    public Local getLocal() {
        return local;
    }

    public static class Vault {
        private String address = "http://localhost:8200";
        private String token = "";
        /** 逻辑键 -> Vault 路径，如 jwt-secret: secret/data/admin/jwt。 */
        private Map<String, String> paths = new HashMap<>();

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public Map<String, String> getPaths() {
            return paths;
        }

        public void setPaths(Map<String, String> paths) {
            this.paths = paths;
        }
    }

    public static class Local {
        /** 本地密钥映射（建议由环境变量注入，勿明文提交）。 */
        private Map<String, String> secrets = new HashMap<>();

        public Map<String, String> getSecrets() {
            return secrets;
        }

        public void setSecrets(Map<String, String> secrets) {
            this.secrets = secrets;
        }
    }
}
