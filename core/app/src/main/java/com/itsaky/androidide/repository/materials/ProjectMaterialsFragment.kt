package com.itsaky.androidide.repository.materials

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.zero.studio.view.filetree.interfaces.FileClickListener
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.widget.FileTree
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.viewModels
import com.itsaky.androidide.fragments.BaseFragment
import com.itsaky.androidide.projects.materials.MaterialSourceType
import com.itsaky.androidide.projects.materials.ProjectMaterialItem
import java.io.Serializable

class ProjectMaterialsFragment : BaseFragment() {
  private val viewModel: ProjectMaterialsViewModel by viewModels()

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    return ComposeView(requireContext()).apply {
      setContent {
        val state by viewModel.uiState.collectAsState()
        ProjectMaterialsScreen(state = state, onSelect = viewModel::select)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    viewModel.refresh()
  }
}

@Composable
private fun ProjectMaterialsScreen(state: ProjectMaterialsUiState, onSelect: (ProjectMaterialItem) -> Unit) {
  var selected by remember(state.selected, state.items) { mutableStateOf(state.selected) }

  Row(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (state.loading) {
      Column(
          modifier = Modifier.weight(1f).fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
            Text("Loading materials...", modifier = Modifier.padding(top = 12.dp))
          }
    } else {
      AndroidView(
          modifier = Modifier.weight(1f),
          factory = { context ->
            val tree = FileTree(context)
            tree.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            tree.setOnFileClickListener(
                object : FileClickListener {
                  override fun onClick(node: Node<FileObject>) {
                    val clicked = (node.value as? MaterialTreeFileObject)?.material
                    if (clicked != null) {
                      selected = clicked
                      onSelect(clicked)
                    }
                  }
                })
            tree.loadFiles(buildMaterialsTree(state.items), true)
            tree
          },
          update = { tree -> tree.loadFiles(buildMaterialsTree(state.items), true) })
    }

    Column(modifier = Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Material Detail", style = MaterialTheme.typography.titleMedium)
      Text(selected?.title ?: "None")
      Text(selected?.description ?: "Select one material")
      Text(selected?.path ?: "")
    }
  }
}

private fun buildMaterialsTree(items: List<ProjectMaterialItem>): MaterialTreeFileObject {
  val root = MaterialTreeFileObject(name = "Project Materials", isDir = true, material = null)
  val byType = items.groupBy { it.sourceType }

  MaterialSourceType.entries.forEach { type ->
    val typeNode = MaterialTreeFileObject(type.name, true, null)
    val groupedModules = byType[type].orEmpty().groupBy { it.id.substringBefore(':', "misc") }
    groupedModules.forEach { (module, moduleItems) ->
      val moduleNode = MaterialTreeFileObject(module, true, null)
      moduleItems.sortedBy { it.title }.forEach { item ->
        moduleNode.children += MaterialTreeFileObject(item.title, false, item)
      }
      typeNode.children += moduleNode
    }
    root.children += typeNode
  }
  return root
}

private data class MaterialTreeFileObject(
    private val name: String,
    private val isDir: Boolean,
    val material: ProjectMaterialItem?,
    val children: MutableList<MaterialTreeFileObject> = mutableListOf(),
) : FileObject, Serializable {
  override fun listFiles(): List<FileObject> = children
  override fun isDirectory(): Boolean = isDir
  override fun isFile(): Boolean = !isDir
  override fun getName(): String = name
  override fun getParentFile(): FileObject? = null
  override fun getAbsolutePath(): String = material?.id ?: "virtual://$name"
}
