package android.zero.studio.chatai.server.mcp.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.zero.studio.chatai.server.mcp.R
import android.zero.studio.chatai.server.mcp.core.ToolControlCenter
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class ToolControlDialogFragment : DialogFragment() {

  data class ToolItem(val name: String, val desc: String)

  private val tools =
      listOf(
          ToolItem("workspace_list", "列出工作区文件/目录"),
          ToolItem("workspace_read_text", "读取文本文件"),
          ToolItem("workspace_write_text", "写入文本文件"),
          ToolItem("workspace_search_text", "全文检索"),
          ToolItem("workspace_analyze", "工作区结构分析"),
          ToolItem("ls", "兼容别名 -> workspace_list"),
          ToolItem("read_file", "兼容别名 -> workspace_read_text"),
          ToolItem("write_file", "兼容别名 -> workspace_write_text"),
          ToolItem("search_code", "兼容别名 -> workspace_search_text"),
          ToolItem("get_project_structure", "兼容别名 -> workspace_analyze"),
      )

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val v = requireActivity().layoutInflater.inflate(R.layout.dialog_mcp_tool_control, null)
    val rv = v.findViewById<RecyclerView>(R.id.toolRecycler)
    rv.layoutManager = LinearLayoutManager(requireContext())
    rv.adapter = ToolControlAdapter(requireContext(), tools)
    return MaterialAlertDialogBuilder(requireContext())
        .setView(v)
        .setPositiveButton("关闭", null)
        .create()
  }

  private class ToolControlAdapter(
      private val ctx: Context,
      private val data: List<ToolItem>,
  ) : RecyclerView.Adapter<ToolControlAdapter.VH>() {

    private val prefs = ctx.getSharedPreferences("McpToolControl", Context.MODE_PRIVATE)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
      val v = LayoutInflater.from(parent.context).inflate(R.layout.item_mcp_tool_control, parent, false)
      return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
      val item = data[position]
      holder.name.text = item.name
      holder.desc.text = item.desc
      val enabled = prefs.getBoolean(item.name, true)
      holder.toggle.setOnCheckedChangeListener(null)
      holder.toggle.isChecked = enabled
      ToolControlCenter.setEnabled(item.name, enabled)

      holder.toggle.setOnCheckedChangeListener { _, isChecked ->
        prefs.edit().putBoolean(item.name, isChecked).apply()
        ToolControlCenter.setEnabled(item.name, isChecked)
      }
    }

    override fun getItemCount(): Int = data.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
      val name = v.findViewById<android.widget.TextView>(R.id.toolName)
      val desc = v.findViewById<android.widget.TextView>(R.id.toolDesc)
      val toggle = v.findViewById<MaterialSwitch>(R.id.toolSwitch)
    }
  }
}
