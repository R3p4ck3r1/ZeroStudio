package com.itsaky.androidide.fragments.editor.markdown

/**
 * 负责把 [MarkdownRender] 渲染出的 body HTML 片段，套进一个完整的 HTML 页面模板里：
 *
 *  - `<head>` 注入 github-markdown.css（亮/暗均覆盖）+ highlight.js + github 主题
 *  - `<body>` 用 `<article class="markdown-body">` 包裹
 *  - `<meta viewport>` 让移动端 WebView 渲染正常
 *  - `data-theme` 由外部传入 (`"light"` / `"dark"`)，未传入时跟随系统 `prefers-color-scheme`
 *
 * 所有 CSS / JS 路径都走 `/_assets/...` 这个相对路径，配合 [LocalResourceHttpServer.baseUrl]，
 * 整个页面同源加载，无 file:// 跨域。
 */
object MarkdownPageTemplate {

  /**
   * @param bodyHtml 已经过 [MarkdownRender.renderBody] 处理后的 HTML 片段
   * @param theme `"light"` 或 `"dark"`，传 `null` 时跟随系统
   * @return 可直接 `loadDataWithBaseURL(baseUrl, ...)` 的 HTML 字符串
   */
  fun wrap(bodyHtml: String, theme: String? = null): String {
    val themeAttr = if (theme.isNullOrBlank()) "" else " data-theme=\"$theme\""
    return wrapRaw(bodyHtml, themeAttr)
  }

  /**
   * 兼容直接传入完整属性串的版本。
   */
  fun wrapRaw(bodyHtml: String, htmlAttrs: String = ""): String = buildString {
    append("<!DOCTYPE html>\n")
    append("<html$htmlAttrs>\n")
    append("<head>\n")
    append("<meta charset=\"utf-8\">\n")
    append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">\n")
    append("<meta name=\"color-scheme\" content=\"light dark\">\n")
    append("<title>Markdown Preview</title>\n")
    append("<link rel=\"stylesheet\" href=\"/_assets/markdown/github-markdown.min.css\">\n")
    append("<link rel=\"stylesheet\" href=\"/_assets/highlight/highlight.min.css\">\n")
    append("<script defer src=\"/_assets/highlight/highlight.min.js\"></script>\n")
    append("<style>\n")
    append("  html, body { background: transparent; }\n")
    append("  body { margin: 0; padding: 16px; }\n")
    append("  .markdown-body { box-sizing: border-box; max-width: 980px; margin: 0 auto; }\n")
    append("  .markdown-body video, .markdown-body audio { max-width: 100%; }\n")
    append("  .markdown-body img { max-width: 100%; height: auto; }\n")
    append("  .markdown-body pre code.hljs { padding: 16px; border-radius: 6px; }\n")
    append("</style>\n")
    append("<script>\n")
    append("  document.addEventListener('DOMContentLoaded', function() {\n")
    append("    if (window.hljs) { try { window.hljs.highlightAll(); } catch (e) {} }\n")
    append("  });\n")
    append("</script>\n")
    append("</head>\n")
    append("<body>\n")
    append("<article class=\"markdown-body\">\n")
    append(bodyHtml)
    append("\n</article>\n")
    append("</body>\n")
    append("</html>")
  }
}
