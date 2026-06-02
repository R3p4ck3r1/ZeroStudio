package com.itsaky.androidide.utils

import android.content.Context
import android.studio.zero.regular.expression.preview.RegexPreviewFragment
import android.zero.studio.chatai.server.mcp.McpFragment
import com.itsaky.androidide.fragments.output.EditorProcessApmFragment
import com.itsaky.androidide.fragments.toolbox.EditorToolboxEntry
import com.itsaky.androidide.fragments.toolbox.EditorToolboxRegistry
import com.itsaky.androidide.repository.dependencies.analyzer.ui.DependencyUpdateFragment
import com.itsaky.androidide.repository.materials.ProjectMaterialsFragment
import com.itsaky.androidide.resources.R
import me.rerere.rikkahub.RouteFragment

/** Registers pluggable tools shown by the editor toolbox bottom-sheet tab. */
object EditorToolboxActions {
  @JvmStatic
  fun registerActions(context: Context) {
    var order = -1
    EditorToolboxRegistry.register(
        EditorToolboxEntry(
            id = "editor.toolbox.dependencyUpdates",
            title = context.getString(R.string.title_dependency_updates),
            description = context.getString(R.string.desc_dependency_updates),
            iconRes = R.drawable.ic_tools_android_build_sdkmanager,
            fragmentClass = DependencyUpdateFragment::class.java,
            order = ++order,
        )
    )
    EditorToolboxRegistry.register(
        EditorToolboxEntry(
            id = "editor.toolbox.regexPreview",
            title = context.getString(R.string.title_regular_preview),
            description = context.getString(R.string.desc_regular_preview),
            iconRes = R.drawable.ic_widget_grid_layout,
            fragmentClass = RegexPreviewFragment::class.java,
            order = ++order,
        )
    )
    EditorToolboxRegistry.register(
        EditorToolboxEntry(
            id = "editor.toolbox.mcpServer",
            title = context.getString(R.string.title_mcp_server),
            description = context.getString(R.string.desc_mcp_server),
            iconRes = R.drawable.ic_ai_mcp_server,
            fragmentClass = McpFragment::class.java,
            order = ++order,
        )
    )
    EditorToolboxRegistry.register(
        EditorToolboxEntry(
            id = "editor.toolbox.chatRoute",
            title = context.getString(R.string.title_chat_route),
            description = context.getString(R.string.desc_chat_route),
            iconRes = R.drawable.ic_settings,
            fragmentClass = RouteFragment::class.java,
            order = ++order,
        )
    )
    EditorToolboxRegistry.register(
        EditorToolboxEntry(
            id = "editor.toolbox.projectMaterials",
            title = context.getString(R.string.title_project_materials),
            description = context.getString(R.string.desc_project_materials),
            iconRes = R.drawable.ic_folder,
            fragmentClass = ProjectMaterialsFragment::class.java,
            order = ++order,
        )
    )
    EditorToolboxRegistry.register(
        EditorToolboxEntry(
            id = "editor.toolbox.editorProcessApm",
            title = context.getString(R.string.view_apm_panel),
            description = context.getString(R.string.desc_editor_process_apm),
            iconRes = R.drawable.ic_bug,
            fragmentClass = EditorProcessApmFragment::class.java,
            order = ++order,
        )
    )
  }
}
