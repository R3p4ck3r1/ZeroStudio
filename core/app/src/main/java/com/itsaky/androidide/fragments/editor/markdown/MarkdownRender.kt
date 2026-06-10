package com.itsaky.androidide.fragments.editor.markdown

import android.content.Context
import androidx.core.text.HtmlCompat
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 负责把 Markdown 文本渲染成「可直接喂给 WebView 的 HTML 字符串」。
 *
 * 渲染管线：
 *  1. **Markwon 4.6.2**：MD → Spanned
 *  2. **HtmlCompat.toHtml**：Spanned → 字符串 HTML
 *  3. **Jsoup 二次处理**：把 `<img>` 按扩展名分流成 `<video>` / `<audio>` / 保留 `<img>`，
 *     并在 `<img>` / `<a>` 上补 `loading="lazy"`、`target="_blank"`、`referrerpolicy="no-referrer"`
 *
 * 输出：纯净的 HTML 片段（不含 `<html>` / `<body>` 包装），由 [MarkdownPageTemplate] 包裹成完整页面。
 */
class MarkdownRender(private val context: Context) {

  // Markwon 实例：UI 线程持有，所有调用都在主线程。
  private val markwon: Markwon = Markwon.builder(context)
    .usePlugin(StrikethroughPlugin.create())
    .usePlugin(TablePlugin.create(context))
    .usePlugin(TaskListPlugin.create(context))
    .usePlugin(LinkifyPlugin.create())
    .usePlugin(HtmlPlugin.create())
    .build()

  /**
   * 渲染 Markdown 文本为 HTML 片段。
   *
   * @param markdown 源 Markdown
   * @return 包装前的 body HTML 字符串
   */
  fun renderBody(markdown: String): String {
    val spanned = markwon.toMarkdown(markdown)
    val raw = HtmlCompat.toHtml(spanned, HtmlCompat.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)
    val body = extractBody(raw)
    return transformMedia(body)
  }

  /**
   * Markwon 的 [HtmlCompat.toHtml] 输出是完整 HTML（含 `<html><body>...</body></html>` 包裹），
   * 这里只取 `<body>` 内的内容。
   */
  private fun extractBody(raw: String): String {
    val doc: Document = Jsoup.parseBodyFragment(raw)
    val body = doc.body() ?: return raw
    return body.html()
  }

  /**
   * Jsoup 二次处理：把 `<img>` 按扩展名分流成 `<video>` / `<audio>`，并给所有 `<a>` 加 `target="_blank"`。
   */
  private fun transformMedia(body: String): String {
    val doc: Document = Jsoup.parseBodyFragment(body)
    val root = doc.body() ?: return body

    // 媒体扩展名分流
    val imgs = root.select("img").toList() // snapshot, we'll modify the tree
    for (img in imgs) {
      val src = img.attr("src").trim()
      if (src.isEmpty()) continue
      val ext = src.substringAfterLast('.', "").substringBefore('?').substringBefore('#').lowercase()
      val replacement: Element? = when (ext) {
        "mp4", "webm", "mov" -> newMediaElement(doc, "video", src, img.attr("alt"), controls = true)
        "mp3", "wav", "ogg", "m4a" -> newMediaElement(doc, "audio", src, img.attr("alt"), controls = true)
        else -> null
      }
      if (replacement != null) {
        // 复制原 img 上自定义的属性（如 width/height/style）
        for (attr in img.attributes()) {
          if (attr.key in setOf("width", "height", "style", "class")) {
            replacement.attr(attr.key, attr.value)
          }
        }
        img.replaceWith(replacement)
      } else {
        // 静态图：补 loading="lazy" 和 referrerpolicy
        if (!img.hasAttr("loading")) img.attr("loading", "lazy")
        if (!img.hasAttr("decoding")) img.attr("decoding", "async")
        if (!img.hasAttr("referrerpolicy")) img.attr("referrerpolicy", "no-referrer")
        if (img.attr("alt").isBlank()) img.attr("alt", "")
      }
    }

    // 所有 <a> 链接在新窗口打开 + 防止 referrer 泄露
    val anchors = root.select("a[href]").toList()
    for (a in anchors) {
      a.attr("target", "_blank")
      a.attr("rel", "noopener noreferrer")
    }

    // 保留换行：把 blockquote/pre/li 内连续 TextNode 间的 "\n" 保留为 <br>
    root.select("p, li, blockquote").forEach { ensureBlockTextNewlines(it) }

    return root.html()
  }

  private fun newMediaElement(
    doc: Document,
    tag: String,
    src: String,
    alt: String,
    controls: Boolean
  ): Element {
    val el = doc.createElement(tag)
    el.attr("src", src)
    if (controls) el.attr("controls", "")
    el.attr("preload", "metadata")
    if (alt.isNotBlank()) el.attr("aria-label", alt)
    return el
  }

  /**
   * Markwon 在 inline code / 纯文本块里会把多个 TextNode 之间用 "\n" 隔开，
   * 默认 Jsoup 会丢弃这些换行。这里把它们转成 <br>，贴近 GitHub 的渲染。
   */
  private fun ensureBlockTextNewlines(block: Element) {
    val children = block.childNodes().toList()
    for (i in 0 until children.size - 1) {
      val cur = children[i]
      val next = children[i + 1]
      if (cur is TextNode && next is TextNode) {
        if (cur.text().endsWith("\n") || next.text().startsWith("\n")) {
          // 注入 <br> 节点
          val br = Element("br")
          cur.after(br)
        }
      }
    }
  }
}
