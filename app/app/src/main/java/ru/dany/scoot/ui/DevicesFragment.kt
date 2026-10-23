package ru.dany.scoot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import ru.dany.scoot.MainActivity
import ru.dany.scoot.R
import ru.dany.scoot.ble.ScannedDevice
import ru.dany.scoot.core.ClientState
import ru.dany.scoot.core.LogBus
import ru.dany.scoot.core.Prefs
import ru.dany.scoot.core.ScooterClient
import ru.dany.scoot.databinding.FragmentDevicesBinding
import ru.dany.scoot.databinding.ItemDeviceBinding
import kotlinx.coroutines.launch

class DevicesFragment : Fragment() {

    private var binding: FragmentDevicesBinding? = null
    private lateinit var adapter: DeviceAdapter
    private var scanning = false
    private var devices: List<ScannedDevice> = emptyList()
    private var incompatible: Set<String> = emptySet()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentDevicesBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = DeviceAdapter { device -> connect(device) }
        binding?.deviceList?.layoutManager = LinearLayoutManager(requireContext())
        binding?.deviceList?.adapter = adapter
        binding?.scanButton?.setOnClickListener { toggleScan() }
        binding?.demoButton?.setOnClickListener { startDemo() }
        refreshDemoButton()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ScooterClient.devices.collect { list ->
                        devices = list
                        render()
                    }
                }
                launch {
                    ScooterClient.incompatible.collect { set ->
                        incompatible = set
                        render()
                    }
                }
                launch {
                    ScooterClient.state.collect { state ->
                        scanning = state == ClientState.SCANNING
                        binding?.scanButton?.text = if (scanning) {
                            getString(R.string.scan_stop)
                        } else {
                            getString(R.string.scan_start)
                        }
                        render()
                        refreshDemoButton()
                    }
                }
            }
        }
    }

    private fun render() {
        val b = binding ?: return
        adapter.submit(devices, incompatible)
        b.scanStatus.text = if (scanning) {
            getString(R.string.scan_searching, devices.size)
        } else {
            getString(R.string.scan_hint)
        }
        val empty = devices.isEmpty()
        b.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        b.emptyState.text = getString(if (scanning) R.string.scan_empty_scanning else R.string.scan_empty)
    }

    private fun toggleScan() {
        val prefs = Prefs.get(requireContext())
        if (prefs.demoMode) {
            prefs.demoMode = false
            ScooterClient.disconnect()
            refreshDemoButton()
            LogBus.info("DEMO", "Демо-режим выключен")
        }
        val client = ScooterClient
        if (!client.bluetoothSupported()) {
            LogBus.err("BLE", getString(R.string.bt_not_supported))
            Toast.makeText(requireContext(), R.string.bt_not_supported, Toast.LENGTH_LONG).show()
            return
        }
        if (!client.bluetoothEnabled()) {
            LogBus.warn("BLE", getString(R.string.bt_disabled))
            Toast.makeText(requireContext(), R.string.bt_disabled, Toast.LENGTH_LONG).show()
            return
        }
        if (scanning) {
            client.stopScan()
        } else {
            client.startScan()
        }
    }

    private fun startDemo() {
        val prefs = Prefs.get(requireContext())
        if (ScooterClient.isDemo) {
            ScooterClient.disconnect()
            prefs.demoMode = false
            refreshDemoButton()
            return
        }
        prefs.demoMode = true
        refreshDemoButton()
        ScooterClient.connect(ScooterClient.DEMO_ADDRESS, ScooterClient.DEMO_NAME)
        (activity as? MainActivity)?.showControlTab()
    }

    private fun connect(device: ScannedDevice) {
        ScooterClient.connect(device.address, device.name)
        (activity as? MainActivity)?.showControlTab()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refreshDemoButton()
    }

    private fun refreshDemoButton() {
        val b = binding ?: return
        val demo = ScooterClient.isDemo
        b.demoButton.text = if (demo) getString(R.string.device_disconnect) else getString(R.string.settings_demo_mode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private class DeviceAdapter(
        private val onClick: (ScannedDevice) -> Unit,
    ) : RecyclerView.Adapter<DeviceAdapter.Holder>() {

        private val items = ArrayList<ScannedDevice>()
        private var incompatible: Set<String> = emptySet()

        fun submit(list: List<ScannedDevice>, blocked: Set<String>) {
            items.clear()
            items.addAll(list)
            incompatible = blocked
            notifyDataSetChanged()
        }

        class Holder(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val device = items[position]
            val context = holder.binding.root.context
            val blocked = device.address in incompatible || !device.hasUart
            holder.binding.deviceName.text = device.name
            holder.binding.deviceAddress.text = device.address
            holder.binding.deviceAdvert.text = device.advert
            holder.binding.deviceAdvert.visibility = if (device.advert.isEmpty()) View.GONE else View.VISIBLE
            holder.binding.deviceRssi.text = if (device.hasUart && !blocked) {
                context.getString(R.string.device_rssi_uart, device.rssi)
            } else {
                context.getString(R.string.device_rssi, device.rssi)
            }
            holder.binding.deviceBadge.text = context.getString(
                if (blocked) R.string.device_badge_no_uart else R.string.device_badge_uart,
            )
            holder.binding.deviceBadge.setTextColor(
                ContextCompat.getColor(context, if (blocked) R.color.status_error else R.color.status_ok),
            )
            val card = holder.binding.root as MaterialCardView
            card.strokeWidth = if (blocked) dp(context, 1) else 0
            card.strokeColor = ContextCompat.getColor(context, R.color.status_error)
            holder.binding.root.setOnClickListener { onClick(device) }
        }

        private fun dp(context: android.content.Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()
    }
}
