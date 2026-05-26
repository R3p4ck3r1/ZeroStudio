package com.itsaky.androidide.repository.materials

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import com.itsaky.androidide.fragments.BaseFragment
import com.itsaky.androidide.projects.materials.ProjectMaterialItem

class ProjectMaterialsFragment : BaseFragment() {
  private val viewModel: ProjectMaterialsViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setContent {
        val state by viewModel.uiState.collectAsState()
        ProjectMaterialsScreen(state, viewModel::select)
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
  Row(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    LazyColumn(modifier = Modifier.weight(1f)) {
      items(state.items, key = { it.id }) { item ->
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(item) }) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(item.apiName, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
    Column(modifier = Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Material Detail", style = MaterialTheme.typography.titleMedium)
      Text(state.selected?.title ?: "None")
      Text(state.selected?.description ?: "Select one material")
      Text(state.selected?.path ?: "")
    }
  }
}
