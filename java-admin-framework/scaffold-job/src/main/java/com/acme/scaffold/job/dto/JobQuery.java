package com.acme.scaffold.job.dto;

/**
 * 定时任务分页查询入参。全部筛选字段可选，为空表示不筛选。
 *
 * @param jobName     任务名称（模糊匹配）
 * @param jobGroup    任务分组（精确匹配）
 * @param status      调度状态（PAUSED / RUNNING / COMPLETED）
 * @param triggerType 触发类型（CRON / SIMPLE / ONE_TIME）
 * @param taskKey     处理器标识（精确匹配）
 * @param page        页码（从 1 开始）
 * @param size        每页条数
 */
public record JobQuery(String jobName, String jobGroup, String status, String triggerType,
                       String taskKey, int page, int size) {

    public JobQuery {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
    }
}
