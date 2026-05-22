# Tooling 9.5.1 协议兼容测试计划（迭代1-第1周收口项）

> 日期：2026-05-22  
> 目标：补齐 `new server + old client` 与 `old server + new client` 的最小可执行验证闭环。

## 1. 测试范围

- 协议对象：
  - `ExecutionRequest`
  - `ExecutionResult`
  - `InitializeProjectParams`
  - `InitializeResult`
  - `ToolingServerMetadata`
- 路径：
  - legacy `executeTasks`
  - 新路径 `execute(request)`

## 2. 组合矩阵（最小版）

| 组合 | 说明 | 预期 |
|---|---|---|
| C1 | new server + old client | 旧客户端可正常初始化并执行任务，不因新字段崩溃。 |
| C2 | old server + new client | 新客户端可自动降级，不依赖缺失 capability 字段。 |
| C3 | new server + new client | 全功能路径可用（requestId、operationTypes、throttle metadata）。 |

## 3. 用例清单

### T1 初始化兼容
- 步骤：
  1. 发起项目初始化；
  2. 校验 capability 字段存在/缺失时均可返回可解析结果。
- 验收：
  - 无序列化异常；
  - 新客户端在 capability 缺失时 fallback 到默认 operationTypes。

### T2 执行请求兼容
- 步骤：
  1. 使用最小任务列表执行 `assembleDebug`（或样例任务）；
  2. 分别通过 `executeTasks` 与 `execute(request)` 调用；
  3. 对比成功态与失败态映射。
- 验收：
  - 成功态统一映射 `TaskExecutionResult.SUCCESS`；
  - 失败态包含可读 diagnostics。

### T3 新字段容忍
- 步骤：
  1. 在 request 中注入新字段组合（arguments/jvmArguments/operationTypes/requestId）；
  2. 在旧端组合上验证忽略行为。
- 验收：
  - 旧端不崩溃；
  - 未支持字段被忽略或降级，不中断任务执行。

### T4 事件协商与节流兼容
- 步骤：
  1. 请求 operationTypes 子集；
  2. 设置 max event rate；
  3. 观察元数据和事件输出。
- 验收：
  - 协商结果可读；
  - 事件频率受控且不出现明显事件丢闭合（需与后续事件正确性测试联动）。

## 4. 自动化落地建议

1. 在 `tooling/api` 与 `tooling/impl` 增加兼容测试入口（可先用 smoke 层）。  
2. 提供可参数化脚本：`-Dandroidide.use.tooling.execute=true/false` 切换。  
3. 先覆盖 C1/C2 的最小“初始化 + 单任务执行”路径，再扩展到事件协商场景。  

## 5. 出口标准（Week1）

- C1/C2/C3 三组最小用例跑通（至少 T1+T2）；  
- 输出一次兼容测试报告（通过/失败项与失败原因）；  
- 失败项进入 `tooling-951-gap-register.md` 的 P0/P1 跟踪。  

