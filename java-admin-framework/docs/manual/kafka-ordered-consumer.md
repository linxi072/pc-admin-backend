# Kafka 海量数据有序消费

针对「数据频繁更新 + 必须保证消费时序性」场景的消息消费模块。核心目标：**同一业务键的多次更新，永远按版本递增的顺序被处理**——乱序到达的旧版本消息被可靠丢弃，不产生状态回退。

## 1. 保序四层机制

保序不能单靠某一层，本模块由四层共同保证，任一层单独都有盲区：

| 层 | 机制 | 位置 | 防什么 |
|---|---|---|---|
| ① 生产端有序 | 同 key 经 murmur2 哈希稳定落同一分区；开启 `enable.idempotence=true`（`max.in.flight<=5`） | 生产方约定（§4） | 重试导致的分区内乱序 |
| ② 分区内串行 | 容器单 worker 线程顺序处理 poll 批内记录（Kafka 单 poll 同分区按 offset 升序） | `OrderedKafkaConsumer` | 并发处理导致的乱序 |
| ③ 版本兜底 | 消息头 `x-version` 携带业务版本号；消费端「严格大于才处理」 | `OrderingRules`（纯函数） | 跨分区/重投/网络延迟的乱序旧版本 |
| ④ 提交语义 | `enable.auto.commit=false` + 整批处理完成后 `commitSync()` | `KafkaRecordSource` | 处理未完成就提交位移导致的消息丢失 |

②与③的关系：②保证「同一分区内严格 FIFO」，③兜底「分区内仍有乱序可能」的场景（生产端多副本、重试、客户端缓冲）。海量 + 频繁更新场景两者都要。

## 2. 模块结构

```
com.acme.scaffold.kafka
├── rules/OrderingRules.java            纯函数保序规则（murmur2/分区选择/版本比较，零依赖可单测）
├── domain/OrderedRecord.java           领域模型（topic/partition/offset/key/version/payload）
├── domain/KafkaOrderedConsumerProperties.java  配置契约（@ConfigurationProperties，领域层）
├── port/RecordSource.java              拉取源端口（测试以内存假实现注入）
├── adapter/KafkaRecordSource.java      KafkaConsumer 适配器（保序 consumer 配置 + x-version 解析）
├── config/KafkaOrderedConsumerConfig.java      条件装配（enabled=true 才注册）
└── service/OrderedKafkaConsumer.java   核心容器（SmartLifecycle：单线程串行 + 版本去重 + 手动提交）
    service/OrderedRecordHandler.java   业务处理 SPI
    service/LoggingOrderedRecordHandler.java    默认日志实现（兜底）
```

依赖方向符合分层规范：`config → adapter → port → domain`，`service → port/domain/rules`，`rules` 零依赖。

## 3. 快速启用

`application.yml`（默认关闭，无集群环境不建连）：

```yaml
kafka:
  ordered-consumer:
    enabled: true
    bootstrap-servers: kafka-1:9092,kafka-2:9092,kafka-3:9092
    group-id: scaffold-ordered-consumer
    topic: business.data.updated
    poll-timeout: 200ms      # 单次 poll 最长阻塞
    max-poll-records: 500    # 单批上限（背压，防 poll 间隔超时引发 rebalance）
```

业务接管处理（实现 SPI 并标注 `@Primary`，或移除默认实现）：

```java
@Component
@Primary
public class ProductSyncHandler implements OrderedRecordHandler {
    @Override
    public void handle(OrderedRecord record) {
        // record.key()     业务唯一键（如商品 ID）
        // record.version() 业务版本号（生产端写入 x-version 头）
        // record.payload() JSON 负载
        // 注意：at-least-once 投递下可能重复送达，实现须幂等
    }
}
```

## 4. 生产端约定（保序根基）

```properties
# 同 key 稳定分区 + 生产有序（Kafka 默认分区器即 murmur2(key) % partitions）
enable.idempotence=true
max.in.flight.requests.per.connection=5
acks=all
```

消息约定：
- **key = 业务唯一键**（如 `product-12345`）——同 key 永远落同一分区，是保序的根基；
- **header `x-version` = 业务版本号**（Long 十进制字符串，如数据库版本号/自增序号/事件时间戳）——消费端乱序兜底的依据；
- value = JSON 负载（消费端以 String 反序列化后交业务解析）。

多实例部署：同一 `group-id` 多实例分摊分区；同 key 必落同一分区故仍由单实例串行处理，保序不受实例数影响。

## 5. 运行指标与运维

容器暴露计数器（`OrderedKafkaConsumer`，可接 Actuator 指标）：

| 指标 | 含义 | 关注点 |
|---|---|---|
| `handledCount` | 已处理记录总数 | 吞吐基线 |
| `staleCount` | 因旧版本/重复被丢弃数 | 持续走高说明生产端重试多或版本设计有问题 |
| `commitFailureCount` | 位移提交失败次数 | >0 需关注 broker 可用性（失败会重投，版本表兜底） |
| `processedVersionTableSize` | 版本表 key 数 | 内存占用评估（每 key 一个 Long） |

异常语义：
- **单条处理失败**：中断本批并放弃提交 → 下轮重投同批，已处理记录被版本表挡下（at-least-once + 幂等）；
- **提交失败**：计数 + 10s 节流告警，主循环继续；
- **毒消息**（永远处理失败）：会重复重投——生产环境应在 handler 内做失败计数并转入死信队列（业务层职责）。

## 6. 测试

```bash
mvn -o test -Dtest='OrderingRulesTest,OrderedKafkaConsumerTest' -DfailIfNoTests=false
```

- `OrderingRulesTest`（17 例）：murmur2 确定性、同 key 分区稳定性、分区边界、版本比较 null 语义、`shouldProcess` 严格大于语义、乱序到达序列的处理递进；
- `OrderedKafkaConsumerTest`（10 例）：内存假源验证串行处理、旧版本/重复丢弃且不影响提交、异常放弃提交、无版本放行、版本表维度、提交失败不中断、生命周期与入参校验。

## 7. 离线构建说明

`kafka-clients:3.9.2` 已从本机 Kafka broker 发行版（`devRepository/kafka/libs/kafka-clients-3.9.2.jar`）手动安装至本地 Maven 仓库，离线 `mvn -o` 可直接解析。其他环境首次构建需联网从中央仓库解析；`slf4j-api` 由 spring-boot 提供，`zstd/lz4/snappy` 压缩编解码器为 optional（缺失时自动降级为不压缩）。
