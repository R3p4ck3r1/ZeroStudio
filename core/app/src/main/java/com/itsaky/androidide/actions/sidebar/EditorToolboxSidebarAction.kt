package com.itsaky.androidide.actions.sidebar

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.itsaky.androidide.R
import com.itsaky.androidide.fragments.toolbox.EditorToolboxFragment
import kotlin.reflect.KClass

/** Sidebar entry that hosts the editor toolbox in the editor's left drawer. */
class EditorToolboxSidebarAction(context: Context, override val order: Int) : AbstractSidebarAction() {

  companion object {
    const val ID = "ide.editor.sidebar.toolbox"
  }

  override val id: String = ID
  override val fragmentClass: KClass<out Fragment> = EditorToolboxFragment::class

  init {
    label = context.getString(com.itsaky.androidide.resources.R.string.title_editor_toolbox)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_view_grid)
  }
}
