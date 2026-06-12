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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.catpuppyapp.puppygit.screen.BranchListScreen
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitBranchesComposeBinding
import com.itsaky.androidide.projects.IProjectManager

/**
 * 分支管理页面（2a2 版本）。
 *
 * 2a1 之后：body 改为承载 puppygit `BranchListScreen` Composable。
 * 2a2 之后：RecyclerView + 手撸 loadBranches / createBranchFromHead /
 * checkoutSelectedBranch 全部删除；不再直接 import `Libgit2Helper` /
 * `git24j.core.*`。
 *
 * 顶部 mini-toolbar 的三个按钮（refresh / new_branch / checkout）暂时
 * **保留**但只 emit `GitUiEvent.Operation` 事件，**不**实际执行 git 操作
 * ——实际功能（新建 / 切换 / 删除）由 [BranchListScreen] 自带的
 * 长按菜单 + FAB 完整覆盖。2b 阶段会重新设计工具栏。
 *
 * @author android_zero
 */
class GitBranchesFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitBranchesComposeBinding? = null
  private val binding
    get() = _binding!!

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitBranchesComposeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()
    val compose = setGitContent {
      val repoId = RepoIdResolver.rememberRepoIdForWorkdir(workdir)
      if (repoId != null) {
        // 这里是 tab 内部，没有"向上"导航——naviUp 永远 false，
        // 让 BranchListScreen 知道它不是单独一屏（不要 pop）。
        BranchListScreen(repoId = repoId, naviUp = { false })
      } else {
        // 解析失败或无打开项目：显示占位
        Text(
          text = workdir?.let { "Resolving repo…" } ?: "No opened project",
          modifier = Modifier.padding(16.dp),
        )
      }
    }
    binding.gitContentContainer.addView(compose)
  }

  override fun setupToolbar() {
    // 保留三按钮（refresh / new_branch / checkout）。2b 阶段会重新设计。
    // 当前实现：仅 emit Operation 事件；实际 git 操作由 BranchListScreen
    // 自带 UI 完成。
    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      emitGitOperation("branches", "refresh")
    }

    addToolbarAction(R.drawable.ic_add_24, getString(R.string.new_branch)) {
      emitGitOperation("branches", "create_branch_dialog")
    }

    addToolbarAction(R.drawable.ic_call_split_24, getString(R.string.checkout)) {
      emitGitOperation("branches", "checkout_selected")
    }
  }

  override fun onDestroyView() {
    binding.gitContentContainer.removeAllViews()
    super.onDestroyView()
    _binding = null
  }

  private fun resolveWorkspaceDirPath(): String? {
    val projectManager = IProjectManager.getInstance()
    val workspaceDir =
        runCatching { projectManager.getWorkspace()?.getProjectDir()?.path }.getOrNull()
    if (!workspaceDir.isNullOrBlank()) {
      return workspaceDir
    }
    return runCatching { projectManager.projectDirPath }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
  }

  private fun emitGitOperation(section: String, action: String) {
    val activity = activity as? FragmentActivity ?: return
    ViewModelProvider(activity)[GitUiEventViewModel::class.java]
        .emit(GitUiEvent.Operation(section = section, action = action))
  }
}
