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

package com.itsaky.androidide.editor.utils

import io.github.rosemoe.sora.text.CharArrayWrapper
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentLockAccessor
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.floor

/**
 * Utility methods for reading and writing [Content] objects. This class is similar to
 * [ContentIO][io.github.rosemoe.sora.text.ContentIO] but provides ability to track the progress.
 *
 * @author Akash Yadav
 */
object ContentReadWrite {

  /**
   * Write this [Content] to the given [File].
   *
   * @param progressConsumer A function which is invoked to notify about the write progress.
   */
  @JvmStatic
  fun Content.writeTo(file: File, progressConsumer: ((Int) -> Unit)? = null) {
    val consumer = discreteProgressConsumer(progressConsumer = progressConsumer)
    val parent = file.parentFile
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw RuntimeException("Failed to create parent directory for file: ${file.absolutePath}")
    }
    val outputDir = parent ?: file.absoluteFile.parentFile
    val tempFile =
        File.createTempFile("${file.name}.", ".tmp", outputDir).apply { deleteOnExit() }

    tempFile.writer().buffered(DEFAULT_BUFFER_SIZE * 2).use { writer ->
      val lastLine = lineCount - 1
      val contentLength = length.takeIf { it > 0 } ?: 1

      ContentLockAccessor.lock(this, false)
      var totalWrote = 0.0
      try {
        for (lineIdx in 0..lastLine) {
          val line = getLine(lineIdx)
          writer.write(line.backingCharArray, 0, line.length)

          val separatorChars = line.lineSeparator.chars
          writer.write(separatorChars)

          totalWrote += line.length + separatorChars.size
          val saveProgress = (totalWrote / contentLength) * 100
          consumer(floor(saveProgress).toInt())
        }
      } catch (err: IOException) {
        throw RuntimeException(
            "Failed to write editor's content to file: ${file.absolutePath}",
            err,
        )
      } finally {
        ContentLockAccessor.unlock(this, false)
        consumer(100)
      }

      writer.flush()
    }

    try {
      try {
        Files.move(
            tempFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
      } catch (_: AtomicMoveNotSupportedException) {
        Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
      }
    } catch (err: IOException) {
      throw RuntimeException(
          "Failed to replace file with written content: ${file.absolutePath}",
          err,
      )
    } finally {
      if (tempFile.exists()) {
        tempFile.delete()
      }
    }
  }

  /**
   * Reads this file's content to a new [Content] object.
   *
   * @param progressConsumer A function to consume the read progress.
   */
  @JvmStatic
  fun File.readContent(progressConsumer: ((Int) -> Unit)? = null): Content {
    val consumer = discreteProgressConsumer(progressConsumer = progressConsumer)
    return Content().apply {
      isUndoEnabled = false
      inputStream().use { input ->
        val total = input.available().let { if (it == 0) 1 else it } // avoid divide by 0
        input.reader().use { reader ->
          val buffer = CharArray(DEFAULT_BUFFER_SIZE * 2)
          val wrapper = CharArrayWrapper(buffer, 0)
          var totalRead = 0.0
          var count: Int
          while (true) {
            count = reader.read(buffer)
            if (count == -1) {
              break
            }
            if (count == 0) {
              continue
            }

            totalRead += count

            val progress = floor((totalRead / total) * 100).toInt()

            if (buffer[count - 1] == '\r') {
              val peek = reader.read()
              if (peek == '\n'.code) {
                wrapper.setDataCount(count - 1)
                var line = lineCount - 1
                insert(line, getColumnCount(line), wrapper)

                line = lineCount - 1
                insert(line, getColumnCount(line), "\r\n")
                consumer(progress)
                continue
              } else if (peek != -1) {
                wrapper.setDataCount(count)
                var line = lineCount - 1
                insert(line, getColumnCount(line), wrapper)

                line = lineCount - 1
                insert(line, getColumnCount(line), peek.toChar().toString())
                consumer(progress)
                continue
              }
            }
            wrapper.setDataCount(count)

            val line = lineCount - 1
            insert(line, getColumnCount(line), wrapper)

            consumer(progress)
          }
        }
        isUndoEnabled = true
      }
    }
  }

  @JvmStatic
  private fun discreteProgressConsumer(
      stepSize: Int = 5,
      progressConsumer: ((Int) -> Unit)?,
  ): (Int) -> Unit {
    var lastProgress = -1
    val consumer =
        fun(progress: Int) {
          if (lastProgress == -1 || progress >= 100 || progress - lastProgress >= stepSize) {
            progressConsumer?.invoke(progress)
            lastProgress = progress
          }
        }

    return consumer
  }
}
