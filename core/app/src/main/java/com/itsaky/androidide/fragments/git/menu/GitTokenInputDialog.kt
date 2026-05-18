package com.itsaky.androidide.fragments.git.menu

import android.content.Context
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.fragments.git.GitCredentialManager

class GitTokenInputDialog(private val context: Context) {

  fun show(onSaved: (() -> Unit)? = null) {
    val initial = GitCredentialManager.read(context)
    val density = context.resources.displayMetrics.density
    val pad = (20 * density).toInt()

    val container =
        LinearLayout(context).apply {
          orientation = LinearLayout.VERTICAL
          setPadding(pad, (8 * density).toInt(), pad, 0)
        }

    val tokenLayout =
        TextInputLayout(context).apply {
          hint = context.getString(R.string.git_token_input_hint)
          endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
    val tokenEt = TextInputEditText(context).apply { setText(initial.token) }
    tokenLayout.addView(tokenEt)
    container.addView(tokenLayout)

    MaterialAlertDialogBuilder(context)
        .setTitle(context.getString(R.string.git_token_dialog_title))
        .setMessage(context.getString(R.string.git_token_dialog_message))
        .setView(container)
        .setPositiveButton(context.getString(R.string.git_save)) { _, _ ->
          GitCredentialManager.saveToken(context, tokenEt.text?.toString().orEmpty())
          onSaved?.invoke()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
  }
}
