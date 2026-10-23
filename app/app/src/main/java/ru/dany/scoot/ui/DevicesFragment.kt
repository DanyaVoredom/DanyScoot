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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.dany.scoot.MainActivity
import ru.dany.scoot.R
import ru.dany.scoot.ble.DeviceClassifier
import ru.dany.scoot.ble.DeviceKind
import ru.dany.scoot.ble.DeviceSort
import ru.dany.scoot.ble.ScannedDevice
import ru.dany.scoot.core.ClientState
import ru.dany.scoot.core.LogBus
import ru.dany.scoot.core.Prefs
import ru.dany.scoot.core.ScooterClient
import ru.dany.scoot.databinding.FragmentDevicesBinding
import ru.dany.scoot.databinding.ItemDeviceBinding
import kotlinx.coroutines.launch

private data class DeviceRow(val device: ScannedDevice, val blocked: Boolean)

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
        binding?.sortButton?.setOnClickListener { showSortDialog() }
        refreshSortButton()
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
                    }
                }
            }
        }
    }

    private fun render() {
        val b = binding ?: return
        val sorted = sortDevices(devices)
        adapter.submitList(sorted.map { DeviceRow(it, it.address in incompatible) })
        b.scanStatus.text = if (scanning) {
            getString(R.string.scan_searching, sorted.size)
        } else {
            getString(R.string.scan_hint)
        }
        val empty = sorted.isEmpty()
        b.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        b.emptyState.text = getString(if (scanning) R.string.scan_empty_scanning else R.string.scan_empty)
    }

    private fun sortDevices(list: List<ScannedDevice>): List<ScannedDevice> {
        val mode = Prefs.get(requireContext()).deviceSort
        val comparator = when (mode) {
            DeviceSort.DISTANCE -> compareByDescending<ScannedDevice> { it.rssi }
            DeviceSort.NAME -> compareBy { it.name.lowercase() }
            DeviceSort.ADDRESS -> compareBy { it.address }
            DeviceSort.KIND -> compareBy<ScannedDevice> { it.kind.ordinal }.thenByDescending { it.rssi }
        }
        return list.sortedWith(comparator)
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
                refreshSortButton()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshSortButton() {
        val b = binding ?: return
        val mode = Prefs.get(requireContext()).deviceSort
        b.sortButton.text = getString(R.string.scan_sort_value, getString(sortLabel(mode)))
    }

    private fun sortLabel(mode: DeviceSort): Int = when (mode) {
        DeviceSort.DISTANCE -> R.string.sort_distance
        DeviceSort.NAME -> R.string.sort_name
        DeviceSort.ADDRESS -> R.string.sort_address
        DeviceSort.KIND -> R.string.sort_kind
    }

    private fun toggleScan() {
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

    private fun connect(device: ScannedDevice) {
        ScooterClient.connect(device.address, device.name, device.kind)
        (activity as? MainActivity)?.showControlTab()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            refreshSortButton()
            render()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private class DeviceAdapter(
        private val onClick: (ScannedDevice) -> Unit,
    ) : ListAdapter<DeviceRow, DeviceAdapter.Holder>(DIFF) {

        class Holder(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = getItem(position)
            val device = row.device
            val context = holder.binding.root.context
            holder.binding.deviceName.text = device.name
            holder.binding.deviceAddress.text = device.address
            holder.binding.deviceAdvert.text = device.advert
            holder.binding.deviceAdvert.visibility = if (device.advert.isEmpty()) View.GONE else View.VISIBLE
            holder.binding.deviceRssi.text = context.getString(
                R.string.device_rssi_distance,
                device.rssi,
                DeviceClassifier.formatDistance(device.rssi),
            )
            holder.binding.deviceBadge.text = context.getString(DeviceClassifier.labelRes(device.kind))
            holder.binding.deviceBadge.setTextColor(
                ContextCompat.getColor(context, if (row.blocked) R.color.status_error else R.color.status_ok),
            )
            val card = holder.binding.root as MaterialCardView
            card.strokeWidth = if (row.blocked) dp(context, 1) else 0
            card.strokeColor = ContextCompat.getColor(context, R.color.status_error)
            holder.binding.root.setOnClickListener { onClick(device) }
        }

        private fun dp(context: android.content.Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<DeviceRow>() {
                override fun areItemsTheSame(oldItem: DeviceRow, newItem: DeviceRow): Boolean =
                    oldItem.device.address == newItem.device.address

                override fun areContentsTheSame(oldItem: DeviceRow, newItem: DeviceRow): Boolean =
                    oldItem == newItem
            }
        }
    }
}
