package ru.dany.scoot.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.dany.scoot.R
import ru.dany.scoot.core.LogBus
import ru.dany.scoot.core.LogLevel
import ru.dany.scoot.core.LogLine
import ru.dany.scoot.databinding.FragmentLogBinding
import ru.dany.scoot.databinding.ItemLogBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogFragment : Fragment() {

    private var binding: FragmentLogBinding? = null
    private lateinit var adapter: LogAdapter
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentLogBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = LogAdapter { line -> copyLine(line) }
        binding?.logList?.layoutManager = LinearLayoutManager(requireContext())
        binding?.logList?.adapter = adapter
        binding?.clearLog?.setOnClickListener {
            LogBus.clear()
            adapter.submit(emptyList())
        }
        binding?.copyLog?.setOnClickListener { copyAll() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LogBus.lines.collect { list ->
                    adapter.submit(list)
                    if (binding?.autoscroll?.isChecked == true && list.isNotEmpty()) {
                        binding?.logList?.scrollToPosition(list.size - 1)
                    }
                }
            }
        }
    }

    private fun copyAll() {
        val list = LogBus.lines.value
        if (list.isEmpty()) return
        copy(list.joinToString("\n") { format(it) }, R.string.log_copied)
    }

    private fun copyLine(line: LogLine) {
        copy(format(line), R.string.log_line_copied)
    }

    private fun format(line: LogLine): String =
        "${timeFormat.format(Date(line.timeMs))} ${line.tag}: ${line.text}"

    private fun copy(text: String, message: Int) {
        val manager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("DanyScoot log", text))
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private inner class LogAdapter(
        private val onLongClick: (LogLine) -> Unit,
    ) : RecyclerView.Adapter<LogAdapter.Holder>() {

        private val items = ArrayList<LogLine>()

        fun submit(list: List<LogLine>) {
            val old = items.size
            val samePrefix = old > 0 && list.size >= old && list.subList(0, old) == items
            val slide = old > 1 && list.size == old && list.subList(1, old) == items.subList(1, old)
            when {
                list.isEmpty() -> {
                    items.clear()
                    notifyDataSetChanged()
                }
                samePrefix && list.size > old -> {
                    items.clear()
                    items.addAll(list)
                    notifyItemRangeInserted(old, list.size - old)
                }
                slide -> {
                    items.removeAt(0)
                    items.add(list.last())
                    notifyItemRemoved(0)
                    notifyItemInserted(items.size - 1)
                }
                list == items -> Unit
                else -> {
                    items.clear()
                    items.addAll(list)
                    notifyDataSetChanged()
                }
            }
        }

        inner class Holder(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val line = items[position]
            val root = holder.binding.root
            val color = when (line.level) {
                LogLevel.TX -> themeColor(root, R.attr.colorLogTx)
                LogLevel.RX -> themeColor(root, R.attr.colorLogRx)
                LogLevel.OK -> themeColor(root, R.attr.colorLogOk)
                LogLevel.WARN -> themeColor(root, R.attr.colorLogWarn)
                LogLevel.ERR -> themeColor(root, R.attr.colorLogErr)
                LogLevel.DEBUG -> themeColor(root, R.attr.colorLogDebug)
                LogLevel.VERBOSE -> themeColor(root, R.attr.colorLogVerbose)
                LogLevel.INFO -> themeColor(root, R.attr.colorLogInfo)
            }
            holder.binding.logLine.text = format(line)
            holder.binding.logLine.setTextColor(color)
            holder.binding.logLine.setBackgroundColor(Color.TRANSPARENT)
            root.setOnLongClickListener {
                onLongClick(line)
                true
            }
        }

        private fun themeColor(view: View, attr: Int): Int {
            val value = TypedValue()
            if (!view.context.theme.resolveAttribute(attr, value, true)) {
                return ContextCompat.getColor(view.context, R.color.text_primary)
            }
            return if (value.resourceId != 0) {
                ContextCompat.getColor(view.context, value.resourceId)
            } else {
                value.data
            }
        }
    }
}
