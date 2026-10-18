package ru.dany.scoot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ru.dany.scoot.MainActivity
import ru.dany.scoot.R
import ru.dany.scoot.core.ClientState
import ru.dany.scoot.core.LogBus
import ru.dany.scoot.core.Prefs
import ru.dany.scoot.core.Provider
import ru.dany.scoot.core.ScooterClient
import ru.dany.scoot.core.WhooshMode
import ru.dany.scoot.databinding.FragmentProvidersBinding
import kotlinx.coroutines.launch

class ProvidersFragment : Fragment() {

    private var binding: FragmentProvidersBinding? = null
    private val providers = Provider.entries
    private val whooshModes = WhooshMode.entries
    private var providerSelected = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentProvidersBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())

        b.providerSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            providers.map { it.title },
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        b.providerSpinner.setSelection(providers.indexOf(Provider.from(prefs.provider)).coerceAtLeast(0))
        b.whooshModeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            whooshModes.map { it.title },
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        b.whooshModeSpinner.setSelection(whooshModes.indexOf(WhooshMode.from(prefs.whooshMode)).coerceAtLeast(0))

        b.providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!providerSelected) {
                    providerSelected = true
                    updateVisibility()
                    return
                }
                val provider = providers[position.coerceIn(providers.indices)]
                prefs.provider = provider.key
                ScooterClient.setProvider(provider)
                updateVisibility()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        b.whooshModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val mode = whooshModes[position.coerceIn(whooshModes.indices)]
                prefs.whooshMode = mode.key
                ScooterClient.setWhooshMode(mode)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        loadFields()
        updateVisibility()

        b.scanButton.setOnClickListener {
            saveFields()
            ScooterClient.startScan()
            (activity as? MainActivity)?.showDevicesTab()
        }
        b.reconnectButton.setOnClickListener {
            saveFields()
            if (!ScooterClient.reconnectLast()) {
                LogBus.warn("PROVIDER", "Нет последнего устройства")
            }
        }
        b.disconnectButton.setOnClickListener { ScooterClient.disconnect() }
        b.whooshStartButton.setOnClickListener {
            saveFields()
            ScooterClient.whooshStart()
        }
        b.whooshStopButton.setOnClickListener {
            saveFields()
            ScooterClient.whooshStop()
        }
        b.whooshOnlineButton.setOnClickListener {
            saveFields()
            ScooterClient.whooshOnlineSend(b.whooshUnlockInput.text?.toString().orEmpty(), "WHOOSH online")
        }
        b.whooshUnlockButton.setOnClickListener {
            saveFields()
            ScooterClient.whooshOnlineSend(Prefs.get(requireContext()).whooshUnlockHex, "WHOOSH unlock")
        }
        b.whooshLockButton.setOnClickListener {
            saveFields()
            ScooterClient.whooshOnlineSend(Prefs.get(requireContext()).whooshLockHex, "WHOOSH lock")
        }
        b.yandexAuthButton.setOnClickListener {
            saveFields()
            ScooterClient.yandexAuth()
        }
        b.yandexLockButton.setOnClickListener {
            saveFields()
            ScooterClient.yandexLock()
        }
        b.yandexUnlockButton.setOnClickListener {
            saveFields()
            ScooterClient.yandexUnlock()
        }
        b.yandexSoundButton.setOnClickListener {
            saveFields()
            ScooterClient.yandexSound()
        }
        b.yandexCableButton.setOnClickListener {
            saveFields()
            ScooterClient.yandexCableUnlock()
        }
        b.yandexBatteryButton.setOnClickListener {
            saveFields()
            ScooterClient.yandexBatteryCover()
        }
        b.rawButton.setOnClickListener {
            saveFields()
            ScooterClient.sendRawHex(b.rawInput.text?.toString().orEmpty())
        }
        b.saveButton.setOnClickListener {
            saveFields()
            Toast.makeText(requireContext(), R.string.provider_save, Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ScooterClient.state.collect { updateStatus() }
                }
            }
        }
        updateStatus()
    }

    private fun loadFields() {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())
        b.whooshTokenInput.setText(prefs.whooshToken)
        b.whooshStartInput.setText(prefs.whooshStartId)
        b.whooshStopInput.setText(prefs.whooshStopId)
        b.whooshUnlockInput.setText(prefs.whooshUnlockHex)
        b.whooshLockInput.setText(prefs.whooshLockHex)
        b.yandexKeyInput.setText(prefs.yandexKey)
        b.yandexServiceInput.setText(prefs.yandexServiceUuid)
        b.yandexWriteInput.setText(prefs.yandexWriteUuid)
        b.yandexNotifyInput.setText(prefs.yandexNotifyUuid)
        b.yandexCccdInput.setText(prefs.yandexCccdUuid)
        b.yandexDeviceInput.setText(prefs.yandexDeviceId)
        b.yandexNonceInput.setText(prefs.yandexNonce)
    }

    private fun saveFields() {
        val b = binding ?: return
        val prefs = Prefs.get(requireContext())
        val provider = providers[b.providerSpinner.selectedItemPosition.coerceIn(providers.indices)]
        val mode = whooshModes[b.whooshModeSpinner.selectedItemPosition.coerceIn(whooshModes.indices)]
        prefs.provider = provider.key
        prefs.whooshMode = mode.key
        prefs.whooshToken = b.whooshTokenInput.text?.toString().orEmpty()
        prefs.whooshStartId = b.whooshStartInput.text?.toString().orEmpty()
        prefs.whooshStopId = b.whooshStopInput.text?.toString().orEmpty()
        prefs.whooshUnlockHex = b.whooshUnlockInput.text?.toString().orEmpty()
        prefs.whooshLockHex = b.whooshLockInput.text?.toString().orEmpty()
        prefs.yandexKey = b.yandexKeyInput.text?.toString().orEmpty()
        prefs.yandexServiceUuid = b.yandexServiceInput.text?.toString().orEmpty()
        prefs.yandexWriteUuid = b.yandexWriteInput.text?.toString().orEmpty()
        prefs.yandexNotifyUuid = b.yandexNotifyInput.text?.toString().orEmpty()
        prefs.yandexCccdUuid = b.yandexCccdInput.text?.toString().orEmpty()
        prefs.yandexDeviceId = b.yandexDeviceInput.text?.toString().orEmpty()
        prefs.yandexNonce = b.yandexNonceInput.text?.toString().orEmpty()
        ScooterClient.setProvider(provider)
        ScooterClient.setWhooshMode(mode)
        ScooterClient.refreshProfile()
    }

    private fun updateVisibility() {
        val b = binding ?: return
        val provider = providers[b.providerSpinner.selectedItemPosition.coerceIn(providers.indices)]
        val whoosh = if (provider == Provider.WHOOSH) View.VISIBLE else View.GONE
        val yandex = if (provider == Provider.YANDEX) View.VISIBLE else View.GONE
        b.whooshSection.visibility = whoosh
        b.whooshCard.visibility = whoosh
        b.yandexSection.visibility = yandex
        b.yandexCard.visibility = yandex
        updateStatus()
    }

    private fun updateStatus() {
        val b = binding ?: return
        val state = ScooterClient.state.value
        val stateText = when (state) {
            ClientState.IDLE -> getString(R.string.status_disconnected)
            ClientState.SCANNING -> getString(R.string.status_scanning)
            ClientState.CONNECTING -> getString(R.string.status_connecting)
            ClientState.DISCOVERING -> getString(R.string.status_discovering)
            ClientState.AUTHENTICATING -> getString(R.string.status_authenticating)
            ClientState.READY -> getString(R.string.status_ready)
            ClientState.DEMO -> getString(R.string.status_demo)
            ClientState.ERROR -> getString(R.string.status_error)
        }
        val provider = providers[b.providerSpinner.selectedItemPosition.coerceIn(providers.indices)]
        val name = ScooterClient.deviceName
        b.providerStatus.text = if (name.isEmpty()) "${provider.title}: $stateText" else "${provider.title}: $name: $stateText"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
