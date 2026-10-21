import { request } from './http'

// Kafka 有序消费监控（GET/POST /api/kafka，权限 system:kafka:read / :manage）。
// 契约来源：kafka/controller/KafkaMonitorController.java。
// 消费容器未启用（acme.kafka.ordered-consumer.enabled=false）时 status.enabled=false，
// 补偿操作返回 false——页面据此展示降级态而非报错。

// GET /api/kafka/consumer/status -> KafkaConsumerStatusView（容器运行态与累计指标）
export const getConsumerStatus = () =>
  request({ method: 'GET', url: '/api/kafka/consumer/status' })

// GET /api/kafka/consumer/poison?page&size&key -> PageResult<KafkaPoisonMessageView>（死信列表）
export const pagePoison = (params) =>
  request({ method: 'GET', url: '/api/kafka/consumer/poison', params })

// POST /api/kafka/consumer/poison/{id}/retry -> Boolean（清除失败计数与版本表占位，允许重投）
export const retryPoison = (id) =>
  request({ method: 'POST', url: `/api/kafka/consumer/poison/${encodeURIComponent(id)}/retry` })

// POST /api/kafka/consumer/poison/{id}/discard -> Boolean（从死信存储移除，视为已人工确认）
export const discardPoison = (id) =>
  request({ method: 'POST', url: `/api/kafka/consumer/poison/${encodeURIComponent(id)}/discard` })
