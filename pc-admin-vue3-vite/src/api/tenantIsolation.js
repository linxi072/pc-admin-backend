import { request } from './http'

/**
 * GET /api/system/tenant-isolation/status -> TenantIsolationStatusView
 *
 * 租户隔离态势（M28 只读运维诊断）：聚合隔离「配置态 + 运行态 + 当前执行作用域 + 配置校验结果」，
 * 供运维诊断页只读展示，不写库、不触发隔离逻辑。
 *
 * 字段说明：
 *   isolationEnabled   多租户隔离总开关（app.tenant.enabled）
 *   enforceEnabled     强制隔离运行态（app.tenant.enforce，行级 tenant_id 注入是否生效）
 *   configured         配置快照是否已注入（TenantEnforcement.configure 是否被启动时调用）
 *   defaultTenantId    默认租户 ID
 *   platformAdminRoleCode  平台管理员角色码
 *   headerName         tenant 请求头名称
 *   claimName          Token 中承载 tenant 的声明名
 *   platformTables     平台共享维表（豁免隔离的表集合）
 *   scopeSet           当前请求线程是否已设置租户作用域
 *   currentTenantId    当前执行作用域的租户 ID
 *   currentPlatformAdmin  当前作用域是否平台管理员
 *   currentBypass      当前作用域是否跨租户旁路
 *   validationStatus   配置校验状态：OK / INVALID / DISABLED
 *   validationMessage  配置校验说明（INVALID 时含修正建议）
 */
export const getTenantIsolationStatus = () =>
  request({ method: 'GET', url: '/api/system/tenant-isolation/status' })

/**
 * GET /api/system/tenant-isolation/audit -> TenantIsolationAuditReport
 *
 * 存量隔离自检（M45 安全开启 enforce 预检）：扫描全部业务表 tenant_id 归属情况，
 * 给出「可否安全开启强制隔离」的判定，供运维在开启多租户 enforce 前只读预检。
 *
 * 字段说明：
 *   totalBusinessTables 纳入检查的业务表总数
 *   violatedTables      存在未归属数据的表数量（含检查失败的表）
 *   totalViolatedRows   未归属行数合计（检查失败的表不计入合计）
 *   safeToEnable       是否可安全开启 enforce
 *   byTable            各表违规明细（tableName / violatedRows；-1 表示检查失败）
 *   message            面向运维的判定说明
 */
export const auditTenantIsolation = () =>
  request({ method: 'GET', url: '/api/system/tenant-isolation/audit' })
