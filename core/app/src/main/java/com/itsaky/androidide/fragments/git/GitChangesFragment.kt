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
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.catpuppyapp.puppygit.data.entity.RepoEntity
import com.catpuppyapp.puppygit.git.StatusTypeEntrySaver
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitChangesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 变更与提交页面。 */
class GitChangesFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitChangesBinding? = null
  private val binding
    get() = _binding!!

  private val rows = mutableListOf<ChangeRow>()
  private val selectedPaths = mutableSetOf<String>()
  private val adapter = ChangeAdapter(rows)
  private var watchJob: Job? = null
  private var lastSnapshotSignature: String? = null

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitChangesBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    // 2b 重新设计：分组组织按钮，去掉冗余操作
    //
    // 分组1: Staged 操作区 (提交已暂存内容)
    addToolbarAction(R.drawable.ic_check_24, getString(R.string.commit)) {
      emitGitOperation("changes", "commit")
      commitChanges()
    }

    // 分组分隔线
    addToolbarSeparator()

    // 分组2: Remote 操作区 (推送/拉取)
    // Push - 使用 ic_arrow_upward_24 (上传箭头，区别于 Pull 的云下载)
    addToolbarAction(R.drawable.ic_arrow_upward_24, getString(R.string.push)) {
      emitGitOperation("changes", "push")
      pushCurrentBranch(force = false)
    }

    // Pull - ic_cloud_download_24 (云下载图标)
    addToolbarAction(R.drawable.ic_cloud_download_24, getString(R.string.pull)) {
      emitGitOperation("changes", "pull_fetch_origin")
      pullFromOrigin()
    }

    // Force Push - 危险操作放最后
    addToolbarAction(R.drawable.ic_warning_24, "Force Push") {
      emitGitOperation("changes", "force_push")
      pushCurrentBranch(force = true)
    }

    // 分组分隔线
    addToolbarSeparator()

    // 分组3: Staging 操作区 (暂存/取消暂存/丢弃)
    addToolbarAction(R.drawable.ic_select_all_24, getString(R.string.stage_all)) {
      emitGitOperation("changes", "stage_all")
      stageAll()
    }

    addToolbarAction(R.drawable.ic_remove_circle_outline_24, getString(R.string.unstage)) {
      emitGitOperation("changes", "unstage_all")
      unstageAll()
    }

    addToolbarAction(R.drawable.ic_delete_sweep_24, getString(R.string.revert)) {
      emitGitOperation("changes", "discard_all")
      discardAll()
    }

    // 分组分隔线
    addToolbarSeparator()

    // 分组4: 刷新 (放在最后)
    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      emitGitOperation("changes", "refresh")
      loadChanges()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.rvChanges.layoutManager = LinearLayoutManager(context)
    binding.rvChanges.adapter = adapter
    binding.btnCommitAndPushInline.setOnClickListener {
      emitGitOperation("changes", "commit_and_push_inline")
      commitThenPush()
    }
    bindImeInsets()
    loadChanges(force = true)
  }

  override fun onStart() {
    super.onStart()
    startChangesWatcher()
  }

  override fun onStop() {
    watchJob?.cancel()
    watchJob = null
    super.onStop()
  }

  private fun loadChanges(force: Boolean = false) {
    val projectDir = resolveWorkspaceDirPath()
    if (projectDir == null) {
      Toast.makeText(context, "No opened project", Toast.LENGTH_SHORT).show()
      return
    }

    viewLifecycleOwner.lifecycleScope.launch {
      val ret = withContext(Dispatchers.IO) { runCatching { readChangeSnapshot(projectDir) } }

      ret.onSuccess {
        if (!force && it.signature == lastSnapshotSignature) {
          return@onSuccess
        }
        lastSnapshotSignature = it.signature
        selectedPaths.clear()
        rows.clear()
        rows.addAll(it.rows)
        adapter.notifyDataSetChanged()
      }
      ret.onFailure {
        Toast.makeText(context, it.localizedMessage ?: "Failed to load changes", Toast.LENGTH_LONG)
            .show()
      }
    }
  }

  private fun stageAll() {
    withRepo { repo ->
      val ret = Libgit2Helper.stageAll(repo, repoId = "")
      if (ret.hasError()) {
        throw RuntimeException(ret.msg)
      }
    }
  }

  private fun unstageAll() {
    withRepo { repo ->
      val (_, staged) =
          Libgit2Helper.checkIndexIsEmptyAndGetIndexList(
              repo = repo,
              repoId = "",
              onlyCheckEmpty = false,
          )
      val paths = staged.orEmpty().map { it.relativePathUnderRepo }
      if (paths.isEmpty()) {
        throw RuntimeException("No staged file")
      }
      Libgit2Helper.unStageItems(repo, paths)
    }
  }

  private fun discardAll() {
    withRepo { repo ->
      val ret = Libgit2Helper.resetHardToHead(repo)
      if (ret.hasError()) {
        throw RuntimeException(ret.msg)
      }
    }
  }

  private fun commitChanges(onSuccess: (() -> Unit)? = null) {
    val msg = binding.etCommitMessage.text.toString().trim()
    if (msg.isBlank()) {
      Toast.makeText(context, getString(R.string.please_input_commit_msg), Toast.LENGTH_SHORT)
          .show()
      return
    }

    val ctx = context ?: return
    GitCredentialManager.ensureConfigured(ctx) { cfg ->
      withRepo(
          action = { repo ->
            val settings = SettingsUtil.getSettingsSnapshot()
            val ret =
                Libgit2Helper.createCommit(
                    repo = repo,
                    msg = msg,
                    username = cfg.username,
                    email = cfg.email,
                    amend = binding.cbAmend.isChecked,
                    cleanRepoStateIfSuccess = true,
                    settings = settings,
                )
            if (ret.hasError()) {
              throw RuntimeException(ret.msg)
            }
          },
          onSuccess = onSuccess,
      )
    }
  }

  private fun commitThenPush() {
    commitChanges(onSuccess = { pushCurrentBranch(force = false) })
  }

  private fun pushCurrentBranch(force: Boolean) {
    val context = context ?: return
    GitCredentialManager.ensureConfigured(context) { cfg ->
      withRepo { repo ->
        if (Libgit2Helper.resolveRemote(repo, "origin") == null) {
          throw IllegalStateException("Remote origin not found")
        }

        val branch =
            repo.head()?.shorthand()?.removePrefix("refs/heads/")?.ifBlank { "main" } ?: "main"
        val hasLocalBranch =
            Libgit2Helper.getBranchList(repo).any {
              it.type == com.github.git24j.core.Branch.BranchType.LOCAL && it.shortName == branch
            }
        if (!hasLocalBranch) {
          throw IllegalStateException("Current branch '$branch' is invalid")
        }

        val refspec = "refs/heads/$branch:refs/heads/$branch"
        val credential = GitCredentialManager.toHttpCredential(cfg)
        Libgit2Helper.push(repo, "origin", listOf(refspec), credential, force)
      }
    }
  }

  private fun pullFromOrigin() {
    val context = context ?: return
    GitCredentialManager.ensureConfigured(context) { cfg ->
      withRepo { repo ->
        if (Libgit2Helper.resolveRemote(repo, "origin") == null) {
          throw IllegalStateException("Remote origin not found")
        }
        val workdir = repo.workdir() ?: throw IllegalStateException("Repository workdir is null")
        val repoEntity =
            RepoEntity(
                repoName = java.io.File(workdir).name,
                fullSavePath = workdir,
                branch = repo.head()?.shorthand().orEmpty(),
            )
        Libgit2Helper.fetchRemoteForRepo(
            repo = repo,
            remoteName = "origin",
            credential = GitCredentialManager.toHttpCredential(cfg),
            repoFromDb = repoEntity,
        )
      }
    }
  }

  private fun withRepo(onSuccess: (() -> Unit)? = null, action: (Repository) -> Unit) {
    val projectDir = resolveWorkspaceDirPath()
    if (projectDir == null) {
      Toast.makeText(context, "No opened project", Toast.LENGTH_SHORT).show()
      return
    }

    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val ret = runCatching { Repository.open(projectDir).use(action) }
      withContext(Dispatchers.Main) {
        ret.onSuccess {
          loadChanges(force = true)
          onSuccess?.invoke()
          Toast.makeText(context, "Git operation completed", Toast.LENGTH_SHORT).show()
        }
        ret.onFailure {
          Toast.makeText(context, it.localizedMessage ?: "Git operation failed", Toast.LENGTH_LONG)
              .show()
        }
      }
    }
  }

  private fun startChangesWatcher() {
    if (watchJob?.isActive == true) return
    watchJob =
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
          while (isActive) {
            val projectDir = resolveWorkspaceDirPath()
            if (projectDir != null) {
              runCatching { readChangeSnapshot(projectDir) }
                  .onSuccess { snapshot ->
                    if (snapshot.signature != lastSnapshotSignature && isAdded) {
                      withContext(Dispatchers.Main) {
                        if (!isAdded || view == null) return@withContext
                        lastSnapshotSignature = snapshot.signature
                        selectedPaths.clear()
                        rows.clear()
                        rows.addAll(snapshot.rows)
                        adapter.notifyDataSetChanged()
                      }
                    }
                  }
            }

            delay(1500)
          }
        }
  }

  private fun readChangeSnapshot(projectDir: String): ChangeSnapshot {
    return Repository.open(projectDir).use { repo ->
      val statusList = Libgit2Helper.getWorkdirStatusList(repo)
      val unstaged = Libgit2Helper.getWorktreeChangeList(repo, statusList, repoId = "")
      val (_, staged) =
          Libgit2Helper.checkIndexIsEmptyAndGetIndexList(
              repo = repo,
              repoId = "",
              onlyCheckEmpty = false,
          )

      val stagedRows = staged.orEmpty()
      val builtRows = buildRows(stagedRows, unstaged)
      val head = runCatching { repo.head()?.target()?.toString() ?: "" }.getOrDefault("")
      val signature = buildSignature(head, stagedRows, unstaged)
      ChangeSnapshot(rows = builtRows, signature = signature)
    }
  }

  private fun buildSignature(
      head: String,
      staged: List<StatusTypeEntrySaver>,
      unstaged: List<StatusTypeEntrySaver>,
  ): String {
    val stagedSig =
        staged.joinToString("|") { "${it.relativePathUnderRepo}:${it.changeType.orEmpty()}" }
    val unstagedSig =
        unstaged.joinToString("|") { "${it.relativePathUnderRepo}:${it.changeType.orEmpty()}" }
    return "$head#$stagedSig#$unstagedSig"
  }

  private fun bindImeInsets() {
    val commitCard = binding.cardCommitInput
    val defaultBottomMargin = (commitCard.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
      val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
      commitCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        bottomMargin = defaultBottomMargin + imeBottom
      }
      insets
    }
    ViewCompat.requestApplyInsets(binding.root)
  }

  private fun buildRows(
      staged: List<StatusTypeEntrySaver>,
      unstaged: List<StatusTypeEntrySaver>,
  ): List<ChangeRow> {
    val list = mutableListOf<ChangeRow>()
    list.add(ChangeRow.Header("Staged (${staged.size})"))
    list.addAll(staged.map { ChangeRow.Entry(it, true) })
    list.add(ChangeRow.Header("Unstaged (${unstaged.size})"))
    list.addAll(unstaged.map { ChangeRow.Entry(it, false) })
    return list
  }

  private sealed class ChangeRow {
    data class Header(val title: String) : ChangeRow()

    data class Entry(val item: StatusTypeEntrySaver, val staged: Boolean) : ChangeRow()
  }

  private data class ChangeSnapshot(
      val rows: List<ChangeRow>,
      val signature: String,
  )

  private inner class ChangeAdapter(private val data: List<ChangeRow>) :
      RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int =
        if (data[position] is ChangeRow.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
      return if (viewType == 0) {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
        HeaderVH(view)
      } else {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
        ItemVH(view)
      }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      when (val row = data[position]) {
        is ChangeRow.Header -> (holder as HeaderVH).title.text = row.title
        is ChangeRow.Entry -> (holder as ItemVH).bind(row)
      }
    }

    override fun getItemCount(): Int = data.size

    private inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
      val title: TextView = view.findViewById(android.R.id.text1)
    }

    private inner class ItemVH(view: View) : RecyclerView.ViewHolder(view) {
      private val title: TextView = view.findViewById(android.R.id.text1)
      private val subtitle: TextView = view.findViewById(android.R.id.text2)

      fun bind(row: ChangeRow.Entry) {
        val item = row.item
        val selected = selectedPaths.contains(item.relativePathUnderRepo)
        title.text = (if (selected) "☑ " else "☐ ") + item.relativePathUnderRepo
        val state = if (row.staged) "Staged" else "Unstaged"
        subtitle.text = "$state · ${item.changeType.orEmpty()}"

        itemView.setOnClickListener {
          selectedPaths.clear()
          selectedPaths.add(item.relativePathUnderRepo)
          notifyDataSetChanged()
          GitSharedState.openDiffForPath(item.relativePathUnderRepo)
          emitGitOperation("changes", "open_diff")
          Toast.makeText(context, "Open diff: ${item.relativePathUnderRepo}", Toast.LENGTH_SHORT)
              .show()
          (requireActivity() as? androidx.fragment.app.FragmentActivity)?.let {
            androidx.lifecycle
                .ViewModelProvider(it)[GitUiEventViewModel::class.java]
                .emit(GitUiEvent.OpenDiff(item.relativePathUnderRepo))
          }
        }

        itemView.setOnLongClickListener {
          if (row.staged) {
            withRepo { repo ->
              Libgit2Helper.unStageItems(repo, listOf(item.relativePathUnderRepo))
            }
          } else {
            withRepo { repo -> Libgit2Helper.stageStatusEntryAndWriteToDisk(repo, listOf(item)) }
          }
          true
        }
      }
    }
  }

  override fun onDestroyView() {
    binding.rvChanges.adapter = null
    selectedPaths.clear()
    rows.clear()
    super.onDestroyView()
    _binding = null
  }
}
