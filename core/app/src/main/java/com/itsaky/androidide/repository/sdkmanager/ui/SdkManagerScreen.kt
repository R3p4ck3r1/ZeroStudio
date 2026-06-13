/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.repository.sdkmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import com.itsaky.androidide.R
import com.itsaky.androidide.repository.sdkmanager.models.SdkTreeNode
import com.itsaky.androidide.repository.sdkmanager.services.SdkInstallerManager
import com.itsaky.androidide.repository.sdkmanager.tree.SdkTreeView
import com.itsaky.androidide.repository.sdkmanager.viewmodel.SdkManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/** @author android_zero 使用 Jetpack Compose + Material 3 构建的 SDK Manager 主界面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SdkManagerScreen(onClose: () -> Unit, viewModel: SdkManagerViewModel) {
  var selectedTabIndex by remember { mutableStateOf(0) }
  val tabs = listOf("SDK Platforms", "SDK Tools", "SDK Update Sites")

  val isLoading by viewModel.isLoading.collectAsState()
  val hasPendingChanges by viewModel.hasPendingChanges.collectAsState()

  var showActionDialog by remember { mutableStateOf(false) }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.sdk_manager)) },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
        )
      },
      bottomBar = {
        BottomActionRow(
            hasPendingChanges = hasPendingChanges,
            onCancel = onClose,
            onApply = { showActionDialog = true },
        )
      },
  ) { paddingValues ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
    ) {
      TabRow(selectedTabIndex = selectedTabIndex) {
        tabs.forEachIndexed { index, title ->
          Tab(
              selected = selectedTabIndex == index,
              onClick = { selectedTabIndex = index },
              text = { Text(title) },
          )
        }
      }

      if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading SDK definitions...", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else {
        when (selectedTabIndex) {
          0 -> SdkPlatformTab(viewModel)
          1 -> SdkToolsTab(viewModel)
          2 ->
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Update Sites configuration is handled internally by manifest.json.",
                    color = Color.Gray,
                )
              }
        }
      }
    }
  }

  if (showActionDialog) {
    val (toInstall, toDelete) = viewModel.getPendingTasks()
    ActionConfirmAndRunDialog(
        toInstall = toInstall,
        toDelete = toDelete,
        onDismiss = {
          showActionDialog = false
          viewModel.loadData() // 刷新视图模型状态
        },
    )
  }
}

@Composable
fun SdkPlatformTab(viewModel: SdkManagerViewModel) {
  val treeNodes by viewModel.platformsTree.collectAsState()
  SdkTreeViewWrapper(nodes = treeNodes, isPlatformsTab = true, viewModel = viewModel)
}

@Composable
fun SdkToolsTab(viewModel: SdkManagerViewModel) {
  val treeNodes by viewModel.toolsTree.collectAsState()
  SdkTreeViewWrapper(nodes = treeNodes, isPlatformsTab = false, viewModel = viewModel)
}

/** 将底层的 RecyclerView (SdkTreeView) 包裹进 Compose，并绘制 Material 3 风格的多列表头。 */
@Composable
fun SdkTreeViewWrapper(
    nodes: List<SdkTreeNode>,
    isPlatformsTab: Boolean,
    viewModel: SdkManagerViewModel,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    Text(
        text =
            if (isPlatformsTab)
                "Each Android SDK Platform package includes the Android platform and sources pertaining to an API level by default."
            else "Manager for the Android SDK Tools used by AndroidIDE.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Spacer(modifier = Modifier.width(36.dp)) // 为折叠箭头预留空间
      Text(
          "Name",
          modifier = Modifier.weight(0.45f),
          fontWeight = FontWeight.SemiBold,
          fontSize = 12.sp,
      )
      Text(
          "API Level",
          modifier = Modifier.weight(0.15f),
          fontWeight = FontWeight.SemiBold,
          fontSize = 12.sp,
      )
      Text(
          "Revision",
          modifier = Modifier.weight(0.15f),
          fontWeight = FontWeight.SemiBold,
          fontSize = 12.sp,
      )
      Text(
          "Status",
          modifier = Modifier.weight(0.25f),
          fontWeight = FontWeight.SemiBold,
          fontSize = 12.sp,
      )
    }
    Divider()

    AndroidView(
        factory = { context ->
          SdkTreeView(context).apply {
            bindData(nodes) { clickedNode ->
              viewModel.toggleCheck(clickedNode, isPlatformsTab)
              // 刷新视图
              refreshViews()
            }
          }
        },
        update = { view ->
          view.bindData(nodes) { clickedNode ->
            viewModel.toggleCheck(clickedNode, isPlatformsTab)
            view.refreshViews()
          }
        },
        modifier = Modifier.fillMaxSize(),
    )
  }
}

@Composable
fun BottomActionRow(hasPendingChanges: Boolean, onCancel: () -> Unit, onApply: () -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
      horizontalArrangement = Arrangement.End,
  ) {
    TextButton(onClick = onCancel) { Text("Cancel") }
    Spacer(modifier = Modifier.width(8.dp))
    Button(onClick = onApply, enabled = hasPendingChanges) { Text("Apply") }
    Spacer(modifier = Modifier.width(8.dp))
    Button(onClick = { if (hasPendingChanges) onApply() else onCancel() }) { Text("OK") }
  }
}

/** 任务执行弹出框 结合 SdkInstallerManager 执行真实的下载、Shell 解压与删除。 */

/**
 * Stable, append-only log entry for [ActionConfirmAndRunDialog]. The `id` is
 * a monotonically increasing sequence that lets the LazyColumn use it as a
 * stable `key` for [androidx.compose.foundation.lazy.items], which in turn
 * allows Compose to keep item identity stable across many small appends
 * (preventing the `MutableIntervalList.get` IndexOutOfBoundsException that
 * was previously raised when the list was mutated from a non-Main thread).
 */
private data class LogEntry(val id: Int, val msg: String)

@Composable
fun ActionConfirmAndRunDialog(
    toInstall: List<SdkTreeNode>,
    toDelete: List<SdkTreeNode>,
    onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var isRunning by remember { mutableStateOf(false) }
  var isFinished by remember { mutableStateOf(false) }
  var currentProgress by remember { mutableStateOf(0f) }
  var currentTaskName by remember { mutableStateOf("") }

  // 修复选项
  var applyNdkFix by remember { mutableStateOf(true) }
  var applyCmakePatch by remember { mutableStateOf(true) }
  val installingNdk = toInstall.any { it.componentType == "ndk" }
  val installingCmake = toInstall.any { it.componentType == "cmake" }

  // Root-cause fix for "IndexOutOfBoundsException: Index 5, size 5" in the
  // LazyColumn log view below:
  //
  //   1. The previous code used `mutableStateListOf<String>()` and a plain
  //      `::addLog` callback that appended to it from background coroutines
  //      (TermuxCommand / OkHttp download streams run on Dispatchers.IO).
  //      Mutating a `SnapshotStateList` from a non-Main thread is unsafe and
  //      races with the `LazyColumn` measure pass reading `consoleLogs[i]`
  //      via `MutableIntervalList.get` -> `IndexOutOfBoundsException`.
  //
  //   2. Items had no `key`, so every mutation forced a full identity table
  //      rebuild and made the race window much wider.
  //
  //   3. `LaunchedEffect(consoleLogs.size) { animateScrollToItem(lastIndex) }`
  //      re-ran on every append and called `lastIndex` *outside* the snapshot
  //      read; an item could be removed (via the same coroutine truncating
  //      logs) before the scroll started, producing the exact "size 5, but
  //      asked for 5" crash.
  //
  // The fix is structural: producers (any thread) push to a thread-safe
  // queue, a single Main-thread collector drains the queue into an
  // immutable list, and the LazyColumn uses stable `key` blocks keyed by
  // a monotonically-increasing sequence id.
  val pendingLogs = remember { ConcurrentLinkedQueue<String>() }
  val consoleLogs = remember { mutableStateOf<List<LogEntry>>(emptyList()) }
  val logSeq = remember { AtomicInteger(0) }

  // Drain any pending log writes on the Main thread. The collector runs
  // forever; it is cancelled when the dialog leaves composition.
  LaunchedEffect(Unit) {
    // Collect the queue at ~60Hz so very high-frequency producers don't
    // starve the UI, but logs still appear fluidly.
    while (true) {
      val batch = ArrayList<String>(8)
      while (true) {
        val next = pendingLogs.poll() ?: break
        batch.add(next)
        // Cap a single drain so a flood of logs doesn't block Main for too long.
        if (batch.size >= 64) break
      }
      if (batch.isNotEmpty()) {
        val base = logSeq.get()
        val entries = batch.mapIndexed { i, msg -> LogEntry(base + i + 1, msg) }
        logSeq.addAndGet(entries.size)
        // Single, atomic list replacement on Main. Compose's snapshot system
        // guarantees the LazyColumn sees a consistent immutable snapshot.
        consoleLogs.value = consoleLogs.value + entries
      } else {
        kotlinx.coroutines.delay(16L)
      }
    }
  }

  // Backwards-compatible `addLog` that producers (which may be on any
  // thread) call. Thread-safe by construction.
  fun addLog(msg: String) {
    pendingLogs.offer(msg)
  }

  AlertDialog(
      onDismissRequest = { if (!isRunning) onDismiss() },
      properties =
          DialogProperties(dismissOnBackPress = !isRunning, dismissOnClickOutside = !isRunning),
      title = { Text(if (isFinished) "Tasks Completed" else "Confirm Changes") },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          // 任务确认预览阶段
          if (!isRunning && !isFinished) {
            Text(
                "The following packages will be installed/updated:",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            toInstall.forEach { Text("- ${it.name}", fontSize = 13.sp) }
            if (toInstall.isEmpty()) Text("  (None)", color = Color.Gray, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "The following packages will be removed:",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            toDelete.forEach {
              Text("- ${it.name}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            if (toDelete.isEmpty()) Text("  (None)", color = Color.Gray, fontSize = 13.sp)

            if (installingNdk || installingCmake) {
              Spacer(modifier = Modifier.height(16.dp))
              Divider()
              Spacer(modifier = Modifier.height(8.dp))
              Text("Additional Configurations:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
              if (installingNdk) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Checkbox(checked = applyNdkFix, onCheckedChange = { applyNdkFix = it })
                  Text(
                      "Apply NDK Fixes (symlinks & patches)",
                      fontSize = 13.sp,
                      modifier = Modifier.clickable { applyNdkFix = !applyNdkFix },
                  )
                }
              }
              if (installingCmake) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Checkbox(checked = applyCmakePatch, onCheckedChange = { applyCmakePatch = it })
                  Text(
                      "Apply CMake Patches",
                      fontSize = 13.sp,
                      modifier = Modifier.clickable { applyCmakePatch = !applyCmakePatch },
                  )
                }
              }
            }
          }

          // 任务执行及日志面板阶段
          if (isRunning || isFinished) {
            Text(text = "Current: $currentTaskName", style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(
                progress = { currentProgress },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            // 日志控制台
            val listState = rememberLazyListState()
            // Use the size of the *current* list (read inside the snapshot,
            // within the same composition that reads consoleLogs) to avoid
            // the "IndexOutOfBoundsException: Index size-1, size size-1" race
            // that happens when the producer thread mutates the list between
            // the read of `consoleLogs.size` and the start of the scroll.
            val logCount = consoleLogs.value.size
            LaunchedEffect(logCount) {
              if (logCount > 0) listState.animateScrollToItem(logCount - 1)
            }
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(250.dp)
                        .background(Color(0xFF1E1E1E), shape = MaterialTheme.shapes.small)
                        .padding(8.dp)
            ) {
              val logs = consoleLogs.value
              LazyColumn(state = listState) {
                items(items = logs, key = { it.id }) { entry ->
                  val textColor =
                      when {
                        entry.msg.startsWith("ERR") || entry.msg.startsWith("WARN") ->
                            Color(0xFFFF5252)
                        entry.msg.startsWith(">>") -> Color(0xFF64B5F6)
                        else -> Color(0xFFA5D6A7)
                      }
                  Text(
                      text = entry.msg,
                      color = textColor,
                      fontSize = 11.sp,
                      fontFamily = FontFamily.Monospace,
                      lineHeight = 14.sp,
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        if (!isFinished) {
          Button(
              onClick = {
                isRunning = true
                coroutineScope.launch {
                  // 执行卸载任务
                  for (node in toDelete) {
                    currentTaskName = "Removing ${node.name}"
                    currentProgress = 0f
                    SdkInstallerManager.deletePackage(node, ::addLog)
                    currentProgress = 1f
                  }

                  // 执行安装任务 (调用 TermuxCommand DSL 及终端解压算法)
                  for (node in toInstall) {
                    currentTaskName = "Installing ${node.name}"
                    currentProgress = 0f
                    val success =
                        SdkInstallerManager.downloadAndInstall(
                            context = context,
                            node = node,
                            applyNdkFix = applyNdkFix,
                            applyCmakePatch = applyCmakePatch,
                            onProgress = { currentProgress = it },
                            onLog = ::addLog,
                        )
                    if (!success) {
                      addLog("ERROR: Failed to install ${node.name}. Continuing next task.")
                    }
                  }

                  isFinished = true
                  isRunning = false
                  currentTaskName = "All tasks completed."
                  currentProgress = 1f
                }
              },
              enabled = !isRunning,
          ) {
            Text("Execute")
          }
        } else {
          Button(onClick = onDismiss) { Text("Finish") }
        }
      },
      dismissButton = {
        if (!isRunning && !isFinished) {
          TextButton(onClick = onDismiss) { Text("Cancel") }
        }
      },
  )
}
