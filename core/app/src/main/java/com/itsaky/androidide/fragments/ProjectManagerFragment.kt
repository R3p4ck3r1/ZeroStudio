package com.itsaky.androidide.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.itsaky.androidide.utils.Environment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProjectManagerFragment : BaseFragment() {

  private data class ProjectTab(val title: String, val filePath: String? = null, val treeUri: Uri? = null)

  private val tabState = mutableStateListOf<ProjectTab>()

  private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri ?: return@registerForActivityResult
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    runCatching { requireContext().contentResolver.takePersistableUriPermission(uri, flags) }
    val tabName = DocumentFile.fromTreeUri(requireContext(), uri)?.name ?: "Folder"
    tabState.add(ProjectTab(title = tabName, treeUri = uri))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (tabState.isEmpty()) {
      tabState.add(
          ProjectTab(
              title = Environment.PROJECTS_FOLDER,
              filePath = Environment.PROJECTS_DIR.absolutePath,
          ))
    }
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent { MaterialTheme { ProjectManagerScreen() } }
    }
  }

  @Composable
  private fun ProjectManagerScreen() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    if (selectedTabIndex > tabState.lastIndex) {
      selectedTabIndex = 0
    }

    val selectedTab = tabState.getOrNull(selectedTabIndex)
    val projects by produceState(initialValue = emptyList<String>(), selectedTab) {
      val tab = selectedTab
      value = if (tab == null) emptyList() else loadProjects(tab)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ScrollableTabRow(selectedTabIndex = selectedTabIndex, modifier = Modifier.weight(1f)) {
          tabState.forEachIndexed { index, tab ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { selectedTabIndex = index },
                text = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
          }
        }
        FloatingActionButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.padding(start = 8.dp)) {
          Icon(Icons.Default.Add, contentDescription = "Add folder")
        }
      }

      if (selectedTab == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
        return
      }

      LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(projects, key = { it }) { projectName ->
          Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Folder, contentDescription = null)
              Text(text = projectName, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium)
            }
          }
        }
      }
    }
  }

  private suspend fun loadProjects(tab: ProjectTab): List<String> = withContext(Dispatchers.IO) {
    if (tab.filePath != null) {
      File(tab.filePath)
          .listFiles { file -> file.isDirectory }
          ?.asSequence()
          ?.sortedBy { it.name.lowercase() }
          ?.map { it.name }
          ?.toList()
          .orEmpty()
    } else {
      val uri = tab.treeUri ?: return@withContext emptyList()
      DocumentFile.fromTreeUri(requireContext(), uri)
          ?.listFiles()
          ?.asSequence()
          ?.filter { it.isDirectory }
          ?.mapNotNull { it.name }
          ?.sortedBy { it.lowercase() }
          ?.toList()
          .orEmpty()
    }
  }
}
