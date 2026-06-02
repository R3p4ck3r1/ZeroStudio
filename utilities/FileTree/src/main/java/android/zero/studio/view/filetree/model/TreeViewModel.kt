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

package android.zero.studio.view.filetree.model

import android.zero.studio.view.filetree.interfaces.FileObject

/** @author android_zero */
data class Node<T>(
    var value: T,
    var parent: Node<T>? = null,
    var child: List<Node<T>>? = null,
    var isExpand: Boolean = false,
    var level: Int = 0,
    var isSelected: Boolean = false,
    var isHighlighted: Boolean = false,
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Node<*>

    return value.contentKey() == other.value.contentKey() &&
        parent.nodeKey() == other.parent.nodeKey() &&
        child.nodeKeys() == other.child.nodeKeys() &&
        isExpand == other.isExpand &&
        level == other.level &&
        isSelected == other.isSelected &&
        isHighlighted == other.isHighlighted
  }

  override fun hashCode(): Int {
    var result = value.contentKey().hashCode()
    result = 31 * result + parent.nodeKey().hashCode()
    result = 31 * result + child.nodeKeys().hashCode()
    result = 31 * result + isExpand.hashCode()
    result = 31 * result + level
    result = 31 * result + isSelected.hashCode()
    result = 31 * result + isHighlighted.hashCode()
    return result
  }

  fun deleteChild(childNode: Node<T>) {
    val currentChildren = child?.toMutableList() ?: return
    currentChildren.remove(childNode)
    child = currentChildren
  }

  fun addChild(childNode: Node<T>) {
    val currentChildren = child?.toMutableList() ?: mutableListOf()
    childNode.parent = this
    childNode.level = this.level + 1
    currentChildren.add(childNode)
    child = currentChildren
  }
}

private fun Any?.contentKey(): Any? =
    when (this) {
      is FileObject -> fileObjectKey()
      else -> this
    }

private fun FileObject.fileObjectKey(): FileObjectKey =
    FileObjectKey(
        absolutePath = getAbsolutePath(),
        name = getName(),
        isDirectory = isDirectory(),
        isFile = isFile(),
    )

private fun Node<*>?.nodeKey(): Any? = this?.value.contentKey()

private fun List<Node<*>>?.nodeKeys(): List<Any?>? = this?.map { it.value.contentKey() }

private data class FileObjectKey(
    val absolutePath: String,
    val name: String,
    val isDirectory: Boolean,
    val isFile: Boolean,
)

object TreeViewModel {

  // add child node
  fun <T> add(parent: Node<T>, child: List<Node<T>>? = null) {
    // check
    child?.let { if (it.isNotEmpty()) parent.isExpand = true }

    parent.parent?.let {
      val nodes = it.child
      if (
          nodes != null && nodes.size == 1 && ((child != null && child.isEmpty()) || child == null)
      ) {
        parent.isExpand = true
      }
    }

    // parent associate with child
    parent.child = child

    child?.forEach {
      it.parent = parent
      it.level = parent.level + 1
    }
  }

  // remove child node
  fun <T> remove(parent: Node<T>, child: List<Node<T>>? = null) {
    parent.child?.let {
      if (it.isNotEmpty()) {
        parent.isExpand = false
      }
    }
    parent.child = null

    child?.forEach { childNode ->
      childNode.parent = null
      childNode.level = 0
      if (childNode.isExpand) {
        childNode.isExpand = false
        childNode.child?.let { listNodes -> remove(childNode, listNodes) }
      }
    }
  }

  private fun <T> getChildren(parent: Node<T>, result: MutableList<Node<T>>): List<Node<T>> {
    parent.child?.let { result.addAll(it) }
    parent.child?.forEach {
      if (it.isExpand) {
        getChildren(it, result)
      }
    }
    return result
  }

  fun <T> getChildren(parent: Node<T>) = getChildren(parent, mutableListOf())
}
