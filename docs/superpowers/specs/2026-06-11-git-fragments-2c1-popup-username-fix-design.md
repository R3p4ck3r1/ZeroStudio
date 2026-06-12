# 2c1 GitPopupManager 用户名/邮箱修复

| 字段 | 值 |
| --- | --- |
| 父 spec | `2026-06-11-git-fragments-refactor-roadmap.md` |
| 前置 | 2a1 (puppygit 基础设施) |
| 后置 | 2c2 (快捷菜单) |
| 范围 | `core/app/.../fragments/git/menu/GitPopupManager.kt` |
| 状态 | draft, 实施中 |

## 1. 背景

`GitPopupManager.showSetUserInfoDialog` 打开"设置 git 全局用户名/邮箱"
对话框时，初始状态是空字符串：

```kotlin
val usernameState = remember { mutableStateOf("") }
val emailState = remember { mutableStateOf("") }
```

`AskGitUsernameAndEmailDialog` 内部用 `LaunchedEffect(Unit)` 异步读
puppygit `PuppyGitSettings.json`（同步封装成 `doJobThenOffLoading`）
并把值填进 `usernameState` / `emailState`。结果：

1. **空白闪一下**：对话框弹出瞬间字段是空的，要等几十毫秒
   `LaunchedEffect` 跑完才显示当前配置。
2. **竞态覆盖**：用户如果手快在 `LaunchedEffect` 跑完前点"确定"，会
   把空字符串当成新配置写回 global config，**清掉**之前配置好的
   username/email。
3. 不可见但不致命：`refreshUserInfo` 在 dialog 关闭后被调，会再次读
   出空值并显示在 popup 头里。

## 2. 目标 / 非目标

### 2.1 目标

- `showSetUserInfoDialog` 打开瞬间，字段显示**当前** global config 的值
- 不再需要 `LaunchedEffect` 二次填充（`AskGitUsernameAndEmailDialog`
  内部仍保留该逻辑，作为兜底，零影响）
- 任何时间点保存 → 写入的是对话框里看到的内容，不存在竞态

### 2.2 非目标

- 不动 `AskGitUsernameAndEmailDialog`（puppygit 内部，跨模块；保留
  LaunchedEffect 兜底逻辑）
- 不动 `showTokenCredentialDialog`（走 `GitCredentialManager`，与本
  spec 无关）
- 不动 popup 菜单结构 / 工具栏 / 其它 fragment
- 不重写 popup 框架（保留 `PopupWindow` 实现）

## 3. 修复方案

在 `ComposeView.setContent` 块**外**同步读 `getGitUsernameAndEmailFromGlobalConfig()`，
把读到的 `Pair<String, String>` 作为 `mutableStateOf(...)` 的初值。

```kotlin
private fun showSetUserInfoDialog() {
  dismiss()

  // 2c1 修复:同步预填,避免空白闪一下 + 竞态覆盖
  val (currentUsername, currentEmail) = Libgit2Helper.getGitUsernameAndEmailFromGlobalConfig()

  val composeHostDialog = ComponentDialog(context)
  composeHostDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
  composeHostDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

  val composeView = ComposeView(context).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
      val usernameState = remember { mutableStateOf(currentUsername) }
      val emailState = remember { mutableStateOf(currentEmail) }
      // ... 后续不变
    }
  }
  composeHostDialog.setContentView(composeView)
  composeHostDialog.show()
}
```

`getGitUsernameAndEmailFromGlobalConfig()` 内部走 `PrefMan.get(...)`
读 SharedPreferences，是同步 IO 阻塞调用。在 dialog 弹出路径上
一次性调用（不循环）可接受。后续若对首屏时间敏感，可改成
`runOnIOThread` + 状态回调，但不在本 sub-spec 范围。

## 4. 架构 / 改动点

### 4.1 关键改动

- **`GitPopupManager.kt::showSetUserInfoDialog`**：在 `setContent` 块前
  同步读当前值，作为 `mutableStateOf(...)` 初值

不动：dialog 结构、`AskGitUsernameAndEmailDialog` 调用参数、`onOk` 回调

### 4.2 数据流（修复后）

```
用户点击 popup 头
  ↓
GitPopupManager.showSetUserInfoDialog()
  ↓ Libgit2Helper.getGitUsernameAndEmailFromGlobalConfig() (同步)
  ↓ Pair<currentUsername, currentEmail>
  ↓
ComponentDialog.setContent {
  usernameState = remember { mutableStateOf(currentUsername) }   ← 初值已是当前值
  emailState    = remember { mutableStateOf(currentEmail) }      ← 初值已是当前值
  AskGitUsernameAndEmailDialog(..., isForGlobal = true, ...) {
    onOk = save(usernameState.value, emailState.value)            ← 一定是当前值
  }
}
  ↓
对话框立即显示当前配置,无空白闪,无竞态
```

## 5. 不变量

- `AskGitUsernameAndEmailDialog` 内部 `LaunchedEffect` 仍跑（读
  `PuppyGitSettings.json`）—— 它是兜底,在 `currentUsername` /
  `currentEmail` 与 `PuppyGitSettings.json` 不一致时仍会纠正
  （本 sub-spec 不动该行为）
- `onOk` 写入路径不变（仍走 `saveGitUsernameAndEmailForGlobal`）
- `refreshUserInfo` 仍由 `onOk` 成功时调用（dialog 关闭后更新 popup 头）
- 其它 popup 行为不变

## 6. 验证

| 验证项 | 方式 |
| --- | --- |
| 对话框打开瞬间字段非空 | code review（`mutableStateOf(currentUsername)` 初值已设） |
| 保存空字符串 → 仍能写空（清除功能保留） | code review（`enableOk = { true }` 不变,允许空值） |
| 保存非空值 → 写入的是用户看到的内容 | code review（`onOk` 用 `usernameState.value`,而 state 初值已是当前值） |
| `refreshUserInfo` 仍被调 | code review（`onOk` 内部不变） |
| 编译通过 | code review（沙箱无网络 gradle 编译跳过） |

## 7. 风险

| 风险 | 缓解 |
| --- | --- |
| `getGitUsernameAndEmailFromGlobalConfig` 同步 IO 在主线程上,大项目可能微卡 | 一次调用,可接受;后续若 perf 不达标再异步化 |
| `AppModel.realAppContext` 未初始化时 NPE | 2a1 的 `PuppyGitIntegration.ensureNativeLoaded` 已被 popup 弹起时跑过（popup 在 fragment 内,fragment.onViewCreated 跑过 bootstrap）;若仍 NPE 由调用链上层保护 |

## 8. 关联

- 父 spec: `2026-06-11-git-fragments-refactor-roadmap.md`
- 后置: 2c2 (快捷菜单)
- 调用 puppygit API: `com.catpuppyapp.puppygit.utils.Libgit2Helper.{getGitUsernameAndEmailFromGlobalConfig, saveGitUsernameAndEmailForGlobal}`
- 调用 puppygit 组件: `com.catpuppyapp.puppygit.compose.AskGitUsernameAndEmailDialog`（屏幕类，roadmap 1.4 允许）
