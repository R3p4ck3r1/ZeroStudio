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

package android.zero.studio.lsp.clangd

import com.itsaky.androidide.eventbus.events.Event
import com.itsaky.androidide.lsp.models.DiagnosticResult

/**
 * Clangd 代码诊断事件。
 * 
 * 功能与用途：
 * 封装 [DiagnosticResult]，利用 AndroidIDE 的全局事件总线机制 ([Event]) 进行传递，
 * 以便任意关注特定文件报错的组件（例如 EditorFragment 或底部的 Diagnostics 列表）可以自由订阅。
 *
 * @author android_zero
 */
class ClangdDiagnosticsEvent(val result: DiagnosticResult) : Event()