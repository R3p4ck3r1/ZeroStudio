package com.itsaky.androidide.fragments.toolbox

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import com.itsaky.androidide.resources.R

/** Multi-purpose editor toolbox with a fixed grid tab and lazily materialized tool fragments. */
class EditorToolboxFragment : Fragment() {

  private var containerId: Int = View.NO_ID

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    containerId = View.generateViewId()
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent { MaterialTheme { EditorToolboxScreen() } }
    }
  }

  override fun onDestroyView() {
    releaseToolFragments(allowStateLoss = true)
    containerId = View.NO_ID
    super.onDestroyView()
  }

  @Composable
  private fun EditorToolboxScreen() {
    val usageStore = remember { ToolboxUsageStore(requireContext()) }
    var selectedTab by rememberSaveable { mutableStateOf(GRID_TAB_ID) }
    var openedToolIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var usageVersion by rememberSaveable { mutableIntStateOf(0) }

    val entries = remember(usageVersion) {
      EditorToolboxRegistry.getEntries().sortedWith(
          compareByDescending<EditorToolboxEntry> { usageStore.getLaunchCount(it.id) }
              .thenBy { it.order }
              .thenBy { it.title }
      )
    }
    val openedEntries = openedToolIds.mapNotNull { EditorToolboxRegistry.find(it) }
    val visibleTabIds = buildList {
      add(GRID_TAB_ID)
      openedEntries.mapTo(this) { it.id }
    }

    LaunchedEffect(selectedTab, visibleTabIds) {
      if (selectedTab !in visibleTabIds) {
        selectedTab = GRID_TAB_ID
      }
    }

    LaunchedEffect(selectedTab, openedToolIds, containerId) {
      freezeInactiveToolFragments(activeToolId = selectedTab.takeUnless { it == GRID_TAB_ID })
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(modifier = Modifier.fillMaxSize()) {
        EditorToolboxTabs(
            selectedTab = selectedTab,
            openedEntries = openedEntries,
            onSelectTab = { selectedTab = it }
        )
        HorizontalDivider()
        Box(modifier = Modifier.fillMaxSize()) {
          FragmentHost(
              visible = selectedTab != GRID_TAB_ID,
              modifier = Modifier.fillMaxSize(),
          )
          if (selectedTab == GRID_TAB_ID) {
            ToolboxGrid(
                entries = entries,
                usageStore = usageStore,
                onOpen = { entry ->
                  usageStore.recordLaunch(entry.id)
                  usageVersion++
                  if (entry.id !in openedToolIds) {
                    openedToolIds = openedToolIds + entry.id
                  }
                  selectedTab = entry.id
                }
            )
          }
        }
      }
    }
  }

  @Composable
  private fun EditorToolboxTabs(
      selectedTab: String,
      openedEntries: List<EditorToolboxEntry>,
      onSelectTab: (String) -> Unit,
  ) {
    val tabs = remember(openedEntries) {
      buildList {
        add(
            ToolboxTabSpec(
                id = GRID_TAB_ID,
                title = getString(R.string.title_editor_toolbox_grid),
                iconRes = R.drawable.ic_widget_grid_view,
            )
        )
        openedEntries.mapTo(this) { entry ->
          ToolboxTabSpec(id = entry.id, title = entry.title, iconRes = entry.iconRes)
        }
      }
    }
    val requestedIndex = tabs.indexOfFirst { it.id == selectedTab }.coerceAtLeast(0)
    var tabRowSelectedIndex by remember { mutableIntStateOf(0) }
    val safeSelectedIndex = tabRowSelectedIndex.coerceIn(0, tabs.lastIndex.coerceAtLeast(0))

    LaunchedEffect(tabs, requestedIndex) {
      tabRowSelectedIndex = requestedIndex
    }

    ScrollableTabRow(
        selectedTabIndex = safeSelectedIndex,
        modifier = Modifier.height(36.dp),
        edgePadding = 4.dp,
    ) {
      tabs.forEach { tab ->
        Tab(
            selected = selectedTab == tab.id,
            onClick = { onSelectTab(tab.id) },
            modifier = Modifier.height(36.dp),
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(tab.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tab.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                )
              }
            },
        )
      }
    }
  }

  @Composable
  private fun ToolboxGrid(
      entries: List<EditorToolboxEntry>,
      usageStore: ToolboxUsageStore,
      onOpen: (EditorToolboxEntry) -> Unit,
  ) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      items(items = entries, key = { it.id }) { entry ->
        ToolboxGridButton(
            entry = entry,
            launchCount = usageStore.getLaunchCount(entry.id),
            onClick = { onOpen(entry) }
        )
      }
    }
  }

  @Composable
  private fun ToolboxGridButton(entry: EditorToolboxEntry, launchCount: Int, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(126.dp),
        shape = RoundedCornerShape(22.dp),
        elevation =
            CardDefaults.elevatedCardElevation(defaultElevation = 3.dp, pressedElevation = 8.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
    ) {
      Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
      ) {
        Icon(
            painter = painterResource(entry.iconRes),
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = entry.description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (launchCount > 0) {
          Spacer(modifier = Modifier.height(3.dp))
          Text(
              text = getString(R.string.editor_toolbox_usage_count, launchCount),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.outline,
              fontSize = 10.sp,
              maxLines = 1,
          )
        }
      }
    }
  }

  @Composable
  private fun FragmentHost(visible: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { context ->
          FragmentContainerView(context).apply {
            id = containerId
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
          }
        },
        update = { it.visibility = if (visible) View.VISIBLE else View.GONE },
    )
  }

  private fun freezeInactiveToolFragments(activeToolId: String?) {
    if (containerId == View.NO_ID || view == null || childFragmentManager.isStateSaved) return

    val manager = childFragmentManager
    val transaction = manager.beginTransaction().setReorderingAllowed(true)
    var hasChanges = false
    val activeTag = activeToolId?.let(::toolTag)
    val activeEntry = activeToolId?.let(EditorToolboxRegistry::find)

    manager.fragments
        .filter { it.tag?.startsWith(TOOL_TAG_PREFIX) == true }
        .forEach { fragment ->
          if (fragment.tag == activeTag) {
            if (fragment.isHidden) {
              transaction.show(fragment)
              hasChanges = true
            }
            transaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            hasChanges = true
          } else {
            if (!fragment.isHidden) {
              transaction.hide(fragment)
              hasChanges = true
            }
            transaction.setMaxLifecycle(fragment, Lifecycle.State.CREATED)
            hasChanges = true
          }
        }

    if (activeTag != null && activeEntry != null && manager.findFragmentByTag(activeTag) == null) {
      val fragment =
          manager.fragmentFactory.instantiate(
              activeEntry.fragmentClass.classLoader!!,
              activeEntry.fragmentClass.name,
          )
      transaction.add(containerId, fragment, activeTag)
      transaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
      hasChanges = true
    }

    if (hasChanges) {
      transaction.commitNow()
    }
  }

  private fun releaseToolFragments(allowStateLoss: Boolean) {
    if (childFragmentManager.isStateSaved && !allowStateLoss) return
    val fragments =
        childFragmentManager.fragments.filter { it.tag?.startsWith(TOOL_TAG_PREFIX) == true }
    if (fragments.isEmpty()) return
    val transaction = childFragmentManager.beginTransaction().setReorderingAllowed(true)
    fragments.forEach { transaction.remove(it) }
    if (allowStateLoss) {
      transaction.commitNowAllowingStateLoss()
    } else {
      transaction.commitNow()
    }
  }

  @Stable
  private data class ToolboxTabSpec(
      val id: String,
      val title: String,
      val iconRes: Int,
  )

  private class ToolboxUsageStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLaunchCount(id: String): Int = preferences.getInt(id, 0)

    fun recordLaunch(id: String) {
      preferences.edit().putInt(id, getLaunchCount(id) + 1).apply()
    }
  }

  private companion object {
    const val GRID_TAB_ID = "editor.toolbox.grid"
    const val TOOL_TAG_PREFIX = "editor.toolbox.tool."
    const val PREFS_NAME = "editor_toolbox_usage"

    fun toolTag(toolId: String): String = TOOL_TAG_PREFIX + toolId
  }
}
