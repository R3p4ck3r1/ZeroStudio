# Gradle Tooling API 9.5.1 深度对接审计矩阵（源码逐对象）

> 本文档仅基于仓库内源码核对：
> - Tooling API 源：`gradle/libs/android/zero/studio/gradle/gradle-tooling-api/9.5.1/gradle-tooling-api-9.5.1-sources/org/gradle/tooling`
> - 项目实现：`tooling/api`, `tooling/model`, `tooling/events`, `tooling/impl`, `tooling/builder-model-impl`

## 0. 方法与范围
- 本次聚焦 **`org.gradle.tooling` 外部可消费 API**，不把 `org.gradle.cache` / `org.gradle.groovy` 等 Gradle 内部运行时包误当作 IDE 客户端必须直连的对接对象。
- 逐对象分组：连接、构建执行、模型拉取、事件、测试执行、Action 执行、异常与兼容。

## 1. API 对接总览（结论）

| 领域 | 9.5.1 关键对象 | 当前状态 | 结论 |
|---|---|---|---|
| 连接 | `GradleConnector`, `ProjectConnection` | 已使用 | ✅ 基础可用 |
| 构建执行 | `BuildLauncher`, `LongRunningOperation`, `CancellationTokenSource` | 已使用 | ✅ 基础可用 |
| 模型拉取 | `BuildController`, `ModelBuilder` | 已使用 | ✅ 基础可用 |
| 事件主干 | `events.ProgressListener`, `OperationType` | 已使用 | ✅ 基础可用 |
| Problems事件族 | `events.problems.*` | 未接入 | ❌ 关键缺口 |
| Lifecycle阶段事件 | `events.lifecycle.*` | 未接入 | ❌ 关键缺口 |
| 测试执行 | `TestLauncher`, `TestSpecs` | 未接入 | ❌ 缺口 |
| Action执行扩展 | `BuildActionExecuter`, `IntermediateResultHandler`, streamed values | 未接入 | ❌ 缺口 |
| 兼容协商 | `UnsupportedVersionException` 等 + 能力快照 | 仅异常处理，缺少能力模型 | ⚠️ 部分 |

## 2. 逐对象审计（带证据）

### 2.1 连接与执行主链路
- 已实现 `ProjectConnection` + `newBuild()` 任务执行。
- 已实现 `CancellationTokenSource` 取消。
- 已实现 launcher 参数与输出流配置。

判定：**已对接核心 build path**。

### 2.2 模型同步链路
- 已实现 `RootModelBuilder` + `BuildController.findModel`。
- Android 模型侧已对接 AGP 模型链（这部分偏 AGP，不赘述）。

判定：**已对接核心 model path**。

### 2.3 事件主干 vs 事件族缺口
- 已对接：Task / ProjectConfiguration / Download / Transform / WorkItem / Generic。
- 未对接：
  - `org.gradle.tooling.events.problems.ProblemEvent`
  - `ProblemSummariesEvent`
  - `ProblemAggregationEvent`
  - `org.gradle.tooling.events.lifecycle.BuildPhaseStartEvent/ProgressEvent/FinishEvent`

判定：**事件主干已搭好，但 9.5.1 新增高价值事件族未落地**。

### 2.4 测试执行 API
- 9.5.1 提供 `TestLauncher` + `TestSpecs` 系列用于按类/方法/任务路径执行测试。
- 项目内未发现 `TestLauncher` 对接代码与请求协议。

判定：**未对接**。

### 2.5 Action 执行扩展
- 9.5.1 提供 `BuildActionExecuter`、`IntermediateResultHandler`、`StreamedValueListener` 等扩展。
- 项目内未发现相关消费路径。

判定：**未对接**。

### 2.6 异常与兼容
- 项目已有 `UnsupportedVersionException` / `UnsupportedOperationConfigurationException` / `UnsupportedBuildArgumentException` 捕获。
- 但未形成 `CapabilitySnapshot`（服务端输出能力位 + 客户端决策）的显式模型。

判定：**部分对接**。

## 3. 模块级精确改造点

### 3.1 `tooling/api`
新增：
1. `RunTestsParams`（封装 TestLauncher 目标）
2. `ToolingCapabilitySnapshot`（problems/lifecycle/testLauncher/actionExecuter 支持位）
3. `BuildActionRequest`（可选）

### 3.2 `tooling/events`
新增事件 DTO 族：
1. Problems：ProblemEvent/Summaries/Aggregation + 子结构（Location/Severity/Definition/Solution）
2. Lifecycle：BuildPhaseStart/Progress/Finish

### 3.3 `tooling/impl`
1. `Main.progressUpdateTypes()` 增补与按能力开启
2. `ForwardingProgressListener` 分发 problems/lifecycle
3. `EventTransformer` 增加 problems/lifecycle 转换
4. `ToolingApiServerImpl` 新增 `executeTests()` 与能力降级分支
5. 可选：`runBuildAction()`（BuildActionExecuter 封装）

### 3.4 `tooling/model` / `builder-model-impl`
1. 增加 capability snapshot 模型
2. 为新增 events DTO 提供可序列化默认对象/转换器

## 4. 任务拆分（强执行版）

### T1（必须）Problems 事件闭环
- 产物：事件 DTO + transformer + listener 接线 + client 可见
- 验收：可收到 ProblemSummariesEvent 并展示问题计数

### T2（必须）Lifecycle 阶段事件闭环
- 产物：BuildPhaseStart/Finish DTO + 接线
- 验收：UI 能区分配置/执行阶段

### T3（必须）TestLauncher 对接
- 产物：API params + server 执行入口 + cancel/日志/错误映射
- 验收：可按 taskPath 或 class/method 触发测试

### T4（建议）Capability Snapshot
- 产物：init/sync 时返回能力位
- 验收：客户端按能力路由（不支持则自动降级）

### T5（建议）Action Executer 扩展
- 产物：BuildAction 请求通路（可延后）
- 验收：至少支持一个 action smoke case

## 5. 关键更正（对你反馈的回应）
- 是的，之前文档不够细，且未把“Tooling API外部接口”与“Gradle内部实现包”边界讲透。
- 本文已纠正：只按 `org.gradle.tooling` 逐对象评估，并把未对接对象点名到类级别。

