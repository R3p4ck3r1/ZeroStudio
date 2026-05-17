package android.zero.studio.chatai.server.mcp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.zero.studio.chatai.server.mcp.core.McpHttpServer
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 前台服务，保持 MCP Server 在后台存活
 *
 * @author android_zero
 */
class McpService : Service() {

  private val job = SupervisorJob()
  private val scope = CoroutineScope(Dispatchers.IO + job)
  private var httpServer: McpHttpServer? = null

  // 状态监控
  var isRunning = false
    private set

  var requestCount = AtomicLong(0)
  var startTime = 0L
  var currentWorkspace: File? = null

  private val _logFlow = MutableSharedFlow<String>(replay = 20)
  val logFlow = _logFlow.asSharedFlow()

  inner class LocalBinder : Binder() {
    fun getService(): McpService = this@McpService
  }

  private val binder = LocalBinder()

  override fun onBind(intent: Intent?): IBinder = binder

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val workspacePath = intent?.getStringExtra("WORKSPACE_PATH")
    if (workspacePath != null) {
      val dir = File(workspacePath)
      // 如果目录不存在，尝试创建（针对应用私有目录等）
      if (!dir.exists()) dir.mkdirs()
      startMcpServer(dir)
    }
    startForegroundService()
    return START_STICKY
  }

  // 更新工作区并重启服务器
  fun updateWorkspace(newWorkspace: File) {
    if (currentWorkspace?.absolutePath == newWorkspace.absolutePath && isRunning) {
      scope.launch { _logFlow.emit("Workspace is already set to: ${newWorkspace.name}") }
      return
    }

    scope.launch { _logFlow.emit("Updating workspace to: ${newWorkspace.absolutePath}") }
    startMcpServer(newWorkspace)
  }

  fun startMcpServer(workspace: File) {
    stopMcpServer()

    try {
      currentWorkspace = workspace
      httpServer =
          McpHttpServer(8080, workspace) { message ->
            scope.launch {
              _logFlow.emit(message)
              if (message.startsWith("Exec") || message.startsWith("Client")) {
                requestCount.incrementAndGet()
              }
            }
          }
      httpServer?.start(5000, false) // 5秒超时
      isRunning = true
      startTime = System.currentTimeMillis()
      scope.launch { _logFlow.emit("Server started on port 8080\nWorkspace: ${workspace.name}") }

      // 更新前台通知内容
      updateNotification("Active: ${workspace.name}")
    } catch (e: Exception) {
      scope.launch { _logFlow.emit("CRITICAL: Failed to start server: ${e.message}") }
      isRunning = false
      // 即使失败也保持前台服务，以便用户查看日志
    }
  }

  fun stopMcpServer() {
    if (httpServer != null && httpServer!!.isAlive) {
      httpServer?.stop()
      scope.launch { _logFlow.emit("Server stopped.") }
    }
    isRunning = false
    updateNotification("Stopped")
  }

  override fun onDestroy() {
    stopMcpServer()
    job.cancel()
    super.onDestroy()
  }

  private fun startForegroundService() {
    val channelId = "mcp_service_core"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
          NotificationChannel(channelId, "MCP Core Service", NotificationManager.IMPORTANCE_LOW)
      getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    val notification = createNotification("Initializing...")
    startForeground(1, notification)
  }

  private fun updateNotification(contentText: String) {
    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.notify(1, createNotification(contentText))
  }

  private fun createNotification(contentText: String): android.app.Notification {
    val channelId = "mcp_service_core"
    val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
    val pendingIntent =
        PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

    return NotificationCompat.Builder(this, channelId)
        .setContentTitle("MCP Server")
        .setContentText(contentText)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .build()
  }
}
