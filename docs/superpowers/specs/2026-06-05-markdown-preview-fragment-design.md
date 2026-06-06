# Markdown Preview Fragment 开发设计文档

**日期**: 2026-06-05
**版本**: 1.0
**状态**: 已批准

---

## 1. 概述

本设计文档描述了 AndroidIDE (ZeroStudio) 中 Markdown 预览 Fragment 的开发计划。该功能旨在提供完整的 Markdown 可视化预览支持，包括图片、SVG、视频、音频、HTML/JS/CSS 嵌入式渲染等。

## 2. 需求分析

### 2.1 功能需求

- **Markdown 渲染**: 使用 Compose Markdown 渲染标准 Markdown 内容
- **图片渲染**: 支持本地图片和网络图片
- **SVG 渲染**: 支持 SVG 矢量图形
- **视频播放**: 支持嵌入式 HTML5 video
- **音频播放**: 支持嵌入式 HTML5 audio
- **Web 内容**: 支持嵌入的 HTML、JavaScript、CSS 代码块渲染
- **网络资源**: 支持从网络加载资源
- **URL 链接**: 支持 URL 资源的点击加载

### 2.2 用户交互需求

- **打开方式**: 通过编辑器工具栏按钮打开 Markdown 预览
- **多文件支持**: 支持同时打开多个 Markdown 预览 tab
- **文件后缀绑定**: 可绑定的文件后缀（如 md, mdr, markdown）

### 2.3 架构需求

- **拔插式管理**: 创建独立的 FragmentTabRegistry 管理 fragment tab
- **类似 EditorToolboxActions**: 采用类似的插件式管理模式
- **生命周期管理**: 支持 tab 的添加、切换、关闭

## 3. 技术方案

### 3.1 使用的库

- **Compose Markdown**: `com.github.jeziellago:com.github.jeziellago:58aa5aba6a` (Compose 版本)
- **网络图片加载**: `io.coil-kt:coil-compose` (已存在于项目中)
- **SVG 支持**: `io.coil-kt:coil-svg` (已存在于项目中)
- **媒体播放**: `androidx.media3` (已存在于项目中)

### 3.2 核心组件

#### 3.2.1 FragmentTabRegistry

新建 `FragmentTabRegistry` 用于管理 EditorHandlerActivity 中的 fragment tab。

```kotlin
object FragmentTabRegistry {
    val entries = mutableListOf<FragmentTabEntry>()

    fun register(entry: FragmentTabEntry)
    fun unregister(id: String)
    fun get(id: String): FragmentTabEntry?
    fun getByFileExtension(extension: String): FragmentTabEntry?
}

data class FragmentTabEntry(
    val id: String,
    val title: String,
    val iconRes: Int,
    val fragmentClass: Class<out Fragment>,
    val fileExtension: String? = null,
    val order: Int,
)
```

#### 3.2.2 EditorFragmentTabManager

管理 EditorHandlerActivity 中的 fragment tab，处理 tab 的添加、切换、关闭。

#### 3.2.3 MarkdownPreviewFragment

核心预览 Fragment，使用 Compose 渲染 Markdown 内容。

### 3.3 Action 系统

#### 3.3.1 MarkdownPreviewAction

编辑器工具栏 Action，点击后打开 Markdown 预览。

#### 3.3.2 MarkdownPreviewActions

Action 注册管理器，类似 EditorToolboxActions 的插件式管理。

## 4. 文件结构

```
core/app/src/main/java/com/itsaky/androidide/
├── fragments/editor/
│   ├── FragmentTabRegistry.kt
│   ├── FragmentTabEntry.kt
│   ├── EditorFragmentTabManager.kt
│   └── markdown/
│       └── MarkdownPreviewFragment.kt
├── actions/editor/markdown/
│   ├── MarkdownPreviewAction.kt
│   └── MarkdownPreviewActions.kt
└── utils/
    └── MarkdownPreviewActions.kt (Action 注册)

core/app/src/main/res/
├── values/strings.xml (添加字符串资源)
└── drawable/ (添加图标资源)
```

## 5. 实现步骤

1. 创建 `FragmentTabRegistry` 和 `FragmentTabEntry`
2. 创建 `EditorFragmentTabManager`
3. 创建 `MarkdownPreviewFragment`
4. 创建 `MarkdownPreviewAction`
5. 创建 `MarkdownPreviewActions` (Action 注册)
6. 修改 `EditorHandlerActivity` 支持 fragment tab
7. 添加字符串和图标资源
8. 测试和调试

## 6. 修改现有文件

### 6.1 EditorHandlerActivity.kt

- 注入 `EditorFragmentTabManager`
- 添加 fragment tab 的显示逻辑
- 处理 tab 切换和关闭事件

### 6.2 strings.xml

添加:
- `title_markdown_preview`
- `desc_markdown_preview`

### 6.3 图标资源

添加 Markdown 预览图标

---

## 7. 风险和注意事项

- **性能**: 网络图片和 SVG 加载可能影响性能，需要使用合适的缓存策略
- **WebView 安全**: HTML/JS/CSS 嵌入式渲染需要考虑安全风险
- **内存管理**: 多文件 tab 需要注意内存释放
