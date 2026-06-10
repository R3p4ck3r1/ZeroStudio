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
package com.itsaky.androidide.fragments.git

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.catpuppyapp.puppygit.data.entity.RepoEntity
import com.catpuppyapp.puppygit.utils.AppModel
import com.github.git24j.core.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * workspace 项目路径 ↔ puppygit [RepoEntity.id] 映射。
 *
 * puppygit 屏幕（[com.catpuppyapp.puppygit.screen.BranchListScreen] 等）
 * 都用 `repoId: String` 标识仓库；而 AndroidIDE 现有的 git UI 全部以
 * "工作区当前打开的项目" 为单位。需要这个 bridge 把"打开的项目路径"
 * 翻译成 puppygit 能识别的 id。
 *
 * 行为：
 *  1. 第一次调用时 puppygit 可能还没初始化（`AppModel.dbContainer`
 *     lateinit）；调用方应先调 [PuppyGitIntegration.ensureReady] 再调
 *     本 helper——但 [resolveForWorkdir] 内部也会校验并抛清晰异常。
 *  2. puppygit DB 中已存在该路径 → 复用现有 id（无 IO 写）。
 *  3. 不存在 → 临时 upsert 一条 [RepoEntity]，返回新 id。
 *
 * @author android_zero
 */
object RepoIdResolver {

  /**
   * 把 [workdir]（绝对路径）解析为 puppygit DB 中的 `repoId`。
   *
   * 抛 [IllegalStateException] 当：
   *  - `workdir` 为空
   *  - puppygit 运行时未初始化（[AppModel.dbContainer] 还没设好）
   *  - puppygit DB IO 失败
   */
  suspend fun resolveForWorkdir(workdir: String): String = withContext(Dispatchers.IO) {
    require(workdir.isNotBlank()) { "workdir must not be blank" }
    val container = AppModel.dbContainer  // lateinit，若未初始化会抛 UninitializedPropertyAccessException
    val repoRepo = container.repoRepository

    // 已注册？直接返回 id
    val existing = repoRepo.getByFullSavePath(
      fullSavePath = workdir,
      onlyReturnReadyRepo = false,
      requireSyncRepoInfoWithGit = false,
    )
    if (existing != null) return@withContext existing.id

    // 未注册 → upsert
    val name = File(workdir).name.ifBlank { "repo" }
    val branch = runCatching {
      Repository.open(workdir).use { repo -> repo.head()?.shorthand().orEmpty() }
    }.getOrDefault("")
    val entity = RepoEntity(
      repoName = name,
      fullSavePath = workdir,
      branch = branch,
    )
    repoRepo.insert(entity)

    // 重新读一次拿 id（entity 的 id 在构造时已 auto-gen，但 puppygit 的
    // `insert` 流程可能会改名做去重；稳妥起见再查一次）
    val re = repoRepo.getByFullSavePath(
      fullSavePath = workdir,
      onlyReturnReadyRepo = false,
      requireSyncRepoInfoWithGit = false,
    )
    re?.id ?: error("RepoIdResolver: upsert succeeded but read-back failed for $workdir")
  }

  /**
   * Composable 包装。在 [workdir] 变化时自动 re-resolve，把结果存入
   * [remember] 的 [androidx.compose.runtime.MutableState] 里返回。
   *
   * 用法：
   * ```
   * val repoId = RepoIdResolver.rememberRepoIdForWorkdir(workdir)
   * if (repoId != null) {
   *   BranchListScreen(repoId = repoId, naviUp = { false })
   * }
   * ```
   *
   * @return repoId（首次成功解析后非 null；解析失败或 workdir 空时为 null）
   */
  @Composable
  fun rememberRepoIdForWorkdir(workdir: String?): String? {
    // 读 LocalContext 是为了在 `setContent` 块内强制让本函数成为
    // @Composable（确保 PuppyGitIntegration.ensureReady 已经先跑过一次）
    val ctx = LocalContext.current
    val state = remember(workdir) { mutableStateOf<String?>(null) }
    LaunchedEffect(workdir) {
      if (workdir.isNullOrBlank()) {
        state.value = null
        return@LaunchedEffect
      }
      runCatching { resolveForWorkdir(workdir) }
        .onSuccess { state.value = it }
        .onFailure {
          // 解析失败时保持 null；调用方负责显示"未打开项目"占位
          state.value = null
        }
    }
    return state.value
  }
}
