# Markdown 预览：Compose-Native 重构

| 项目 | 内容 |
| --- | --- |
| **Spec author** | android_zero |
| **Date** | 2026-06-11 |
| **Status** | draft |
| **Supersedes** | `2026-06-10-markdown-preview-rich-rendering-design.md`（WebView + nanohttpd 方案） |
| **Scope** | 替换 `core/app` 的 WebView-based markdown 渲染管线 |
| **Out of scope** | git 片段重构（见独立 spec） |

## 1. 背景 / 问题

PR #304 在 `core/app/.../fragments/editor/markdown/MarkdownPreviewFragment.kt` 中引入了「Markwon→HTML→WebView+highlight.js+github-markdown-css」渲染管线。实际效果（见 PR 反馈截图）：

- **排版与样式丢失**：markdown 列表、标题、引用、表格等在 WebView 内仅以裸文字呈现，未应用 github-markdown-css 的样式（推测 WebView 的 loadDataWithBaseURL 与 `<link>` 资源加载的时序存在 bug）。
- **代码高亮异常**：`<pre><code>` 内仍是源码文字，无任何颜色。
- **嵌入式资源**：依赖 nanohttpd 在 `127.0.0.1:<port>` 启动一个 HTTP server 提供 `/file/<rel>` 与 `/_assets/*`。WebView 加载时拿不到资源（截图证实）。
- **维护成本高**：额外引入 nanohttpd + 2 份 1.2k~121k 静态资源 + 一套 ViewModel/WebView 桥接代码。
- **崩溃风险**：Android 12 Samsung SM-A217F 上 WebViewChromium SIGSEGV，需要做 factory-only 创建、attach 后再加载、关闭多个 file 访问开关、LAYER_TYPE_SOFTWARE fallback 等加固。

更糟的是 `core/git/.../com/catpuppyapp/puppygit/compose/MarkDownContainer.kt` 已经验证：直接用 `dev.jeziellago.compose.markdowntext.MarkdownText`（同款 composable）即可获得完整的 GFM + 语法高亮 + Coil 图片 + 文本可选可复制，**不需要**WebView / 本地 HTTP server / bundled CSS-JS 资源。

## 2. 目标

1. 删掉 WebView 渲染管线，**所有** markdown 处理在 Compose 里完成。
2. 复用 `core/git` 已 vendored 的 `dev.jeziellago.compose.markdowntext.MarkdownText`（来自 `com.github.jeziellago:compose-markdown`，已被 puppygit 长期使用）。
3. 网络图片（`http://`、`https://`）和**同目录**文件图片（`./x.png`、`docs/x.png`）都能正确加载。
4. ViewModel 化简为 `produceState` 内联读文件，删除「Loading/Loaded/Error」密封类。
5. 不破坏现有 fragment 接口（`newInstance(filePath)` / `newInstanceWithContent(content)` / `SUPPORTED_EXTENSIONS` / `ARG_MARKDOWN_CONTENT`），调用方零改动。

## 3. 非目标 / 显式不做的事

- **不**支持 markdown 规范的 `<video>` / `<audio>` 嵌入式标签（标准本身不支持，先前 WebView 版本通过 Jsoup 改 HTML 是过度设计）。需要时用户写 HTML 内嵌。
- **不**迁移到 RikkaHub 的 KMP markdown（`com.github.rikkahub:markdown`）或 JetBrains markdown（`org.jetbrains:markdown`）。这两个库已声明在 toml，但 puppygit 实际用的是 compose-markdown，本 spec 不引入新依赖。
- **不**做 dark mode 自动切换样式。`MarkdownText` 自带 `syntaxHighlightColor` 等 token，配合 Material3 `LocalContentColor` 已经能跟随主题。
- **不**修改 `core/git/.../dev/jeziellago/compose/markdowntext/` 的源码。compose-markdown 已在 core/git vendored，core/app 通过 `implementation(projects.core.git)` 即可访问。

## 4. 架构 / 渲染链路

```
MarkdownPreviewAction.openMarkdownPreview(file)
    ↓ fragmentTabManager.openFileTab(file.absolutePath, "md")
    ↓ openTab(entry, filePath) → fragment.arguments.putString("file_path", ...)
    ↓
MarkdownPreviewFragment.onCreate { filePath, inlineContent }
    ↓ onCreateView: ComposeView.setContent { MarkdownPreviewScreen(...) }
    ↓
MarkdownPreviewScreen {
  val state by produceState<UiState>(initial = Loading) {
    runCatching { readContent(filePath, inlineContent) }
      .onSuccess { value = Loaded(it, parentDir) }
      .onFailure { value = Error(it) }
  }
  when (state) {
    Loading → CircularProgressIndicator
    Error   → Text(state.msg)
    Loaded  → MarkdownText(
                markdown   = state.text,
                linkifyMask = EMAIL | URL,
                isTextSelectable = true,
                coilStore    = MarkdownImageSources(state.parentDir, context),
                imageLoader  = remember { Coil2ImageLoader(context) },
                style        = MaterialTheme.typography.bodyMedium,
              )
  }
}
```

`MarkdownText` 内部走「Markwon → Spanned → TextView (AndroidView)」，不暴露中间产物。

## 5. 关键组件

### 5.1 `MarkdownImageSources`（新增，~40 行）

`dev.jeziellago.compose.markdowntext.plugins.image.ImagesPlugin.CoilStore` 的实现，逻辑：

```kotlin
class MarkdownImageSources(
    private val context: Context,
    private val basePathNoEndSlash: String,
) : ImagesPlugin.CoilStore {
    override fun load(drawable: AsyncDrawable): ImageRequest =
        ImageRequest.Builder(context)
            .data(resolve(drawable.destination))
            .build()

    override fun cancel(d: Disposable) = d.dispose()

    private fun resolve(path: String): Any {
        val s = path.trim()
        // 网络 / data: / 绝对 file:// / 绝对 file path → 原样交给 Coil
        if (s.startsWith("http://") || s.startsWith("https://") ||
            s.startsWith("data:") || s.startsWith("file://") ||
            s.startsWith("/") || File(s).isAbsolute) {
            return s
        }
        // 相对路径：拼到 markdown 所在目录
        val file = File(basePathNoEndSlash, s)
        return file.absolutePath
    }
}
```

- 不读取 `drawable.destination` 之外的内容；不缓存。
- 不阻止同目录遍历：相对路径解析只接受「父目录 + 相对路径」→ 受 fragment 持有的 filePath 控制，不来自用户输入。
- 当 `inlineContent` 模式（无 `filePath`）时传空 basePathNoEndSlash → 相对路径解析会落到 `File("", "x.png")` → `x.png`（当前工作目录），无法加载是预期行为。

### 5.2 `MarkdownPreviewFragment`（改写）

| 元素 | 改动 |
| --- | --- |
| `viewModels()` | 删 |
| `MarkdownPreviewViewModel` | 删（4.4） |
| `AndroidView` / WebView / PendingLoad / Listener | 删 |
| 顶层 Composable | 用 `produceState` 读文件；直接 `MarkdownText(...)` 渲染 |
| `companion object` | 保留 `ARG_MARKDOWN_CONTENT` / `SUPPORTED_EXTENSIONS` / `newInstance*`，签名一致 |
| `LOG` | 改名为 `MarkdownPreviewFragment_LOG`（在 `MarkdownPreviewScreen` 内局部，避免和 `produceState` lambda 内的 capture 冲突） |

接口兼容：`EditorFragmentTabManager.openTab` 不需要改动；`MarkdownPreviewAction` 不需要改动。

### 5.3 读文件 / 错误处理

- 用 `Dispatchers.IO` 读 `filePath`；`inlineContent` 直接使用，跳过 IO。
- `produceState` 内 `runCatching` 捕获 `IOException` / `SecurityException` / 其它 `Throwable`，转 `Error(message)`。
- 错误展示用单行 `Text`（不抛 Crashes）。

### 5.4 `MarkdownPreviewViewModel`（删）

- 不再需要 ViewModel：文件读取天然在 Composable 生命周期内；状态由 `produceState` 提供。
- 旧调用方 `viewModel.load(...)` 全部消失。
- `EditorHandlerActivity` / `EditorFragmentTabManager` 不需要改动。

## 6. 资产 / 资源清理

| 路径 | 动作 |
| --- | --- |
| `core/app/src/main/assets/highlight/highlight.min.js` | git rm |
| `core/app/src/main/assets/highlight/highlight.min.css` | git rm |
| `core/app/src/main/assets/markdown/github-markdown.min.css` | git rm |
| `core/app/src/main/assets/` 整个空目录 | 移除 |

旧 spec `docs/superpowers/specs/2026-06-10-markdown-preview-rich-rendering-design.md` 头部加一行：
> Superseded by `2026-06-11-markdown-preview-compose-native-design.md`，保留作为历史参考。

## 7. 依赖改动

`core/app/build.gradle.kts`：

```diff
   implementation(libs.bundles.io.markwon)            # 删除（WebView 链路不再直接用 Markwon）
-  // Local HTTP server for serving markdown preview assets
-  implementation(libs.common.org.nanohttpd)           # 删除
+  // compose-markdown 内部使用 coil 2.x（与 core/app 自己的 coil 3.x 是分开的版本）
+  implementation("io.coil-kt:coil:2.7.0")            # 新增（API 仅 ImageRequest/Disposable/ImageLoader）
```

`gradle/libs.versions.toml`：
- 不动。`libs.bundles.io.markwon` 仍存在（其它模块可能用），`libs.common.org.nanohttpd` 也仍存在（不再被 core/app 使用）。
- `libs.androidx.compose.runtime.livedata` 仍保留（PR #305 引入，未来可能用到）。

## 8. 测试 / 验收

| 验证项 | 方式 |
| --- | --- |
| 沙箱无网络，Gradle 编译跳过；改完后运行 `git grep` 确认无残留引用 | 手动 |
| 现有调用方零改动：`git grep MarkdownPreviewFragment` | 手动 |
| `MarkdownText` 的 `coilStore.load` 在网络/相对路径下均能构造合法 `ImageRequest` | 单元读路径（沙箱限制） |
| `produceState` 状态切换：Loading → Loaded/Error；切换 `inlineContent`/`filePath` 时正确重新 load | 代码 review（`LaunchedEffect` 等价语义由 `produceState` key 提供） |
| 不再 import `org.nanohttpd.*` / `org.jsoup.*` / `io.noties.markwon.*` / `WebView` / `TextView`（除 import 报错用） | 手动 grep |

## 9. 风险

| 风险 | 缓解 |
| --- | --- |
| coil 2.x 与 core/app 现有 coil 3.x 包名不同，IDE 索引可能冲突 | 两版本 namespace 不重叠（`coil.*` vs `coil3.*`），运行时无冲突；core/app 只在 `MarkdownImageSources` 文件内引用 coil 2.x，作用域明确 |
| compose-markdown 内部是 AndroidView，理论上会引入 TextView 渲染成本 | 单页 markdown 文本量通常 < 1MB，可接受；与 WebView 比较无 OOM 风险 |
| `produceState` 不跨配置变更 | 当前 fragment 通过 `arguments` 重新读 filePath，recreate 后会重新走 Loading → Loaded，用户可接受 |
| `MarkdownPreviewAction` 当前 `saveResult` 行为未变；用户对未保存文件也可触发预览 | 沿用现状，spec 不动这部分 |

## 10. 关联

- PR #304 (merged): 引入 WebView + nanohttpd 方案，本 spec 完全替代
- PR #305 (open): 补 `androidx.compose.runtime:runtime-livedata` 依赖，本 spec 仍保留（避免后续删除）
- PR #306 (open): 修复 WebView 工厂/更新 bug，本 spec 替代
- `core/git/.../com/catpuppyapp/puppygit/compose/MarkDownContainer.kt`: 参考实现
- `core/git/.../dev/jeziellago/compose/markdowntext/MarkdownText.kt`: 本 spec 直接调用的 composable
