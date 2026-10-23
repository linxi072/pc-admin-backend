package com.acme.scaffold.config.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存值序列化类型保持单测（联机 IT 曾复现的生产缺陷的回归锁）。
 *
 * <p><b>缺陷形态</b>：{@code GenericJackson2JsonRedisSerializer} 直接使用调用方传入的
 * {@link ObjectMapper}，而应用共享 mapper 未激活 default typing —— 缓存值以无类型 JSON
 * 落库，读回时 Jackson 无从得知目标类型，只能给 {@code LinkedHashMap}；
 * {@code @Cacheable} 方法声明 {@code List<XxxVO>}，命中缓存后调用方强转即
 * {@code ClassCastException}（机构树/菜单树/字典等泛型返回值全部命中，
 * 且 record 为 final，{@code NON_FINAL} typing 也覆盖不到）。</p>
 *
 * <p><b>修复契约</b>：{@link CacheConfig} 以应用 mapper 的<b>独立副本</b>激活
 * {@code EVERYTHING} typing + 包前缀白名单。本测试在不依赖 Redis 的前提下锁定四点：
 * 泛型与 final record 类型读回不丢失、白名单外类型拒绝反序列化（防 gadget 链）、
 * 装配过程不污染共享 mapper（REST 响应不得带 @class）、以及「不激活 typing 就会
 * 退化成 LinkedHashMap」这一缺陷形态本身（防止回归者误删 typing 配置后还自以为通过）。</p>
 */
class CacheSerializationTest {

    /** 与 {@link CacheConfig#cacheManager} 完全一致的 typed mapper 构造逻辑。 */
    private static ObjectMapper typedCacheMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTypingAsProperty(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.acme.scaffold.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.time.")
                        .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                "@class");
        return mapper;
    }

    private final GenericJackson2JsonRedisSerializer typed =
            new GenericJackson2JsonRedisSerializer(typedCacheMapper());

    /** 未激活 typing 的 mapper：复现缺陷形态（读回退化为 Map）。 */
    private final GenericJackson2JsonRedisSerializer untyped =
            new GenericJackson2JsonRedisSerializer(new ObjectMapper());

    /** final record：VO 的典型形态，NON_FINAL typing 覆盖不到它。 */
    record OrgNode(Long id, String name, List<OrgNode> children) {
    }

    @Test
    @DisplayName("typed mapper：List<final record> 序列化后读回类型与内容不丢失")
    void typedRoundTripPreservesGenericType() {
        List<OrgNode> tree = List.of(
                new OrgNode(1L, "总部", List.of(new OrgNode(2L, "华东", List.of()))),
                new OrgNode(3L, "华南", List.of()));

        byte[] bytes = typed.serialize(tree);
        Object readBack = typed.deserialize(bytes);

        assertInstanceOf(List.class, readBack, "读回应为 List");
        OrgNode first = assertInstanceOf(OrgNode.class, ((List<?>) readBack).get(0),
                "元素应为 OrgNode 而非 LinkedHashMap");
        assertEquals(1L, first.id());
        assertEquals("总部", first.name());
        assertEquals(1, first.children().size(), "嵌套集合应完整保留");
        assertEquals(2L, first.children().get(0).id());
    }

    @Test
    @DisplayName("缺陷形态锁：未激活 typing 时读回退化为 LinkedHashMap（防误删 typing 配置）")
    void untypedRoundTripDegradesToMap() {
        List<OrgNode> tree = List.of(new OrgNode(1L, "总部", List.of()));

        Object readBack = untyped.deserialize(untyped.serialize(tree));

        assertInstanceOf(List.class, readBack);
        assertInstanceOf(Map.class, ((List<?>) readBack).get(0),
                "未激活 typing 时应退化为 Map——这正是生产缺陷形态");
    }

    @Test
    @DisplayName("白名单外类型拒绝反序列化（防 Redis 投毒 gadget 链）")
    void rejectsTypesOutsideAllowlist() {
        // javax.script.ScriptEngineManager 不在白名单：伪造 @class 指向它必须被拒绝
        String forged = "[\"java.util.ArrayList\",[[\"javax.script.ScriptEngineManager\",{}]]]";
        byte[] bytes = forged.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThrows(Exception.class, () -> typed.deserialize(bytes),
                "白名单外类型必须拒绝反序列化，否则缓存值可被利用执行 gadget 链");
    }

    @Test
    @DisplayName("装配过程不污染共享 mapper：REST 出口响应体不得出现 @class")
    void cacheConfigLeavesAppMapperUntyped() throws Exception {
        ObjectMapper appMapper = new ObjectMapper();
        RedisConnectionFactory factory = Mockito.mock(RedisConnectionFactory.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RedisConnectionFactory> factoryProvider =
                Mockito.mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectMapper> mapperProvider = Mockito.mock(ObjectProvider.class);
        Mockito.when(factoryProvider.getObject()).thenReturn(factory);
        Mockito.when(mapperProvider.getIfAvailable()).thenReturn(appMapper);

        // 构造 cacheManager 即触发内部 mapper 副本与 typing 激活
        new CacheConfig().cacheManager(factoryProvider, mapperProvider, new CacheProperties());

        String json = appMapper.writeValueAsString(new OrgNode(1L, "总部", List.of()));
        assertTrue(json.contains("\"id\""), "共享 mapper 应输出普通字段");
        assertTrue(!json.contains("@class"),
                "共享 mapper 被污染：所有 HTTP 响应体都会带上 @class，破坏前端契约");
    }
}
