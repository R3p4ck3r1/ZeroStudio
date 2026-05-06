package com.itsaky.androidide.fragments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment as AndroidEnvironment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.viewModels
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ProjectManagerFragment : BaseFragment() {

  private data class ProjectTab(val title: String, val filePath: String? = null, val treeUri: Uri? = null) {
    fun stableKey(): String = filePath ?: treeUri.toString()
  }

  private val viewModel by viewModels<MainViewModel>(ownerProducer = { requireActivity() })
  private val tabState = mutableStateListOf<ProjectTab>()
  private val tabProjectsState = mutableStateMapOf<String, List<String>>()
  private val loadingState = mutableStateMapOf<String, Boolean>()

  private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri ?: return@registerForActivityResult
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    runCatching { requireContext().contentResolver.takePersistableUriPermission(uri, flags) }
    val tabName = DocumentFile.fromTreeUri(requireContext(), uri)?.name ?: "Folder"
    val filePath = tryResolvePathFromTreeUri(uri)
    val newTab = ProjectTab(title = tabName, filePath = filePath, treeUri = uri)
    if (tabState.none { it.stableKey() == newTab.stableKey() }) {
      tabState.add(newTab)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (tabState.isEmpty()) {
      tabState.add(ProjectTab(title = Environment.PROJECTS_FOLDER, filePath = Environment.PROJECTS_DIR.absolutePath))
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent { MaterialTheme { ProjectManagerScreen() } }
    }
  }

  @Composable
  private fun ProjectManagerScreen() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val scope = remember { CoroutineScope(Dispatchers.Main.immediate) }

    if (selectedTabIndex > tabState.lastIndex) selectedTabIndex = 0
    val selectedTab = tabState.getOrNull(selectedTabIndex)

    selectedTab?.let { tab ->
      val key = tab.stableKey()
      if (!tabProjectsState.containsKey(key) && loadingState[key] != true) {
        loadTabProjects(scope, tab, forceRefresh = false)
      }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ScrollableTabRow(selectedTabIndex = selectedTabIndex, modifier = Modifier.weight(1f)) {
          tabState.forEachIndexed { index, tab ->
            Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = {
              Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            })
          }
        }
        FloatingActionButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.padding(start = 8.dp)) {
          Icon(Icons.Default.Add, contentDescription = "Add folder")
        }
      }

      if (selectedTab == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
      }

      val key = selectedTab.stableKey()
      val projects = tabProjectsState[key].orEmpty()
      val isLoading = loadingState[key] == true

      PullToRefreshBox(isRefreshing = isLoading, onRefresh = { loadTabProjects(scope, selectedTab, forceRefresh = true) }) {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(projects, key = { it }) { projectPath ->
            val name = File(projectPath).name
            Card(
                modifier =
                    Modifier.fillMaxWidth().clickable {
                      viewModel.openProject(requireContext(), File(projectPath))
                    },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                  Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Text(text = name, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium)
                  }
                }
          }
        }
      }
    }
  }

  private fun loadTabProjects(scope: CoroutineScope, tab: ProjectTab, forceRefresh: Boolean) {
    val key = tab.stableKey()
    if (!forceRefresh) {
      val cached = readCache(requireContext(), key)
      if (cached.isNotEmpty()) {
        tabProjectsState[key] = cached
      }
    }

    scope.launch {
      loadingState[key] = true
      val merged = withContext(Dispatchers.IO) { scanAndMergeProjects(tab, tabProjectsState[key].orEmpty()) }
      tabProjectsState[key] = merged
      writeCache(requireContext(), key, merged)
      loadingState[key] = false
    }
  }

  private fun scanAndMergeProjects(tab: ProjectTab, current: List<String>): List<String> {
    val scanned = scanTopLevelProjects(tab)
    if (scanned.isEmpty()) return current.distinct().sortedBy { File(it).name.lowercase() }
    return (current + scanned).distinct().sortedBy { File(it).name.lowercase() }
  }

  private fun scanTopLevelProjects(tab: ProjectTab): List<String> {
    if (tab.filePath != null) {
      return File(tab.filePath)
          .listFiles { file -> file.isDirectory }
          ?.asSequence()
          ?.map { it.absolutePath }
          ?.distinct()
          ?.toList()
          .orEmpty()
    }

    val treeUri = tab.treeUri ?: return emptyList()
    return DocumentFile.fromTreeUri(requireContext(), treeUri)
        ?.listFiles()
        ?.asSequence()
        ?.filter { it.isDirectory }
        ?.mapNotNull { tryResolvePathFromTreeUri(it.uri) }
        ?.distinct()
        ?.toList()
        .orEmpty()
  }

  private fun readCache(context: Context, key: String): List<String> {
    val raw = context.getSharedPreferences("project_manager_cache", Context.MODE_PRIVATE).getString(key, null) ?: return emptyList()
    return runCatching {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val path = arr.optString(i)
          if (path.isNotBlank()) add(path)
        }
      }
    }.getOrDefault(emptyList()).distinct()
  }

  private fun writeCache(context: Context, key: String, data: List<String>) {
    val unique = data.distinct()
    val arr = JSONArray()
    unique.forEach { arr.put(it) }
    context.getSharedPreferences("project_manager_cache", Context.MODE_PRIVATE).edit().putString(key, arr.toString()).apply()
  }

  private fun tryResolvePathFromTreeUri(uri: Uri): String? {
    val docId = DocumentFile.fromTreeUri(requireContext(), uri)?.uri?.lastPathSegment ?: return null
    val normalized = docId.substringAfter(':', "")
    if (normalized.isBlank()) return null
    return File(AndroidEnvironment.getExternalStorageDirectory(), normalized).absolutePath
  }
}
