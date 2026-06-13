// FILE: core/projects/src/main/java/com/itsaky/androidide/projects/IProjectManager.kt
/*
 *  This file is part of AndroidIDE.
 *  @author Akash Yadav
 *  @author android_zero
 */

package com.itsaky.androidide.projects

import androidx.annotation.RestrictTo
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.android.AndroidModule
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.tooling.api.IProject
import com.itsaky.androidide.utils.ServiceLoader
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ProjectFlowBus {
  private val _events = MutableSharedFlow<Any>(extraBufferCapacity = 64)
  val events = _events.asSharedFlow()

  fun post(event: Any) = _events.tryEmit(event)
}

interface IProjectManager {

  companion object {
    private var projectManager: IProjectManager? = null

    @JvmStatic
    fun getInstance(): IProjectManager {
      return projectManager
          ?: ServiceLoader.load(IProjectManager::class.java).findFirstOrThrow().also {
            projectManager = it
          }
    }
  }

  /**
   * 当前打开的工程目录绝对路径；如果当前没有打开工程则返回 `null`。
   *
   * <p>治本修复（v20260610 之后）：此 getter 不再抛 `IllegalStateException`。
   * 历史行为是当 `_projectDir == null` 时抛
   * `"Cannot get project directory. Path has not been set."`，这导致所有调用方都必须
   * 套 `try/catch` 或 `runCatching`，是性能 + 可读性的双重损失，也违反了
   * "getter 不应抛业务异常" 的设计原则（参考 Effective Java Item 44 / 49）。
   *
   * <p>调用方应当：
   * <ul>
   *   <li>需要工程目录做实际操作时：用 `projectDirPath ?: return early`</li>
   *   <li>确定工程已打开、但 Kotlin null-safety 不让过编译时：调用 [requireProjectDirPath]</li>
   * </ul>
   *
   * <p>性能：此修复后无异常开销，调用方不需要 `runCatching` 包裹。
   */
  val projectDirPath: String?
    get() = projectDir?.path

  /**
   * 当前打开的工程目录 [File]；未打开工程时返回 `null`。
   * 配套 [projectDirPath] 的语义修正，行为变化见 [projectDirPath] 的 Javadoc。
   */
  val projectDir: File?

  /**
   * 当工程目录**已经设置**时返回其绝对路径；否则抛 [IllegalStateException]。
   * 等价于旧版 `projectDirPath` 的非空契约，保留供**确实**需要非空路径的调用方使用。
   */
  fun requireProjectDirPath(): String =
      projectDirPath
          ?: throw IllegalStateException("Cannot get project directory. Path has not been set.")

  /**
   * 当工程目录**已经设置**时返回其 [File]；否则抛 [IllegalStateException]。
   * 等价于旧版 `projectDir` 的非空契约。
   */
  fun requireProjectDir(): File =
      projectDir
          ?: throw IllegalStateException("Cannot get project directory. Path has not been set.")
  val projectSyncIssues: ProjectSyncIssues?

  fun openProject(directory: File)

  fun openProject(path: String) = openProject(File(path))

  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
  suspend fun setupProject(
      project: IProject = Lookup.getDefault().lookup(BuildService.KEY_PROJECT_PROXY)!!
  )

  suspend fun isGradleSyncNeeded(projectDir: File): Boolean

  fun getWorkspace(): IWorkspace?

  fun requireWorkspace(): IWorkspace = getWorkspace() ?: throw IWorkspace.NotConfiguredException()

  fun getAndroidModules(): List<AndroidModule>

  fun getAndroidAppModules(): List<AndroidModule>

  fun getAndroidLibraryModules(): List<AndroidModule>

  fun findModuleForFile(file: File, checkExistence: Boolean = true): ModuleProject?

  fun findModuleForFile(file: Path, checkExistence: Boolean = true): ModuleProject? =
      findModuleForFile(file.toFile(), checkExistence)

  fun notifyFileCreated(file: File)

  fun notifyFileDeleted(file: File)

  fun notifyFileRenamed(from: File, to: File)

  fun destroy()
}

fun IProjectManager.isPluginProject(): Boolean {
  val cached =
      (this as? com.itsaky.androidide.projects.internal.ProjectManagerImpl)?.pluginProjectCached
  if (cached != null) return cached

  val result =
      File(projectDir, com.itsaky.androidide.utils.Environment.PLUGIN_API_JAR_RELATIVE_PATH)
          .exists()
  if (this is com.itsaky.androidide.projects.internal.ProjectManagerImpl) {
    this.pluginProjectCached = result
  }
  return result
}
