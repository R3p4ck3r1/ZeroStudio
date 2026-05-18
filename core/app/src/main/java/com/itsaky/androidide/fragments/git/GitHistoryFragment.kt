/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.fragments.git

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.catpuppyapp.puppygit.data.entity.RepoEntity
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Branch
import com.github.git24j.core.Oid
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitHistoryBinding
import com.itsaky.androidide.projects.IProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Git 历史记录页面。 */
class GitHistoryFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitHistoryBinding? = null
  private val binding
    get() = _binding!!

  private val commits = mutableListOf<CommitRow>()
  private val adapter = CommitAdapter(commits)
  private var selectedCommit: CommitRow? = null

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitHistoryBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      emitGitOperation("history", "refresh")
      loadCommits(limit = 150)
    }

    addToolbarAction(R.drawable.ic_cloud_download_24, getString(R.string.fetch)) {
      emitGitOperation("history", "fetch_origin")
      fetchOriginAndRefresh()
    }

    addToolbarAction(R.drawable.ic_filter_list_24, getString(R.string.filter)) {
      emitGitOperation("history", "filter_latest_200")
      loadCommits(limit = 200)
    }

    addToolbarAction(R.drawable.ic_content_copy_24, getString(R.string.copy_hash)) {
      emitGitOperation("history", "copy_hash")
      copySelectedHash()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.rvHistory.layoutManager = LinearLayoutManager(context)
    binding.rvHistory.adapter = adapter
    loadCommits(limit = 100)
    observeGitEvents()
  }

  private fun loadCommits(limit: Int) {
    withRepo { repo ->
      val merged = loadMergedLocalRemoteCommits(repo, limit)
      commits.clear()
      commits.addAll(merged)
      selectedCommit = commits.firstOrNull()
    }
  }

  private fun copySelectedHash() {
    val commit = selectedCommit
    if (commit == null) {
      Toast.makeText(context, "Select a commit first", Toast.LENGTH_SHORT).show()
      return
    }

    val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("git-hash", commit.hash))
    Toast.makeText(context, "Copied ${commit.shortHash}", Toast.LENGTH_SHORT).show()
  }

  private fun observeGitEvents() {
    val vm =
        androidx.lifecycle.ViewModelProvider(requireActivity())[GitUiEventViewModel::class.java]
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
        vm.events.collect { event ->
          if (event is GitUiEvent.Operation && event.section == "changes") {
            loadCommits(limit = 150)
          }
        }
      }
    }
  }

  private fun fetchOriginAndRefresh() {
    val ctx = context ?: return
    GitCredentialManager.ensureConfigured(ctx) { cfg ->
      withRepo { repo ->
        val remote = Libgit2Helper.resolveRemote(repo, "origin")
        if (remote == null) {
          throw IllegalStateException("Remote origin not found")
        }

        val repoPath = repo.workdir() ?: throw IllegalStateException("Repository workdir is null")
        val repoEntity =
            RepoEntity(
                repoName = java.io.File(repoPath).name,
                fullSavePath = repoPath,
                branch = repo.head()?.shorthand().orEmpty(),
            )
        Libgit2Helper.fetchRemoteForRepo(
            repo = repo,
            remoteName = "origin",
            credential = GitCredentialManager.toHttpCredential(cfg),
            repoFromDb = repoEntity,
        )
      }
      loadCommits(limit = 150)
    }
  }

  private fun resolveStatusBadge(hasLocal: Boolean, hasRemote: Boolean): String =
      when {
        hasLocal && hasRemote -> "SYNCED"
        hasLocal -> "LOCAL_ONLY"
        hasRemote -> "REMOTE_ONLY"
        else -> "UNKNOWN"
      }

  private fun loadMergedLocalRemoteCommits(repo: Repository, limit: Int): List<CommitRow> {
    val settings = SettingsUtil.getSettingsSnapshot()
    val localHead = repo.head()?.id() ?: return emptyList()
    val branch = repo.head()?.shorthand()?.removePrefix("refs/heads/").orEmpty()
    val remoteBranchShort = "origin/$branch"

    val remoteHeadOid =
        Libgit2Helper.getBranchList(repo)
            .firstOrNull {
              it.type == Branch.BranchType.REMOTE && it.shortName == remoteBranchShort
            }
            ?.oidStr
            ?.takeIf { it.isNotBlank() }
            ?.let { Oid.of(it) }

    val merged = linkedMapOf<String, Pair<CommitRow, Pair<Boolean, Boolean>>>()

    collectCommits(repo, localHead, limit, settings).forEach { row ->
      merged[row.hash] = row to (true to false)
    }

    remoteHeadOid?.let { remoteOid ->
      collectCommits(repo, remoteOid, limit, settings).forEach { row ->
        val old = merged[row.hash]
        if (old == null) {
          merged[row.hash] = row.copy(statusBadge = "REMOTE_ONLY") to (false to true)
        } else {
          merged[row.hash] = old.first.copy(statusBadge = "SYNCED") to (true to true)
        }
      }
    }

    return merged.values
        .map { (row, flags) ->
          row.copy(statusBadge = resolveStatusBadge(flags.first, flags.second))
        }
        .take(limit)
  }

  private fun collectCommits(
      repo: Repository,
      startOid: Oid,
      limit: Int,
      settings: com.catpuppyapp.puppygit.settings.AppSettings,
  ): List<CommitRow> {
    val rw = Libgit2Helper.createRevwalk(repo, startOid) ?: return emptyList()
    val list = mutableListOf<CommitRow>()
    var count = 0
    var next = rw.next()
    while (next != null && count < limit) {
      val dto =
          Libgit2Helper.getSingleCommitSimple(
              repo = repo,
              repoId = "",
              commitOidStr = next.toString(),
              settings = settings,
          )
      list.add(
          CommitRow(
              hash = dto.oidStr,
              shortHash = dto.shortOidStr,
              subject = dto.shortMsg,
              author = dto.author,
              statusBadge = "LOCAL_ONLY",
              message = dto.msg,
          )
      )
      next = rw.next()
      count++
    }
    return list
  }

  private fun withRepo(action: (Repository) -> Unit) {
    val projectDir =
        IProjectManager.getInstance().getWorkspace()?.getProjectDir()?.path
            ?: IProjectManager.getInstance().projectDirPath
    if (projectDir.isBlank()) {
      Toast.makeText(context, "No opened project", Toast.LENGTH_SHORT).show()
      return
    }

    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val ret = runCatching { Repository.open(projectDir).use(action) }
      withContext(Dispatchers.Main) {
        ret.onSuccess {
          adapter.notifyDataSetChanged()
          Toast.makeText(context, "History loaded", Toast.LENGTH_SHORT).show()
        }
        ret.onFailure {
          Toast.makeText(context, it.localizedMessage ?: "Load history failed", Toast.LENGTH_LONG)
              .show()
        }
      }
    }
  }

  private data class CommitRow(
      val hash: String,
      val shortHash: String,
      val subject: String,
      val author: String,
      val statusBadge: String,
      val message: String,
  )

  private inner class CommitAdapter(private val data: List<CommitRow>) :
      RecyclerView.Adapter<CommitAdapter.VH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
      val view =
          LayoutInflater.from(parent.context)
              .inflate(android.R.layout.simple_list_item_2, parent, false)
      return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
      val item = data[position]
      val active = selectedCommit?.hash == item.hash
      holder.title.text =
          if (active) "✓ ${item.shortHash} ${item.subject}" else "${item.shortHash} ${item.subject}"
      holder.subtitle.text = "[${item.statusBadge}] ${item.author}"
      holder.itemView.setOnClickListener {
        selectedCommit = item
        notifyDataSetChanged()
        showCommitDetails(item)
      }
    }

    override fun getItemCount(): Int = data.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
      val title: TextView = view.findViewById(android.R.id.text1)
      val subtitle: TextView = view.findViewById(android.R.id.text2)
    }
  }

  private fun showCommitDetails(item: CommitRow) {
    AlertDialog.Builder(requireContext())
        .setTitle(item.shortHash)
        .setMessage("Status: ${item.statusBadge}\nAuthor: ${item.author}\n\n${item.message}")
        .setPositiveButton("Open Diff") { _, _ ->
          GitSharedState.openDiffForCommit(item.hash)
          androidx.lifecycle
              .ViewModelProvider(requireActivity())[GitUiEventViewModel::class.java]
              .emit(GitUiEvent.OpenDiff(item.hash))
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
  }

  override fun onDestroyView() {
    binding.rvHistory.adapter = null
    selectedCommit = null
    commits.clear()
    _binding = null
    super.onDestroyView()
  }
}
