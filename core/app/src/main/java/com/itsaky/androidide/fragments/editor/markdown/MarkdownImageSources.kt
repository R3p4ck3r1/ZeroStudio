package com.itsaky.androidide.fragments.editor.markdown

import android.content.Context
import coil.request.Disposable
import coil.request.ImageRequest
import dev.jeziellago.compose.markdowntext.plugins.image.ImagesPlugin
import io.noties.markwon.image.AsyncDrawable
import java.io.File

/**
 * 把 markdown 文档里 `<img src="...">` 引用的资源解析成可被 Coil 加载的 `ImageRequest`。
 *
 * 解析规则（按顺序匹配，命中即返回）：
 * 1. `http://` / `https://` / `data:`：网络资源，原样返回。
 * 2. `file://` 开头的绝对路径：原样返回，Coil 用 file loader 加载。
 * 3. 以 `/` 开头的绝对路径：原样返回，Coil 用 file loader 加载。
 * 4. 其它视为相对于 markdown 文件所在目录的相对路径，拼出绝对路径返回。
 *
 * 如果 markdown 是 inline（无 filePath）模式，相对路径无法解析，会落到「当前进程工作
 * 目录 + 相对路径」。这种场景下找不到文件是预期行为，Coil 会显示 error 占位。
 *
 * @param context 任意 Context（只用于构造 ImageRequest.Builder）
 * @param basePathNoEndSlash markdown 所在目录的绝对路径；如果不是以 `/` 结尾会被截断
 *                          反斜杠。仅在相对路径场景下使用。
 */
class MarkdownImageSources(
    private val context: Context,
    basePathNoEndSlash: String,
) : ImagesPlugin.CoilStore {

    private val basePath: String =
        if (basePathNoEndSlash.endsWith("/") || basePathNoEndSlash.isEmpty()) {
            basePathNoEndSlash
        } else {
            "$basePathNoEndSlash/"
        }

    override fun load(drawable: AsyncDrawable): ImageRequest =
        ImageRequest.Builder(context)
            .data(resolve(drawable.destination))
            .build()

    override fun cancel(disposable: Disposable) {
        disposable.dispose()
    }

    private fun resolve(destination: String): Any {
        val trimmed = destination.trim()
        if (trimmed.isEmpty()) return destination

        // 1. 网络 / data: URI
        if (trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("data:")) {
            return trimmed
        }

        // 2. file:// URI
        if (trimmed.startsWith("file://")) {
            return trimmed
        }

        // 3. 绝对路径
        if (trimmed.startsWith("/")) {
            return trimmed
        }
        if (File(trimmed).isAbsolute) {
            return trimmed
        }

        // 4. 相对路径：拼到 basePath
        return File(basePath + trimmed).absolutePath
    }
}
