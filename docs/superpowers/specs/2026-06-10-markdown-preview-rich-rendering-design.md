# Markdown Preview Rich Rendering 设计文档

**日期**: 2026-06-10
**版本**: 1.0
**状态**: ⚠️ **Superseded by `2026-06-11-markdown-preview-compose-native-design.md`**

> 本 spec 描述的 WebView + nanohttpd 方案在 PR #304 落地后效果不达预期：
> 排版丢失、代码无高亮、同目录图片不渲染，反而引入了 WebView 崩溃风险。
> 后续在 PR #306 尝试修复工厂/更新 bug，最终决定改用
> `dev.jeziellago.compose.markdowntext.MarkdownText`。
>
> 本文件**保留作为历史参考**，不再维护。

---

## 1. 概述

将 `com.itsaky.androidide.fragments.editor.markdown.MarkdownPreviewFragment`
从「手写 MD→HTML + 裸 WebView」升级为
「**Markwon 4.6.2 渲染 + nanohttpd 资源服务 + bundled highlight.js / github-markdown-css**」方案。

### 1.1 解决的问题

- 不支持 ` ```javascript ` 围栏代码块的语法高亮
- 不支持网络图片（`<img src="https://...">`）
- 不支持本地相对路径图片（`![alt](docs/About/pages.png)`）
- 排版与网页（GitHub / HackMD / VSCode）相比简陋、间距/字号/暗色模式不达标

### 1.2 范围

| 改动 | 是否在范围 |
| --- | --- |
| `core/app/.../markdown/MarkdownPreviewFragment.kt` 重写 | 是 |
| 新增 `MarkdownPreviewViewModel` / `MarkdownRender` / `LocalResourceHttpServer` / `MarkdownPageTemplate` | 是 |
| 新增 `core/app/src/main/assets/highlight/*` + `assets/markdown/*` | 是 |
| `core/app/build.gradle.kts` 添加 `common-org-nanohttpd` 依赖 | 是 |
| `MarkdownPreviewAction` / `EditorFragmentTabManager` / `core/git` | 否 |
| IDE 主题切换系统 | 否（仅消费 `AppCompatDelegate.getDefaultNightMode()`） |

---

## 2. 架构

```
MarkdownPreviewFragment (Fragment)
  └─ ComposeView
       └─ AndroidView
            └─ WebView（懒加载，view.isAttachedToWindow 后再 loadDataWithBaseURL）
                 └─ loadDataWithBaseURL("http://127.0.0.1:<port>/", html, ...)

MarkdownPreviewViewModel
  ├─ MarkdownContentLoader ── IO 读 .md / 解析 arg
  ├─ LocalResourceHttpServer ── 单例 nanohttpd
  │     ├─ /_assets/*   ── 读 app assets（highlight.js / github-markdown-css）
  │     └─ /*           ── 读 baseDir 下的本地文件
  └─ MarkwonRenderer
        └─ Markwon 4.6.2 + Table / TaskList / Strikethrough / Linkify / HtmlPlugin
            （关闭 Markwon 自带 ImagePlugin，由 WebView 处理 <img>）
```

资源请求、高亮脚本、CSS 全部走 `http://127.0.0.1:<port>/`，WebView 同源加载，
完全规避 file:// 跨域问题。

---

## 3. 文件与依赖

### 3.1 新增文件

| 路径 | 作用 |
| --- | --- |
| `core/app/src/main/java/com/itsaky/androidide/fragments/editor/markdown/MarkdownPreviewViewModel.kt` | 协程：读文件 / 启动服务 / 渲染 HTML |
| `core/app/src/main/java/com/itsaky/androidide/fragments/editor/markdown/MarkdownRender.kt` | Markwon 包装，配置插件链 |
| `core/app/src/main/java/com/itsaky/androidide/fragments/editor/markdown/LocalResourceHttpServer.kt` | nanohttpd 单例，端口 0（系统分配） |
| `core/app/src/main/java/com/itsaky/androidide/fragments/editor/markdown/MarkdownPageTemplate.kt` | 拼最终 HTML（head 注入 CSS/JS、body 包 `.markdown-body`） |
| `core/app/src/main/assets/highlight/highlight.min.js` | highlight.js 11.x 通用 min bundle（语言子集：javascript, typescript, python, json, bash, xml, css, yaml, markdown, gradle, kotlin, java, groovy, diff, ini） |
| `core/app/src/main/assets/highlight/highlight.min.css` | github 主题 |
| `core/app/src/main/assets/markdown/github-markdown.css` | github-markdown-css 5.5 min（亮色 + 暗色） |

### 3.2 修改文件

| 路径 | 改动 |
| --- | --- |
| `core/app/src/main/java/com/itsaky/androidide/fragments/editor/markdown/MarkdownPreviewFragment.kt` | 重写：ComposeView + AndroidView(WebView) + 加载 `MarkdownPreviewViewModel.renderedHtml` |
| `core/app/build.gradle.kts` | 添加 `implementation(libs.common.org.nanohttpd)` |

### 3.3 依赖

| 库 | 版本 | 状态 |
| --- | --- | --- |
| `io.noties.markwon:core` | 4.6.2 | 已在 `libs.bundles.io.markwon` |
| `io.noties.markwon:ext-tables` 等 | 4.6.2 | 已在 bundle |
| `org.nanohttpd:nanohttpd` | 2.3.1 | `libs.common.org.nanohttpd`，需在 `core/app` 加依赖 |
| highlight.js 11.x | 自带 min bundle | assets 内 |
| github-markdown-css 5.5 | 自带 min css | assets 内 |

---

## 4. 渲染管线

### 4.1 MD → HTML

```kotlin
val markwon = Markwon.builder(context)
    .usePlugin(StrikethroughPlugin.create())
    .usePlugin(TablePlugin.create(context))
    .usePlugin(TaskListPlugin.create(context))
    .usePlugin(LinkifyPlugin.create())
    .usePlugin(HtmlPlugin.create())
    .build()

val html = markwon.toMarkdown(md)              // -> Spanned
val htmlString = HtmlCompat.toHtml(html, ...)  // -> 字符串
```

Markwon 默认会为图片生成 `<img>`、代码块生成 `<pre><code class="language-xxx">`，
与 highlight.js 完全兼容。

### 4.2 HTML 包装

`MarkdownPageTemplate` 在渲染结果外套一层：

```html
<!DOCTYPE html>
<html data-theme="light|dark">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <link rel="stylesheet" href="/_assets/markdown/github-markdown.css">
  <link rel="stylesheet" href="/_assets/highlight/highlight.min.css">
  <script defer src="/_assets/highlight/highlight.min.js"
          onload="hljs.highlightAll();"></script>
  <style>
    body { background: var(--md-bg); color: var(--md-fg); }
    .markdown-body { box-sizing: border-box; min-width: 200px; max-width: 980px;
                     margin: 0 auto; padding: 32px; }
  </style>
</head>
<body>
  <article class="markdown-body">{renderedBody}</article>
</body>
</html>
```

`data-theme` 由 Fragment 在 `WebView.loadUrl` 之前根据
`AppCompatDelegate.getDefaultNightMode()` 拼到 `htmlString` 头部。

### 4.3 资源解析

WebView 以 `http://127.0.0.1:<port>/` 为 base 后，
所有相对路径自动走 nanohttpd：

| Markdown 写法 | 浏览器最终请求 | nanohttpd 路由 |
| --- | --- | --- |
| `![alt](docs/x.png)` | `http://127.0.0.1:<port>/docs/x.png` | 文件服务，读 `baseDir/docs/x.png` |
| `![alt](https://a/b.svg)` | `https://a/b.svg`（直连外网） | 不走本服务 |
| ` ```javascript ` 代码块 | 客户端由 `highlight.min.js` 处理 | 不走网络 |
| `<script src="/_assets/...">` | `http://127.0.0.1:<port>/_assets/...` | 静态路由，stream assets |

### 4.4 媒体格式分流

Markwon 输出后用 Jsoup 二次处理 `<img>`：

| 扩展名 | 替换为 |
| --- | --- |
| `.mp4` `.webm` `.mov` | `<video controls preload="metadata" src="..."></video>` |
| `.mp3` `.wav` `.ogg` `.m4a` | `<audio controls src="..."></audio>` |
| 其它 | `<img loading="lazy" src="..." alt="...">` |

---

## 5. WebView 崩溃加固

针对 Android 12 Samsung (SM-A217F, a21s, exynos850) 上
`WebViewChromium` 在 `realpath` 栈溢出导致 SIGSEGV 的问题：

- **延迟创建**：`AndroidView.factory` 内才 `new WebView(ctx)`，factory 不抛异常
- **延后加载**：`update` 回调里检查 `view.isAttachedToWindow`，未 attach 时
  `view.post { update(it) }`，已 attach 才 `loadDataWithBaseURL`
- **关闭危险开关**：
  - `setAllowFileAccessFromFileURLs(false)`
  - `setAllowUniversalAccessFromFileURLs(false)`
  - `setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)`
  - `setAllowFileAccess(true)` 保留（content:// 兜底路径），但默认走 HTTP
- **崩溃隔离**：`factory` 用 `runCatching` 包 `WebView(...)`；失败时返回只读 `TextView` 显示错误
- **降级层**：若 Chromium 仍崩，`webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)`
- **服务预热**：`LocalResourceHttpServer.start(baseDir)` 在 `ViewModel.init`
  中同步起好（不放在 WebView 工厂内异步起），避免 WebView 已 load 但服务未就绪
- **WebView 客户端**：保留 `setJavaScriptEnabled(true)`（highlight.js 需要）

---

## 6. LocalResourceHttpServer 设计

### 6.1 单例

```kotlin
object LocalResourceHttpServer {
    private var server: NanoHTTPD? = null
    private val started = AtomicBoolean(false)
    @Volatile var port: Int = -1; private set
    @Volatile var baseDir: File? = null; private set

    @Synchronized
    fun start(baseDir: File): Int { ... }

    fun stop() { ... }
}
```

### 6.2 路由

| Path prefix | 处理 |
| --- | --- |
| `/_assets/` | `context.assets.open(path)` 直接流式返回 |
| `/` 其它 | 走 `baseDir.resolve(URLDecoder.decode(uri, UTF_8))`，防 `..` 越狱 |

### 6.3 进程级生命周期

- 在 `BaseApplication.onTerminate` 或 `onTrimMemory` 不显式 stop
- 在 `MarkdownPreviewViewModel.onCleared` 不 stop（多 tab 共用）
- 进程退出时由 OS 回收 socket

---

## 7. ViewModel 状态机

```kotlin
sealed interface MarkdownPreviewState {
    data object Loading : MarkdownPreviewState
    data class Loaded(val html: String, val port: Int) : MarkdownPreviewState
    data class Error(val message: String) : MarkdownPreviewState
}
```

加载流程：

1. `viewModelScope.launch(Dispatchers.IO)` 读 `.md`（或直接拿 `arg[ARG_MARKDOWN_CONTENT]`）
2. `LocalResourceHttpServer.start(file.parentFile ?: Environment.HOME)` —— 复用
3. `MarkdownRender.render(md, file.parentFile)` —— 用 Jsoup 处理 `<img>` 媒体分流
4. `MarkdownPageTemplate.wrap(html, isDark)` —— 注入 head/body
5. `MutableStateFlow<MarkdownPreviewState>` 推到 UI

Compose 端 `collectAsState()`，Loading / Error / Loaded 走 box 三态渲染。

---

## 8. 错误处理

| 场景 | 行为 |
| --- | --- |
| `.md` 不存在 | `Error("Markdown file does not exist:\n$filePath")` |
| `.md` 不可读 | `Error("Markdown file is not readable:\n$filePath")` |
| 文件 IO 抛 IOException | `Error(t.message ?: "Unable to load Markdown file.")` |
| Markwon 抛异常 | `Error("Failed to render Markdown: ${t.message}")` |
| nanohttpd 启动失败（端口占用） | `Error("Local resource server failed to start.")` |
| WebView 构造抛异常 | UI 显示 `TextView("Preview is not available on this device.")`，不闪退 |
| 资源 404 | WebView 走浏览器默认行为（破图标），不向用户报错 |

所有异常都在 `viewModelScope.launch` 的 try/catch 内兜住，process 不挂。

---

## 9. 验收

- [ ] 打开任意 `.md` → 点 `MarkdownPreviewAction` → 新 tab 显示已渲染内容
- [ ] 围栏 ` ```javascript ` 渲染出彩色 token
- [ ] 围栏 ` ``` `（无语言）也能高亮（自动检测）
- [ ] 行内 `` `code` `` 由 Markwon 渲染，保留 monospace
- [ ] `![alt](docs/About/pages.png)` 相对路径显示
- [ ] `![alt](https://img.shields.io/.../blue.svg)` 网络显示
- [ ] `.mp4` 走原生 `<video controls>`
- [ ] `.mp3` 走原生 `<audio controls>`
- [ ] 切换系统深色模式 → 排版自动跟随
- [ ] LeakCanary 反复打开/关闭 5 次无内存泄漏
- [ ] SM-A217F (Android 12) 上 WebView 不再 SIGSEGV
- [ ] 关闭 IDE_HOME 目录权限的 `.md`（如只读）不闪退
- [ ] `ARG_MARKDOWN_CONTENT` 形式的内嵌渲染仍可用（baseDir 传 `null`，相对路径不可加载）

---

## 10. 风险与回退

- **assets 包体积**：highlight.js min (~50KB) + github-markdown-css min (~30KB) = ~80KB 增加。可接受
- **nanohttpd 端口占用**：理论上有，但应用作用域内单实例 + 进程退出回收
- **WebView SIGSEGV 复发**：若加固方案仍崩，可改用 `LAYER_TYPE_SOFTWARE` 兜底，或回退到方案 B（commonmark + JDK HttpServer）
- **GFM 表格/任务列表兼容性**：Markwon 4.6.2 默认 GFM，已覆盖
