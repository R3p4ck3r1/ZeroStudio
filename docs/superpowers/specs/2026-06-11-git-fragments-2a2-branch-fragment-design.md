# 2a2 具体 Fragment 嵌入 puppygit Screen

| 字段 | 值 |
| --- | --- |
| 父 spec | `2026-06-11-git-fragments-refactor-roadmap.md` |
| 前置 | 2a1 (puppygit 基础设施) |
| 后置 | 2b (工具栏) / 2c2 (快捷菜单) |
| 范围 | 单 fragment 试点：`GitBranchesFragment` |
| 状态 | draft, 实施中 |

## 1. 背景

2a1 提供了 `PuppyGitIntegration` + `BaseGitPageFragment.setGitContent`，
但 7 个具体 fragment（Changes / History / Branches / Stash / Diff /
Projects / Collaboration / Code Review / Conflicts / Host / Pipelines /
Tags）的 body 仍然是 RecyclerView + 手撸业务逻辑，**直接 import
`com.catpuppyapp.puppygit.*`**。

本 sub-spec 把其中一个最简单、最有代表性的 fragment
**`GitBranchesFragment`** 改造成完全复用 puppygit 现有的
`BranchListScreen` Composable，验证整体迁移路径。

## 2. 目标 / 非目标

### 2.1 目标

- `GitBranchesFragment` body 完全用 `BranchListScreen(repoId, naviUp)`
  替代（经由 `setGitContent`）
- 该 fragment 源码内**不再**直接 import `com.catpuppyapp.puppygit.*`
- workspace 项目路径 ↔ puppygit `repoId` 映射有专门 helper 解决
- 顶部 mini-toolbar 的"刷新/新建/检出"按钮行为**保留**（详见 §4.5）

### 2.2 非目标（留给后续 PR / spec）

- 其他 6 个 fragment（Changes / History / Stash / Diff / …）暂**不动**，
  留作 2a2 后续 PR
- `IndexScreen` / `ChangeListInnerPage` / `RepoInnerPage` 等大而全的
  复合 composable 不直接嵌入（需要整套状态管理，复杂度远大于本
  sub-spec 范围；先从单屏单 composable 的小屏开始）
- `GitHostFragment` 的 TabLayout 仍保留（7 tab 现状）
- `GitPopupManager` / `GitCredentialManager` / `GitSharedState` 不动

## 3. 仓库身份映射：`RepoIdResolver`

`BranchListScreen` 需要 `repoId: String`，而当前 fragment 是按"工作区打开
的项目路径"操作的（`IProjectManager.getInstance().projectDirPath`）。
两者没有现成映射。

新增一个轻量 helper：

```kotlin
// core/app/.../fragments/git/RepoIdResolver.kt

object RepoIdResolver {
  /**
   * 把工作区项目目录的绝对路径解析为 puppygit DB 中的 `repoId`。
   *
   * - 若 DB 中不存在该路径，会**就地注册**（upsert 一条 RepoEntity）
   * - 若 DB 中已存在，复用现有 id
   * - 抛异常时调用方决定回退策略（典型回退：toast 提示 + 显示空 UI）
   *
   * 该函数会**自动**触发 [PuppyGitIntegration.ensureNativeLoaded] 之前
   * 的 init（第一次调用时），因为 puppygit 的 AppDataContainer
   * 在 `init_forPreview` 后才可用。
   */
  suspend fun resolveForWorkdir(workdir: String): String = withContext(Dispatchers.IO) {
    require(workdir.isNotBlank()) { "workdir must not be blank" }
    val ctx = AppModel.realAppContext  // 由 ensureReady 设好
    val container = AppModel.dbContainer
    val repoDao = container.repoRepository
    val existing = repoDao.getByPath(workdir)
    if (existing != null) return@withContext existing.id

    val name = File(workdir).name.ifBlank { "repo" }
    val entity = RepoEntity(
      repoName = name,
      fullSavePath = workdir,
      branch = Repository.open(workdir).use { runCatching { it.head()?.shorthand().orEmpty() }.getOrDefault("") }
    )
    repoDao.insertOrReplace(entity)
    repoDao.getByPath(workdir)!!.id
  }

  /**
   * 非 suspend 包装（puppygit Composable 不便用 suspend；改用 LaunchedEffect
   * 调 suspend 版本，结果传 Composable）。
   */
  @Composable
  fun rememberRepoIdForWorkdir(workdir: String): String? {
    val state = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(workdir) {
      if (workdir.isNotBlank()) {
        runCatching { resolveForWorkdir(workdir) }.onSuccess { state.value = it }
      }
    }
    return state.value
  }
}
```

`RepoIdResolver` 替代具体 fragment 里的 `Repository.open(projectDir).use { ... }`，
成为 fragment ↔ puppygit 的桥。

## 4. 架构

### 4.1 fragment 生命周期

```
GitBranchesFragment.onCreateView()
  ↓
_baseView = ComposeView(...).apply {
  setContent {
    val workdir = remember { resolveWorkspaceDirPath() }
    val repoId = RepoIdResolver.rememberRepoIdForWorkdir(workdir.orEmpty())
    PuppyGitIntegration.ensureReady()    // 触发 init_forPreview
    InitContent(ctx) {
      if (repoId != null && workdir != null) {
        BranchListScreen(repoId = repoId, naviUp = { false /* 此处是 tab 内部，不 pop */ })
      } else {
        Text("No opened project")       // 占位
      }
    }
  }
}
```

### 4.2 旧 RecyclerView 代码删除

只删 **GitBranchesFragment 内部** 的 RecyclerView 业务代码：
- `GitBranchesFragment.kt` 的 `loadBranches` / `createBranchFromHead` /
  `checkoutSelectedBranch` / `BranchItem` / `BranchAdapter` 等
- `FragmentGitBranchesBinding`（由 layout 自动生成）

`core/app/src/main/res/layout/fragment_git_branches.xml` **不删**——
被 `GitPullRequestsFragment` / `GitConflictsFragment` /
`GitPipelinesFragment` / `GitCodeReviewFragment` 复用
（`inflate(R.layout.fragment_git_branches)`）。新 `GitBranchesFragment`
改用 `fragment_git_branches_compose.xml`（新增 layout：toolbar + FrameLayout
container，运行时挂 ComposeView）。

### 4.3 import 隔离

| import | 2a2 之后 |
| --- | --- |
| `com.catpuppyapp.puppygit.utils.Libgit2Helper` | ❌ 删除 |
| `com.github.git24j.core.Branch` / `Repository` | ❌ 删除 |
| `com.itsaky.androidide.databinding.FragmentGitBranchesBinding` | ❌ 删除（改用 FragmentGitBranchesComposeBinding） |
| `androidx.recyclerview.widget.*` | ❌ 删除 |
| `com.catpuppyapp.puppygit.screen.BranchListScreen` | ✅ 新增（合法 import） |
| `com.catpuppyapp.puppygit.utils.AppModel` | ✅ 通过 PuppyGitIntegration 间接用 |

> spec 边界说明：`com.catpuppyapp.puppygit.screen.BranchListScreen` 这种
> **screen 类** 是 spec roadmap 1.4 节明确允许的 import 类型；其他
> `*Helper` / `*Util` / data class 等内部类**仍然禁止**。

### 4.4 toolbar / GitUiEvent 行为

- 顶部 mini-toolbar 三个按钮（refresh / new_branch / checkout）**保留**，
  2b 阶段会重新设计；本 spec 不动
- `GitUiEventViewModel.Operation` 事件：保留 emit，监听逻辑由
  `GitHostFragment` 负责（本 spec 不动 host）
- `GitUiEventViewModel.Error`：保留 emit

### 4.5 关键改动

- **`RepoIdResolver.kt`** (新增,约 90 行)：workspace path ↔ puppygit repoId
  映射；含 suspend 解析 + @Composable `rememberRepoIdForWorkdir`
- **`GitBranchesFragment.kt`** (重写,约 120 行)：body 是
  `setGitContent { BranchListScreen(...) }`；toolbar 行为保留
- **`fragment_git_branches_compose.xml`** (新增,约 17 行)：toolbar +
  FrameLayout container；运行时挂 ComposeView
- **`fragment_git_branches.xml`**：**不删**（被 4 个其它 fragment 复用）

## 5. 不变量

- 顶部 mini-toolbar 三按钮（refresh / new_branch / checkout）行为不变
- GitHostFragment 7 个 tab 结构不变
- 其他 6 个 fragment body 零变化
- `GitSharedState` / `GitUiEventViewModel` / `GitPopupManager` 零变化
- `PuppyGitIntegration.ensureReady()` 在 `setGitContent` 块内首次被
  调用时初始化（沿用 2a1 的懒加载语义）

## 6. 验证

| 验证项 | 方式 |
| --- | --- |
| `GitBranchesFragment` 编译通过 | code review（沙箱无网络 gradle 编译跳过） |
| `GitBranchesFragment` 源码内**无** `com.catpuppyapp.puppygit.utils.*` / `com.catpuppyapp.puppygit.git.*` / `com.github.git24j.*` import | `git grep` |
| `fragment_git_branches.xml` / `FragmentGitBranchesBinding` 不再被引用 | `git grep` |
| `BranchListScreen` 在被引用时 `repoId` 已非空 | code review（`rememberRepoIdForWorkdir` 返回非 null 才渲染） |
| 旧 `loadBranches` / `createBranchFromHead` / `checkoutSelectedBranch` 已删除 | `git grep` |
| `RepoIdResolver.resolveForWorkdir` upsert 行为正确（已存在复用 id） | code review |
| 顶部 mini-toolbar 按钮点击仍能 emit `GitUiEvent.Operation` | code review（`setupToolbar` 保留） |

## 7. 风险

| 风险 | 缓解 |
| --- | --- |
| puppygit 屏幕需要 `AppModel.navController` / `homeTopBarScrollBehavior` 已设好 | 2a1 的 `ensureReady` 跑 `init_forPreview`，末尾调 `init_3` 已设这两个字段 |
| 旧按钮"checkout"硬编码的 `selectedBranch` 状态与新 `BranchListScreen` 长按菜单冲突 | 2a2 阶段旧按钮**暂时只 emit 事件不真正执行**（避免双 UI 路径），2b 阶段会重新设计 |
| `RepoIdResolver.resolveForWorkdir` 在 puppygit DB 未初始化时崩溃 | 内部走 `AppModel.dbContainer` 读；为空时抛 `IllegalStateException` 让 fragment 提示"No project" |
| 旧 `Repository.open(projectDir).use { ... }` 业务代码残留 | 显式删除清单（见 §4.5） |

## 8. 关联

- 父 spec: `2026-06-11-git-fragments-refactor-roadmap.md`
- 前置: `2026-06-11-git-fragments-2a1-puppygit-integration-design.md`
- 后续: 2a2 子迭代（其他 6 个 fragment）/ 2b（toolbar）/ 2c1-2c2（菜单）
