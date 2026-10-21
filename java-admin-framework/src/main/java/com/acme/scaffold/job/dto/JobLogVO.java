package com.acme.scaffold.job.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志视图。
 *
 * <p>id / jobId 为 18 位雪花 ID，超出 JS Number 安全整数范围，统一按字符串序列化防精度丢失。
 *
 * @param id           日志主键（字符串序列化）
 * @param jobId        任务主键（字符串序列化）
 * @param fireTime     触发时间
 * @param finishedTime 结束时间
 * @param durationMs   执行耗时（毫秒）
 * @param status       执行结果（SUCCESS / FAILED）
 * @param errorMessage 失败信息
 * @param traceId      链路追踪 ID
 */
public record JobLogVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long jobId,
        LocalDateTime fireTime,
        LocalDateTime finishedTime,
        Long durationMs,
        String status,
        String errorMessage,
        String traceId) {
}
