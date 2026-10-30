package com.acme.scaffold.job.port;

import com.acme.scaffold.job.entity.SysJobDO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 任务依赖编排（DAG）持久化端口：隔离 {@code sys_job} 的读取与「完成驱动」所需的写回，
 * 业务编排逻辑（{@link com.acme.scaffold.job.service.JobDagService}）只依赖本接口，不触碰 jOOQ DSL。
 *
 * <p>依赖边以 {@code sys_job.depends_on}（JSON 数组文本）存储；本端口负责：
 * <ul>
 *   <li>构建「被依赖者 → 依赖者」的反向索引（{@link #findDependents} / {@link #dependentIds} / {@link #dependentsMap}）；</li>
 *   <li>提供任务读取与存在性集合（{@link #findById} / {@link #allIds}）；</li>
 *   <li>提供完成驱动所需的精确状态写回（{@link #setRunning} / {@link #setStatus}）。</li>
 * </ul>
 */
public interface JobDagPort {

    /** 所有未删除任务的主键集合（用于依赖存在性校验）。 */
    Set<Long> allIds();

    /** 按 ID 加载任务（已删除或不存在返回 empty）。 */
    Optional<SysJobDO> findById(Long id);

    /** 返回依赖指定任务的所有任务（被依赖者 → 依赖者 的反向索引）。 */
    List<SysJobDO> findDependents(Long id);

    /** 依赖指定任务的任务 ID 列表（与 {@link #findDependents} 等价，仅返回 ID）。 */
    List<Long> dependentIds(Long id);

    /** 批量返回「任务ID → 依赖它的任务ID列表」映射，用于列表/详情批量填充。 */
    Map<Long, List<Long>> dependentsMap(Set<Long> ids);

    /** 将任务状态置为 RUNNING 并记录调度引用与下次执行时间（完成驱动即时触发时调用）。 */
    void setRunning(Long id, String schedulerRef, LocalDateTime nextFireTime);

    /** 仅更新任务调度状态（精确写，不触碰其他列）。 */
    void setStatus(Long id, String status);
}
