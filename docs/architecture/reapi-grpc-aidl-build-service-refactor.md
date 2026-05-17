# 使用 gRPC + Remote Execution API (REAPI) + AIDL 重构构建服务架构方案（替代 lsp4j）

## 1. 目标与边界

### 1.1 重构目标
- 在 `./tooling/***`、`core/project`、`core/app` 中彻底移除当前基于 `lsp4j` 的构建服务协议与通信通道。
- 建立统一的 **三层传输架构**：
  1) 进程内/本机跨进程：AIDL（Android Binder）
  2) 远程高性能 RPC：gRPC（HTTP/2 + Protobuf）
  3) 远程执行与缓存协议：REAPI（CAS/AC/Execution）
- 用“能力分层 + 协议分层”的方式，让 IDE UI、项目模型、构建执行彼此解耦。

### 1.2 非目标（避免范围膨胀）
- 不在第一阶段重写全部语言服务能力（仅聚焦构建、同步、任务执行、日志、状态流）。
- 不在客户端一次性切换所有模块；采用可回滚灰度迁移。

---

## 2. 当前痛点（为何必须替换 lsp4j 路径）

1. **协议语义错位**：LSP/lsp4j 本质是“编辑器语言服务协议”，用于构建服务时语义不自然，导致消息定义臃肿、扩展成本高。  
2. **资源开销高**：JSON-RPC 序列化开销、对象分配与反序列化压力明显，移动端易出现内存抖动和 GC 频繁。  
3. **链路不统一**：本地 IPC、远程执行、日志流、取消/超时语义分散，造成状态管理复杂。  
4. **不可观测性弱**：调用链、重试、缓存命中与队列积压缺少统一指标模型，难做容量治理。

---

## 3. 目标架构（推荐终态）

## 3.1 分层总览

- **UI 层（core/app）**
  - 只面向 `BuildOrchestratorFacade`（单入口）
  - 不直接接触 gRPC stub / REAPI 细节

- **项目编排层（core/project）**
  - 负责项目状态机、任务图、并发调度、取消传播
  - 输出标准化 `BuildPlan` / `ExecutionIntent`

- **传输与执行层（tooling/***）**
  - `AIDL Gateway`：App ↔ 本地构建守护进程
  - `gRPC Control Plane`：构建控制、心跳、日志、事件流
  - `REAPI Data/Exec Plane`：Action 缓存、CAS、远程执行

## 3.2 关键组件

1. **Build Orchestrator（客户端）**
   - 接收“构建/同步/运行测试”意图
   - 生成可缓存 Action（命令、输入树、环境）
   - 调用 REAPI：先查 AC，未命中再执行

2. **Session Manager（服务端）**
   - 为每个项目会话分配隔离上下文（工作目录、凭据、资源配额）
   - 统一管理生命周期：创建、续租、超时回收

3. **Execution Broker**
   - 将 Action 分派至本地执行器或远程集群
   - 内置并发阈值、优先级、背压队列

4. **Streaming Bus（gRPC streaming）**
   - 双向流：状态、日志、进度、诊断事件
   - 支持事件分级（UI 高优先、日志低优先）

5. **Artifact Service（REAPI CAS）**
   - 输入/输出工件统一内容寻址
   - 减少重复传输，降低 I/O 与内存峰值

---

## 4. 协议设计（AIDL + gRPC + REAPI 协同）

## 4.1 AIDL 角色定位（Android 本地入口）

AIDL 仅承担：
- 客户端进程与本机构建守护进程的连接管理
- 会话建立、前台任务绑定、生命周期感知（前后台/低内存）
- 快速失败与降级（例如网络不可用时自动本地执行）

建议 AIDL 接口：
- `IBuildGatewayService`
  - `openSession(ProjectDescriptor): SessionToken`
  - `startBuild(SessionToken, BuildRequest)`
  - `cancelOperation(OperationId)`
  - `subscribeEvents(SessionToken, IBuildEventCallback)`
  - `closeSession(SessionToken)`

> 设计原则：AIDL 只传“轻量控制消息 + Binder 句柄”，大量日志/诊断流经 gRPC 流，避免 Binder 大对象与主线程压力。

## 4.2 gRPC 控制面（核心交互）

使用 protobuf 定义强类型接口：
- `BuildControlService`
  - `StartOperation`
  - `CancelOperation`
  - `GetOperation`
  - `StreamOperationEvents`（server streaming）
  - `InteractiveChannel`（bidirectional streaming，可选）

关键语义：
- 每个操作强制携带 `operation_id`、`session_id`、`deadline`、`priority`
- 所有可重试请求必须带 `idempotency_key`
- 错误码映射统一（gRPC status ↔ 领域错误码）

## 4.3 REAPI 数据与执行面

严格按 REAPI 语义拆分：
- **CAS**：上传输入树、下载产物
- **Action Cache (AC)**：基于 Action Digest 查命中
- **Execution**：未命中时执行，返回执行元数据与输出引用

核心机制：
1. Action 规范化（命令、env、platform properties）
2. 输入根目录 Merkle 化，得到 digest
3. 先查 AC；命中直接回包（秒级反馈）
4. 未命中进入 Execution，结果回写 CAS+AC

---

## 5. 性能与稳定性极致优化策略

## 5.1 内存与对象分配优化
- 使用 protobuf（避免 JSON 解析热点）。
- Event 对象池化（尤其日志/进度事件）。
- 分块传输大日志（chunked streaming），禁止一次性聚合全量日志到内存。
- 对 UI 仅保留“窗口化日志缓存”（如最近 N 行），历史落盘。

## 5.2 减少 UI 卡顿
- `core/app` 只消费“节流后的状态快照”，例如 100~200ms 合并推送一次。
- 重日志流在后台线程消费，主线程只收 summary。
- 进度事件去抖与 coalescing（相同阶段重复事件合并）。

## 5.3 OOM 防护
- 为每会话设置内存预算与软硬阈值：
  - 软阈值：触发日志降采样、低优先级任务降速
  - 硬阈值：拒绝新任务并提示用户
- 大工件统一走文件流/零拷贝路径，不走 Binder 大 Parcel。
- 构建守护进程采用“空闲回收 + 热启动池”平衡内存与冷启动。

## 5.4 并发与背压
- 统一队列模型：`interactive > foreground build > background sync`
- 每类任务设置最大并发，防止资源争抢。
- gRPC 流实现应用层背压信号（客户端可下发 `window_hint`）。

## 5.5 网络与远程执行鲁棒性
- 断网/高延迟时自动切换本地执行策略（Policy-based fallback）。
- REAPI 下载采用并行小块 + 自适应并发。
- 关键 RPC 指数退避重试，强制总 deadline，避免无限等待。

---

## 6. 模块重构蓝图（按目录落地）

## 6.1 tooling/***
建议新增子模块：
- `tooling/transport-contract`：protobuf + AIDL 契约定义
- `tooling/transport-grpc-client`：客户端 stub 与拦截器
- `tooling/transport-grpc-server`：服务端实现
- `tooling/reapi-client`：CAS/AC/Execution 封装
- `tooling/build-orchestrator`：面向 core/project 的统一编排 API

并将现有 lsp4j 依赖迁移策略：
- 先引入新接口适配层（anti-corruption layer）
- 再逐步替换调用点
- 最后删除 lsp4j 与 JSON-RPC 相关代码路径

## 6.2 core/project
- 新建 `ProjectBuildDomainService`
  - 负责 BuildPlan 生成、任务图、取消传播
- 新建 `BuildStateStore`
  - 采用不可变快照，便于 UI 差量渲染
- 新建 `BuildPolicyEngine`
  - 决策本地执行 / 远程执行 / 混合执行

## 6.3 core/app
- 引入 `BuildViewModelFacade`
  - 只消费 `BuildUiState` 与 `BuildEventSummary`
- UI 层不再处理底层协议对象（proto/aidl）
- 前台生命周期变化通过 AIDL 网关通知会话优先级

---

## 7. 迁移路线图（分阶段、可回滚）

### Phase 0：基线与观测先行
- 建立现网基线：构建时长、峰值内存、ANR、OOM、UI 帧率。
- 增加 tracing/metrics（会话、队列、缓存命中、流量）。

### Phase 1：双栈期（lsp4j 与新架构并存）
- 新建 transport-contract + orchestrator，接入少量命令（如 sync）。
- 用 feature flag 控制灰度用户。

### Phase 2：核心路径切换
- build/test/run 迁移到 gRPC + REAPI。
- AIDL 只保留入口与生命周期管理。

### Phase 3：完全移除 lsp4j
- 删除依赖、旧协议模型、桥接适配器。
- 清理无用线程池与遗留状态机。

### Phase 4：性能收敛
- 根据指标调优并发、缓存策略、事件节流参数。
- 固化 SLO 与自动回归基准。

---

## 8. 可观测性与运维治理

必须落地四类指标：
1. **延迟**：P50/P95/P99（启动、队列、执行、产物下载）
2. **资源**：RSS、堆峰值、GC 次数、线程数
3. **质量**：失败率、重试率、取消成功率
4. **缓存**：AC 命中率、CAS 命中率、字节节省量

日志与追踪要求：
- 全链路 `trace_id/session_id/operation_id` 贯穿
- 错误分层（用户错误、环境错误、系统错误）
- 关键事件结构化输出，便于回放与根因分析

---

## 9. 安全与隔离

- gRPC 使用 mTLS（设备证书或短期令牌）
- REAPI 凭据最小权限与短时有效期
- 多项目会话隔离（目录、缓存命名空间、配额）
- 工件下载校验 digest，避免污染与篡改

---

## 10. 风险清单与对策

1. **协议迁移期复杂度上升**  
   对策：双栈仅保留最短窗口；每阶段可回滚。

2. **远程执行不稳定影响体验**  
   对策：本地执行兜底 + 快速超时 + 自愈重试。

3. **事件风暴导致 UI 抖动**  
   对策：事件分级、节流、合并、窗口化缓存。

4. **缓存一致性问题**  
   对策：Action 规范化与环境指纹严格一致。

---

## 11. 最小可行接口草案（示例）

- `BuildOrchestratorFacade`
  - `submit(request): OperationHandle`
  - `cancel(operationId)`
  - `observe(sessionId): Flow<BuildEventSummary>`

- `BuildPolicyEngine`
  - `selectRoute(request, deviceState, networkState): ExecutionRoute`

- `ReapiExecutionClient`
  - `computeActionDigest(plan)`
  - `lookupActionCache(digest)`
  - `execute(action)`
  - `fetchOutputs(resultRefs)`

---

## 12. 预期收益（落地后）

- 构建链路语义统一，减少协议适配层复杂度。
- Protobuf + 流式传输显著降低序列化与内存抖动。
- REAPI 缓存命中可显著缩短重复构建耗时。
- AIDL 与 gRPC 分工明确，降低 Binder 压力与 UI 卡顿概率。
- 可观测性完善后，可持续优化 OOM/ANR/性能瓶颈。

---

## 13. 建议的验收标准（必须量化）

- 相比现网基线：
  - 构建中位时长下降 ≥ 20%
  - 峰值内存下降 ≥ 25%
  - OOM 率下降 ≥ 40%
  - UI 卡顿（慢帧）下降 ≥ 30%
  - 重复构建缓存命中场景时长下降 ≥ 50%

若以上指标不达标，不进入全面切流。
