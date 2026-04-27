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
import com.itsaky.androidide.lsp.kotlin.compiler.KotlinCompilerProvider
import com.itsaky.androidide.lsp.kotlin.compiler.KotlinCompilerService
import com.itsaky.androidide.lsp.kotlin.compiler.KotlinSourceFileManager
import com.itsaky.androidide.projects.IWorkspace
import com.itsaky.androidide.projects.ModuleProject
import com.itsaky.androidide.projects.android.AndroidModule
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

/**
 * 核心：Kotlin 工作区设置与事件生命周期管理。
 *
 * <p>
 * 作用：主要负责在 KotlinLspIntegration 组装完毕后，利用 Java NIO WatchService 去监听 Gradle 编译产出目录 (build/generated)
 * 的变动。若有变动意味着类路径更新，则动态向 Server 发送通知 更新解析缓存，使得诸如 R 类修改后立刻即可获得代码补全与识别。 </p>
 *
 * @author android_zero
 */
class KotlinWorkspaceSetup(private val context: Context, private val workspace: IWorkspace) {

  companion object {
    private val log = LoggerFactory.getLogger(KotlinWorkspaceSetup::class.java)
  }

  private var compilerService: KotlinCompilerService? = null
  private val classpathProvider = KotlinClasspathProvider()
  private val indexCache = KotlinIndexCache(workspace.getProjectDir().absolutePath)

  private var buildWatcher: WatchService? = null
  private var watcherJob: Job? = null
  private val watchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  fun setup(processManager: KotlinServerProcessManager) {
    initializeCompilerService()
    classpathProvider.initialize(compilerService)

    // 给 ProcessManager 配置注入依赖计算工具
    processManager.setClasspathProvider(classpathProvider)

    startBuildWatcher(processManager)

    // 这里不再主动接管 startServer，它在 KotlinLspIntegration 中独立执行
    // 主要在环境完备后做增量通知检测
  }

  private fun startBuildWatcher(processManager: KotlinServerProcessManager) {
    try {
      buildWatcher = FileSystems.getDefault().newWatchService()

      val modulesToWatch = mutableListOf<File>()
      workspace.getSubProjects().filterIsInstance<AndroidModule>().forEach { module ->
        val buildDir = File(module.path.replace(":", "/").removePrefix("/"), "build")
        if (buildDir.exists()) {
          modulesToWatch.add(buildDir)
        }
      }

      if (modulesToWatch.isEmpty()) {
        log.info("No build directories found to watch. Skipping build watcher bootstrap.")
        return
      }

      val watchKeys = mutableMapOf<WatchKey, File>()
      modulesToWatch.forEach { buildDir ->
        try {
          val generatedDir = File(buildDir, "generated")
          if (generatedDir.exists()) {
            val key =
                generatedDir
                    .toPath()
                    .register(
                        buildWatcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE,
                    )
            watchKeys[key] = generatedDir
            log.info("Watching for Kotlin build changes in: ${generatedDir.absolutePath}")
          }
        } catch (e: Exception) {
          log.warn("Failed to watch directory: ${buildDir.absolutePath}", e)
        }
      }

      watcherJob = watchScope.launch {
        var lastReloadTime = 0L
        val reloadDebounceMs = 500L

        while (isActive) {
          try {
            val key = buildWatcher?.poll(1, TimeUnit.SECONDS) ?: continue
            val events = key.pollEvents()

            if (events.isNotEmpty()) {
              val now = System.currentTimeMillis()
              if (now - lastReloadTime > reloadDebounceMs) {
                delay(reloadDebounceMs)
                val checkKey = buildWatcher?.poll(100, TimeUnit.MILLISECONDS)
                if (checkKey == null) {
                  log.info("Build changes detected, notifying KLS to reload classpath...")
                  reloadClasspathAndIndex()
                  lastReloadTime = System.currentTimeMillis()
                } else {
                  checkKey.reset()
                }
              }
            }
            key.reset()
          } catch (e: Exception) {
            if (e is CancellationException) break
          }
        }
      }
    } catch (e: Exception) {
      log.error("Failed to start build watcher", e)
    }
  }

  private suspend fun reloadClasspathAndIndex() {
    withContext(Dispatchers.IO) {
      try {
        classpathProvider.invalidateCache()

        // 当发生更改时，通过 LSP 命令让 Server 刷新
        val server =
            ILanguageServerRegistry.getDefault().getServer("kotlin-lsp")
                as? KotlinLanguageServerImpl
        server?.executeWorkspaceCommand("kotlinRefreshBazelClassPath", emptyList())

        log.info("Classpath and index reloaded successfully via KLS Workspace Command")
      } catch (e: Exception) {
        log.error("Failed to reload classpath", e)
      }
    }
  }

  fun cleanup() {
    try {
      watcherJob?.cancel()
      buildWatcher?.close()
      watchScope.cancel()

      compilerService?.destroy()
      KotlinCompilerProvider.getInstance().destroy()
      KotlinSourceFileManager.clearCache()
    } catch (e: Exception) {
      log.warn("Error cleaning up workspace setup", e)
    }
  }

  private fun initializeCompilerService() {
    try {
      var mainModule: ModuleProject? =
          workspace.getSubProjects().filterIsInstance<AndroidModule>().firstOrNull {
            it.isApplication
          }
      if (mainModule == null)
          mainModule = workspace.getSubProjects().filterIsInstance<AndroidModule>().firstOrNull()

      if (mainModule != null) {
        compilerService = KotlinCompilerProvider.get(mainModule)
      } else {
        compilerService = KotlinCompilerService.NO_MODULE_COMPILER
      }
    } catch (e: Exception) {
      log.error("Failed to initialize compiler service", e)
      compilerService = KotlinCompilerService.NO_MODULE_COMPILER
    }
  }
}
