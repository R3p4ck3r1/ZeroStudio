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

package com.itsaky.androidide.lsp.kotlin

import android.content.Context
import com.itsaky.androidide.lsp.api.ILanguageServerRegistry
import com.itsaky.androidide.lsp.kotlin.events.KotlinLanguageClientImpl
import com.itsaky.androidide.lsp.kotlin.events.KotlinTextDocumentSyncHandler
import com.itsaky.androidide.lsp.kotlin.settings.KotlinServerSettings
import com.itsaky.androidide.projects.IProjectManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.slf4j.LoggerFactory

/**
 * 核心：Kotlin LSP 深度集成的中枢与大门。
 *
 * 作用与功能： 对外暴露唯一的初始化 API `setup()`。 调用此方法后，将依次完成：
 * 1. 注册编辑器生命周期同步（TextDocument Sync）。
 * 2. 检查并启动后台 Kotlin 服务进程并附带 WorkspaceSetup 以支持动态依赖计算。
 * 3. 实例化并绑定 ILanguageClient（接收诊断信息等）。
 * 4. 推送初始的用户首选项配置（Settings）。
 *
 * @author android_zero
 */
object KotlinLspIntegration {

  private val log = LoggerFactory.getLogger(KotlinLspIntegration::class.java)
  private val isInitialized = AtomicBoolean(false)
  private val isStarting = AtomicBoolean(false)
  private var workspaceSetup: KotlinWorkspaceSetup? = null

  /**
   * 启动并组装 Kotlin Language Server 环境。 建议在 ProjectManagerImpl 完成 `setupProject` 或 Application
   * onCreate 后调用。
   *
   * @param context Application 或 Activity Context
   */
  @Synchronized
  fun setup(context: Context) {
    val registry = ILanguageServerRegistry.getDefault()
    val existingServer = registry.getServer("kotlin-lsp") as? KotlinLanguageServerImpl
    if (isInitialized.get()) {
      val workspace = IProjectManager.getInstance().getWorkspace()
      if (existingServer != null) {
        if (workspace != null) {
          existingServer.setupWorkspace(workspace)
        }
        existingServer.applySettings(KotlinServerSettings())
        log.info("Kotlin LSP already initialized. Refreshed workspace/configuration binding.")
        return
      }

      if (isStarting.get()) {
        log.debug("Kotlin LSP startup is already in progress; skipping duplicate setup request.")
        return
      }

      // 已标记初始化，但实际 server 不存在（例如安装取消/启动失败）时允许自动重试。
      log.warn("Kotlin LSP marked initialized but no active server found. Re-initializing...")
      isInitialized.set(false)
    }

    if (!isInitialized.compareAndSet(false, true)) {
      return
    }
    isStarting.set(true)

    try {
      KotlinTextDocumentSyncHandler.init()

      val processManager = KotlinServerProcessManager(context.applicationContext)

      // 当 ProjectManager 有 Workspace 时直接使用，若还没生成可自行通过 Event 绑定，此处理论必定已有 Workspace
      val workspace = IProjectManager.getInstance().getWorkspace()
      if (workspace != null) {
        workspaceSetup = KotlinWorkspaceSetup(context.applicationContext, workspace)
        workspaceSetup?.setup(processManager) // 会将 ClassPathProvider 供给 ProcessManager
      }

      processManager.startServer()

      val clientImpl = KotlinLanguageClientImpl()
      ILanguageServerRegistry.getDefault().connectClient(clientImpl)

      thread(name = "KlsConfigApplyThread") {
        var attempts = 0
        var server: KotlinLanguageServerImpl? = null
        while (attempts < 20 && server == null) {
          server =
              ILanguageServerRegistry.getDefault().getServer("kotlin-lsp")
                  as? KotlinLanguageServerImpl
          if (server == null) Thread.sleep(500)
          attempts++
        }

        if (server != null) {
          server.connectClient(clientImpl)
          // 下发首选项设置与初始化数据，包括 R 类支持等机制的激活
          server.applySettings(KotlinServerSettings())
          isStarting.set(false)
          log.info("Kotlin LSP Integration successfully stitched together with Workspace support!")
        } else {
          isInitialized.set(false)
          isStarting.set(false)
          log.warn("Kotlin LSP Server failed to start or register within timeout.")
        }
      }
    } catch (e: Exception) {
      isInitialized.set(false)
      isStarting.set(false)
      log.error("Critical error during Kotlin LSP Integration setup", e)
    }
  }
}
