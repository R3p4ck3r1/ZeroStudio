# Build gRPC 模块开发计划与架构设计（AIDL + gRPC + REAPI/BEP）

## 1. 目标
- 建立一套面向本地 JVM（Termux）构建服务的专属协议层，整体设计对齐 BSP 的「初始化 / 能力协商 / 构建请求 / 事件流」模型。
- 使用 gRPC + Proto3 作为高性能、强类型二进制协议承载。
- 使用 AIDL 作为 Android 进程边界的桥接层，降低跨进程对象转换成本。
- 对齐 REAPI 与 BEP 的设计思想：
  - REAPI：动作执行、结果状态、缓存语义。
  - BEP：构建全生命周期事件流。

## 2. 分层架构
1. **协议层（Proto）**
   - `build_service.proto`：定制 BSP 风格服务接口。
   - `build/bazel/**`：复制 REAPI 官方 proto 定义作为底层执行语义。
2. **传输层（gRPC In-Process）**
   - 在 JVM 内部使用 in-process channel/server，避免实际网络流量。
3. **桥接层（AIDL）**
   - AIDL 提供 Android 侧调用入口与事件回调接口。
4. **领域层（Kotlin API）**
   - `BuildGrpcModule` 暴露初始化、启动构建、关闭服务等抽象能力。

## 3. 开发阶段计划
- **Phase 1（已完成）**：模块脚手架、proto 接口、AIDL 基础桥接、Gradle 代码生成配置。
- **Phase 2**：实现 `BuildSessionService` 服务端（含初始化握手、构建状态机、事件发布器）。
- **Phase 3**：实现 AIDL <-> gRPC 适配器（请求映射、事件反压、错误码映射）。
- **Phase 4**：引入 REAPI Action 执行器（先本地执行，再扩展缓存与远程执行策略开关）。
- **Phase 5**：兼容 BSP 客户端语义（能力协商、任务/目标模型补齐、诊断增强）。
- **Phase 6**：性能基准与稳定性测试（吞吐、内存、序列化开销、长任务流式回压）。

## 4. 关键设计约束
- 构建服务默认部署于项目内 JVM/Termux 运行时。
- gRPC 仅作为高效二进制 RPC 与流式事件框架，不依赖外部网络。
- AIDL 仅负责进程边界交互，内部数据尽量保持 proto 语义一致，减少重复建模。

## 5. 环境与构建配置要求
- JDK 17。
- protobuf gradle plugin + grpc java/kotlin codegen。
- 额外引入 `proto-google-common-protos` 解决 REAPI 依赖的 `google/api` 与 `google/rpc` imports。

