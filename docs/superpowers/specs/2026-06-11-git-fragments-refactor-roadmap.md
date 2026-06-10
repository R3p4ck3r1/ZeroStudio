# Git Fragments 重构路线图

| 项目 | 内容 |
| --- | --- |
| **Spec author** | android_zero |
| **Date** | 2026-06-11 |
| **Status** | draft |
| **Scope** | core/app/.../fragments/git/ 整体重构 |
| **Supersedes** | 无（直接替代） |

## 1. 背景

`core/app/src/main/java/com/itsaky/androidide/fragments/git/` 当前实现是
简化版 git UI：4 个具体 fragment + 工具栏 + 多个 popup manager，UI
用 `RecyclerView + android.R.layout.simple_list_item_2`，数据直接调
`Libgit2Helper` 拉。`GitPopupManager` 的用户名/邮箱配置链路也已经坏掉。

`core/git/src/main/java/com/catpuppyapp/puppygit/` 模块（400+ 文件）已经
有完整的 git client UI：HomeScreen、BranchListScreen、CommitListScreen、
StashListScreen、TagListScreen、DiffScreen 等等，全部 Compose 实现。

用户要求：
- 完完全全引入 puppygit 的 git 能力
- core/app 的 fragment 改为 puppygit screen 的「TabLayout 预览」载体
- 新增 `GitProjectFragment` 主 fragment + 顶部工具栏
- 把 init/credential/用户名邮箱 等不常用功能整合到「快捷菜单」
- 修复 `GitPopupManager` 用户名/邮箱配置
- 移除 core/app/.../fragments/git/ 对 puppygit 的直接 import

## 2. 拆分依据（依赖层次）

按**依赖层次**拆，5 个 sub-spec：

| Sub-spec | 内容 | 依赖 | 状态 |
| --- | --- | --- | --- |
| **2a1** | 基础设施：`PuppyGitIntegration` 懒加载入口；`BaseGitPageFragment` 暴露 `setGitContent`；`GitRuntimeBootstrap` 改写 | 无 | **当前 spec** |
| **2a2** | 具体 fragment 改造：`GitChangesFragment` / `GitHistoryFragment` / `GitBranchesFragment` body 嵌入 puppygit screen；移除 `com.catpuppyapp.puppygit.*` 的 import | 2a1 | 待开始 |
| **2b** | 新增 `GitProjectFragment` 主 fragment + 顶部工具栏（commit/push/sync/pull）+ 内部 TabLayout 切换 puppygit screen | 2a2 | 待开始 |
| **2c1** | 修复 `GitPopupManager` 用户名/邮箱配置：定位 `AskGitUsernameAndEmailDialog` 链路错误，纠正 `Settings` 读写 | 2a1 | 待开始 |
| **2c2** | 不常用功能（init / credential 管理 / 用户名邮箱）整合到「快捷菜单」（二级菜单或 BottomSheet） | 2c1 | 待开始 |

依赖图：

```
[2a1] ──→ [2a2] ──→ [2b]
   │                  ↑
   └────→ [2c1] ──→ [2c2]
```

## 3. 跨 sub-spec 不变量

| 不变量 | 备注 |
| --- | --- |
| `puppygit.AppModel` 仅初始化一次 | 由 `PuppyGitIntegration` 锁 |
| `BaseGitPageFragment` 工具栏位置恒定在顶部 | 2a1 不变；2b 给 `GitProjectFragment` 单独扩展 |
| `core/app/.../fragments/git/` 不能 import `com.catpuppyapp.puppygit.*` 内部数据类 | 2a2 之后强制；只有 `AppModel`、`InitContent`、具体 screen 类允许 |
| 用户名/邮箱、credential 走 Settings API，不直接读 `Repository.config()` | 2c1 修 |
| `GitCredentialManager` 保留作为密码提供器；2c2 移到「快捷菜单」 | |

## 4. 验收（最终状态）

- core/app/.../fragments/git/ 文件清单：
  - `BaseGitPageFragment.kt`
  - `GitChangesFragment.kt`、`GitHistoryFragment.kt`、`GitBranchesFragment.kt`、`GitDiffFragment.kt`、`GitStashFragment.kt`、`GitTagsFragment.kt`（具体 fragment）
  - `GitProjectFragment.kt`（2b 新增）
  - `GitRuntimeBootstrap.kt`（薄壳）
  - `PuppyGitIntegration.kt`（2a1 新增）
  - `GitSharedState.kt` / `GitUiEventViewModel.kt`（保留，跨 fragment 状态）
  - `menu/GitPopupManager.kt`（2c1/2c2 修）
  - `menu/GitBranchPopupManager.kt`（删除，迁到 puppygit BranchListScreen）
  - `menu/GitQuickMenu.kt`（2c2 新增）

- 编译通过，runtime 不崩
- 用户主流程（打开 git 页 → 改 → 提交 → 推送）无回归
- `git grep "com.catpuppyapp.puppygit"` 在 `core/app/.../fragments/git/` 下
  命中数 ≤ 5（仅白名单：AppModel、InitContent、具体 screen 类）

## 5. 关联

- 子 spec：
  - `2026-06-11-git-fragments-2a1-puppygit-integration-design.md`（基础）
  - 2a2 / 2b / 2c1 / 2c2 待补充
- 关联：PR #305（runtime-livedata）；待 PR：markdown compose-native 重构
