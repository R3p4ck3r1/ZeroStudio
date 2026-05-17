package android.zero.studio.chatai.server.mcp.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

  private val logs = ArrayList<LogEntry>()
  private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

  data class LogEntry(val time: Long, val message: String)

  fun addLog(message: String) {
    if (logs.size > 500) logs.removeAt(0) // 限制日志数量
    logs.add(LogEntry(System.currentTimeMillis(), message))
    notifyItemInserted(logs.size - 1)
  }

  fun clear() {
    logs.clear()
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
    // 使用简单的内置布局，或者你可以创建一个 item_log.xml
    val view =
        LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
    return LogViewHolder(view)
  }

  override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
    val entry = logs[position]
    holder.timeView.text = dateFormat.format(Date(entry.time))
    holder.msgView.text = entry.message

    when {
      entry.message.contains("Error") || entry.message.contains("CRITICAL") ->
          holder.msgView.setTextColor(Color.parseColor("#FF5252")) // Red
      entry.message.contains("Exec") ->
          holder.msgView.setTextColor(Color.parseColor("#4CAF50")) // Green
      entry.message.contains("Client") ->
          holder.msgView.setTextColor(Color.parseColor("#2196F3")) // Blue
      else -> holder.msgView.setTextColor(Color.parseColor("#E0E0E0")) // White/Grey
    }
    holder.timeView.setTextColor(Color.GRAY)
  }

  override fun getItemCount() = logs.size

  class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val timeView: TextView = view.findViewById(android.R.id.text2)
    val msgView: TextView = view.findViewById(android.R.id.text1)
  }
}
