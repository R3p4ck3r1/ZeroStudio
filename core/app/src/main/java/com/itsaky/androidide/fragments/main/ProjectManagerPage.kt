package com.itsaky.androidide.fragments.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.Environment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProjectManagerPage(onOpenProject: (String) -> Unit) {
  val projects = remember { mutableStateListOf<String>() }
  val loading = remember { mutableStateOf(false) }
  val refreshTick = remember { mutableIntStateOf(0) }

  suspend fun reloadProjects() {
    loading.value = true
    val result =
        withContext(Dispatchers.IO) {
          val root = Environment.getProjectsDir()
          if (!root.exists()) return@withContext emptyList<String>()
          root.listFiles()
              ?.filter { it.isDirectory && File(it, ".idea").exists() || File(it, "app").exists() }
              ?.map { it.absolutePath }
              ?.sortedBy { it.lowercase() }
              ?: emptyList()
        }
    projects.clear()
    projects.addAll(result)
    loading.value = false
  }

  LaunchedEffect(refreshTick.intValue) { reloadProjects() }

  Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
          text = stringResource(R.string.main_nav_projects),
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.weight(1f),
      )
      IconButton(onClick = { refreshTick.intValue++ }) {
        Icon(Icons.Default.Refresh, contentDescription = "refresh")
      }
    }

    if (loading.value) {
      Text(text = stringResource(R.string.loading))
    }

    if (!loading.value && projects.isEmpty()) {
      Text(text = stringResource(R.string.main_empty_history))
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(projects, key = { it }) { path ->
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onOpenProject(path) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(text = File(path).name, style = MaterialTheme.typography.titleMedium)
            Text(text = path, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
}
