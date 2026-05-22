# LSP4J-RPC 彻底移除与 AIDL + gRPC + REAPI + Proto 迁移总方案（v1）

> 日期：2026-05-22  
> 适用范围：`core/app`、`core/projects`、`tooling/api`、`tooling/impl`、`tooling/events`、`tooling/model`、后续新增 `tooling/transport-*` 模块。  
> 目标：在不阻断当前迭代交付的前提下，完成构建服务通信协议从 `lsp4j-jsonrpc` 到 `AIDL + gRPC + REAPI(proto)` 的渐进迁移，并显著优化超大项目构建稳定性与性能。

---

## 1. 迁移动因与问题复盘

当前构建链路存在以下结构性瓶颈：

1. **序列化/反序列化开销高**：`lsp4j-jsonrpc + gson` 在大事件流下对象分配频繁。  
2. **单通道语义不足**：缺少针对大规模事件、流式日志、背压控制的原生治理。  
3. **超大构建缓存场景不稳定**：`./build` 达 5GB~100GB+ 时，进程内存峰值和 GC 压力显著，易出现 UI 卡顿、无响应、OOM。  
4. **执行/缓存能力未标准化**：当前协议层没有直接对齐 REAPI 的 CAS/Action/Execution 语义。

---

## 2. 新协议职责分层（最终态）

### 2.1 AIDL（端内 IPC）
- 负责 Android 客户端进程 ↔ 本机构建服务进程的 **低延迟 IPC**。
- 承担：会话管理、生命周期、前后台状态、权限边界。

### 2.2 gRPC + Proto（远程调用与流式传输）
- 负责构建服务 ↔ 远程执行/缓存服务端的 RPC。
- 承担：双向流、超时、重试、压缩、跨语言协议统一。

### 2.3 REAPI（远程执行标准能力）
- 用于大项目构建加速核心能力：
  - CAS（内容寻址缓存）
  - Action Cache
  - Remote Execution
- 与 Gradle 本地执行形成“本地优先 + 远程可选”的混合执行策略。

### 2.4 协议网关（Transport Gateway）
新增传输抽象层：

- `tooling/transport-spi`：统一请求/响应/事件语义（不绑定具体协议）。
- `tooling/transport-aidl`：AIDL 适配器。
- `tooling/transport-grpc`：gRPC+proto 适配器。
- `tooling/transport-legacy-lsp4j`：仅过渡期保留，最终删除。

---

## 3. 与 Gradle Tooling API 9.5.1 升级的协同关系

- 9.5.1 升级的 `ExecutionRequest/ExecutionResult/capability` 是协议中立层，必须继续保留。  
- 事件能力（operationTypes、速率控制）将迁移为：
  - AIDL：本地事件订阅 + Binder 背压阈值；
  - gRPC：server-streaming/bidi-streaming + 窗口流控。  
- 后续 `ModelSnapshot`、`QueryService` 与 `PhasedAction` 均在 transport-spi 层建统一 contract，底层分别映射到 AIDL/gRPC。

---

## 4. 分阶段落地计划（建议 5 个 Sprint）

## Sprint A（当前周 + 下周）：协议中立化与迁移准备

1. 在 `tooling/api` 增加 transport-neutral contract（不含 lsp4j 注解版本）。
2. 新建 `transport-spi`（接口）与 `transport-legacy-lsp4j`（桥接实现）。
3. 保持现有功能不变，仅替换调用入口为 SPI。

**验收**
- build/sync/run 功能行为不退化。
- `lsp4j` 仍可用，但仅作为“legacy adapter”。

## Sprint B：AIDL 本地 IPC 首通

1. 定义 `IToolingSession.aidl`、`IToolingEventCallback.aidl`。
2. 建立会话握手（capabilities、maxEventRate、clientProcessState）。
3. 将 execute/sync 主链路接入 AIDL transport。

**验收**
- 在本地设备仅通过 AIDL 跑通一次完整构建。
- 大量事件下 UI thread 无显著卡顿（以 trace 指标为准）。

## Sprint C：gRPC + Proto 远程通道

1. 建立 proto 契约：`execution.proto`、`events.proto`、`models.proto`。
2. 完成 gRPC client/server 最小可用链路。
3. 支持断线重连、deadline、gzip/zstd（可配置）。

**验收**
- 远程执行链路完成 execute + event streaming。
- 关键请求具备 deadline 与重试策略。

## Sprint D：REAPI 接入（可选开关）

1. 引入 REAPI 所需 proto 与客户端适配层。
2. 实现 CAS push/pull、Action 查询、Execution 提交。
3. 本地缓存与 REAPI cache 双写/回填策略。

**验收**
- 对同一大型项目二次构建耗时下降（需基准报告）。
- 缓存命中指标可观测。

## Sprint E：移除 lsp4j-rpc

1. 删除 `tooling/api`、`tooling/model` 对 `org.eclipse.lsp4j.jsonrpc` 的直接依赖。
2. 删除 `ToolingApiLauncher` 及相关 JSON-RPC 启动逻辑。
3. 清理 `proguard` 与依赖树中的 lsp4j 保留项。

**验收**
- `rg -n "org.eclipse.lsp4j|jsonrpc" core tooling` 无构建服务相关结果。
- 构建服务仅通过 AIDL/gRPC/REAPI 通路工作。

---

## 5. 模块级任务拆解（与当前升级范围对齐）

### 5.1 `tooling/api`
- 新增：transport-neutral DTO（Execution、Capability、EventEnvelope、FailureClass）。
- 改造：现有接口去注解耦合（JsonRequest/JsonNotification 仅留在 legacy adapter）。

### 5.2 `tooling/impl`
- 新增：`TransportGateway`、`TransportSessionManager`。
- 改造：`ToolingApiServerImpl` 不再感知 JSON-RPC，改依赖 SPI。

### 5.3 `tooling/events`
- 新增：统一 `EventEnvelope`（eventId/parentId/traceId/opType/payload）。
- 改造：事件序列化策略从 gson DTO 切为 proto message（gRPC 通道）。

### 5.4 `tooling/model`
- 新增：模型快照分片传输元数据（chunkId/chunkCount/contentHash）。
- 改造：大对象查询改按需分页。

### 5.5 `core/app`
- 新增：AIDL client、连接状态机、前后台节流策略。
- 改造：BuildService 与 UI 消费层由 JSON-RPC 回调改为 Flow/Channel 消费。

### 5.6 `core/projects`
- 新增：能力探测后的请求降级策略（无 REAPI 时自动本地执行）。
- 改造：增量刷新与查询分层，避免单次加载全量模型。

---

## 6. 当前迭代（Iteration1-Week1）追加可执行项

1. 输出 transport-spi 草案接口文件清单与命名约定。  
2. 完成 `lsp4j` 依赖点清单（代码 + gradle + proguard）并分级：立即可删/过渡保留。  
3. 为兼容测试矩阵新增“legacy-lsp4j vs transport-spi-adapter”回归样例。  
4. 补充性能基线采样脚本（构建耗时、峰值 RSS、事件吞吐、GC 次数）。

---

## 7. 风险与治理

1. **一次性切换风险高**：采用“双栈并行 + 开关灰度”，避免硬切。  
2. **AIDL 接口演进困难**：从 v1 起使用 versioned parcelable 与 reserved 字段。  
3. **gRPC 移动端连接稳定性**：必须定义弱网重试与前后台生命周期策略。  
4. **REAPI 服务器异构**：先适配最小子集（CAS + Execute），高级能力后置。

---

## 8. 里程碑定义（完成标准）

- **M1**：Transport SPI 落地，legacy-lsp4j 变成适配器。  
- **M2**：AIDL 本地执行链路成为默认。  
- **M3**：gRPC 远程链路可用于执行与事件。  
- **M4**：REAPI 缓存与执行可选启用。  
- **M5**：仓库内彻底移除构建服务 lsp4j-rpc 依赖。
