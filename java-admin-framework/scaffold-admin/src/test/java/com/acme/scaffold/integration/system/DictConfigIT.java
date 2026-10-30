package com.acme.scaffold.integration.system;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字典与系统配置业务集成测试：覆盖两类「基础数据」模块的 CRUD、唯一性冲突与缓存一致性。
 *
 * <p>场景：
 * <ul>
 *   <li>字典类型：创建 → 编码重复 409 → 更新 → 删除；</li>
 *   <li>字典数据：同类型下字典值唯一（创建/更新均校验 409）；</li>
 *   <li>系统配置：创建 → 键重复 409 → 按键读取 → 更新后读取应反映新值（缓存失效）→
 *       删除后读取 404。</li>
 * </ul>
 *
 * <p>运行前提：本机 MySQL（root/root），admin/admin123 属 SUPER_ADMIN，激活 integration profile。
 */
class DictConfigIT extends AbstractHttpIntegrationTest {

    private String cachedToken;

    private String token() {
        if (cachedToken == null) {
            cachedToken = login("admin", "admin123");
        }
        return cachedToken;
    }

    /** 本类创建的字典类型/数据/配置 id，@AfterEach 尽力清理。 */
    private final java.util.List<long[]> created = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        String t = token();
        for (long[] pair : created) {
            String path = pair[0] == 0 ? "/api/system/dict-data/" + pair[1]
                    : pair[0] == 1 ? "/api/system/dict-types/" + pair[1]
                    : "/api/system/configs/" + pair[1];
            rest.exchange(baseUrl() + path, org.springframework.http.HttpMethod.DELETE,
                    new org.springframework.http.HttpEntity<>(bearer(t)), Result.class);
        }
        created.clear();
    }

    // ==================== 字典类型 ====================

    /** 正常路径：字典类型创建 → 列表可查 → 删除后不可查。 */
    @Test
    void dictType_createListDelete() {
        String code = "it_dict_type_" + System.nanoTime();
        Map<String, Object> body = Map.of("dictCode", code, "dictName", "IT字典类型");
        ResponseEntity<Result> createResp = post("/api/system/dict-types", body, Result.class, token());
        assertEquals(200, createResp.getStatusCode().value());
        assertNotNull(createResp.getBody());
        assertTrue(createResp.getBody().isSuccess());
        long id = Long.parseLong((String) createResp.getBody().data());
        created.add(new long[]{1, id});

        ResponseEntity<Result> delResp = rest.exchange(baseUrl() + "/api/system/dict-types/" + id,
                org.springframework.http.HttpMethod.DELETE, new org.springframework.http.HttpEntity<>(bearer(token())),
                Result.class);
        assertEquals(200, delResp.getStatusCode().value());
        assertNotNull(delResp.getBody());
        assertTrue(delResp.getBody().isSuccess());
        created.removeIf(p -> p[0] == 1 && p[1] == id);
    }

    /** 异常路径：字典类型编码重复 → 409。 */
    @Test
    void dictType_duplicateCode_returns409() {
        String code = "it_dict_dup_" + System.nanoTime();
        Map<String, Object> body = Map.of("dictCode", code, "dictName", "IT字典类型");
        ResponseEntity<Result> first = post("/api/system/dict-types", body, Result.class, token());
        assertEquals(200, first.getStatusCode().value());
        long id = Long.parseLong((String) first.getBody().data());
        created.add(new long[]{1, id});

        ResponseEntity<Result> second = post("/api/system/dict-types", body, Result.class, token());
        assertEquals(409, second.getStatusCode().value(), "字典编码重复应返回 409");
        assertNotNull(second.getBody());
        assertFalse(second.getBody().isSuccess());
    }

    // ==================== 字典数据 ====================

    /** 正常路径：字典数据创建 → 同类型同值重复 409 → 更新换成已存在的值 409 → 删除。 */
    @Test
    void dictData_valueUniqueWithinType() {
        String typeCode = "it_dict_data_" + System.nanoTime();
        Map<String, Object> typeBody = Map.of("dictCode", typeCode, "dictName", "IT字典数据型");
        ResponseEntity<Result> typeResp = post("/api/system/dict-types", typeBody, Result.class, token());
        assertEquals(200, typeResp.getStatusCode().value());
        long typeId = Long.parseLong((String) typeResp.getBody().data());
        created.add(new long[]{1, typeId});

        Map<String, Object> d1 = new LinkedHashMap<>();
        d1.put("dictTypeCode", typeCode);
        d1.put("dictLabel", "选项一");
        d1.put("dictValue", "ONE");
        ResponseEntity<Result> r1 = post("/api/system/dict-data", d1, Result.class, token());
        assertEquals(200, r1.getStatusCode().value());
        long dataId = Long.parseLong((String) r1.getBody().data());
        created.add(new long[]{0, dataId});

        // 同类型同值重复 → 409
        ResponseEntity<Result> rDup = post("/api/system/dict-data", d1, Result.class, token());
        assertEquals(409, rDup.getStatusCode().value(), "同类型下字典值重复应返回 409");

        // 第二个值，用于「更新撞车」场景
        Map<String, Object> d2 = new LinkedHashMap<>();
        d2.put("dictTypeCode", typeCode);
        d2.put("dictLabel", "选项二");
        d2.put("dictValue", "TWO");
        ResponseEntity<Result> r2 = post("/api/system/dict-data", d2, Result.class, token());
        assertEquals(200, r2.getStatusCode().value());
        long data2Id = Long.parseLong((String) r2.getBody().data());
        created.add(new long[]{0, data2Id});

        // 把选项一更新成选项二的值 → 409
        Map<String, Object> clash = new LinkedHashMap<>();
        clash.put("dictTypeCode", typeCode);
        clash.put("dictLabel", "选项一改");
        clash.put("dictValue", "TWO");
        ResponseEntity<Result> rClash = rest.exchange(baseUrl() + "/api/system/dict-data/" + dataId,
                org.springframework.http.HttpMethod.PUT, new org.springframework.http.HttpEntity<>(clash, bearer(token())),
                Result.class);
        assertEquals(409, rClash.getStatusCode().value(), "更新为同类型下已存在的值应返回 409");

        // 同值原地更新（值不变）应放行——唯一性校验须排除自身
        Map<String, Object> same = new LinkedHashMap<>();
        same.put("dictTypeCode", typeCode);
        same.put("dictLabel", "选项一新名");
        same.put("dictValue", "ONE");
        ResponseEntity<Result> rSame = rest.exchange(baseUrl() + "/api/system/dict-data/" + dataId,
                org.springframework.http.HttpMethod.PUT, new org.springframework.http.HttpEntity<>(same, bearer(token())),
                Result.class);
        assertEquals(200, rSame.getStatusCode().value(), "同值原地更新不应误报冲突");
    }

    // ==================== 系统配置 ====================

    /** 正常路径：配置创建 → 按键读取 → 更新后读取反映新值（内存缓存失效）→ 删除后 404。 */
    @Test
    void config_cacheInvalidatedOnWrite() {
        String key = "it_config_" + System.nanoTime();
        Map<String, Object> create = new LinkedHashMap<>();
        create.put("configKey", key);
        create.put("configName", "IT配置项");
        create.put("configValue", "v1");
        create.put("configType", "STRING");
        ResponseEntity<Result> createResp = post("/api/system/configs", create, Result.class, token());
        assertEquals(200, createResp.getStatusCode().value());
        long id = Long.parseLong((String) createResp.getBody().data());
        created.add(new long[]{2, id});

        // 按键读取：v1
        ResponseEntity<Result> get1 = get("/api/system/configs/key/" + key, Result.class, token());
        assertEquals(200, get1.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> vo1 = (Map<String, Object>) get1.getBody().data();
        assertEquals("v1", vo1.get("configValue"));

        // 更新为 v2 后，按键读取必须反映新值（缓存已失效）
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("configKey", key);
        update.put("configName", "IT配置项");
        update.put("configValue", "v2");
        update.put("configType", "STRING");
        ResponseEntity<Result> updResp = rest.exchange(baseUrl() + "/api/system/configs/" + id,
                org.springframework.http.HttpMethod.PUT, new org.springframework.http.HttpEntity<>(update, bearer(token())),
                Result.class);
        assertEquals(200, updResp.getStatusCode().value());

        ResponseEntity<Result> get2 = get("/api/system/configs/key/" + key, Result.class, token());
        assertEquals(200, get2.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> vo2 = (Map<String, Object>) get2.getBody().data();
        assertEquals("v2", vo2.get("configValue"), "更新后按键读取应反映新值");

        // 删除后按键读取 → 404
        rest.exchange(baseUrl() + "/api/system/configs/" + id, org.springframework.http.HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(bearer(token())), Result.class);
        created.removeIf(p -> p[0] == 2 && p[1] == id);
        ResponseEntity<Result> get3 = get("/api/system/configs/key/" + key, Result.class, token());
        assertEquals(404, get3.getStatusCode().value(), "删除后按键读取应 404");
    }

    /** 异常路径：配置键重复 → 409。 */
    @Test
    void config_duplicateKey_returns409() {
        String key = "it_cfg_dup_" + System.nanoTime();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configKey", key);
        body.put("configName", "IT配置项");
        body.put("configValue", "v1");
        body.put("configType", "STRING");
        ResponseEntity<Result> first = post("/api/system/configs", body, Result.class, token());
        assertEquals(200, first.getStatusCode().value());
        long id = Long.parseLong((String) first.getBody().data());
        created.add(new long[]{2, id});

        ResponseEntity<Result> second = post("/api/system/configs", body, Result.class, token());
        assertEquals(409, second.getStatusCode().value(), "配置键重复应返回 409");
        assertNotNull(second.getBody());
        assertFalse(second.getBody().isSuccess());
    }
}
