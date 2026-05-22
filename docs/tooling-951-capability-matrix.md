# Tooling 9.5.1 Capability Matrix v1（迭代1-第1周）

> 更新时间：2026-05-22  
> 评级：`full`（完整支持）、`partial`（部分支持）、`none`（未支持）

## 1. 协议层（`tooling/api`）

| 能力 | 状态 | 说明 |
|---|---|---|
| 通用执行请求 `ExecutionRequest` | full | 已支持 tasks / arguments / jvmArguments / operationTypes / requestId。 |
| 通用执行结果 `ExecutionResult` | full | 已支持 success/failure/diagnostics/requestId。 |
| 能力协商 `ToolingClientCapabilities` | partial | 已支持 operationTypes 与事件速率协商；模型粒度协商未完备。 |
| 初始化协商回传（`InitializeResult`） | partial | 已回传协商 operationTypes；更多 server feature flags 待扩展。 |
| 兼容策略（新增字段 optional） | partial | 新字段大多可选；未形成系统化兼容测试矩阵。 |

## 2. 服务端执行层（`tooling/impl`）

| 能力 | 状态 | 说明 |
|---|---|---|
| `IToolingApiServer.execute(request)` 实现 | full | 已落地 execute 路径并保留 legacy executeTasks。 |
| 请求合法性校验 | full | 包含 project 初始化校验、任务列表非空校验、目录可访问性校验。 |
| OperationType 协商应用 | full | request operationTypes 与 server 支持集求交并生效。 |
| 失败分类与诊断 | partial | 已输出 failure + diagnostics，但分类维度仍可继续细化。 |
| 请求链路追踪（requestId） | partial | execute 路径已贯通；同步/模型查询链路尚未全面贯通。 |
| 统一执行管线（preflight->execute->collect->persist） | partial | execute 主路径具备；模型持久化/标准化阶段仍未完全统一。 |

## 3. 事件层（`tooling/events` + `tooling/impl/progress`）

| 能力 | 状态 | 说明 |
|---|---|---|
| 事件类型订阅（OperationType） | full | 已支持按 operation types 订阅。 |
| 事件节流（rate limiting） | full | 已支持最大事件速率限制。 |
| 事件上下文链（eventId/parentId/projectPath/plugin） | partial | 现有事件具备基础字段；统一上下文字段规范未完全完成。 |
| 事件正确性保障（start/progress/finish 完整闭合验证） | none | 缺少自动化验证。 |

## 4. 客户端执行消费层（`core/app` + `core/projects`）

| 能力 | 状态 | 说明 |
|---|---|---|
| BuildService `execute(request)` 对外能力 | full | 已提供统一入口，并可映射 legacy 结果类型。 |
| 开关回退（`androidide.use.tooling.execute`） | full | 可在新旧执行路径间切换。 |
| QuickRun / RunTasks / ProjectManager 接入 | full | 关键调用点已接入 execute 可选路径。 |
| 客户端 requestId 可观测日志 | partial | QuickRun/RunTasks 已补齐；其它入口待补齐。 |
| 运行态状态聚合（running/cancelled/failed 摘要） | partial | 基础能力存在，统一查询 API 仍待完善。 |

## 5. 模型与查询层（`tooling/model` / `core/projects`）

| 能力 | 状态 | 说明 |
|---|---|---|
| ToolingServerMetadata 协商信息暴露 | partial | 已有 supported/negotiated operation types、max rate；其它能力标签待补。 |
| raw/normalized 分层模型 | none | 尚未实现分层快照。 |
| 增量刷新查询（按模块/变体/事件） | partial | 有部分局部机制，但非完整统一查询体系。 |

## 6. 测试与验证

| 能力 | 状态 | 说明 |
|---|---|---|
| 模块编译回归 | partial | 环境受限下可执行有限编译检查。 |
| 新旧 client/server 协议兼容测试 | none | 尚未建立自动化矩阵。 |
| 事件正确性与吞吐测试 | none | 尚未建立自动化测试。 |
| 压测（大项目、内存峰值、事件体积） | none | 尚未系统开展。 |

## 7. 总结

- **第1周核心主线“协议 + 执行 + 基础协商”已基本打通**。  
- **当前短板主要在：文档化矩阵、gap 注册、兼容测试和事件正确性自动化**。  
- 下一步应优先补齐 `gap-register` 与兼容测试基线，避免进入迭代2后风险外溢。  

