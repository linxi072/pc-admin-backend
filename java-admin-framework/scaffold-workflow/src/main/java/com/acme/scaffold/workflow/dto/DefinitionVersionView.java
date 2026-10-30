package com.acme.scaffold.workflow.dto;

/**
 * 流程定义版本链条目（M23）：同 processKey 的每次部署形成一个版本，
 * 版本号降序展示，suspended 标记挂起态（挂起定义的新实例无法发起）。
 */
public record DefinitionVersionView(
        String id,
        int version,
        String name,
        String key,
        boolean suspended,
        String deploymentId,
        java.time.LocalDateTime deployTime) {
}
