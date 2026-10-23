package ru.dany.scoot.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.dany.scoot.R
import ru.dany.scoot.ble.GattDump
import ru.dany.scoot.ble.GattServiceInfo
import ru.dany.scoot.ble.gattName
import ru.dany.scoot.ble.shortUuid
import ru.dany.scoot.core.LogBus
import ru.dany.scoot.core.ScooterClient
import ru.dany.scoot.databinding.FragmentGattBinding
import ru.dany.scoot.databinding.ItemGattServiceBinding
import kotlinx.coroutines.launch

class GattFragment : Fragment() {

    private var binding: FragmentGattBinding? = null
    private lateinit var adapter: GattAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentGattBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = GattAdapter()
        binding?.gattList?.layoutManager = LinearLayoutManager(requireContext())
        binding?.gattList?.adapter = adapter
        binding?.gattCopy?.setOnClickListener { copyDump() }
        binding?.gattReconnect?.setOnClickListener { reconnect() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { ScooterClient.gatt.collect { showDump(it) } }
            }
        }
    }

    private fun showDump(dump: GattDump?) {
        val b = binding ?: return
        if (dump == null) {
            b.gattDevice.text = getString(R.string.gatt_wait)
            b.gattStatus.text = getString(R.string.gatt_hint)
            adapter.submit(emptyList())
            b.gattCopy.isEnabled = false
            return
        }
        b.gattDevice.text = getString(
            R.string.gatt_device,
            dump.name.ifEmpty { "Unknown" },
            dump.address,
            dump.services.size,
            dump.services.sumOf { it.characteristics.size },
        )
        b.gattStatus.text = if (dump.services.isEmpty()) {
            getString(R.string.gatt_empty)
        } else {
            getString(R.string.gatt_hint)
        }
        adapter.submit(dump.services)
        b.gattCopy.isEnabled = true
    }

    private fun copyDump() {
        val dump = ScooterClient.gatt.value ?: return
        copy(dump.toText(), R.string.gatt_copied)
    }

    private fun reconnect() {
        if (!ScooterClient.hasLastDevice()) {
            Toast.makeText(requireContext(), R.string.gatt_no_device, Toast.LENGTH_SHORT).show()
            return
        }
        LogBus.info("GATT", "Переподключение")
        ScooterClient.reconnectLast()
    }

    private fun copy(text: String, message: Int) {
        val manager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("DanyScoot GATT", text))
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private class GattAdapter : RecyclerView.Adapter<GattAdapter.Holder>() {

        private val items = ArrayList<GattServiceInfo>()

        fun submit(list: List<GattServiceInfo>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        class Holder(val binding: ItemGattServiceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemGattServiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val service = items[position]
            holder.binding.serviceName.text = service.name ?: "Сервис"
            holder.binding.serviceShort.text = shortUuid(service.uuid)
            holder.binding.serviceUuid.text = service.uuid.toString()
            holder.binding.serviceChars.text = if (service.characteristics.isEmpty()) {
                holder.binding.root.context.getString(R.string.gatt_empty)
            } else {
                service.characteristics.joinToString("\n") { char ->
                    val name = gattName(char.uuid)
                    val label = if (name != null) "$name · " else ""
                    "$label${char.uuid}\n  ${char.describe()}"
                }
            }
        }
    }
}
