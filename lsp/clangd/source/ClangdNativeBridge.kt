/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package android.zero.studio.lsp.clangd

/**
 * Clangd LSP 底层 JNI 桥接层。
 * 
 * 功能与用途：
 * 本类负责直接与 Native 层 (C/C++) 的 Clangd 服务进行通信。它封装了所有底层的 JNI 方法，
 * 包括服务的初始化、关闭、文档同步 (Open/Change/Close) 以及各种 LSP 请求 (Hover, Completion 等)。
 * 
 * 工作流程线路图：
 * [AndroidIDE 业务层] 
 *        | (调用 ILanguageServer 接口)
 *        v
 * [ClangdLanguageServer] (将参数转为基础类型并调用 Bridge)
 *        |
 *        v
 * [ClangdNativeBridge] (通过 JNI 边界)
 *        |
 *        v
 * [simple_lsp_jni.cpp / clangd_server.cpp] (Native 进程间通信或管道写入 clangd)
 * 
 * 上下文与父类关系：
 * 作为单例对象 (Object) 存在，不继承其他类。它是整个 C/C++ 语言服务器在 Android/Java 层的唯一入口点。
 * 此类返回的均为原生类型或 JSON 字符串，由上层 [ILanguageServer] 实现类反序列化为 `com.itsaky.androidide.lsp.models.*` 对象。
 *
 * @author android_zero
 */
object ClangdNativeBridge {

    /**
     * 库加载初始化。在首次访问时加载底层的编译器驱动与 LSP 管道支持库。
     */
    init {
        try {
            System.loadLibrary("native_compiler")
            nativeOnLoad()
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    // ========================================================================
    // 生命周期管理 (Lifecycle Management)
    // ========================================================================

    /**
     * 初始化底层 JNI 环境及全局引用。
     */
    @JvmStatic
    private external fun nativeOnLoad(): Int

    /**
     * 初始化 Clangd 语言服务器。
     *
     * @param clangdPath libclangd.so 的绝对路径。
     * @param workDir 当前工作区的根目录路径。
     * @param completionLimit 每次补全请求返回的最大条目数限制。
     * @return 成功返回 true，失败返回 false。
     */
    @JvmStatic
    external fun nativeInitialize(clangdPath: String, workDir: String, completionLimit: Int): Boolean

    /**
     * 关闭并清理 Clangd 语言服务器进程及管道。
     */
    @JvmStatic
    external fun nativeShutdown()

    /**
     * 检查 Clangd 服务是否处于活动且已初始化状态。
     *
     * @return 如果服务正在运行返回 true。
     */
    @JvmStatic
    external fun nativeIsInitialized(): Boolean

    // ========================================================================
    // 文档同步 (Text Document Synchronization)
    // ========================================================================

    /**
     * 发送 textDocument/didOpen 通知。
     * 
     * @param fileUri 文件的标准 URI 格式 (例如: file:///...)。
     * @param content 文件的完整文本内容。
     * @param languageId 语言标识符 (如 "c", "cpp")。
     */
    @JvmStatic
    external fun nativeDidOpen(fileUri: String, content: String, languageId: String?)

    /**
     * 发送 textDocument/didChange 通知。
     * 
     * @param fileUri 文件的标准 URI 格式。
     * @param content 修改后的完整/增量文本内容。
     * @param version 文档的当前版本号，递增。
     */
    @JvmStatic
    external fun nativeDidChange(fileUri: String, content: String, version: Int)

    /**
     * 发送 textDocument/didClose 通知。
     * 
     * @param fileUri 被关闭文件的标准 URI 格式。
     */
    @JvmStatic
    external fun nativeDidClose(fileUri: String)

    // ========================================================================
    // LSP 核心请求 (LSP Requests)
    // ========================================================================

    /**
     * 发送 textDocument/hover 请求。
     * 
     * @param fileUri 目标文件的 URI。
     * @param line 行号 (0-based)。
     * @param character 列号 (0-based)。
     * @return 返回该请求的唯一 requestId (长整型)，后续可通过 [nativeGetResult] 轮询获取。
     */
    @JvmStatic
    external fun nativeRequestHover(fileUri: String, line: Int, character: Int): Long

    /**
     * 发送 textDocument/completion 请求。
     * 
     * @param fileUri 目标文件的 URI。
     * @param line 行号 (0-based)。
     * @param character 列号 (0-based)。
     * @param triggerCharacter 触发补全的字符 (例如 ".", "->", "::")，若为手动触发则为 null。
     * @return 返回请求的唯一 requestId。
     */
    @JvmStatic
    external fun nativeRequestCompletion(fileUri: String, line: Int, character: Int, triggerCharacter: String?): Long

    /**
     * 发送 textDocument/definition 请求 (跳转到定义)。
     * 
     * @param fileUri 目标文件的 URI。
     * @param line 行号 (0-based)。
     * @param character 列号 (0-based)。
     * @return 返回请求的唯一 requestId。
     */
    @JvmStatic
    external fun nativeRequestDefinition(fileUri: String, line: Int, character: Int): Long

    /**
     * 发送 textDocument/references 请求 (查找所有引用)。
     * 
     * @param fileUri 目标文件的 URI。
     * @param line 行号 (0-based)。
     * @param character 列号 (0-based)。
     * @param includeDeclaration 是否在结果中包含声明本身。
     * @return 返回请求的唯一 requestId。
     */
    @JvmStatic
    external fun nativeRequestReferences(fileUri: String, line: Int, character: Int, includeDeclaration: Boolean): Long

    // ========================================================================
    // 结果获取与取消 (Result Polling & Cancellation)
    // ========================================================================

    /**
     * 获取指定请求的结果。由于底层采用管道通信，这是非阻塞的轮询方法。
     * 
     * @param requestId 之前请求返回的 ID。
     * @return 如果结果已准备好，返回 JSON 格式的字符串；如果仍在处理中，返回 null。
     */
    @JvmStatic
    external fun nativeGetResult(requestId: Long): String?

    /**
     * 向语言服务器发送取消请求的指令 ($/cancelRequest)。
     * 
     * @param requestId 需要取消的请求 ID。
     */
    @JvmStatic
    external fun nativeCancelRequestInternal(requestId: Long)

    /**
     * 当请求超时时，通知底层清理对应的请求状态。
     * 
     * @param requestId 超时的请求 ID。
     */
    @JvmStatic
    external fun nativeNotifyRequestTimeout(requestId: Long)
}