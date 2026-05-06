package com.itsaky.androidide.fragments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.system.Os
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.viewModels
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.viewmodel.MainViewModel
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ProjectManagerFragment : BaseFragment() {
  private data class ProjectTab(val title: String, val filePath: String? = null, val treeUri: Uri? = null) {
    fun stableKey(): String = filePath ?: treeUri.toString()
    fun rootPathOrNull(): String? = filePath ?: treeUri?.let { uriToPath(it) }
  }

  private data class ClipboardProject(val sourcePath: String)
  private data class ProjectAppMeta(val label: String? = null, val iconResName: String? = null, val iconFilePath: String? = null)

  private val viewModel by viewModels<MainViewModel>(ownerProducer = { requireActivity() })
  private val tabState = mutableStateListOf<ProjectTab>()
  private val tabProjectsState = mutableStateMapOf<String, List<String>>()
  private val loadingState = mutableStateMapOf<String, Boolean>()
  private val appMetaState = mutableStateMapOf<String, ProjectAppMeta>()
  private var clipboardState by mutableStateOf<ClipboardProject?>(null)
  private var selectedTabIndexState by mutableIntStateOf(0)

  private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri ?: return@registerForActivityResult
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    runCatching { requireContext().contentResolver.takePersistableUriPermission(uri, flags) }
    val tabName = DocumentFile.fromTreeUri(requireContext(), uri)?.name ?: getString(R.string.project_manager_folder)
    val filePath = uriToPath(uri)
    val tab = ProjectTab(tabName, filePath, uri)
    if (tabState.none { it.stableKey() == tab.stableKey() }) {
      tabState.add(tab)
      selectedTabIndexState = tabState.lastIndex
      persistTabs(requireContext())
      loadTabProjects(viewLifecycleScope, tab, true)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    restoreTabs(requireContext())
    if (tabState.isEmpty()) {
      tabState.add(ProjectTab(Environment.PROJECTS_FOLDER, Environment.PROJECTS_DIR.absolutePath))
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent { MaterialTheme { ProjectManagerScreen() } }
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun ProjectManagerScreen() {
    val scope = remember { viewLifecycleScope }
    var menuProjectPath by remember { mutableStateOf<String?>(null) }
    var showPropertiesFor by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val hasTabs = tabState.isNotEmpty()
    val safeIndex = if (hasTabs) selectedTabIndexState.coerceIn(0, tabState.lastIndex) else 0
    val selectedTab = if (hasTabs) tabState[safeIndex] else null

    LaunchedEffect(tabState.size, selectedTabIndexState) {
      if (selectedTabIndexState != safeIndex) selectedTabIndexState = safeIndex
    }

    LaunchedEffect(selectedTab?.stableKey()) {
      val tab = selectedTab ?: return@LaunchedEffect
      val key = tab.stableKey()
      if (!tabProjectsState.containsKey(key) && loadingState[key] != true) {
        loadTabProjects(scope, tab, false)
      }
    }

    Box(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScrollableTabRow(selectedTabIndex = safeIndex, modifier = Modifier.fillMaxWidth(), edgePadding = 0.dp) {
          tabState.forEachIndexed { index, tab ->
            Tab(selected = safeIndex == index, onClick = {
              selectedTabIndexState = index
              persistTabs(requireContext())
            }, text = {
              Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            })
          }
        }

        if (selectedTab == null) return
        val key = selectedTab.stableKey()
        val projects = tabProjectsState[key].orEmpty()
        val isLoading = loadingState[key] == true

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          if (clipboardState != null) {
            TextButton(onClick = { moveClipboardToTab(scope, selectedTab) }) {
              Icon(Icons.Default.ContentPaste, null)
              Text(stringResource(R.string.project_manager_paste_current_tab), modifier = Modifier.padding(start = 6.dp))
            }
          }
        }

        PullToRefreshBox(isRefreshing = isLoading, onRefresh = { loadTabProjects(scope, selectedTab, true) }) {
          LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projects, key = { it }) { projectPath ->
              val name = File(projectPath).name
              val meta = appMetaState[projectPath]
              Box {
                Card(
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = { viewModel.openProject(requireContext(), File(projectPath)) },
                        onLongClick = { menuProjectPath = projectPath },
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)) {
                      Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        val bitmap = meta?.iconFilePath?.let { path -> BitmapFactory.decodeFile(path)?.asImageBitmap() }
                        if (bitmap != null) {
                          Image(bitmap = bitmap, contentDescription = null)
                        } else if (meta?.iconResName != null) Icon(Icons.Default.Android, null) else Icon(Icons.Default.Folder, null)
                        val line = if (meta?.label.isNullOrBlank()) name else "$name : ${meta?.label}"
                        Text(line, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium)
                      }
                    }
                DropdownMenu(expanded = menuProjectPath == projectPath, onDismissRequest = { menuProjectPath = null }) {
                  DropdownMenuItem(text = { Text(stringResource(R.string.project_manager_rename)) }, onClick = {
                    renameTarget = projectPath
                    renameInput = File(projectPath).name
                    menuProjectPath = null
                  })
                  DropdownMenuItem(text = { Text(stringResource(R.string.project_manager_cut)) }, onClick = {
                    clipboardState = ClipboardProject(projectPath)
                    menuProjectPath = null
                  })
                  DropdownMenuItem(text = { Text(stringResource(R.string.project_manager_move_to)) }, onClick = {
                    clipboardState = ClipboardProject(projectPath)
                    moveClipboardToTab(scope, selectedTab)
                    menuProjectPath = null
                  })
                  DropdownMenuItem(text = { Text(stringResource(R.string.project_manager_delete)) }, onClick = {
                    deleteProject(scope, selectedTab, projectPath)
                    menuProjectPath = null
                  })
                  DropdownMenuItem(text = { Text(stringResource(R.string.project_manager_properties)) }, onClick = {
                    showPropertiesFor = projectPath
                    menuProjectPath = null
                  })
                }
              }
            }
          }
        }
      }

      FloatingActionButton(
          onClick = { folderPicker.launch(null) },
          modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 2.dp).size(28.dp),
      ) {
        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.project_manager_add_folder))
      }
    }

    renameTarget?.let { path ->
      AlertDialog(onDismissRequest = { renameTarget = null }, confirmButton = {
        TextButton(onClick = {
          renameProject(path, renameInput)
          renameTarget = null
        }) { Text(stringResource(android.R.string.ok)) }
      }, dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(android.R.string.cancel)) } }, title = { Text(stringResource(R.string.project_manager_rename)) }, text = { TextField(value = renameInput, onValueChange = { renameInput = it }) })
    }

    showPropertiesFor?.let { path ->
      val props = remember(path) { collectProperties(path) }
      AlertDialog(onDismissRequest = { showPropertiesFor = null }, confirmButton = { TextButton(onClick = { showPropertiesFor = null }) { Text(stringResource(R.string.project_manager_close)) } }, title = { Text(stringResource(R.string.project_manager_properties)) }, text = { Text(props) })
    }
  }

  private fun loadManifestMetaAsync(projectPaths: List<String>) {
    projectPaths.forEach { path ->
      if (appMetaState.containsKey(path)) return@forEach
      readMetaCache(requireContext(), path)?.let { appMetaState[path] = it; return@forEach }
      viewLifecycleScope.launch(Dispatchers.IO) {
        val meta = parseManifestMeta(path)
        if (meta != null) withContext(Dispatchers.Main) {
          appMetaState[path] = meta
          writeMetaCache(requireContext(), path, meta)
        }
      }
    }
  }

  private fun parseManifestMeta(projectPath: String): ProjectAppMeta? {
    val appModule = File(projectPath).resolve("app")
    val manifest = appModule.resolve("src/main/AndroidManifest.xml")
    if (!manifest.exists()) return null
    val xml = manifest.readText()
    val icon = Regex("android:icon=\"([^\"]+)\"").find(xml)?.groupValues?.getOrNull(1)
    val labelValue = Regex("android:label=\"([^\"]+)\"").find(xml)?.groupValues?.getOrNull(1)
    val label = resolveLabel(appModule, labelValue)
    val iconName = resolveIconName(icon)
    val iconFilePath = resolveIconFilePath(appModule, iconName)
    return ProjectAppMeta(label = label, iconResName = iconName, iconFilePath = iconFilePath)
  }

  private fun resolveIconName(iconValue: String?): String? {
    if (iconValue.isNullOrBlank() || !iconValue.startsWith("@")) return null
    return iconValue.removePrefix("@")
  }


  private fun resolveIconFilePath(appModule: File, iconResName: String?): String? {
    if (iconResName.isNullOrBlank()) return null
    val parts = iconResName.split('/')
    if (parts.size != 2) return null
    val type = parts[0]
    val name = parts[1]
    val resRoot = appModule.resolve("src/main/res")
    if (!resRoot.exists()) return null
    val dirs = resRoot.listFiles { f -> f.isDirectory && f.name.startsWith(type) }?.toList().orEmpty()
    val exts = listOf("png", "webp", "jpg", "jpeg", "xml")
    for (d in dirs) for (ext in exts) {
      val f = d.resolve("$name.$ext")
      if (f.exists()) return f.absolutePath
    }
    return null
  }

  private fun resolveLabel(appModule: File, labelValue: String?): String? {
    if (labelValue.isNullOrBlank()) return null
    if (!labelValue.startsWith("@string/")) return labelValue
    val key = labelValue.removePrefix("@string/")
    val stringsFile = appModule.resolve("src/main/res/values/strings.xml")
    if (!stringsFile.exists()) return key
    val xml = stringsFile.readText()
    return Regex("<string\\s+name=\"$key\">(.*?)</string>").find(xml)?.groupValues?.getOrNull(1) ?: key
  }

  private fun moveClipboardToTab(scope: CoroutineScope, tab: ProjectTab) {
    val clip = clipboardState ?: return
    val targetRoot = tab.rootPathOrNull() ?: return
    scope.launch(Dispatchers.IO) {
      val src = File(clip.sourcePath)
      val dst = File(targetRoot, src.name)
      if (src.exists() && src.absolutePath != dst.absolutePath && !dst.exists()) src.renameTo(dst)
      clipboardState = null
      withContext(Dispatchers.Main) { loadTabProjects(this@launch, tab, true) }
    }
  }

  private fun renameProject(path: String, newName: String) { /* unchanged */
    if (newName.isBlank()) return
    val src = File(path)
    val dst = File(src.parentFile, newName)
    if (!dst.exists()) src.renameTo(dst)
    tabState.forEach { tab ->
      tabProjectsState[tab.stableKey()] = tabProjectsState[tab.stableKey()].orEmpty().map { if (it == path) dst.absolutePath else it }
    }
  }

  private fun deleteProject(scope: CoroutineScope, tab: ProjectTab, path: String) {
    scope.launch(Dispatchers.IO) {
      File(path).deleteRecursively()
      withContext(Dispatchers.Main) { loadTabProjects(this@launch, tab, true) }
    }
  }

  private fun loadTabProjects(scope: CoroutineScope, tab: ProjectTab, forceRefresh: Boolean) {
    val key = tab.stableKey()
    if (!forceRefresh) readCache(requireContext(), key).takeIf { it.isNotEmpty() }?.let { tabProjectsState[key] = it }
    scope.launch {
      loadingState[key] = true
      val merged = withContext(Dispatchers.IO) { scanTopLevelProjects(tab).filter { File(it).exists() }.sortedBy { File(it).name.lowercase() } }
      tabProjectsState[key] = merged
      writeCache(requireContext(), key, merged)
      loadManifestMetaAsync(merged)
      loadingState[key] = false
    }
  }

  private fun scanTopLevelProjects(tab: ProjectTab): List<String> {
    val root = tab.rootPathOrNull() ?: return emptyList()
    return File(root).listFiles { file -> file.isDirectory }?.map { it.absolutePath }.orEmpty()
  }

  private fun persistTabs(context: Context) {
    val arr = JSONArray()
    tabState.forEach {
      val item = JSONArray()
      item.put(it.title)
      item.put(it.filePath)
      item.put(it.treeUri?.toString())
      arr.put(item)
    }
    context.getSharedPreferences("project_manager_tabs", Context.MODE_PRIVATE).edit()
        .putString("tabs", arr.toString())
        .putInt("selected", selectedTabIndexState)
        .apply()
  }

  private fun restoreTabs(context: Context) {
    val raw = context.getSharedPreferences("project_manager_tabs", Context.MODE_PRIVATE).getString("tabs", null) ?: return
    runCatching {
      val arr = JSONArray(raw)
      for (i in 0 until arr.length()) {
        val item = arr.getJSONArray(i)
        val title = item.optString(0)
        val filePath = item.optString(1).ifBlank { null }
        val treeUri = item.optString(2).ifBlank { null }?.let(Uri::parse)
        if (title.isNotBlank()) tabState.add(ProjectTab(title, filePath, treeUri))
      }
      selectedTabIndexState = context.getSharedPreferences("project_manager_tabs", Context.MODE_PRIVATE).getInt("selected", 0)
    }
  }



  private fun readMetaCache(context: Context, projectPath: String): ProjectAppMeta? {
    val sp = context.getSharedPreferences("project_manager_meta", Context.MODE_PRIVATE)
    val raw = sp.getString(projectPath, null) ?: return null
    return runCatching {
      val arr = JSONArray(raw)
      ProjectAppMeta(arr.optString(0).ifBlank { null }, arr.optString(1).ifBlank { null }, arr.optString(2).ifBlank { null })
    }.getOrNull()
  }

  private fun writeMetaCache(context: Context, projectPath: String, meta: ProjectAppMeta) {
    val arr = JSONArray()
    arr.put(meta.label)
    arr.put(meta.iconResName)
    arr.put(meta.iconFilePath)
    context.getSharedPreferences("project_manager_meta", Context.MODE_PRIVATE).edit().putString(projectPath, arr.toString()).apply()
  }

  private fun readCache(context: Context, key: String): List<String> { val raw = context.getSharedPreferences("project_manager_cache", Context.MODE_PRIVATE).getString(key, null) ?: return emptyList(); return runCatching { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(::add) } }.getOrDefault(emptyList()).distinct() }
  private fun writeCache(context: Context, key: String, data: List<String>) { val arr = JSONArray(); data.distinct().forEach(arr::put); context.getSharedPreferences("project_manager_cache", Context.MODE_PRIVATE).edit().putString(key, arr.toString()).apply() }

  private fun collectProperties(path: String): String { val file = File(path); if (!file.exists()) return getString(R.string.project_manager_path_not_exists); val size = file.walkTopDown().filter { it.isFile }.sumOf { it.length() }; val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified())); val md5 = digest("MD5", path); val sha1 = digest("SHA-1", path); val sha256 = digest("SHA-256", path); val perms = (if (file.canRead()) "r" else "-") + (if (file.canWrite()) "w" else "-") + (if (file.canExecute()) "x" else "-"); val uidGid = runCatching { Os.stat(path) }.getOrNull()?.let { "uid=${it.st_uid}, gid=${it.st_gid}" } ?: getString(R.string.project_manager_uid_gid_unknown); return getString(R.string.project_manager_properties_template, file.absolutePath, getString(R.string.project_manager_type_folder), size, time, perms, uidGid, md5, sha1, sha256) }
  private fun digest(alg: String, text: String): String = MessageDigest.getInstance(alg).digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

  companion object {
    private fun uriToPath(uri: Uri): String? {
      val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
      val parts = docId.split(':', limit = 2)
      if (parts.size < 2) return null
      return "/storage/${parts[0]}/${parts[1]}"
    }
  }
}
