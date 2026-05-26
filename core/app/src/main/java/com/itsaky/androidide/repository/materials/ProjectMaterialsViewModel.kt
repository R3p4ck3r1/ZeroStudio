package com.itsaky.androidide.repository.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.itsaky.androidide.projects.materials.ProjectMaterialItem
import com.itsaky.androidide.projects.materials.ProjectMaterialsRepository

data class ProjectMaterialsUiState(
    val loading: Boolean = false,
    val items: List<ProjectMaterialItem> = emptyList(),
    val selected: ProjectMaterialItem? = null,
)

class ProjectMaterialsViewModel(private val repository: ProjectMaterialsRepository = ProjectMaterialsRepository()) : ViewModel() {
  private val _uiState = MutableStateFlow(ProjectMaterialsUiState(loading = true))
  val uiState: StateFlow<ProjectMaterialsUiState> = _uiState.asStateFlow()

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      val items = repository.loadMaterials()
      _uiState.value = ProjectMaterialsUiState(false, items, items.firstOrNull())
    }
  }

  fun select(item: ProjectMaterialItem) {
    _uiState.value = _uiState.value.copy(selected = item)
  }
}
