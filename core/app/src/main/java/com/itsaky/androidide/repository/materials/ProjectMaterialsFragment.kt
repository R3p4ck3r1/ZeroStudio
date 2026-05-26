package com.itsaky.androidide.repository.materials

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.viewModels
import com.itsaky.androidide.fragments.BaseFragment
import com.itsaky.androidide.projects.materials.MaterialSourceType
import com.itsaky.androidide.projects.materials.ProjectMaterialItem
import com.unnamed.b.atv.model.TreeNode
import com.unnamed.b.atv.view.AndroidTreeView
import java.io.File

class ProjectMaterialsFragment : BaseFragment() {
  private val viewModel: ProjectMaterialsViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setContent {
        val state by viewModel.uiState.collectAsState()
        ProjectMaterialsScreen(state)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    viewModel.refresh()
  }
}

@Composable
private fun ProjectMaterialsScreen(state: ProjectMaterialsUiState) {
  var selected by remember(state.items) { mutableStateOf(state.selected) }

  Row(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    AndroidView(
        modifier = Modifier.weight(1f),
        factory = { context ->
          val root = buildMaterialsTree(state.items)
          AndroidTreeView(context, root, 0).apply {
            setUseAutoToggle(true)
            setDefaultNodeClickListener { node, _ ->
              val item = node.value?.path?.let { path -> state.items.firstOrNull { it.id == path } }
              if (item != null) selected = item
            }
          }.getView()
        },
        update = { _ -> }
    )

    Column(modifier = Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Material Detail", style = MaterialTheme.typography.titleMedium)
      Text(selected?.title ?: "None")
      Text(selected?.description ?: "Select one material")
      Text(selected?.path ?: "")
    }
  }
}

private fun buildMaterialsTree(items: List<ProjectMaterialItem>): TreeNode {
  val root = TreeNode.root(File("materials-root"))
  val byType = items.groupBy { it.sourceType }

  MaterialSourceType.entries.forEach { type ->
    val typeNode = TreeNode(File(type.name))
    val typeItems = byType[type].orEmpty().sortedBy { it.title }
    typeItems.forEach { item ->
      val module = item.id.substringBefore(':', "misc")
      val moduleNode = typeNode.children.firstOrNull { it.value?.name == module } ?: TreeNode(File(module)).also { typeNode.addChild(it, false) }
      val leaf = TreeNode(File(item.title)).apply { value = File(item.id) }
      moduleNode.addChild(leaf, false)
    }
    root.addChild(typeNode, false)
  }
  return root
}
