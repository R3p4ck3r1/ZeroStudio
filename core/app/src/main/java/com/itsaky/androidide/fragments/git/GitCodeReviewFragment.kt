/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.fragments.git

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.itsaky.androidide.R

/**
 * 代码审查页面 (web 链接到 GitHub/GitLab/Gitee 的 review/diff 页面)。
 *
 * 2a2-B 简化:删除重复的 `openIfAny` 私有方法,复用 BaseGitPageFragment 的 `openWebLinkOrToast`。
 *
 * @author android_zero
 */
class GitCodeReviewFragment : BaseGitPageFragment() {

  private var links: GitHostLinks? = null

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    return inflater.inflate(R.layout.fragment_git_branches, container, false)
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_check_24, "Open Review Page") {
      emitGitOperation("code_review", "open_review_page")
      openWebLinkOrToast(
        links?.pullRequestsUrl ?: links?.mergeRequestsUrl,
        "No remote repository detected",
      )
    }

    addToolbarAction(R.drawable.ic_info_24, "Open Diffs") {
      emitGitOperation("code_review", "open_diffs_page")
      openWebLinkOrToast(
        links?.pullRequestsUrl ?: links?.mergeRequestsUrl,
        "No remote repository detected",
      )
    }

    addToolbarAction(R.drawable.ic_add_24, "New Review Task") {
      emitGitOperation("code_review", "new_review_task")
      openWebLinkOrToast(
        links?.newTaskUrl("Code Review Task", "Created from AndroidIDE code review page"),
        "No remote repository detected",
      )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    links = GitHostWebLinks.resolveForCurrentProject()
  }
}
