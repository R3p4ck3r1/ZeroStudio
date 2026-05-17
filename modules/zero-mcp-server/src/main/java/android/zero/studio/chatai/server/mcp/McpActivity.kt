package android.zero.studio.chatai.server.mcp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.DocumentsContract
import android.widget.Toast
import android.zero.studio.chatai.server.mcp.databinding.ActivityChataiFileMcpServerBinding
import android.zero.studio.chatai.server.mcp.ui.LogAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MCP Server 主控制台 Activity
 *
 * @author android_zero
 */
class McpActivity : AppCompatActivity() {

  private lateinit var binding: ActivityChataiFileMcpServerBinding
  private var mcpService: McpService? = null
  private var isBound = false
  private val logAdapter = LogAdapter()

  private val handler = Handler(Looper.getMainLooper())
  private val updateRunnable =
      object : Runnable {
        override fun run() {
          updateStatusUI()
          handler.postDelayed(this, 1000)
        }
      }

  private val dirPickerLauncher =
      registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { handleSelectedWorkspace(it) }
      }

  private val connection =
      object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
          val binder = service as McpService.LocalBinder
          mcpService = binder.getService()
          isBound = true
          setupServiceObservation()

          mcpService?.currentWorkspace?.let { file ->
            updateWorkspaceUI(file.name, file.absolutePath)
          }
              ?: run {
                val prefs = getSharedPreferences("McpConfig", MODE_PRIVATE)
                val path =
                    prefs.getString("workspace_path", getExternalFilesDir(null)?.absolutePath)
                val name = prefs.getString("workspace_name", "Default")
                if (path != null) {
                  updateWorkspaceUI(name ?: "Unknown", path)
                }
              }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
          mcpService = null
          isBound = false
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityChataiFileMcpServerBinding.inflate(layoutInflater)
    setContentView(binding.root)

    setupUI()
    startService()
  }

  private fun setupUI() {
    // Logs Recycler
    binding.recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
    binding.recyclerView.adapter = logAdapter

    // Buttons
    binding.clearLogs.setOnClickListener { logAdapter.clear() }
    binding.copyLocal.setOnClickListener { copyToClipboard(binding.localUrl.text.toString()) }
    binding.copyWifi.setOnClickListener { copyToClipboard(binding.wifiUrl.text.toString()) }

    // 切换工作区
    binding.switchWorkspace.setOnClickListener { dirPickerLauncher.launch(null) }

    // FAB - 重启/停止服务
    binding.fabAction.setOnClickListener {
      val service = mcpService
      if (service != null && service.isRunning) {
        service.stopMcpServer()
      } else {
        val prefs = getSharedPreferences("McpConfig", MODE_PRIVATE)
        val path = prefs.getString("workspace_path", getExternalFilesDir(null)?.absolutePath)
        if (path != null) {
          // 通过 Service 直接启动
          service?.startMcpServer(File(path))
        } else {
          Toast.makeText(this, "请先选择工作区", Toast.LENGTH_SHORT).show()
        }
      }
      updateStatusUI()
    }

    val wifiIp = getWifiIpAddress()
    binding.wifiUrl.text = "http://$wifiIp:8080/ZeroStudio"
  }

  private fun handleSelectedWorkspace(uri: Uri) {
    try {
      val takeFlags: Int =
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      contentResolver.takePersistableUriPermission(uri, takeFlags)

      val docFile = DocumentFile.fromTreeUri(this, uri)
      val folderName = docFile?.name ?: "Unknown"
      val absolutePath = getPathFromUri(uri) ?: uri.toString() // 如果解析失败，保留 URI 字符串

      updateWorkspaceUI(folderName, absolutePath)

      // 保存配置
      getSharedPreferences("McpConfig", MODE_PRIVATE)
          .edit()
          .putString("workspace_path", absolutePath)
          .putString("workspace_name", folderName)
          .putString("workspace_uri", uri.toString())
          .apply()

      // 通知 Service 更新
      if (mcpService != null) {
        val workspaceFile = File(absolutePath)
        mcpService!!.updateWorkspace(workspaceFile)
        Toast.makeText(this, "工作区已切换: $folderName", Toast.LENGTH_SHORT).show()
      }
    } catch (e: Exception) {
      Toast.makeText(this, "设置工作区失败: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  private fun getPathFromUri(uri: Uri): String? {
    if (DocumentsContract.isTreeUri(uri)) {
      val docId = DocumentsContract.getTreeDocumentId(uri)
      val parts = docId.split(":")
      if (parts.isNotEmpty()) {
        val type = parts[0]
        if ("primary".equals(type, ignoreCase = true)) {
          return getExternalFilesDir(null)
              ?.parentFile
              ?.parentFile
              ?.parentFile
              ?.parentFile
              ?.absolutePath + "/" + parts[1]
        }
      }
    }
    return null
  }

  private fun startService() {
    val intent = Intent(this, McpService::class.java)
    val prefs = getSharedPreferences("McpConfig", MODE_PRIVATE)
    val path = prefs.getString("workspace_path", null)
    if (path != null) {
      intent.putExtra("WORKSPACE_PATH", path)
    }

    ContextCompat.startForegroundService(this, intent)
    bindService(intent, connection, Context.BIND_AUTO_CREATE)
  }

  private fun setupServiceObservation() {
    val service = mcpService ?: return

    lifecycleScope.launch {
      service.logFlow.collectLatest { msg ->
        logAdapter.addLog(msg)
        binding.recyclerView.scrollToPosition(logAdapter.itemCount - 1)
      }
    }
    handler.post(updateRunnable)
  }

  private fun updateStatusUI() {
    val service = mcpService ?: return

    if (service.isRunning) {
      binding.statusText.text = "Active"
      binding.statusText.setTextColor(Color.parseColor("#10B981")) // Green
      binding.fabAction.text = "Stop"
      binding.fabAction.setIconResource(R.drawable.ic_close)
    } else {
      binding.statusText.text = "Stopped"
      binding.statusText.setTextColor(Color.parseColor("#EF4444")) // Red
      binding.fabAction.text = "Start"
      binding.fabAction.setIconResource(R.drawable.ic_run)
    }

    binding.requestCount.text = service.requestCount.get().toString()

    if (service.isRunning && service.startTime > 0) {
      val elapsed = (System.currentTimeMillis() - service.startTime) / 1000
      val h = elapsed / 3600
      val m = (elapsed % 3600) / 60
      val s = elapsed % 60
      binding.runtimeText.text = String.format("%02d:%02d:%02d", h, m, s)
    } else {
      binding.runtimeText.text = "00:00:00"
    }
  }

  private fun updateWorkspaceUI(name: String, path: String) {
    binding.workspaceName.text = name
    binding.workspacePath.text = path
  }

  private fun copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("MCP Address", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
  }

  private fun getWifiIpAddress(): String {
    try {
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces.hasMoreElements()) {
        val networkInterface = interfaces.nextElement()
        if (networkInterface.name.contains("wlan") || networkInterface.name.contains("eth")) {
          val addresses = networkInterface.inetAddresses
          while (addresses.hasMoreElements()) {
            val address = addresses.nextElement()
            if (!address.isLoopbackAddress && address is Inet4Address) {
              return address.hostAddress ?: "Unavailable"
            }
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return "Unavailable"
  }

  override fun onDestroy() {
    super.onDestroy()
    if (isBound) {
      unbindService(connection)
      isBound = false
    }
    handler.removeCallbacks(updateRunnable)
  }
}
