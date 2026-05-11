package com.itsaky.androidide.provider

import android.content.Context
import android.graphics.drawable.Drawable
import android.zero.studio.view.filetree.interfaces.FileIconProvider
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.provider.file
import androidx.core.content.ContextCompat
import com.itsaky.androidide.R
import com.itsaky.androidide.models.FileExtension
import java.io.File

/**
 * 文件图标提供器。
 *
 * @author android_zero
 */
class IDEFileIconProvider(private val context: Context) : FileIconProvider {
  private val chevronRight = ContextCompat.getDrawable(context, R.drawable.ic_chevron_right)
  private val expandMore = ContextCompat.getDrawable(context, R.drawable.ic_chevron_down)

  override fun getIcon(node: Node<FileObject>): Drawable? {
    val fileObj =
        extractNativeFile(node.value)
            ?: return ContextCompat.getDrawable(context, R.drawable.ic_file_type_unknown)

    val iconRes =
        FileExtension.Factory.forFile(fileObj).icon
    return ContextCompat.getDrawable(context, iconRes)
  }

  override fun getChevronRight(): Drawable? = chevronRight

  override fun getExpandMore(): Drawable? = expandMore

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
