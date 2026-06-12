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
import android.widget.Toast
import com.itsaky.androidide.R

/**
 * Pull Requests 列表页面 (web 链接到 GitHub/GitLab/Gitee)。
 *
 * 2a2-B 简化:删除内嵌 RecyclerView Adapter (与工具栏按钮功能重复),
 * 改用工具栏按钮直接打开对应的 web 链接。
 *
 * @author android_zero
 */
class GitPullRequestsFragment : BaseGitPageFragment() {

  private var links: GitHostLinks? = null

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    // 2a2-B: 复用通用的 fragment_git_branches 骨架(空容器即可,本 fragment 不用 RecyclerView)
    return inflater.inflate(R.layout.fragment_git_branches, container, false)
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_add_24, "Open Pull Requests") {
      emitGitOperation("pull_requests", "open_pr_page")
      openWebLinkOrToast(
        links?.pullRequestsUrl ?: links?.mergeRequestsUrl,
        "No remote repository detected",
      )
    }

    addToolbarAction(R.drawable.ic_filter_list_24, "Open Merge Requests") {
      emitGitOperation("pull_requests", "open_mr_page")
      openWebLinkOrToast(
        links?.mergeRequestsUrl ?: links?.pullRequestsUrl,
        "No remote repository detected",
      )
    }

    addToolbarAction(R.drawable.ic_check_24, "New Task (Issue)") {
      emitGitOperation("pull_requests", "create_issue")
      val target = links?.newTaskUrl(title = "Code review task", body = "Created from AndroidIDE")
      openWebLinkOrToast(target, "No remote repository detected")
    }

    addToolbarAction(R.drawable.ic_refresh_24, "Refresh") {
      links = GitHostWebLinks.resolveForCurrentProject()
      emitGitOperation("pull_requests", "refresh_remote_links")
      val msg = if (links != null) "Remote links refreshed" else "No remote repository detected"
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    links = GitHostWebLinks.resolveForCurrentProject()
  }
}
