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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.catpuppyapp.puppygit.constants.Cons
import com.catpuppyapp.puppygit.screen.TreeToTreeChangeListScreen
import com.itsaky.androidide.databinding.FragmentGitDiffComposeBinding

/**
 * 2a2-A 迁移: Git Diff 查看页面,改用 puppygit 的 TreeToTreeChangeListScreen 渲染。
 *
 * 本页面只展示 workdir 变更(Index vs Worktree),commit-vs-commit diff 在 commit list
 * 内点击 commit 后由 puppygit 自己的导航处理。
 *
 * 工具栏由 puppygit 的 TopAppBar 提供,无需自定义 toolbar。
 *
 * @author android_zero
 */
class GitDiffFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitDiffComposeBinding? = null
  private val binding
    get() = _binding!!

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitDiffComposeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    // puppygit 的 TreeToTreeChangeListScreen 自带 TopAppBar,这里不添加自定义按钮
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()

    val compose = setGitContent {
      val repoId = RepoIdResolver.rememberRepoIdForWorkdir(workdir)
      if (repoId != null) {
        // Index vs Worktree:显示未暂存变更
        TreeToTreeChangeListScreen(
          repoId = repoId,
          commit1OidStrCacheKey = Cons.git_IndexCommitHash,
          commit2OidStrCacheKey = Cons.git_LocalWorktreeCommitHash,
          commitForQueryParentsCacheKey = Cons.git_LocalWorktreeCommitHash,
          titleCacheKey = "Workdir Changes",
          naviUp = { false },
        )
      } else {
        Box(modifier = Modifier.fillMaxSize().padding(all = 16.dp)) {
          Text(
            text = workdir?.let { "Resolving repo…" } ?: "No opened project",
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
    binding.gitContentContainer.addView(compose)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
