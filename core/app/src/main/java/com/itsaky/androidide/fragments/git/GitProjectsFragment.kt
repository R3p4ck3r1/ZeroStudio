package com.itsaky.androidide.fragments.git

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.zero.studio.view.filetree.interfaces.FileClickListener
import android.zero.studio.view.filetree.interfaces.FileLongClickListener
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.provider.file
import android.zero.studio.view.filetree.widget.FileTree
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.itsaky.androidide.R
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.databinding.FragmentGitProjectsBinding
import com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
import com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
import com.itsaky.androidide.fragments.git.function.ZeroCloneDialogBottomSheetFragment
import com.itsaky.androidide.fragments.git.menu.GitBranchPopupManager
import com.itsaky.androidide.fragments.git.tree.ListProjectFilesRequestEvent
import com.itsaky.androidide.fragments.git.tree.TreeStateManager
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.preferences.internal.GeneralPreferences
import com.itsaky.androidide.provider.IDEFileIconProvider
import com.itsaky.androidide.viewmodel.FileTreeViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Git 项目侧边栏。
 *
 * @author android_zero
 */
class GitProjectsFragment : BaseGitPageFragment(), FileClickListener, FileLongClickListener {

  private var _binding: FragmentGitProjectsBinding? = null
  private val binding
    get() = _binding!!

  private var fileTreeView: FileTree? = null
  private var loadingJob: Job? = null
  private var tvCurrentBranch: android.widget.TextView? = null
  private var branchPopupManager: GitBranchPopupManager? = null

  private val viewModel: FileTreeViewModel by viewModels({ requireActivity() })
  private var stateManager = TreeStateManager()

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitProjectsBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onStart() {
    super.onStart()
    if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
  }

  override fun onStop() {
    super.onStop()
    if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
    // 自动保存状态
    if (GeneralPreferences.treeRememberExpandedState) {
      fileTreeView?.let { viewModel.saveState(it) }
    }
  }

  override fun setupToolbar() {
    val ctx = context ?: return

    val branchView = LayoutInflater.from(ctx).inflate(R.layout.item_git_toolbar_branch, null)
    tvCurrentBranch = branchView.findViewById(R.id.tv_current_branch)
    updateCurrentBranchName("main")
    tvCurrentBranch?.setOnClickListener {
      if (branchPopupManager == null) {
        branchPopupManager = GitBranchPopupManager(ctx) { name -> updateCurrentBranchName(name) }
      }
      branchPopupManager?.show(it)
    }
    addToolbarCustomView(branchView)

    // 刷新
    addToolbarAction(R.drawable.ic_refresh_file_24dp, getString(R.string.refresh)) {
      if (GeneralPreferences.treeRememberExpandedState) {
        fileTreeView?.reloadFileTreeSilently()
      } else {
        listProjectFiles()
      }
      Toast.makeText(context, "Refreshed silently", Toast.LENGTH_SHORT).show()
    }

    // 定位文件
    addToolbarAction(R.drawable.ic_target_positioning_24dp, "Locate Current File") {
      val activity = context as? EditorHandlerActivity
      val currentFile = activity?.getCurrentEditor()?.file
      if (currentFile != null && currentFile.exists()) {
        fileTreeView?.locateFileAndScroll(currentFile.absolutePath)
      } else {
        Toast.makeText(context, "No active file in editor", Toast.LENGTH_SHORT).show()
      }
    }

    // 展开全部 / 折叠全部 (长按清除记忆)
    val btnCollapse =
        addToolbarAction(R.drawable.ic_chevron_right, "Collapse All") {
          fileTreeView?.let {
            it.collapseAll()
          }
        }
    btnCollapse.setOnLongClickListener {
      fileTreeView?.let {
        stateManager = TreeStateManager()
        it.collapseAll()
        viewModel.treeState.value = ""
      }
      Toast.makeText(context, "Cleared memory and collapsed all", Toast.LENGTH_SHORT).show()
      true
    }

    addToolbarAction(R.drawable.ic_chevron_down, "Expand All") {
      fileTreeView?.let {
        it.expandAll()
      }
    }

    // 撤销 / 重做节点状态
    addToolbarAction(R.drawable.ic_undo, "Undo Node Action") {
      fileTreeView?.let { stateManager.undo(it) }
    }
    addToolbarAction(R.drawable.ic_redo, "Redo Node Action") {
      fileTreeView?.let { stateManager.redo(it) }
    }

    // Git 操作...
    addToolbarAction(R.drawable.ic_cloud_download_24, "Fetch Origin") { fetchOrigin() }
    addToolbarAction(R.drawable.ic_arrow_upward_24, "Push Origin") { pushOrigin() }
    addToolbarAction(R.drawable.ic_git_clone_24dp, getString(R.string.git_clone)) {
      ZeroCloneDialogBottomSheetFragment.newInstance(repoId = "")
          .show(childFragmentManager, "GitProjectsCloneBottomSheet")
    }
    addToolbarAction(R.drawable.ic_check_24, "Quick Commit") {
      emitGitOperation("project", "open_commit_page")
      Toast.makeText(
              context,
              "Use Changes page commit panel; history/diff will auto-sync after commit.",
              Toast.LENGTH_SHORT,
          )
          .show()
    }
  }

  private fun fetchOrigin() {
    val ctx = context ?: return
    GitCredentialManager.ensureConfigured(ctx) { cfg ->
      withRepo(
          action = { repo ->
            if (Libgit2Helper.resolveRemote(repo, "origin") == null) {
              throw IllegalStateException("Remote origin not found")
            }
            val repoEntity =
                com.catpuppyapp.puppygit.data.entity.RepoEntity(
                    repoName =
                        File(
                                repo.workdir()
                                    ?: throw IllegalStateException("Repository workdir is null")
                            )
                            .name,
                    fullSavePath =
                        repo.workdir() ?: throw IllegalStateException("Repository workdir is null"),
                    branch = repo.head()?.shorthand().orEmpty(),
                )
            Libgit2Helper.fetchRemoteForRepo(
                repo = repo,
                remoteName = "origin",
                credential = GitCredentialManager.toHttpCredential(cfg),
                repoFromDb = repoEntity,
            )
          },
          successTip = "Fetched origin",
      )
    }
  }

  private fun pushOrigin() {
    val ctx = context ?: return
    GitCredentialManager.ensureConfigured(ctx) { cfg ->
      withRepo(
          action = { repo ->
            if (Libgit2Helper.resolveRemote(repo, "origin") == null) {
              throw IllegalStateException("Remote origin not found")
            }
            val branch =
                repo.head()?.shorthand()?.removePrefix("refs/heads/")?.ifBlank { "main" } ?: "main"
            val refspec = "refs/heads/$branch:refs/heads/$branch"
            Libgit2Helper.push(
                repo,
                "origin",
                listOf(refspec),
                GitCredentialManager.toHttpCredential(cfg),
                false,
            )
          },
          successTip = "Pushed origin",
      )
    }
  }

  private fun withRepo(action: (com.github.git24j.core.Repository) -> Unit, successTip: String) {
    val projectDir = IProjectManager.getInstance().getWorkspace()?.getProjectDir()?.path
    val repoPath =
        projectDir?.takeIf { it.isNotBlank() } ?: IProjectManager.getInstance().projectDirPath
    if (repoPath.isBlank()) {
      Toast.makeText(context, "No opened project", Toast.LENGTH_SHORT).show()
      return
    }

    CoroutineScope(Dispatchers.IO).launch {
      val ret = runCatching { com.github.git24j.core.Repository.open(repoPath).use(action) }
      withContext(Dispatchers.Main) {
        ret.onSuccess { Toast.makeText(context, successTip, Toast.LENGTH_SHORT).show() }
        ret.onFailure {
          Toast.makeText(context, it.localizedMessage ?: "Git operation failed", Toast.LENGTH_LONG)
              .show()
        }
      }
    }
  }

  private fun updateCurrentBranchName(name: String) {
    tvCurrentBranch?.text = name
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    view.post { listProjectFiles() }
  }

  private fun listProjectFiles() {
    if (loadingJob?.isActive == true) return

    loadingJob =
        CoroutineScope(Dispatchers.Main).launch {
          setLoading(true)
          val root =
              withContext(Dispatchers.IO) {
                IProjectManager.getInstance()
                    .projectDirPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
              }

          if (root == null || !root.exists()) {
            binding.treeContainer.removeAllViews()
            binding.tvEmpty.isVisible = true
            binding.tvEmpty.text = "No project opened"
            setLoading(false)
            return@launch
          }

          binding.tvEmpty.isVisible = false
          setupFileTree(requireContext(), root)
          setLoading(false)
        }
  }

  private fun setupFileTree(ctx: Context, projectRoot: File) {
    val tree =
        (fileTreeView ?: FileTree(ctx).also { fileTreeView = it }).apply {
          setIconProvider(IDEFileIconProvider(ctx))
          setOnFileClickListener(this@GitProjectsFragment)
          setOnFileLongClickListener(this@GitProjectsFragment)
          setAutoExpandSingleChildFolders(GeneralPreferences.treeAutoExpandSingleChild)
          loadFiles(file(projectRoot), true)
        }

    if (tree.parent == null) {
      binding.treeContainer.removeAllViews()
      binding.treeContainer.addView(
          tree,
          ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
          ),
      )

      // 恢复状态
      if (GeneralPreferences.treeRememberExpandedState) {
        tree.post { tree.restoreState(viewModel.savedState) }
      }
    }
  }

  private fun setLoading(loading: Boolean) {
    binding.loading.isVisible = loading
    binding.horizontalScroll.isVisible = !loading
  }

  override fun onClick(node: Node<FileObject>) {
    if (node.value.isDirectory()) {
      stateManager.recordAction(node.value.getAbsolutePath(), node.isExpand)
    }

    val target = IDEFileIconProvider.extractNativeFile(node.value) ?: return
    if (target.isFile) {
      val event = FileClickEvent(target)
      event.put(Context::class.java, requireContext())
      EventBus.getDefault().post(event)
    }
  }

  override fun onLongClick(node: Node<FileObject>) {
    val target = IDEFileIconProvider.extractNativeFile(node.value) ?: return
    val event = FileLongClickEvent(target)
    event.put(Context::class.java, requireContext())
    event.put(Node::class.java, node)
    EventBus.getDefault().post(event)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onListProjectFilesRequest(event: ListProjectFilesRequestEvent?) {
    if (GeneralPreferences.treeRememberExpandedState) {
      fileTreeView?.reloadFileTreeSilently()
    } else {
      listProjectFiles()
    }
  }

  override fun onDestroyView() {
    loadingJob?.cancel()
    loadingJob = null
    tvCurrentBranch?.setOnClickListener(null)
    tvCurrentBranch = null
    branchPopupManager?.dismiss()
    branchPopupManager = null
    fileTreeView = null
    _binding = null
    super.onDestroyView()
  }
}
