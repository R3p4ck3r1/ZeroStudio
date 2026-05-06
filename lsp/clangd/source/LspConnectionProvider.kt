package com.wuxianggujun.tinaide.core.lsp

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * LSP 传输层连接提供者（与具体编辑器实现解耦）。
 *
 * 说明：
 * - 仅负责“启动语言服务器进程/连接”与“提供输入输出流”
 * - 不依赖任何特定编辑器框架的连接抽象
 * - 上层可按需适配到任意 LSP 客户端实现（lsp4j / 自研）
 */
interface LspConnectionProvider : AutoCloseable {

    /**
     * 启动连接。
     */
    @Throws(IOException::class)
    fun start()

    /**
     * 从服务端读取数据的输入流。
     */
    val inputStream: InputStream

    /**
     * 向服务端写入数据的输出流。
     */
    val outputStream: OutputStream

    /**
     * 关闭连接并释放资源。
     */
    override fun close()
}
