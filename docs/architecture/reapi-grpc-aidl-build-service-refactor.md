# 构建服务重构方案（深度源码评估版）

> 目标：基于对 `tooling/***`、`core/projects`、`core/app` 现有源码的全链路分析，给出可落地的 **lsp4j 全量移除** 与 **gRPC + REAPI + AIDL** 重构方案。

---

## 1. 现状全景：现有构建链路如何工作

## 1.1 当前端到端链路（代码事实）

1. `core/app` 的 `GradleBuildService` 作为 Android 前台 Service，同时实现 `BuildService` + `IToolingApiClient`，负责启动/持有 tooling 进程与转发事件。  
2. tooling 侧通过 `ToolingApiLauncher`（lsp4j JSON-RPC）建立 `IToolingApiServer`/`IToolingApiClient` 双向代理。  
3. `tooling/impl/Main.kt` 通过 `System.in/System.out` 启动 JSON-RPC listener；`ToolingApiServerImpl` 调用 Gradle Tooling API 完成 initialize / executeTasks / cancel / shutdown。  
4. `core/projects/ProjectManagerImpl` 依赖 `BuildService` 进行源码生成任务（如 `process...Resources`、`dataBinding...`），并在完成后触发重新索引。  

---

## 2. 现有问题评估（结合源码）

## 2.1 协议层与序列化层问题（lsp4j / Gson / JSON-RPC）

### 问题 A：构建域模型多态过重，JSON 反序列化成本高
- `ToolingApiLauncher.configureGson()` 注册了大量 runtimeTypeAdapter（`ProgressEvent`、`OperationResult`、`ProjectMetadata` 等多层级多态）。
- 每次事件往返都依赖字符串字段（`gsonType`）与反射/分发路径，移动端容易产生对象风暴与 GC 压力。

### 问题 B：通信与业务模型强绑定到 lsp4j 注解
- `IToolingApiServer` / `IToolingApiClient` 直接使用 `@JsonRequest/@JsonNotification/@JsonSegment`。
- 这导致上层 API 合同被协议实现“反向污染”，协议升级代价高。

### 问题 C：IO 通道是 `stdin/stdout` 文本流，结构化背压能力弱
- `Main.kt` 使用 `ToolingApiLauncher.newServerLauncher(..., System.in, System.out)`。
- 日志与协议通道在进程边界上容易竞争，缺乏原生流控与 QoS 优先级。

## 2.2 线程与并发问题

### 问题 D：`newCachedThreadPool()` 无上限扩容风险
- `ToolingApiLauncher.newIoLauncher()` 使用 `Executors.newCachedThreadPool()`。
- 在高频事件或阻塞场景可能扩容过快，引发线程数与内存峰值放大。

### 问题 E：服务端构建互斥状态管理较脆弱
- `ToolingApiServerImpl` 通过 `isBuildInProgress` 布尔位控制并发，runBuild 抛异常阻止并行。
- 无队列/优先级/公平调度模型，无法支持“前台交互 > 后台同步”的真实场景。

## 2.3 生命周期与资源治理问题

### 问题 F：服务层承担过多职责
- `GradleBuildService` 同时负责：前台通知、tooling 进程生命周期、事件消费、日志处理、构建状态管理、资源清理。
- 单体服务复杂度高，不利于替换协议与分层治理。

### 问题 G：构建进程清理依赖 best-effort
- `onDestroy()` 中存在 `killGradlewProcesses()`、`destroy()`、超时 shutdown 等兜底，说明“优雅关闭路径”不稳定。
- 缺乏 session lease + server heartbeat + 操作级 deadline 的协议化治理。

## 2.4 架构耦合问题

### 问题 H：core/projects 与构建执行细节耦合偏深
- `ProjectManagerImpl.generateSources()` 直接拼接任务名并调用 `BuildService.executeTasks()`。
- 项目域逻辑、执行策略、传输协议之间界限不清，难以引入远程执行策略。

### 问题 I：应用层保留 lsp4j 依赖
- `core/app/build.gradle.kts` 仍引入 `org.eclipse.lsp4j`，且 proguard 有 lsp4j keep 规则。
- 表明协议依赖已渗透到 app 层，迁移必须“分层剥离”。

---

## 3. 性能压力根因结论

从源码看，当前性能压力主要来自：
1. **JSON 多态反序列化热路径**（事件频繁时最明显）。
2. **线程池与异步模型缺乏明确上限和背压机制**。
3. **服务职责过载导致 UI/构建生命周期互相干扰**。
4. **协议语义与构建语义不对齐**，造成额外适配和数据复制。

---

## 4. 新架构目标（强约束）

1. 彻底移除 `tooling/***`、`core/projects`、`core/app` 中的 lsp4j 协议依赖。
2. 采用三平面架构：
   - **AIDL（本地控制平面）**：Android 进程间控制/生命周期。
   - **gRPC（控制与事件平面）**：强类型 RPC + 双向流。
   - **REAPI（执行与工件平面）**：CAS/AC/Execution。
3. 将“项目域编排”和“协议实现”完全解耦。

---

## 5. 新架构设计（最佳机制版）

## 5.1 逻辑分层

### A. `core/app`（UI 与 Android 生命周期）
- 新增 `BuildClientFacade`（仅暴露 UI 语义接口）。
- 仅通过 `IBuildGatewayService.aidl` 访问本地构建网关。
- UI 只接收节流后的 `BuildUiSnapshot`（非原始底层事件）。

### B. `core/projects`（项目域与编排域）
- 新增 `ProjectBuildDomainService`：
  - 输入：`BuildIntent`（sync/build/test/generateSources）
  - 输出：`ExecutionPlan`（可本地可远程）
- 新增 `BuildPolicyEngine`：根据网络、设备状态、缓存命中预测选择 Local/Remote/Hybrid。
- `ProjectManagerImpl.generateSources()` 改为提交领域 intent，不再直接拼接执行细节。

### C. `tooling/***`（传输与执行域）
- `tooling/transport-contract`：proto + aidl 合同。
- `tooling/transport-grpc-client`：channel、interceptor、retry、deadline、tracing。
- `tooling/transport-grpc-server`：session/operation 管理、流式事件总线。
- `tooling/reapi-client`：CAS/AC/Execution。
- `tooling/build-orchestrator`：操作状态机、取消传播、背压。

## 5.2 协议设计

### AIDL（本地轻控制）
```aidl
interface IBuildGatewayService {
  SessionToken openSession(in ProjectDescriptor project);
  void submit(in SessionToken session, in BuildIntentParcel intent, in IBuildCallback callback);
  void cancel(in SessionToken session, String operationId);
  void setForegroundState(in SessionToken session, boolean isForeground);
  void closeSession(in SessionToken session);
}
```

约束：
- AIDL 不传大日志正文/大工件。
- 回调只传 operation 状态摘要与必要错误。

### gRPC（强类型控制面）
- `BuildControlService`：`StartOperation`、`CancelOperation`、`GetOperation`、`StreamEvents`。
- 所有请求强制字段：`session_id`、`operation_id`、`deadline_ms`、`idempotency_key`。
- 引入 client hint：`ui_priority`、`max_event_rate`、`preferred_result_detail`。

### REAPI（执行面）
- 先 AC lookup，再 execution。
- 输入树 Merkle 化，输出统一 CAS 引用。
- 输出日志也可选择 CAS chunk 化（避免内存聚合）。

## 5.3 关键机制（性能/稳定性）

1. **事件分级总线**：
   - P0：状态变更（开始/阶段/结束/失败）
   - P1：诊断摘要
   - P2：详细日志（可降采样）
2. **双背压机制**：
   - gRPC HTTP/2 流控 + 应用层 `window_hint`。
3. **操作预算系统**：
   - 每 operation 配置 CPU/内存/日志预算。
   - 预算触发后自动降级事件粒度。
4. **可恢复会话**：
   - session lease + heartbeat，服务重启后可恢复 operation 状态。
5. **确定性取消链**：
   - UI cancel -> AIDL cancel -> gRPC cancel -> Gradle/REAPI cancel token 级联。

---

## 6. 针对当前模块的升级改造清单

## 6.1 tooling/***

### 需要移除
- `tooling/api` 中 lsp4j jsonrpc 依赖与注解接口。
- `ToolingApiLauncher` 的 Gson runtimeTypeAdapter 方案。

### 需要新增
- proto 合同：`build_control.proto`、`build_events.proto`、`build_domain.proto`。
- REAPI adapter：action digest 计算、AC lookup、execution submit、CAS 上传下载。
- `OperationStateMachine`（Queued/Running/Cancelling/Completed/Failed）。

## 6.2 core/projects

### 需要调整
- `BuildService` 由“具体协议调用接口”升级为“领域语义接口”。
- `ProjectManagerImpl` 的 `generateSources()` 改为提交 `GenerateSourcesIntent`。

### 新增能力
- 构建意图去重（同会话同参数短时间内合并）。
- 任务图与变体映射缓存（减少重复组装）。

## 6.3 core/app

### 需要移除
- app 层对 lsp4j/proguard keep 规则依赖。
- `GradleBuildService` 里协议/进程/日志/通知重耦合实现。

### 需要新增
- `BuildGatewayAndroidService`（仅 AIDL 网关 + lifecycle）。
- `BuildEventReducer`（流事件 -> UI 快照，100~200ms 节流）。
- 输出窗口化缓存（UI只保留最近 N 行）。

---

## 7. 迁移路径（可回滚）

### Phase 0：基线测量
- 增加 trace_id/session_id/operation_id。
- 记录当前：构建耗时、峰值内存、GC、慢帧、OOM。

### Phase 1：合同先行
- 引入 AIDL + proto 合同，不切流。
- 建立桥接层：旧 `BuildService` -> 新 `BuildClientFacade`。

### Phase 2：双栈运行
- sync / generateSources 先迁移至 gRPC。
- build/test 保持旧栈，便于回滚。

### Phase 3：执行面切换
- build/test/run 全量走 gRPC + REAPI。
- 开启 AC/CAS 命中监控。

### Phase 4：lsp4j 删除
- 删除 `tooling/api` 的 jsonrpc 注解与 launcher。
- 删除 `core/app` lsp4j 依赖与 proguard keep。

### Phase 5：性能收敛
- 根据指标调优：事件速率、并发窗口、缓存策略。

---

## 8. 量化验收指标（必须同时满足）

1. P50 构建耗时下降 ≥ 20%，P95 下降 ≥ 15%。
2. 构建期间 app 进程峰值内存下降 ≥ 25%。
3. 构建相关慢帧下降 ≥ 30%。
4. OOM 率下降 ≥ 40%。
5. 重复构建（命中 AC）耗时下降 ≥ 50%。
6. 取消请求 2 秒内生效率 ≥ 95%。

---

## 9. 风险与防线

1. **双栈复杂度上升** -> 限期双栈，阶段完成后强制清理旧路径。  
2. **远程执行不稳定** -> 本地 fallback + 短 deadline + 幂等重试。  
3. **事件洪峰影响 UI** -> 事件分级 + 降采样 + 快照节流。  
4. **缓存一致性** -> Action 输入规范化 + 环境指纹强校验。  

---

## 10. 结论

本次方案不是“只换通信协议”，而是将当前构建服务从“lsp4j + 进程文本流 + 单体服务”升级为“领域编排清晰、协议分层明确、执行可观测可治理”的体系化架构。其核心收益在于：
- 消除 JSON-RPC 多态序列化热路径；
- 降低线程/内存抖动与 UI 卡顿风险；
- 引入 REAPI 缓存与远程执行能力；
- 让 `tooling/***`、`core/projects`、`core/app` 在职责上可长期演进。
