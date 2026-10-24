package ru.dany.scoot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ru.dany.scoot.R
import ru.dany.scoot.ble.DeviceKind
import ru.dany.scoot.ble.ScooterCodec
import ru.dany.scoot.core.ClientState
import ru.dany.scoot.core.LogBus
import ru.dany.scoot.core.Prefs
import ru.dany.scoot.core.RemoteInfo
import ru.dany.scoot.core.ScooterClient
import ru.dany.scoot.databinding.FragmentControlBinding
import ru.dany.scoot.spam.SpamService
import java.util.Locale
import kotlinx.coroutines.launch

class ControlFragment : Fragment() {

    private var binding: FragmentControlBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentControlBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())
        b.keyInput.setText(prefs.bleKey)
        b.spamInterval.setText(String.format(Locale.ROOT, "%d", prefs.spamIntervalMs))

        b.authButton.setOnClickListener { sendKey() }
        b.connectButton.setOnClickListener { connect() }
        b.disconnectButton.setOnClickListener { ScooterClient.disconnect() }
        b.unlockButton.setOnClickListener {
            saveKey()
            ScooterClient.unlock()
        }
        b.lockButton.setOnClickListener {
            saveKey()
            ScooterClient.lock()
        }
        b.spamStartButton.setOnClickListener { startSpam() }
        b.spamStopButton.setOnClickListener { stopSpam() }
        b.applyScooterButton.setOnClickListener { applyScooter() }
        b.ejectButton.setOnClickListener {
            saveKey()
            ScooterClient.ejectBattery()
        }
        b.findButton.setOnClickListener {
            saveKey()
            ScooterClient.findCar()
        }
        b.bypassButton.setOnClickListener { ScooterClient.bypassUnlock() }
        b.bruteButton.setOnClickListener { ScooterClient.startKeyBrute() }
        b.bruteStopButton.setOnClickListener { ScooterClient.stopKeyBrute() }
        b.keyBruteButton.setOnClickListener {
            saveKey()
            ScooterClient.startKeyStringBrute()
        }
        b.keyBruteStopButton.setOnClickListener { ScooterClient.stopKeyStringBrute() }
        b.svcBatteryCover.setOnClickListener {
            ScooterClient.sendService(ScooterCodec.CMD_BATTERY_COVER, byteArrayOf(0x01))
        }
        b.svcIotRestart.setOnClickListener {
            ScooterClient.sendService(ScooterCodec.CMD_IOT_RESTART, byteArrayOf(0x01))
        }
        b.svcClearBt.setOnClickListener {
            ScooterClient.sendService(ScooterCodec.CMD_CLEAR_BT, byteArrayOf(0x01))
        }
        b.svcColorLight.setOnClickListener {
            ScooterClient.sendService(
                ScooterCodec.CMD_COLOR_LIGHT,
                byteArrayOf(0x01, 0x00, 0x7C, 0x6A, 0xE8.toByte(), 0x00, 0x00, 0x00),
            )
        }
        b.svcPowerOff.setOnClickListener {
            ScooterClient.sendService(ScooterCodec.CMD_POWER_CONTROL, byteArrayOf(0x01))
        }
        b.svcReadConfig.setOnClickListener {
            ScooterClient.sendService(ScooterCodec.CMD_FW_INFO, ByteArray(0))
        }
        b.telemetryRefresh.setOnClickListener { ScooterClient.requestTelemetry() }
        b.headsetRefresh.setOnClickListener { ScooterClient.refreshRemoteInfo() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { ScooterClient.state.collect { updateStatus(it) } }
                launch { ScooterClient.spamActive.collect { updateSpamState(it) } }
                launch { ScooterClient.spamSent.collect { updateSpamState(ScooterClient.spamActive.value) } }
                launch { ScooterClient.vehicle.collect { updateVehicle() } }
                launch { ScooterClient.iot.collect { updateIot() } }
                launch { ScooterClient.remote.collect { updateRemote(it) } }
            }
        }
        updateStatus(ScooterClient.state.value)
    }

    private fun saveKey() {
        val text = binding?.keyInput?.text?.toString().orEmpty().trim()
        Prefs.get(requireContext()).bleKey = text
    }

    private fun sendKey() {
        saveKey()
        ScooterClient.authenticate()
    }

    private fun connect() {
        if (ScooterClient.isReady) {
            LogBus.info("BLE", "Уже подключено")
            return
        }
        saveKey()
        if (!ScooterClient.bluetoothEnabled()) {
            Toast.makeText(requireContext(), R.string.bt_disabled, Toast.LENGTH_SHORT).show()
            return
        }
        if (!ScooterClient.hasLastDevice()) {
            Toast.makeText(requireContext(), R.string.control_select_scooter, Toast.LENGTH_SHORT).show()
            return
        }
        ScooterClient.reconnectLast()
    }

    private fun startSpam() {
        val text = binding?.spamInterval?.text?.toString().orEmpty().trim()
        val interval = text.toIntOrNull() ?: Prefs.DEFAULT_SPAM_INTERVAL
        Prefs.get(requireContext()).spamIntervalMs = interval
        binding?.spamInterval?.setText(String.format(Locale.ROOT, "%d", Prefs.get(requireContext()).spamIntervalMs))
        saveKey()
        if (!ScooterClient.isReady) {
            LogBus.warn("SPAM", "Сначала авторизуйтесь")
            return
        }
        SpamService.start(requireContext())
    }

    private fun stopSpam() {
        ScooterClient.stopSpam()
        SpamService.stop(requireContext())
    }

    private fun applyScooter() {
        val b = binding ?: return
        val modeIndex = when (b.modeGroup.checkedRadioButtonId) {
            R.id.mode1 -> 0
            R.id.mode2 -> 1
            else -> 2
        }
        saveKey()
        ScooterClient.setScooter(
            headlight = b.headlightSwitch.isChecked,
            modeIndex = modeIndex,
            throttle = b.throttleSwitch.isChecked,
        )
    }

    private fun updateStatus(state: ClientState) {
        val b = binding ?: return
        val name = ScooterClient.deviceName
        val text = when (state) {
            ClientState.IDLE -> getString(R.string.status_disconnected)
            ClientState.SCANNING -> getString(R.string.status_scanning)
            ClientState.CONNECTING -> getString(R.string.status_connecting)
            ClientState.DISCOVERING -> getString(R.string.status_discovering)
            ClientState.AUTHENTICATING -> getString(R.string.status_authenticating)
            ClientState.READY -> getString(R.string.status_ready)
            ClientState.ERROR -> getString(R.string.status_error)
        }
        b.authStatus.text = if (name.isEmpty()) text else "$name: $text"
        val ready = ScooterClient.isReady
        b.unlockButton.isEnabled = ready
        b.lockButton.isEnabled = ready
        b.applyScooterButton.isEnabled = ready
        b.ejectButton.isEnabled = ready
        b.findButton.isEnabled = ready
        b.svcBatteryCover.isEnabled = ready
        b.svcIotRestart.isEnabled = ready
        b.svcClearBt.isEnabled = ready
        b.svcColorLight.isEnabled = ready
        b.svcPowerOff.isEnabled = ready
        b.svcReadConfig.isEnabled = ready
        b.telemetryRefresh.isEnabled = ready
        b.spamStartButton.isEnabled = ready
        b.spamStopButton.isEnabled = ScooterClient.spamActive.value
        b.headsetRefresh.isEnabled = ScooterClient.hasLink
        val linked = ScooterClient.hasLink
        b.bypassButton.isEnabled = linked
        b.bruteButton.isEnabled = linked
        b.bruteStopButton.isEnabled = linked
        b.keyBruteButton.isEnabled = linked
        b.keyBruteStopButton.isEnabled = linked
        val external = ScooterClient.deviceAddress.isNotEmpty() && ScooterClient.activeKind != DeviceKind.SCOOTER
        val headset = ScooterClient.activeKind == DeviceKind.HEADSET
        b.keyField.visibility = if (external) View.GONE else View.VISIBLE
        b.authButton.visibility = if (external) View.GONE else View.VISIBLE
        b.headsetHeader.setText(if (headset) R.string.headset_section else R.string.device_section)
        b.headsetHeader.visibility = if (external) View.VISIBLE else View.GONE
        b.headsetCard.visibility = if (external) View.VISIBLE else View.GONE
        val scooterVisibility = if (external) View.GONE else View.VISIBLE
        b.bypassHeader.visibility = scooterVisibility
        b.bypassCard.visibility = scooterVisibility
        b.lockHeader.visibility = scooterVisibility
        b.lockCard.visibility = scooterVisibility
        b.scooterHeader.visibility = scooterVisibility
        b.scooterCard.visibility = scooterVisibility
        b.serviceHeader.visibility = scooterVisibility
        b.serviceCard.visibility = scooterVisibility
        b.liveHeader.visibility = scooterVisibility
        b.liveCard.visibility = scooterVisibility
    }

    private fun updateSpamState(active: Boolean) {
        val b = binding ?: return
        val label = getString(if (active) R.string.control_spam_active else R.string.control_spam_sent)
        b.spamStatus.text = getString(R.string.control_spam_counter, label, ScooterClient.spamSent.value)
        b.spamStopButton.isEnabled = active
    }

    private fun updateVehicle() {
        val b = binding ?: return
        val v = ScooterClient.vehicle.value
        if (v == null) {
            b.liveBattery.text = getString(R.string.live_unknown)
            b.liveSpeed.text = getString(R.string.live_unknown)
            b.liveMileage.text = getString(R.string.live_unknown)
            b.liveRange.text = getString(R.string.live_unknown)
            return
        }
        b.liveBattery.text = getString(R.string.live_battery_value, v.batteryPercent)
        b.liveSpeed.text = getString(R.string.live_speed_value, v.speed)
        b.liveMileage.text = getString(R.string.live_distance_value, v.singleMileage / 1000.0)
        b.liveRange.text = getString(R.string.live_distance_value, v.remainingRange / 1000.0)
    }

    private fun updateIot() {
        val b = binding ?: return
        val info = ScooterClient.iot.value
        if (info == null) {
            b.liveVersion.text = getString(R.string.live_unknown)
            b.liveLocked.text = getString(R.string.live_unknown)
            return
        }
        b.liveVersion.text = info.text ?: buildString {
            if (info.version.isNotEmpty()) append(info.version)
            val volts = info.value / 100.0
            if (volts in 1.0..100.0) {
                if (isNotEmpty()) append(" / ")
                append(getString(R.string.live_voltage_value, volts))
            }
        }.ifEmpty { getString(R.string.live_unknown) }
        b.liveLocked.text = when {
            info.text != null -> getString(R.string.live_unknown)
            info.locked -> getString(R.string.live_locked_yes)
            else -> getString(R.string.live_locked_no)
        }
    }

    private fun updateRemote(info: RemoteInfo?) {
        val b = binding ?: return
        val battery = info?.batteryPercent
        b.headsetBattery.text = if (battery == null) {
            getString(R.string.live_unknown)
        } else {
            getString(R.string.headset_battery_value, battery)
        }
        b.headsetFirmware.text = info?.firmware?.ifEmpty { null } ?: getString(R.string.live_unknown)
        b.headsetModel.text = info?.model?.ifEmpty { null } ?: getString(R.string.live_unknown)
        b.headsetManufacturer.text = info?.manufacturer?.ifEmpty { null } ?: getString(R.string.live_unknown)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refreshFromPrefs()
    }

    private fun refreshFromPrefs() {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())
        if (!b.keyInput.hasFocus()) b.keyInput.setText(prefs.bleKey)
        if (!b.spamInterval.hasFocus()) b.spamInterval.setText(String.format(Locale.ROOT, "%d", prefs.spamIntervalMs))
        updateStatus(ScooterClient.state.value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
