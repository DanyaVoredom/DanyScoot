package ru.dany.scoot.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.dany.scoot.AiActivity
import ru.dany.scoot.AppTheme
import ru.dany.scoot.BuildConfig
import ru.dany.scoot.R
import ru.dany.scoot.ai.AiFormat
import ru.dany.scoot.ble.DeviceSort
import ru.dany.scoot.ble.ScooterCodec
import ru.dany.scoot.core.LogBus
import ru.dany.scoot.core.Prefs
import ru.dany.scoot.core.ScooterClient
import ru.dany.scoot.core.UpdateManager
import ru.dany.scoot.databinding.FragmentSettingsBinding
import java.util.Locale
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null
    private var loading = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentSettingsBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.settingsAbout.text = getString(
            R.string.settings_about_version,
            getString(R.string.settings_about),
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
        b.settingsCurrentVersion.text = getString(
            R.string.settings_current_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
        b.settingsKeyNineHack.setOnClickListener {
            b.settingsKey.setText(ScooterCodec.KEY_PRESETS[0])
            LogBus.info("SETTINGS", "Выбран ключ NineHack")
        }
        b.settingsKeyLostBot.setOnClickListener {
            b.settingsKey.setText(ScooterCodec.KEY_PRESETS[1])
            LogBus.info("SETTINGS", "Выбран ключ LostBot")
        }
        b.updateCheck.setOnClickListener { checkOrInstall() }
        b.settingsSort.setOnClickListener { showSortDialog() }
        b.settingsAiFormat.setOnClickListener { showAiFormatDialog() }
        b.settingsAiOpen.setOnClickListener {
            startActivity(Intent(requireContext(), AiActivity::class.java))
        }
        setupTheme()
        setupAutosave()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                UpdateManager.status.collect { renderUpdate(it) }
            }
        }
        loadPrefs()
    }

    private fun setupTheme() {
        binding?.themeButton?.setOnClickListener { showThemeDialog() }
    }

    private fun showThemeDialog() {
        val themes = AppTheme.entries
        val labels = themes.map { getString(it.labelRes) }.toTypedArray()
        val current = themes.indexOf(AppTheme.fromId(Prefs.get(requireContext()).theme)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                val theme = themes[which]
                val prefs = Prefs.get(requireContext())
                if (prefs.theme != theme.id) {
                    prefs.theme = theme.id
                    requireActivity().recreate()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSortDialog() {
        val modes = DeviceSort.entries
        val labels = modes.map { getString(sortLabel(it)) }.toTypedArray()
        val prefs = Prefs.get(requireContext())
        val current = modes.indexOf(prefs.deviceSort).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.scan_sort)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                prefs.deviceSort = modes[which]
                renderSort(prefs)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAiFormatDialog() {
        val formats = AiFormat.entries
        val labels = formats.map { getString(it.labelRes) }.toTypedArray()
        val prefs = Prefs.get(requireContext())
        val current = formats.indexOf(AiFormat.fromId(prefs.aiFormat)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_ai_format)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                prefs.aiFormat = formats[which].id
                renderAiFormat(prefs)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupAutosave() {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())
        b.settingsKey.doAfterTextChanged { text ->
            if (!loading) prefs.bleKey = text?.toString().orEmpty()
        }
        b.settingsSpamInterval.doAfterTextChanged { text ->
            if (!loading) text?.toString()?.trim()?.toIntOrNull()?.let { prefs.spamIntervalMs = it }
        }
        b.settingsAutoReconnectAttempts.doAfterTextChanged { text ->
            if (!loading) text?.toString()?.trim()?.toIntOrNull()?.let { prefs.autoReconnectAttempts = it }
        }
        b.settingsAutoReconnectDelay.doAfterTextChanged { text ->
            if (!loading) text?.toString()?.trim()?.toIntOrNull()?.let { prefs.autoReconnectDelayMs = it }
        }
        b.settingsAiBaseUrl.doAfterTextChanged { text ->
            if (!loading) prefs.aiBaseUrl = text?.toString().orEmpty()
        }
        b.settingsAiApiKey.doAfterTextChanged { text ->
            if (!loading) prefs.aiApiKey = text?.toString().orEmpty()
        }
        b.settingsAiModel.doAfterTextChanged { text ->
            if (!loading) prefs.aiModel = text?.toString().orEmpty()
        }
        b.settingsSpamInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !loading) b.settingsSpamInterval.setText(String.format(Locale.ROOT, "%d", prefs.spamIntervalMs))
        }
        b.settingsAutoReconnectAttempts.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !loading) {
                b.settingsAutoReconnectAttempts.setText(String.format(Locale.ROOT, "%d", prefs.autoReconnectAttempts))
            }
        }
        b.settingsAutoReconnectDelay.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !loading) {
                b.settingsAutoReconnectDelay.setText(String.format(Locale.ROOT, "%d", prefs.autoReconnectDelayMs))
            }
        }
        b.settingsAiBaseUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !loading) b.settingsAiBaseUrl.setText(prefs.aiBaseUrl)
        }
        b.settingsAiModel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !loading) b.settingsAiModel.setText(prefs.aiModel)
        }
        b.settingsAutoReconnect.setOnCheckedChangeListener { _, checked ->
            if (!loading) prefs.autoReconnect = checked
        }
        b.settingsAutoResumeSpam.setOnCheckedChangeListener { _, checked ->
            if (!loading) prefs.autoResumeSpam = checked
        }
        b.settingsShowAllDevices.setOnCheckedChangeListener { _, checked ->
            if (!loading) prefs.showAllDevices = checked
        }
        b.settingsHideUnknown.setOnCheckedChangeListener { _, checked ->
            if (!loading) prefs.hideUnknownDevices = checked
        }
        b.settingsShowScanLog.setOnCheckedChangeListener { _, checked ->
            if (!loading) prefs.showScanLog = checked
        }
        b.settingsVerboseLog.setOnCheckedChangeListener { _, checked ->
            if (!loading) {
                prefs.verboseLog = checked
                LogBus.verbose = checked
            }
        }
        b.settingsAutoUpdate.setOnCheckedChangeListener { _, checked ->
            if (!loading) prefs.autoUpdate = checked
        }
    }

    private fun checkOrInstall() {
        val status = UpdateManager.status.value
        if (status is UpdateManager.Status.Ready) {
            val started = UpdateManager.install(requireContext(), status)
            if (!started) {
                Toast.makeText(requireContext(), R.string.settings_update_permission, Toast.LENGTH_LONG).show()
            }
            return
        }
        UpdateManager.check(requireContext())
    }

    private fun renderUpdate(status: UpdateManager.Status) {
        val b = binding ?: return
        b.settingsUpdateStatus.text = when (status) {
            UpdateManager.Status.Idle -> getString(R.string.settings_update_idle)
            UpdateManager.Status.Checking -> getString(R.string.settings_update_checking)
            UpdateManager.Status.UpToDate -> getString(R.string.settings_update_uptodate)
            is UpdateManager.Status.Available -> getString(R.string.settings_update_available, status.version)
            is UpdateManager.Status.Downloading -> getString(R.string.settings_update_downloading, status.percent)
            is UpdateManager.Status.Ready -> getString(R.string.settings_update_ready, status.version)
            is UpdateManager.Status.Failed -> getString(R.string.settings_update_failed, status.message)
        }
        b.updateCheck.text = if (status is UpdateManager.Status.Ready) {
            getString(R.string.settings_install_update)
        } else {
            getString(R.string.settings_check_update)
        }
        b.updateCheck.isEnabled = status !is UpdateManager.Status.Checking &&
            status !is UpdateManager.Status.Downloading
    }

    private fun renderSort(prefs: Prefs) {
        val label = getString(sortLabel(prefs.deviceSort))
        binding?.settingsSort?.text = getString(R.string.scan_sort_value, label)
    }

    private fun renderAiFormat(prefs: Prefs) {
        val label = getString(AiFormat.fromId(prefs.aiFormat).labelRes)
        binding?.settingsAiFormat?.text = getString(R.string.settings_ai_format_value, label)
    }

    private fun sortLabel(mode: DeviceSort): Int = when (mode) {
        DeviceSort.DISTANCE -> R.string.sort_distance
        DeviceSort.NAME -> R.string.sort_name
        DeviceSort.ADDRESS -> R.string.sort_address
        DeviceSort.KIND -> R.string.sort_kind
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) loadPrefs()
    }

    private fun loadPrefs() {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())
        loading = true
        b.themeButton.text = getString(
            R.string.settings_theme_value,
            getString(AppTheme.fromId(prefs.theme).labelRes),
        )
        b.settingsKey.setText(prefs.bleKey)
        b.settingsSpamInterval.setText(String.format(Locale.ROOT, "%d", prefs.spamIntervalMs))
        b.settingsAutoResumeSpam.isChecked = prefs.autoResumeSpam
        b.settingsAutoReconnect.isChecked = prefs.autoReconnect
        b.settingsAutoReconnectAttempts.setText(String.format(Locale.ROOT, "%d", prefs.autoReconnectAttempts))
        b.settingsAutoReconnectDelay.setText(String.format(Locale.ROOT, "%d", prefs.autoReconnectDelayMs))
        b.settingsShowAllDevices.isChecked = prefs.showAllDevices
        b.settingsHideUnknown.isChecked = prefs.hideUnknownDevices
        b.settingsShowScanLog.isChecked = prefs.showScanLog
        b.settingsVerboseLog.isChecked = prefs.verboseLog
        b.settingsAiBaseUrl.setText(prefs.aiBaseUrl)
        b.settingsAiApiKey.setText(prefs.aiApiKey)
        b.settingsAiModel.setText(prefs.aiModel)
        b.settingsAutoUpdate.isChecked = prefs.autoUpdate
        renderSort(prefs)
        renderAiFormat(prefs)
        loading = false
        renderUpdate(UpdateManager.status.value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
