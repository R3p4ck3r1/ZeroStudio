# Tooling 9.5.1 Gap Register（迭代1-第1周）

> 更新时间：2026-05-22  
> 范围：`core/projects`、`core/app`、`tooling/api`、`tooling/impl`、`tooling/events`、`tooling/model`

## P0（立即推进）

### GAP-P0-01：缺少协议兼容自动化矩阵
- **能力名**：新旧 client/server 协议兼容测试。  
- **当前状态**：未建立自动化验证。  
- **影响范围**：`tooling/api`、`tooling/impl`、`core/app`。  
- **风险**：新增字段导致旧端隐式行为变化不可见。  
- **最小实现方案**：
  1. 增加兼容性 smoke 测试任务：`new-server + old-client`、`old-server + new-client`；
  2. 核验新增字段忽略/默认值行为（ExecutionRequest/Result/InitializeResult）。  
- **验收标准**：
  - 两组组合均可完成一次任务执行并回收标准结果；
  - 无序列化/反序列化崩溃。  

### GAP-P0-02：Capability 协商覆盖面不足
- **能力名**：server feature 协商（operation types 之外）。  
- **当前状态**：仅 operationTypes + maxEventRate 形成稳定链路。  
- **影响范围**：`tooling/api`、`tooling/model`、`core/projects`。  
- **风险**：客户端难以做按能力降级，造成误请求。  
- **最小实现方案**：
  1. 在 metadata/result 中增加显式能力标记（如 phasedAction/modelSnapshot/querySupport）；
  2. client 端按 capability 决定功能可用性。  
- **验收标准**：
  - 客户端在 capability 缺失时自动 fallback；
  - capability 字段新增不破坏旧版本解析。  

### GAP-P0-03：事件正确性无自动验收
- **能力名**：事件闭合与顺序校验。  
- **当前状态**：有节流能力，无事件一致性自动验证。  
- **影响范围**：`tooling/events`、`tooling/impl/progress`、`core/app`。  
- **风险**：UI 卡顿/状态机异常难复现。  
- **最小实现方案**：
  1. 新增测试工具统计 start/progress/finish 对齐；
  2. 验证事件 parent-child 链完整性。  
- **验收标准**：
  - 每个 start 必有 finish；
  - 关键 operationType（TASK/PROJECT_CONFIGURATION）通过校验。  

## P1（第2阶段跟进）

### GAP-P1-01：requestId 未覆盖非 execute 主链路
- **能力名**：端到端 trace id 全链路。  
- **当前状态**：execute 路径已覆盖，sync/model 查询未统一。  
- **影响范围**：`tooling/impl`、`core/projects`、`core/app`。  
- **最小实现方案**：同步入口、模型查询入口引入 requestId 并统一日志键。  
- **验收标准**：任一同步请求可从客户端日志串联至服务端执行日志。  

### GAP-P1-02：失败分类粒度不统一
- **能力名**：失败模型标准化（脚本/连接/配置/取消/未知）。  
- **当前状态**：有基础分类，细粒度边界不清晰。  
- **影响范围**：`tooling/api`、`tooling/impl`、`core/app`。  
- **最小实现方案**：扩展 failure 分类映射表与测试样例。  
- **验收标准**：典型失败场景映射稳定且 UI 提示可区分。  

## P2（文档与工程化）

### GAP-P2-01：专项文档未补齐
- **能力名**：交付物文档体系。  
- **当前状态**：已有升级总规划、周状态文档；其余专项文档不完整。  
- **影响范围**：研发协作、回归与灰度管理。  
- **最小实现方案**：补齐 contract/test-plan/rollout 文档。  
- **验收标准**：文档覆盖迭代2所需输入，评审可直接执行。  

## 本周落地顺序建议

1. 先做 **GAP-P0-01**（兼容矩阵）与 **GAP-P0-03**（事件正确性校验）。  
2. 并行推进 **GAP-P0-02** capability 扩展字段设计。  
3. 下周初收敛 **GAP-P1-01** requestId 全链路。  


## 协议迁移专项 GAP（LSP4J-RPC 移除）

### GAP-P0-04：缺少 Transport SPI 抽象层
- **能力名**：协议中立传输层（AIDL/gRPC/legacy 统一上层 contract）。
- **当前状态**：`tooling/api` 仍与 lsp4j 注解耦合。
- **影响范围**：`tooling/api`、`tooling/impl`、`core/app`。
- **风险**：无法并行迁移协议，切换成本与回归风险过高。
- **最小实现方案**：
  1. 新增 `transport-spi` 接口层；
  2. 现有 lsp4j 通道下沉为 `legacy adapter`；
  3. 上层执行链路改依赖 SPI。
- **验收标准**：
  - 不改业务功能即可替换底层传输实现；
  - legacy 与新通道可通过开关切换。

### GAP-P0-05：lsp4j 依赖移除清单未系统化
- **能力名**：构建服务协议依赖治理（代码/依赖/混淆规则）。
- **当前状态**：已识别多处 lsp4j 引用，但未形成删改执行清单。
- **影响范围**：`tooling/api`、`tooling/model`、`core/app`、构建脚本。
- **风险**：后续移除阶段遗漏依赖，导致运行时崩溃或构建失败。
- **最小实现方案**：
  1. 建立依赖点台账（import、gradle 依赖、proguard keep）；
  2. 标记“立即可迁移/需过渡/最后删除”。
- **验收标准**：
  - 每个依赖点均有 owner 与迁移阶段；
  - 进入 Sprint E 时可一键核验剩余项。

### GAP-P0-06：非 Android 项目初始化同步失败（AGP 强耦合）
- **能力名**：Gradle Tooling 通用建模能力（Android/JVM/KTS 智能分流）。
- **当前状态**：根目录无 AGP 插件时 initialize/sync 失败。
- **影响范围**：`tooling/impl`、`tooling/model`、`core/projects`、`core/app`。
- **风险**：IDE 被限制为“只能构建 Android 项目”，违背 Tooling API 通用能力定位。
- **最小实现方案**：
  1. 先拉取 `BuildEnvironment` + `GradleProject/IdeaProject` 轻量模型；
  2. 按模块探测 Android 插件后再请求 Android 模型；
  3. Android 模型失败时仅降级对应模块，不中断全局同步。
- **验收标准**：
  - 纯 JVM/Gradle 项目可完整 initialize + sync；
  - 混合模块项目可同时展示 Android 与非 Android 模块。

### GAP-P1-03：gRPC UDS（Termux）部署约束未固化
- **能力名**：gRPC Unix Domain Socket 传输配置基线。
- **当前状态**：已有 gRPC 迁移方案，但未将 UDS/Termux 约束写入验收清单。
- **影响范围**：`core/app`、`tooling/impl`、后续 `tooling/transport-grpc`。
- **最小实现方案**：
  1. 明确 UDS 路径、权限（0600）、清理策略；
  2. 增加 socket 探活/重连/重建用例。
- **验收标准**：
  - 默认通道为 UDS；
  - 进程异常退出后可自动恢复 socket 通道。
