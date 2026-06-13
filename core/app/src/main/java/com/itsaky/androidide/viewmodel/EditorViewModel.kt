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
package com.itsaky.androidide.viewmodel

import android.view.Gravity.CENTER
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.FileUtils
import com.google.gson.GsonBuilder
import com.itsaky.androidide.models.OpenedFilesCache
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.ILogger
import java.io.File
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EditorViewModel : ViewModel() {

  internal val _isBuildInProgress = MutableLiveData(false)
  internal val _isInitializing = MutableLiveData(false)
  internal val _statusText = MutableLiveData<Pair<CharSequence, Int>>("" to CENTER)

  internal val _displayedFile = MutableLiveData(-1)
  internal val _startDrawerOpened = MutableLiveData(false)
  internal val _isSyncNeeded = MutableLiveData(false)
  internal val _filesModified = MutableLiveData(false)
  internal val _filesSaving = MutableLiveData(false)

  @PublishedApi internal val _openedFiles = MutableLiveData<OpenedFilesCache>()
  @PublishedApi internal val _isBoundToBuildService = MutableLiveData(false)
  @PublishedApi internal val _files = MutableLiveData<MutableList<File>>(ArrayList())

  @PublishedApi internal val mCurrentFile = MutableLiveData<Pair<Int, File?>?>(null)

  @PublishedApi internal val fileIoMutex = Mutex()

  var areFilesModified: Boolean
    get() = _filesModified.value ?: false
    set(value) {
      _filesModified.value = value
    }

  var areFilesSaving: Boolean
    get() = _filesSaving.value ?: false
    set(value) {
      _filesSaving.value = value
    }

  var openedFilesCache: OpenedFilesCache?
    get() = _openedFiles.value
    set(value) {
      this._openedFiles.value = value
    }

  var isBoundToBuildSerice: Boolean
    get() = _isBoundToBuildService.value ?: false
    set(value) {
      _isBoundToBuildService.value = value
    }

  var isBuildInProgress: Boolean
    get() = _isBuildInProgress.value ?: false
    set(value) {
      _isBuildInProgress.value = value
    }

  var isInitializing: Boolean
    get() = _isInitializing.value ?: false
    set(value) {
      _isInitializing.value = value
    }

  var statusText: CharSequence
    get() = this._statusText.value?.first ?: ""
    set(value) {
      _statusText.value = value to (_statusText.value?.second ?: 0)
    }

  var statusGravity: Int
    get() = this._statusText.value?.second ?: CENTER
    set(value) {
      _statusText.value = (_statusText.value?.first ?: "") to value
    }

  var displayedFileIndex: Int
    get() = _displayedFile.value!!
    set(value) {
      _displayedFile.value = value
    }

  var startDrawerOpened: Boolean
    get() = _startDrawerOpened.value ?: false
    set(value) {
      _startDrawerOpened.value = value
    }

  var isSyncNeeded: Boolean
    get() = _isSyncNeeded.value ?: false
    set(value) {
      _isSyncNeeded.value = value
    }

  internal var files: MutableList<File>
    get() = this._files.value ?: Collections.emptyList()
    set(value) {
      this._files.value = value
    }

  private inline fun updateFiles(crossinline action: (files: MutableList<File>) -> Unit) {
    val currentFiles = this.files
    action(currentFiles)
    this.files = currentFiles
  }

  fun addFile(file: File) = updateFiles { it.add(file) }

  fun removeFile(index: Int) = updateFiles {
    it.removeAt(index)
    if (it.isEmpty()) mCurrentFile.value = null
  }

  fun removeAllFiles() = updateFiles {
    it.clear()
    setCurrentFile(-1, null)
  }

  fun setCurrentFile(index: Int, file: File?) {
    displayedFileIndex = index
    mCurrentFile.value = index to file
  }

  fun updateFile(index: Int, newFile: File) = updateFiles { it[index] = newFile }

  fun getOpenedFile(index: Int): File = files[index]

  fun getOpenedFileCount(): Int = files.size

  fun getOpenedFiles(): List<File> = Collections.unmodifiableList(files)

  fun observeFiles(owner: LifecycleOwner?, obs: Observer<MutableList<File>?>?) =
      _files.observe(owner!!, obs!!)

  fun getCurrentFileIndex(): Int = mCurrentFile.value?.first ?: -1

  fun getCurrentFile(): File? = mCurrentFile.value?.second

  inline fun getOrReadOpenedFilesCache(crossinline result: (OpenedFilesCache?) -> Unit) {
    openedFilesCache?.let {
      result(it)
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      val cache = fileIoMutex.withLock {
        try {
          val cacheFile = getOpenedFilesCache(false)
          if (cacheFile.exists() && cacheFile.length() > 0L) {
            cacheFile.bufferedReader().use(OpenedFilesCache::parse)
          } else null
        } catch (err: IOException) {
          null
        }
      }
      withContext(Dispatchers.Main.immediate) { result(cache) }
    }
  }

  fun writeOpenedFiles(cache: OpenedFilesCache?) {
    viewModelScope.launch(Dispatchers.IO) {
      fileIoMutex.withLock {
        try {
          val file = getOpenedFilesCache(true)
          if (cache == null) {
            if (file.exists()) file.delete()
            return@withLock
          }

          file.parentFile?.let { if (!it.exists()) it.mkdirs() }
          if (!file.exists()) file.createNewFile()

          val gson = GsonBuilder().setPrettyPrinting().create()
          file.writeText(gson.toJson(cache))
        } catch (e: Exception) {
          ILogger.ROOT.error("Error in writeOpenedFiles", e)
        }
      }
    }
  }

  @PublishedApi
  internal fun getOpenedFilesCache(forWrite: Boolean = false): File {
    // 治本：projectDir 改 nullable 后，cache 文件路径在没有打开工程时无意义。
    // 用 requireProjectDir() 显式表达"必须有工程" — 行为等价于"未打开工程时抛
    // IllegalStateException"，与原 Objects.requireNonNull 的语义一致。
    val dir = Environment.getProjectCacheDir(IProjectManager.getInstance().requireProjectDir())
    val file = File(dir, "editor/openedFiles.json")

    if (file.exists() && forWrite) {
      try {
        FileUtils.rename(file, "${file.name}.bak")
      } catch (e: Exception) {}
    }
    file.parentFile?.let { if (!it.exists()) it.mkdirs() }
    return file
  }
}
