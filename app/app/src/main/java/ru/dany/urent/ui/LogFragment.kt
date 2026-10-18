package ru.dany.urent.ui

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.dany.urent.R
import ru.dany.urent.core.LogBus
import ru.dany.urent.core.LogLevel
import ru.dany.urent.core.LogLine
import ru.dany.urent.databinding.FragmentLogBinding
import ru.dany.urent.databinding.ItemLogBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogFragment : Fragment() {

    private var binding: FragmentLogBinding? = null
    private lateinit var adapter: LogAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentLogBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = LogAdapter()
        binding?.logList?.layoutManager = LinearLayoutManager(requireContext())
        binding?.logList?.adapter = adapter
        binding?.clearLog?.setOnClickListener {
            LogBus.clear()
            adapter.submit(emptyList())
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private inner class LogAdapter : RecyclerView.Adapter<LogAdapter.Holder>() {

        private val items = ArrayList<LogLine>()
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

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
                LogLevel.INFO -> themeColor(root, R.attr.colorLogInfo)
            }
            holder.binding.logLine.text = buildString {
                append(timeFormat.format(Date(line.timeMs))).append(' ')
                append(line.tag).append(": ").append(line.text)
            }
            holder.binding.logLine.setTextColor(color)
            holder.binding.logLine.setBackgroundColor(Color.TRANSPARENT)
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
