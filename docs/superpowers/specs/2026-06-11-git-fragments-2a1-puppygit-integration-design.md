# Git Fragments 重构 2a1：puppygit 基础设施接入

| 项目 | 内容 |
| --- | --- |
| **Spec author** | android_zero |
| **Date** | 2026-06-11 |
| **Status** | draft |
| **Parent spec** | `2026-06-11-git-fragments-refactor-roadmap.md`（待补） |
| **Scope** | 在 core/app 接入 puppygit 的 AppModel / Theme / InitContent 基础 |
| **Out of scope** | 具体 fragment 改用 puppygit screen（2a2）；工具栏（2b）；popup 修复（2c1/2c2） |

## 1. 背景

`core/app/src/main/java/com/itsaky/androidide/fragments/git/` 现有 4 个具体 fragment
（`GitChangesFragment`、`GitHistoryFragment`、`GitBranchesFragment`、…）走
`RecyclerView + simple_list_item_2` 简化实现，直接调 `Libgit2Helper.*` 拉数据。
用户要求**完全复用** `core/git/.../com/catpuppyapp/puppygit/` 已经写好的 git
UI（`HomeScreen`、`BranchListScreen`、`CommitListScreen`、…）。要复用这些
Composable，必须先把 puppygit 的运行时基础接入 core/app。

本 sub-spec 只负责**基础设施**（运行时不变量、主题、Compose host、初始化入口），
**不**改任何具体 fragment 的 body——那是 2a2 的事。

## 2. 目标

1. 在 core/app 启动时一次性完成 `AppModel.init_forPreview()`，不阻塞主流程
2. `BaseGitPageFragment` 改为 Compose host：保留 `setupToolbar()` 给子类扩展按钮，
   body 改为 `ComposeView.setContent { InitContent { ... } }`
3. `GitRuntimeBootstrap` 改为委托 `AppModel.isInited`，避免重复 `Libgit2.init()`
4. 暴露一个对 IDE 主进程安全的入口 `PuppyGitIntegration.ensureReady(context)`，
   供 2a2/2b/2c1/2c2 调用

## 3. 非目标

- **不**改 4 个具体 git fragment 的 body 渲染逻辑（2a2）
- **不**新建 `GitProjectFragment` 工具栏（2b）
- **不**修复 `GitPopupManager`（2c1）
- **不**改 `core/git/.../utils/AppModel.kt` 本身

## 4. 架构

### 4.1 启动期初始化

```
首次进入 git page fragment.onCreateView:
    BaseGitPageFragment.setGitContent { ... }  // 或 2a2 改写具体 fragment
        ↓
    PuppyGitIntegration.ensureReady()           // Composable,幂等
        ↓
    AppModel.init_forPreview()                 // 已存在;@Composable,内部用 LocalContext.current
        - LibLoader.load()
        - Libgit2.init()
        - Libgit2.optsGitOptSetOwnerValidation(false)
        - AppModel.dbContainer = AppDataContainer(ctx)
        - AppModel.realAppContext = ctx
        - AppModel.masterPassword = ...
        - AppModel.externalFilesDir / externalCacheDir / innerDataDir / ...
        - AppModel.init_3()                    // 内部末尾调用,设 navController + homeTopBarScrollBehavior
```

> 注:`AppModel.init_forPreview()` 的真实签名是 `@Composable fun init_forPreview()`,
> 不是 `init_forPreview(ctx, initActivity = false)`,所以 `PuppyGitIntegration.ensureReady()`
> 必须是 `@Composable` 函数。原 spec 草稿误写为非-Composable,本 spec 已修正。

### 4.2 懒加载

`Application.onCreate` 期间跑 `runBlocking { ... }` 不友好。`ensureReady` 改为
**懒加载**:

- `PuppyGitIntegration.ensureReady()` 默认在第一次被调用时执行(Composable,内部
  用 `LocalContext.current` 拿 context)
- 在 2a2 之后,`BaseGitPageFragment.setGitContent` 返回的 `ComposeView.setContent`
  块中调一次,确保 Composable 拿到的是已初始化状态
- 不在 `Application.onCreate` 强制调,避免增加冷启动耗时

### 4.3 Compose host

2a1 阶段 **不强制** base fragment 改 ComposeView body（避免破坏现有 4 个
具体 fragment 的 RecyclerView 实现），只暴露一个 helper：

```kotlin
abstract class BaseGitPageFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        runCatching { GitRuntimeBootstrap.ensureLoaded() }
        setupToolbar()
    }

    abstract fun setupToolbar()

    /**
     * 创建一个承载 puppygit Compose 内容的 [ComposeView]。
     *
     * 调用方负责把返回的 [ComposeView] 放到布局里。`setContent` 块内部会先
     * 调 [PuppyGitIntegration.ensureReady] 触发 AppModel 初始化，再包一层
     * [InitContent] 提供 Theme / LocalActivity 等 CompositionLocal。
     */
    protected fun setGitContent(content: @Composable () -> Unit): ComposeView {
        val ctx = requireContext()
        return ComposeView(ctx).apply {
            setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PuppyGitIntegration.ensureReady()
                InitContent(context = ctx.applicationContext) { content() }
            }
        }
    }
}
```

`InitContent` 是 puppygit 自己的 Composable（位于 `core/git/.../ui/theme/Theme.kt`），
提供 `Theme` + `LocalActivity` 等 CompositionLocal。本 sub-spec 不直接使用
`InitContent` 内的东西，但 2a2 会用。

### 4.4 关键组件

**`PuppyGitIntegration`**（新增，约 50 行）

```kotlin
object PuppyGitIntegration {
    private val inited = AtomicBoolean(false)
    private val nativeLoaded = AtomicBoolean(false)

    fun isReady(): Boolean = inited.get()

    /** 同步，非-Composable。仅加载 native 库（LibLoader + Libgit2.init）。 */
    fun ensureNativeLoaded() {
        if (nativeLoaded.get()) return
        synchronized(this) {
            if (nativeLoaded.get()) return
            LibLoader.load()
            Libgit2.init()
            Libgit2.optsGitOptSetOwnerValidation(false)
            nativeLoaded.set(true)
        }
    }

    /** Composable。首次调用跑 AppModel.init_forPreview()；后续调用 no-op。 */
    @Composable
    fun ensureReady() {
        if (inited.get()) return
        ensureNativeLoaded()  // init_forPreview 内部也会跑，但幂等
        AppModel.init_forPreview()  // 用 LocalContext.current
        inited.set(true)
    }
}
```

- `ensureReady` 是 `@Composable` 因为 `AppModel.init_forPreview()` 必须
  在 Composable 作用域内调用（用 `LocalContext.current`）。
- `runBlocking` 不再需要：Composable 块天然是同步的（`setContent` 跑完才返回）。
- 单例模式（`object`）防止多个 fragment 入口并发初始化。
- `ensureNativeLoaded` 拆出来：GitRuntimeBootstrap 仍是非-Composable，需要它。
- `inited` 与 `nativeLoaded` 两个独立 flag：`inited` 表示 AppModel 字段
  全部就绪（包括 navController / scrollBehavior）；`nativeLoaded` 只表示
  jni 库已加载，用于向后兼容 GitRuntimeBootstrap。

**`GitRuntimeBootstrap`**（改写）

```kotlin
object GitRuntimeBootstrap {
    @Synchronized
    fun ensureLoaded() {
        // 委托给 PuppyGitIntegration；native 库幂等加载。
        // AppModel 字段初始化在 2a2 之后由 setGitContent 触发。
        PuppyGitIntegration.ensureNativeLoaded()
    }
}
```

向后兼容：保留 `ensureLoaded()` 签名（仍同步、仍无参数），但行为变薄。

## 5. 资产 / 资源

无新增/删除 assets。

## 6. 依赖改动

`core/app/build.gradle.kts`：

- 无新增依赖
- `puppygit` 模块已经在传递依赖中（通过 `core:git` 间接引入），所有 puppygit 类
  可访问

`gradle/libs.versions.toml`：

- 无

## 7. 测试 / 验收

| 验证项 | 方式 |
| --- | --- |
| `PuppyGitIntegration.ensureReady()` 单次进入幂等 | 单测 / 代码 review |
| `PuppyGitIntegration.ensureNativeLoaded()` 单次进入幂等 | code review |
| `BaseGitPageFragment` 仍能正常 inflate 工具栏；`setupToolbar()` 子类扩展点不破 | code review（XML 不变） |
| `setGitContent` 返回的 ComposeView 内部已包含 ensureReady + InitContent | code review |
| `GitRuntimeBootstrap.ensureLoaded()` 多次调用安全（走 nativeLoaded flag） | code review |
| `Application.onCreate` 不再调用 puppygit 初始化（懒加载） | `git grep` |
| 现有具体 fragment（`GitChangesFragment` 等）的 RecyclerView 行为**不变** | code review（2a1 阶段不动 body） |

## 8. 风险

| 风险 | 缓解 |
| --- | --- |
| `AppModel.init_forPreview` 内部含 `LocalContext.current` 调用，必须在 Composable 作用域 | `PuppyGitIntegration.ensureReady` 标 `@Composable`，仅在 `setContent { ... }` 块内调用；不在 `Application.onCreate` 跑 |
| puppygit 内部 `appContext` 是 lateinit；未初始化时访问会 NPE | 自管 `inited` AtomicBoolean；`PuppyGitIntegration.ensureReady` 包 synchronize 双重检查 |
| 现有 `GitRuntimeBootstrap` 的 `loaded` 标志与新流程冲突 | 老 `loaded` 删除，改用 `PuppyGitIntegration.nativeLoaded` 标志 |
| 启动时多线程并发调 `ensureNativeLoaded` | `synchronized` + `AtomicBoolean` 双重检查 |
| 2a1 完成后 2a2 之前的中间状态——具体 fragment 仍跑老逻辑 | 这是预期：2a1 是基础设施，不改业务行为；2a2 才动 fragment body |

## 9. 关联

- 父：git 片段重构路线图（`2026-06-11-git-fragments-refactor-roadmap.md`）
- 子：2a2（具体 fragment 改造）/ 2b（工具栏）/ 2c1（popup 修复）/ 2c2（快捷菜单）
- 核心依赖：`core/git/.../utils/AppModel.kt::init_forPreview`
- 核心 Compose host：`core/git/.../ui/theme/Theme.kt::InitContent`

## 10. 验收清单（PR 模板）

- [ ] `PuppyGitIntegration` 接入 IDEApplication 启动路径（不强制 require runBlocking）
- [ ] `BaseGitPageFragment` 暴露 `setGitContent` 入口
- [ ] `GitRuntimeBootstrap` 改写为薄壳
- [ ] 现有 4 个 fragment 的 RecyclerView 行为零变化
- [ ] 编译通过，runtime 不崩
