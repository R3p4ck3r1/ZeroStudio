/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.fragments.git

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.itsaky.androidide.R

/**
 * CD/CI 流水线状态页面 (web 链接到 GitHub Actions / GitLab Pipelines / Gitee)。
 *
 * 2a2-B 简化:
 * - 删除内嵌 RecyclerView Adapter (与工具栏按钮功能重复)
 * - 删除 `showRunWorkflowDialog` 自定义对话框(用户体验差,改用纯 URL 触发)
 * - `openIfAny` 私有方法 → 复用 BaseGitPageFragment 的 `openWebLinkOrToast`
 *
 * @author android_zero
 */
class GitPipelinesFragment : BaseGitPageFragment() {

  private var links: GitHostLinks? = null

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    return inflater.inflate(R.layout.fragment_git_branches, container, false)
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_info_24, "Open Pipelines") {
      emitGitOperation("pipelines", "open_pipelines")
      openWebLinkOrToast(links?.pipelinesUrl, "No remote repository detected")
    }

    addToolbarAction(R.drawable.ic_check_24, "Open Actions") {
      emitGitOperation("pipelines", "open_actions")
      openWebLinkOrToast(links?.actionsUrl, "No workflow URL detected")
    }

    addToolbarAction(R.drawable.ic_add_24, "Run Workflow on Branch") {
      emitGitOperation("pipelines", "run_workflow")
      val target = links
      if (target == null) {
        Toast.makeText(context, "No remote repository detected", Toast.LENGTH_SHORT).show()
        return@addToolbarAction
      }
      // 简化: 跳转到默认 workflow 页面,不弹自定义 dialog
      val ref = GitHostWebLinks.getCurrentBranchName()
      openWebLinkOrToast(target.workflowRunUrl(yamlFile = "ci.yml", ref = ref))
    }

    addToolbarAction(R.drawable.ic_refresh_24, "Refresh") {
      links = GitHostWebLinks.resolveForCurrentProject()
      emitGitOperation("pipelines", "refresh_remote_links")
      val msg = if (links != null) "Pipeline links refreshed" else "No remote repository detected"
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    links = GitHostWebLinks.resolveForCurrentProject()
  }
}
