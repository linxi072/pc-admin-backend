package com.acme.scaffold.security.secret;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * HashiCorp Vault 密钥源（KV v2）。零 Spring Cloud Vault 依赖，使用 JDK 内置 HttpClient。
 *
 * <p>约定：逻辑键（如 {@code jwt-secret}）经 {@code paths} 映射为 Vault 路径（如 {@code secret/data/admin/jwt}）；
 * 该路径下以 {@code value} 字段存放密钥原文（即 {@code data.data.value}）。取数失败仅返回 empty 并记录日志，
 * 不阻断启动（缺失密钥由下游 JWT 配置兜底为随机临时密钥，符合现有安全策略）。
 */
public class VaultSecretProvider implements SecretProvider {

    private static final Logger log = LoggerFactory.getLogger(VaultSecretProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String address;
    private final String token;
    private final Map<String, String> paths;
    private final HttpClient httpClient;

    public VaultSecretProvider(String address, String token, Map<String, String> paths) {
        this.address = address;
        this.token = token;
        this.paths = paths;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Override
    public Optional<String> get(String logicalKey) {
        String path = paths.get(logicalKey);
        if (path == null) {
            return Optional.empty();
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(address.replaceAll("/+$", "") + "/v1/" + path))
                    .timeout(Duration.ofSeconds(2))
                    .header("X-Vault-Token", token)
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Vault 返回 {}（path={}）", resp.statusCode(), path);
                return Optional.empty();
            }
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode data = root.path("data").path("data");
            if (data.has("value")) {
                return Optional.ofNullable(data.get("value").asText());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Vault 密钥获取失败（path={}）：{}", path, e.getMessage());
            return Optional.empty();
        }
    }
}
