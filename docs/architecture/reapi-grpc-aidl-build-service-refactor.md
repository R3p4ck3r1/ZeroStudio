# 构建服务重构方案（彻底替换版）

> 目标：基于对 `tooling/***`、`core/projects`、`core/app` 的源码评估，执行**一次性彻底替换**：完整移除旧 lsp4j/JSON-RPC 架构，不保留旧协议运行路径，统一升级为 **AIDL + gRPC + REAPI**。

---

## 1. 现有链路与根问题（源码事实）

### 1.1 当前链路
1. `core/app` 的 `GradleBuildService` 同时承担前台服务、构建状态、tooling 进程、协议客户端等职责。  
2. `tooling/api` 通过 `ToolingApiLauncher` + lsp4j JSON-RPC 连接 `IToolingApiServer/IToolingApiClient`。  
3. `tooling/impl/Main.kt` 用 `System.in/System.out` 挂载通信；`ToolingApiServerImpl` 对接 Gradle Tooling API。  
4. `core/projects/ProjectManagerImpl` 直接调用 `BuildService.executeTasks()` 触发生成/构建。  

### 1.2 性能与稳定性根因
1. **JSON 多态序列化热路径过重**：`ToolingApiLauncher.configureGson()` 注册大量 runtimeTypeAdapter，事件频繁时 GC/分配压力大。  
2. **协议绑定侵入业务合同**：`IToolingApiServer/Client` 使用 lsp4j 注解，导致协议与领域接口耦合。  
3. **并发控制粗粒度**：`newCachedThreadPool()` + `isBuildInProgress` 布尔位，缺少上限、优先级与背压。  
4. **服务职责过载**：`GradleBuildService` 单体化，生命周期/通知/构建/通信/清理耦合，演进困难。  
5. **旧链路的文本流通信上限低**：`stdin/stdout` 缺少现代流控、事件分级与 QoS。  

---

## 2. 重构硬约束（这次必须满足）

1. **不保留旧协议运行路径**：
   - 删除 lsp4j JSON-RPC 运行链路；
   - 删除 `ToolingApiLauncher` 作为生产传输入口；
   - 删除 app 层 lsp4j 依赖与 keep 规则。  
2. **统一新传输栈**：
   - 本地控制：AIDL
   - 远程控制与事件：gRPC
   - 执行与缓存：REAPI（CAS/AC/Execution）
3. **统一编排语义**：`core/projects` 只输出领域 Intent/Plan，不再直连旧协议细节。

---

## 3. 新架构（最终态，非双栈）

## 3.1 `core/app`（客户端生命周期层）
- 保留 Android Service 形态，但重构为 `BuildGatewayAndroidService`：
  - 仅负责 AIDL 入口、会话生命周期、前后台优先级。
  - 不再持有旧 lsp4j client/server proxy。
- 新增 `BuildEventReducer`：
  - 将 gRPC 流式事件折叠为 UI 快照；
  - 100~200ms 节流推送，避免主线程抖动。

## 3.2 `core/projects`（领域编排层）
- 新增 `ProjectBuildDomainService`：
  - 输入：`BuildIntent(sync/build/test/generateSources/run)`
  - 输出：`ExecutionPlan`
- 新增 `BuildPolicyEngine`：
  - 决策 Local/Remote/Hybrid（**均基于新栈，不回退旧栈**）。
- `ProjectManagerImpl.generateSources()` 改为提交 intent，不直接拼 task 命令到旧接口。

## 3.3 `tooling/***`（传输与执行层）
- `tooling/transport-contract`：AIDL + Proto 合同。
- `tooling/transport-grpc-client`：deadline/retry/interceptor/tracing。
- `tooling/transport-grpc-server`：session/operation/event stream/backpressure。
- `tooling/reapi-client`：CAS/AC/Execution 统一封装。
- `tooling/build-orchestrator`：状态机、取消链、预算治理。

---

## 4. 协议与机制设计

## 4.1 AIDL（仅本地轻控制）
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
- AIDL 不传大日志/大产物；
- 仅传控制命令与状态摘要。

## 4.2 gRPC（控制与事件主通道）
- `BuildControlService`: `StartOperation` / `CancelOperation` / `GetOperation` / `StreamEvents`。
- 强制字段：`session_id`、`operation_id`、`deadline_ms`、`idempotency_key`。
- 事件双通道：
  - `status stream`（高优先）
  - `log stream`（可降采样）

## 4.3 REAPI（执行与缓存通道）
- Action Digest 标准化（命令、env、platform）；
- 先查 AC，未命中再 Execution；
- 输入输出都走 CAS 引用，减少重复传输和内存峰值。

---

## 5. 极致性能机制（核心）

1. **彻底移除 JSON 热路径**：统一 protobuf 编码，消灭 `gsonType` 多态反序列化。
2. **有界并发模型**：固定线程池 + 分级队列（interactive > build > sync）。
3. **端到端背压**：HTTP/2 流控 + 应用层 `window_hint`。
4. **事件分级与窗口化缓存**：UI 只保留近 N 行，历史落盘。
5. **预算驱动降级**：超预算自动降低日志粒度、合并进度事件。
6. **确定性取消**：AIDL cancel → gRPC cancel → REAPI/Gradle token，2 秒内生效为目标。

---

## 6. 模块级“删旧换新”清单

## 6.1 tooling/***
### 删除
- lsp4j jsonrpc 依赖与注解接口。
- `ToolingApiLauncher` 生产路径。
- 旧 stdout/stderr 文本协议通信入口。

### 新增
- proto: `build_control.proto` / `build_events.proto` / `build_domain.proto`。
- `OperationStateMachine`（Queued/Running/Cancelling/Completed/Failed）。
- REAPI Adapter（Digest/AC/CAS/Execution）。

## 6.2 core/projects
### 删除/替换
- 旧 `BuildService` 协议导向接口（替换为领域导向 API）。
- 直接任务字符串拼装执行路径。

### 新增
- `ProjectBuildDomainService`
- `BuildPolicyEngine`
- `ExecutionPlanCache`

## 6.3 core/app
### 删除
- lsp4j 依赖与相关 Proguard keep。
- 旧 `GradleBuildService` 的协议耦合实现。

### 新增
- `BuildGatewayAndroidService`
- `BuildClientFacade`
- `BuildEventReducer`

---

## 7. 实施策略（不是双栈共存，而是“并行开发 + 一次切换”）

### Stage A：新架构并行开发（不接入线上运行）
- 在隔离模块内完成 AIDL/gRPC/REAPI 全链路。
- 引入压测与回归基线，验证吞吐、内存、慢帧。

### Stage B：预发全量验证
- 在测试渠道启用**仅新架构**。
- 旧架构不参与请求分流（避免混栈干扰数据）。

### Stage C：生产一次切换
- 发布版本时直接切换到新架构；
- 同版本中不再保留旧架构执行入口；
- 若失败，回滚到上一个稳定版本（版本级回滚，而非运行时双栈）。

### Stage D：源码清理封版
- 删除全部旧协议代码、依赖、配置与文档。
- 固化新的 SLO 与性能阈值守卫。

---

## 8. 验收标准（必须达标）

1. P50 构建耗时下降 ≥ 25%，P95 下降 ≥ 20%。
2. 构建阶段 app 进程峰值内存下降 ≥ 30%。
3. 构建相关慢帧下降 ≥ 35%。
4. OOM 率下降 ≥ 40%。
5. 重复构建命中 AC 时长下降 ≥ 55%。
6. 取消请求 2 秒内生效率 ≥ 95%。

---

## 9. 结论

本方案强调“**彻底替换**”而非“新旧混用”：
- 不保留旧 lsp4j 协议运行路径；
- 不保留旧 JSON-RPC 文本通道；
- 通过 AIDL + gRPC + REAPI 全面升级 `tooling/***`、`core/projects`、`core/app` 构建服务架构。

这样才能在构建速度、内存占用、UI 流畅度、取消响应与可观测性上获得最大化收益，实现你要求的“全方面升级改造”。
