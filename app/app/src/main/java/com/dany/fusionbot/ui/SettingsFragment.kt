package com.dany.fusionbot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.dany.fusionbot.BuildConfig
import com.dany.fusionbot.R
import com.dany.fusionbot.ble.ScooterCodec
import com.dany.fusionbot.core.LogBus
import com.dany.fusionbot.core.Prefs
import com.dany.fusionbot.core.ScooterClient
import com.dany.fusionbot.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentSettingsBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.settingsAbout.text = getString(R.string.settings_about) + "\n\nFusionBot " +
            BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
        b.settingsKeyNineHack.setOnClickListener {
            b.settingsKey.setText(ScooterCodec.KEY_PRESETS[0])
            LogBus.info("SETTINGS", "Ключ NineHack: ${ScooterCodec.KEY_PRESETS[0]}")
        }
        b.settingsKeyLostBot.setOnClickListener {
            b.settingsKey.setText(ScooterCodec.KEY_PRESETS[1])
            LogBus.info("SETTINGS", "Ключ LostBot: ${ScooterCodec.KEY_PRESETS[1]}")
        }
        b.settingsSave.setOnClickListener { save() }
        loadPrefs()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) loadPrefs()
    }

    private fun loadPrefs() {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())
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
        Toast.makeText(requireContext(), "Сохранено", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
