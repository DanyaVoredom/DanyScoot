package com.dany.fusionbot.ui

import android.graphics.Color
import android.os.Bundle
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
import com.dany.fusionbot.R
import com.dany.fusionbot.core.LogBus
import com.dany.fusionbot.core.LogLevel
import com.dany.fusionbot.core.LogLine
import com.dany.fusionbot.databinding.FragmentLogBinding
import com.dany.fusionbot.databinding.ItemLogBinding
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
            val grew = list.size > old && (old == 0 || list.subList(0, old) == items)
            items.clear()
            items.addAll(list)
            when {
                grew -> notifyItemRangeInserted(old, items.size - old)
                items.isEmpty() -> notifyDataSetChanged()
                else -> notifyDataSetChanged()
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
            val context = holder.binding.root.context
            val color = when (line.level) {
                LogLevel.TX -> ContextCompat.getColor(context, R.color.source_lostbot)
                LogLevel.RX -> ContextCompat.getColor(context, R.color.source_ninehack)
                LogLevel.OK -> ContextCompat.getColor(context, R.color.ok)
                LogLevel.WARN -> ContextCompat.getColor(context, R.color.warn)
                LogLevel.ERR -> ContextCompat.getColor(context, R.color.err)
                LogLevel.INFO -> ContextCompat.getColor(context, R.color.text_primary)
            }
            holder.binding.logLine.text = buildString {
                append(timeFormat.format(Date(line.timeMs))).append(' ')
                append(line.tag).append(": ").append(line.text)
            }
            holder.binding.logLine.setTextColor(color)
            holder.binding.logLine.setBackgroundColor(Color.TRANSPARENT)
        }
    }
}
