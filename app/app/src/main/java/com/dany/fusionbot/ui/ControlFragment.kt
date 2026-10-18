package com.dany.fusionbot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dany.fusionbot.R
import com.dany.fusionbot.ble.OpcodeCatalog
import com.dany.fusionbot.ble.OpcodeDirection
import com.dany.fusionbot.ble.OpcodeInfo
import com.dany.fusionbot.ble.ScooterCodec
import com.dany.fusionbot.ble.hexByte
import com.dany.fusionbot.ble.parseHex
import com.dany.fusionbot.core.ClientState
import com.dany.fusionbot.core.LogBus
import com.dany.fusionbot.core.Prefs
import com.dany.fusionbot.core.ScooterClient
import com.dany.fusionbot.databinding.FragmentControlBinding
import com.dany.fusionbot.spam.SpamService
import kotlinx.coroutines.launch

class ControlFragment : Fragment() {

    private var binding: FragmentControlBinding? = null
    private var opcodes: List<OpcodeInfo> = emptyList()
    private var opcodesShownForAll = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentControlBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())
        b.keyInput.setText(prefs.bleKey)
        b.spamInterval.setText(prefs.spamIntervalMs.toString())

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
        b.consoleSend.setOnClickListener { sendConsole() }
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

        setupOpcodeSpinner()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { ScooterClient.state.collect { updateStatus(it) } }
                launch { ScooterClient.spamActive.collect { updateSpamState(it) } }
                launch { ScooterClient.spamSent.collect { updateSpamState(ScooterClient.spamActive.value) } }
                launch { ScooterClient.vehicle.collect { updateVehicle() } }
                launch { ScooterClient.iot.collect { updateIot() } }
            }
        }
    }

    private fun setupOpcodeSpinner() {
        val b = binding ?: return
        val all = Prefs.get(requireContext()).showAllOpcodes
        opcodesShownForAll = all
        val list = OpcodeCatalog.all
        opcodes = if (all) list else list.filter { it.direction != OpcodeDirection.SET_ONLY }
        val labels = opcodes.map { "${hexByte(it.code)} ${it.name}" }
        b.opcodeInput.setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown, labels))
        b.opcodeInput.setOnItemClickListener { _, _, position, _ ->
            val info = opcodes.getOrNull(position) ?: return@setOnItemClickListener
            binding?.consoleCmd?.setText(hexByte(info.code))
        }
        b.opcodeInput.setText(labels.firstOrNull().orEmpty(), false)
        opcodes.firstOrNull()?.let { b.consoleCmd.setText(hexByte(it.code)) }
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
        if (!Prefs.get(requireContext()).demoMode && !ScooterClient.bluetoothEnabled()) {
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
        binding?.spamInterval?.setText(Prefs.get(requireContext()).spamIntervalMs.toString())
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

    private fun sendConsole() {
        val cmdText = binding?.consoleCmd?.text?.toString().orEmpty().trim()
        val payloadText = binding?.consolePayload?.text?.toString().orEmpty()
        val cmd = parseCode(cmdText)
        if (cmd == null || cmd !in 0..0xFF) {
            LogBus.err("TX", "Некорректная команда: $cmdText")
            return
        }
        val payload = parseHex(payloadText)
        if (payload == null) {
            LogBus.err("TX", "Некорректные данные: $payloadText")
            return
        }
        saveKey()
        ScooterClient.sendRaw(cmd, payload)
    }

    private fun parseCode(text: String): Int? {
        if (text.isEmpty()) return null
        val lower = text.trim().lowercase()
        return try {
            when {
                lower.startsWith("0x") -> lower.substring(2).toInt(16)
                lower.startsWith("d") -> lower.substring(1).toInt()
                lower.all { it.isDigit() || it in 'a'..'f' } -> lower.toInt(16)
                else -> lower.toInt()
            }
        } catch (e: NumberFormatException) {
            null
        }
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
            ClientState.DEMO -> getString(R.string.status_demo)
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
        b.consoleSend.isEnabled = ready
    }

    private fun updateSpamState(active: Boolean) {
        val b = binding ?: return
        val label = getString(if (active) R.string.control_spam_active else R.string.control_spam_sent)
        b.spamStatus.text = getString(R.string.control_spam_counter, label, ScooterClient.spamSent.value)
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

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refreshFromPrefs()
    }

    private fun refreshFromPrefs() {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())
        if (!b.keyInput.hasFocus()) b.keyInput.setText(prefs.bleKey)
        if (!b.spamInterval.hasFocus()) b.spamInterval.setText(prefs.spamIntervalMs.toString())
        if (prefs.showAllOpcodes != opcodesShownForAll) setupOpcodeSpinner()
        updateStatus(ScooterClient.state.value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
