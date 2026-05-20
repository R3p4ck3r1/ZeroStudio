# Gradle Tooling API 9.5.1 全面对接审计与实施计划

## 目标
将项目内 `./tooling/api`, `./tooling/model`, `./tooling/events`, `./tooling/impl`, `./tooling/builder-model-impl` 从当前实现升级到与 `gradle-tooling-api-9.5.1-sources` 对齐，覆盖客户端、服务端、中间层三端契约与行为。

## 审计基线
- SDK 源码路径：
  - `gradle/libs/android/zero/studio/gradle/gradle-tooling-api/9.5.1/gradle-tooling-api-9.5.1-sources/org/gradle/tooling`
- 当前实现核心：
  - 连接/构建执行：`ToolingApiServerImpl`
  - 模型同步：`RootModelBuilder` + `*ProjectModelBuilder`
  - 事件桥接：`ForwardingProgressListener` + `EventTransformer`

## 总体差距（面向官方 API 文档与建议）
1. **事件能力缺口**：尚未完整消费 9.5.1 problems/lifecycle 事件族。
2. **客户端契约缺口**：`tooling/events` 缺少 problems/lifecycle DTO 契约族。
3. **能力协商缺口**：缺少“按 Gradle/Tooling API 能力自动降级”的显式策略层。
4. **操作能力缺口**：`TestLauncher`/高级 `BuildAction`/分阶段动作能力未体系化封装。
5. **验证缺口**：缺少基于 Tooling API 版本矩阵的端到端回归用例。

## 分阶段实施（客户端+服务端+中间层）

### Phase 0: 接口矩阵建档（审计）
输出 `Tooling API 9.5.1 -> 当前实现` 对照表：
- 连接层：`ProjectConnection` 各入口
- 执行层：`BuildLauncher`, `TestLauncher`, `BuildActionExecuter`, `ModelBuilder`
- 事件层：`OperationType` + `events/*`
- 异常层：`UnsupportedVersionException`/`UnsupportedOperationConfigurationException`/`UnsupportedBuildArgumentException`

交付：可追踪的“支持/部分/未支持/不适用”矩阵。

### Phase 1: Problems 事件族全量接入
- 服务端：在 progress listener 中识别并转换
  - `ProblemEvent`
  - `ProblemSummariesEvent`
  - `ProblemAggregationEvent`
- 中间层：在 `EventTransformer` 增加 problems -> 项目事件 DTO 映射
- 客户端：接收后可用于诊断面板/构建问题聚合

### Phase 2: Lifecycle 构建阶段事件接入
- 接入 `BuildPhaseStartEvent/BuildPhaseProgressEvent/BuildPhaseFinishEvent`
- 增强构建 UI：明确“配置/执行/收尾”等阶段可视化

### Phase 3: 执行 API 能力补齐
- `TestLauncher` 能力接入（按能力协商降级）
- `BuildAction`/必要时 `PhasedBuildAction` 封装到 server 对外契约
- 补齐长任务配置（参数、取消、监听、标准 IO）的一致性策略

### Phase 4: 稳定性与兼容性策略
- 能力探测优先 + 异常兜底：
  - 不支持时降级到可用 API
  - 统一错误语义返回给客户端
- 高事件吞吐下的节流/批处理策略

### Phase 5: 验证与回归
- 版本矩阵：当前兼容版本、目标 9.5.1
- 场景矩阵：Android/Java、多模块、网络下载、配置失败、测试执行
- 指标：事件丢失率、构建时延、错误可解释性

## 模块级改造清单

### `tooling/api`
- 新增请求参数：事件订阅能力、problems 开关、test launcher 选项
- 新增响应/错误码：能力不可用、降级执行、部分结果

### `tooling/model`
- 增加 Tooling API 运行时能力快照模型（版本/特性支持位）
- 新增 problems/lifecycle 对应模型 DTO

### `tooling/events`
- 新增 problems/lifecycle 事件族
- 统一事件基类字段（descriptor/time/displayName）

### `tooling/impl`
- `Main.progressUpdateTypes()` 扩展并按能力启用
- `ForwardingProgressListener`/`EventTransformer` 接 problems/lifecycle
- `ToolingApiServerImpl` 增加 test/build action 等执行入口与降级策略

### `tooling/builder-model-impl`
- 对新增 DTO 提供默认实现（可序列化、可兼容旧字段默认值）

## 风险与缓解
1. **Gradle 版本差异导致运行时异常**
   - 缓解：按能力探测 + fallback API。
2. **事件量过大影响性能**
   - 缓解：批量推送 + 去重 + 可配置采样。
3. **跨端契约不一致**
   - 缓解：先定义统一 schema，再逐端落地。

## 里程碑验收标准
- M1：Problems + Lifecycle 事件可在客户端看到并结构化展示。
- M2：执行入口支持 build/model/test/action，失败具备可解释降级信息。
- M3：9.5.1 API 对照矩阵中“未支持”项仅剩明确标注“不适用”条目。

## 下一步（立即执行）
1. 先做 Phase 0 矩阵文档（接口级逐项落表）。
2. 并行启动 Phase 1 的 events DTO 与 transformer 骨架。
3. 最后接入 server 执行入口并补兼容回退。
