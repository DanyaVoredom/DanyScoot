package ru.dany.scoot

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import ru.dany.scoot.ai.AiChat
import ru.dany.scoot.databinding.ActivityAiBinding
import ru.dany.scoot.ui.AiAdapter
import ru.dany.scoot.ui.applyEdgeToEdgeInsets

class AiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiBinding
    private lateinit var adapter: AiAdapter
    private var busyState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppTheme.current(this).styleRes)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityAiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyEdgeToEdgeInsets()
        adapter = AiAdapter()
        binding.aiList.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.aiList.adapter = adapter
        binding.aiToolbar.setNavigationOnClickListener { finish() }
        binding.aiSend.setOnClickListener { send() }
        binding.aiStop.setOnClickListener { AiChat.stop() }
        binding.aiClear.setOnClickListener { confirmClear() }
        binding.aiInput.doAfterTextChanged { renderSend() }
        observeChat()
        renderBusy(AiChat.busy.value)
    }

    private fun observeChat() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AiChat.messages.collect { messages ->
                        adapter.submitList(messages)
                        binding.aiEmpty.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                        if (messages.isNotEmpty()) {
                            binding.aiList.scrollToPosition(messages.lastIndex)
                        }
                    }
                }
                launch { AiChat.busy.collect { renderBusy(it) } }
                launch { AiChat.status.collect { renderStatus(it) } }
            }
        }
    }

    private fun send() {
        val text = binding.aiInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        binding.aiInput.setText("")
        AiChat.send(this, text)
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ai_clear)
            .setMessage(R.string.ai_clear_confirm)
            .setPositiveButton(R.string.ai_clear) { _, _ -> AiChat.clear() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderBusy(busy: Boolean) {
        busyState = busy
        binding.aiStop.visibility = if (busy) View.VISIBLE else View.GONE
        if (!busy) binding.aiStatus.text = ""
        renderSend()
    }

    private fun renderStatus(status: String) {
        if (!busyState) {
            binding.aiStatus.text = ""
            return
        }
        binding.aiStatus.text = status.ifEmpty { getString(R.string.ai_busy) }
    }

    private fun renderSend() {
        val hasText = binding.aiInput.text?.toString()?.isNotBlank() == true
        binding.aiSend.isEnabled = hasText && !busyState
    }
}
