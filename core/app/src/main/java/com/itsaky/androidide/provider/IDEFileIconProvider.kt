package com.itsaky.androidide.provider

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.zero.studio.view.filetree.interfaces.FileIconProvider
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.provider.file
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.itsaky.androidide.R
import com.itsaky.androidide.models.FileExtension
import java.io.File

/**
 * 文件图标提供器。
 *
 * @author android_zero
 */
class IDEFileIconProvider(private val context: Context) : FileIconProvider {
  private val navigationIconTint: Int
    get() {
      val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
      return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
        0xFFFFFFFF.toInt()
      } else {
        0xFF1F2937.toInt()
      }
    }

  private fun getTintedNavigationIcon(resId: Int): Drawable? {
    return ContextCompat.getDrawable(context, resId)?.mutate()?.also { drawable ->
      DrawableCompat.setTint(drawable, navigationIconTint)
    }
  }

  override fun getIcon(node: Node<FileObject>): Drawable? {
    val fileObj =
        extractNativeFile(node.value)
            ?: return ContextCompat.getDrawable(context, R.drawable.ic_file_type_unknown)

    val iconRes =
        if (node.value.isDirectory()) {
          FileExtension.Factory.forDirectoryName(node.value.getName()).icon
        } else {
          FileExtension.Factory.forFile(fileObj).icon
        }
    return ContextCompat.getDrawable(context, iconRes)
  }

  override fun getChevronRight(): Drawable? = getTintedNavigationIcon(R.drawable.ic_chevron_right)

  override fun getExpandMore(): Drawable? = getTintedNavigationIcon(R.drawable.ic_chevron_down)

  companion object {
    fun extractNativeFile(fileObj: FileObject): File? {
      // 支持 android.zero.studio.view.filetree 的原生 file 以及 DataTree 里的自定义虚拟类
      if (fileObj is file) return fileObj.getNativeFile()
      if (fileObj is File) return fileObj
      // 通过反射或路径降级提取
      return File(fileObj.getAbsolutePath())
    }
  }
}
