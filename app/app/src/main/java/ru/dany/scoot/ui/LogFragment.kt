package ru.dany.scoot.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import ru.dany.scoot.R
import ru.dany.scoot.core.DiagnosticsReport
import ru.dany.scoot.core.LogBus
import ru.dany.scoot.core.LogLevel
import ru.dany.scoot.core.LogLine
import ru.dany.scoot.core.Prefs
import ru.dany.scoot.core.Redactor
import ru.dany.scoot.core.ReportUploader
import ru.dany.scoot.databinding.FragmentLogBinding
import ru.dany.scoot.databinding.ItemLogBinding

class LogFragment : Fragment() {

    private var binding: FragmentLogBinding? = null
    private lateinit var adapter: LogAdapter
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var tagFilter = ""
    private var levelFilter: LogLevel? = null
    private var allLines: List<LogLine> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentLogBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        adapter = LogAdapter { line -> copy(format(line), R.string.log_line_copied) }
        b.logList.layoutManager = LinearLayoutManager(requireContext())
        b.logList.adapter = adapter
        b.clearLog.setOnClickListener { LogBus.clear() }
        b.copyLog.setOnClickListener { copyFiltered() }
        b.exportLog.setOnClickListener { shareFiltered() }
        b.copyEnvironment.setOnClickListener { copyEnvironment() }
        b.createReport.setOnClickListener { shareReport() }
        b.reportUpload.setOnClickListener { uploadReport() }
        b.logTagFilter.setOnEditorActionListener { _, _, _ -> updateFilter(); false }
        b.logTagFilter.setOnFocusChangeListener { _, _ -> updateFilter() }
        setupLevelChips(b)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { LogBus.lines.collect { allLines = it; updateFilter() } }
                launch { ReportUploader.status.collect { renderUploadStatus(it) } }
            }
        }
    }

    private fun setupLevelChips(b: FragmentLogBinding) {
        val chips = listOf(
            b.levelAll to null,
            b.levelVerbose to LogLevel.VERBOSE,
            b.levelDebug to LogLevel.DEBUG,
            b.levelInfo to LogLevel.INFO,
            b.levelTx to LogLevel.TX,
            b.levelRx to LogLevel.RX,
            b.levelOk to LogLevel.OK,
            b.levelWarn to LogLevel.WARN,
            b.levelErr to LogLevel.ERR,
        )
        chips.forEach { (chip, level) ->
            chip.setOnClickListener {
                levelFilter = level
                chips.forEach { (other, _) -> other.isChecked = other === chip }
                updateFilter()
            }
        }
        b.levelAll.isChecked = true
    }

    private fun updateFilter() {
        val b = binding ?: return
        val typed = b.logTagFilter.text?.toString().orEmpty().trim()
        if (typed != tagFilter) tagFilter = typed
        val filtered = allLines.filter { line ->
            (tagFilter.isEmpty() || line.tag.contains(tagFilter, ignoreCase = true)) &&
                (levelFilter == null || line.level == levelFilter)
        }
        adapter.submit(filtered)
        if (binding?.autoscroll?.isChecked == true && filtered.isNotEmpty()) {
            b.logList.scrollToPosition(filtered.size - 1)
        }
    }

    private fun copyFiltered() {
        if (adapter.items.isEmpty()) return
        copy(adapter.items.joinToString("\n") { format(it) }, R.string.log_copied)
    }

    private fun shareFiltered() {
        if (adapter.items.isEmpty()) return
        val text = adapter.items.joinToString("\n") { format(it) }
        shareTextFile("danyscoot-log", text, "text/plain")
    }

    private fun copyEnvironment() {
        copy(DiagnosticsReport.environment(requireContext()), R.string.environment_copied)
    }

    private fun shareReport() {
        val report = DiagnosticsReport.build(requireContext())
        val dir = File(requireContext().cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "danyscoot-report-${report.createdAt}.txt").apply {
            writeText(report.text, Charsets.UTF_8)
        }
        shareFile(file, "text/plain", R.string.report_created)
    }

    private fun uploadReport() {
        val report = DiagnosticsReport.build(requireContext())
        val message = ReportUploader.enqueue(requireContext(), report)
        Toast.makeText(
            requireContext(),
            message ?: getString(R.string.report_queued),
            Toast.LENGTH_SHORT,
        ).show()
        if (message == null) LogBus.info("REPORT", "Создан диагностический отчёт")
    }

    private fun shareTextFile(name: String, text: String, mime: String) {
        val dir = File(requireContext().cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "$name-${System.currentTimeMillis()}.txt").apply {
            writeText(text, Charsets.UTF_8)
        }
        shareFile(file, mime, R.string.log_shared)
    }

    private fun shareFile(file: File, mime: String, message: Int) {
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_title)))
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun renderUploadStatus(status: ReportUploader.Status) {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())
        val lastId = prefs.reportLastId
        val base = when (status) {
            ReportUploader.Status.Idle ->
                if (lastId.isNotEmpty()) getString(R.string.report_last_id, lastId)
                else getString(R.string.report_upload_idle)
            ReportUploader.Status.Waiting -> getString(R.string.report_upload_waiting)
            ReportUploader.Status.Uploading -> getString(R.string.report_uploading)
            is ReportUploader.Status.Success -> getString(R.string.report_upload_success, status.id)
            is ReportUploader.Status.Failed -> getString(R.string.report_upload_failed, status.message)
        }
        val pending = ReportUploader.pendingCount(requireContext())
        b.reportStatus.text = if (pending > 0) "$base · ${getString(R.string.report_pending, pending)}" else base
    }

    private fun format(line: LogLine): String =
        "${timeFormat.format(Date(line.timeMs))} ${line.level.name} ${line.tag}: " +
            Redactor.text(line.text, DiagnosticsReport.secretsOf(Prefs.get(requireContext())))

    private fun copy(text: String, message: Int) {
        val manager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("DanyScoot log", text))
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private inner class LogAdapter(private val onLongClick: (LogLine) -> Unit) : RecyclerView.Adapter<LogAdapter.Holder>() {
        val items = ArrayList<LogLine>()

        fun submit(list: List<LogLine>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        inner class Holder(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

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
            root.setOnLongClickListener { onLongClick(line); true }
        }

        private fun themeColor(view: View, attr: Int): Int {
            val value = TypedValue()
            if (!view.context.theme.resolveAttribute(attr, value, true)) {
                return ContextCompat.getColor(view.context, R.color.text_primary)
            }
            return if (value.resourceId != 0) ContextCompat.getColor(view.context, value.resourceId) else value.data
        }
    }
}
