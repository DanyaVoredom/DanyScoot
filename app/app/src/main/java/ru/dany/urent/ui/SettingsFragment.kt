package ru.dany.urent.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.dany.urent.AppTheme
import ru.dany.urent.BuildConfig
import ru.dany.urent.R
import ru.dany.urent.ble.ScooterCodec
import ru.dany.urent.core.LogBus
import ru.dany.urent.core.Prefs
import ru.dany.urent.core.ScooterClient
import ru.dany.urent.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null

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
        b.settingsKeyNineHack.setOnClickListener {
            b.settingsKey.setText(ScooterCodec.KEY_PRESETS[0])
            LogBus.info("SETTINGS", "Ключ NineHack: ${ScooterCodec.KEY_PRESETS[0]}")
        }
        b.settingsKeyLostBot.setOnClickListener {
            b.settingsKey.setText(ScooterCodec.KEY_PRESETS[1])
            LogBus.info("SETTINGS", "Ключ LostBot: ${ScooterCodec.KEY_PRESETS[1]}")
        }
        b.settingsSave.setOnClickListener { save() }
        setupTheme()
        loadPrefs()
    }

    private fun setupTheme() {
        val b = binding ?: return
        b.themeButton.setOnClickListener { showThemeDialog() }
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

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) loadPrefs()
    }

    private fun loadPrefs() {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())
        b.themeButton.text = getString(
            R.string.settings_theme_value,
            getString(AppTheme.fromId(prefs.theme).labelRes),
        )
        b.settingsKey.setText(prefs.bleKey)
        b.settingsSpamInterval.setText(prefs.spamIntervalMs.toString())
        b.settingsAutoResumeSpam.isChecked = prefs.autoResumeSpam
        b.settingsAutoReconnect.isChecked = prefs.autoReconnect
        b.settingsAutoReconnectAttempts.setText(prefs.autoReconnectAttempts.toString())
        b.settingsAutoReconnectDelay.setText(prefs.autoReconnectDelayMs.toString())
        b.settingsShowAllDevices.isChecked = prefs.showAllDevices
        b.settingsShowScanLog.isChecked = prefs.showScanLog
        b.settingsShowAllOpcodes.isChecked = prefs.showAllOpcodes
        b.settingsDemoMode.isChecked = prefs.demoMode
    }

    private fun save() {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())
        prefs.bleKey = b.settingsKey.text.toString()
        prefs.spamIntervalMs = b.settingsSpamInterval.text.toString().trim().toIntOrNull() ?: prefs.spamIntervalMs
        prefs.autoResumeSpam = b.settingsAutoResumeSpam.isChecked
        prefs.autoReconnect = b.settingsAutoReconnect.isChecked
        prefs.autoReconnectAttempts = b.settingsAutoReconnectAttempts.text.toString().trim().toIntOrNull()
            ?: prefs.autoReconnectAttempts
        prefs.autoReconnectDelayMs = b.settingsAutoReconnectDelay.text.toString().trim().toIntOrNull()
            ?: prefs.autoReconnectDelayMs
        prefs.showAllDevices = b.settingsShowAllDevices.isChecked
        prefs.showScanLog = b.settingsShowScanLog.isChecked
        prefs.showAllOpcodes = b.settingsShowAllOpcodes.isChecked
        val demoChanged = prefs.demoMode != b.settingsDemoMode.isChecked
        prefs.demoMode = b.settingsDemoMode.isChecked
        b.settingsKey.setText(prefs.bleKey)
        b.settingsSpamInterval.setText(prefs.spamIntervalMs.toString())
        b.settingsAutoReconnectAttempts.setText(prefs.autoReconnectAttempts.toString())
        b.settingsAutoReconnectDelay.setText(prefs.autoReconnectDelayMs.toString())
        if (demoChanged) {
            ScooterClient.disconnect()
        }
        LogBus.ok("SETTINGS", "Настройки сохранены")
        Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
