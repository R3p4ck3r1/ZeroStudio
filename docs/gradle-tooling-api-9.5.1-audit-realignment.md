# Gradle Tooling API 9.5.1 审计纠偏报告（基于本地源码逐项核对）

## 说明（纠偏）
本报告只基于仓库内源码比对，不使用外部网页信息：
- `gradle/libs/android/zero/studio/gradle/gradle-tooling-api/9.5.1/gradle-tooling-api-9.5.1-sources/org/gradle/`
- 项目内：`tooling/api`, `tooling/model`, `tooling/events`, `tooling/impl`, `tooling/builder-model-impl`

并且本次结论明确区分：
1) `org.gradle.tooling`（Tooling API 客户端接口）
2) `org.gradle.*` 其他内部/运行时实现包（通常不是外部 Tooling API 消费面）

## 快速统计
- `org/gradle/tooling` 源文件（含子包）：240 个。
- 项目内出现 `org.gradle.tooling` 引用：88 处（Kotlin 文件）。

> 结论：项目已经具备 Tooling API 使用基础，但距离“9.5.1 全量能力对齐”仍存在明确缺口，尤其是 problems/lifecycle 事件族与执行器能力矩阵。

## 已对接（确认）
1. 连接/构建执行主链路：`ProjectConnection` + `newBuild()` + 取消令牌。
2. 模型同步主链路：`BuildController` + `findModel`。
3. Progress 事件主链路：`ProgressListener` + `OperationType` 订阅。
4. 已启用操作类型：`TASK/PROJECT_CONFIGURATION/FILE_DOWNLOAD/TRANSFORM/WORK_ITEM/GENERIC`。

## 未完整对接（重点差距）

### A. problems 事件族（9.5.1 重要能力）
在 9.5.1 源码中存在 `org.gradle.tooling.events.problems.*`（ProblemEvent/ProblemSummariesEvent/ProblemAggregationEvent 等），
但项目代码未见该包的直接消费与转换映射。

影响：
- IDE 不能结构化接收 Gradle 问题流与聚合问题统计。
- 构建失败诊断仍依赖文本日志或旧事件，不利于精准提示。

### B. lifecycle 构建阶段事件族
9.5.1 提供 `org.gradle.tooling.events.lifecycle.*`（BuildPhaseStart/Progress/Finish）。
项目代码未见针对该族的映射 DTO 与上报。

影响：
- 构建 UI 无法准确展示阶段级进度（配置/执行/收尾）。

### C. 执行器能力矩阵未体系化
本地实现核心使用 `BuildLauncher`，但对 `TestLauncher` / 更完整 action executer 能力未形成系统级 API 契约与降级策略。

影响：
- 客户端无法稳定按能力选择最佳执行路径。
- 版本差异场景下降级行为不可预测。

### D. 能力协商与兼容策略未显式产品化
虽然代码里处理了部分 `Unsupported*` 异常，但尚未形成统一 capability snapshot + 决策层（服务端/客户端共享）。

## 为什么之前看起来“不专业/不全”
- 之前文档更偏“计划”，缺少明确的源码分层边界（`org.gradle.tooling` vs `org.gradle.*`）。
- 缺少可量化统计与“已对接/未对接”证据化列举。
- 未明确指出某些 `org.gradle.*` 包属于 Gradle 内部实现，不等于 Tooling API 外部接口待对接项。

## 下一步执行计划（务实版）

### Step 1（先做，1 次提交）
新增“能力矩阵”文档，逐项覆盖 `org.gradle.tooling` 主要接口族：
- connection/build/model/test/action/events/exceptions
- 每项标注：已支持 / 部分支持 / 未支持 / 不适用（附代码位置）

### Step 2（2-3 次提交）
接 problems + lifecycle 事件：
- `tooling/events` 新增 DTO
- `EventTransformer` 增加转换
- `ForwardingProgressListener` 接入分发

### Step 3（1-2 次提交）
执行器能力补齐：
- 引入 `TestLauncher` 对外请求/响应协议
- action executer 能力包装与降级

### Step 4（1 次提交）
统一 capability snapshot：
- 服务端返回 Tooling API 能力位
- 客户端按能力选择路径

### Step 5（1 次提交）
端到端回归与验证文档：
- 场景：Android/Java、多模块、失败诊断、测试运行、事件流完整性

## 当前可直接开始的开发任务
- 任务 T1：先实现 problems 事件最小闭环（ProblemEvent + ProblemSummariesEvent）
- 任务 T2：补 lifecycle 阶段事件（BuildPhaseStart/Finish）
- 任务 T3：在客户端协议层增加 capability 字段（可选）

