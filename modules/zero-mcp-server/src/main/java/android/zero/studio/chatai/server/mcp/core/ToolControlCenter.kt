package android.zero.studio.chatai.server.mcp.core

import java.util.concurrent.ConcurrentHashMap

/** 全局工具开关控制中心（进程内）。 */
object ToolControlCenter {
  private val toolEnabled = ConcurrentHashMap<String, Boolean>()

  fun setEnabled(toolName: String, enabled: Boolean) {
    toolEnabled[toolName] = enabled
  }

  fun isEnabled(toolName: String): Boolean = toolEnabled[toolName] ?: true

  fun snapshot(): Map<String, Boolean> = toolEnabled.toMap()
}
