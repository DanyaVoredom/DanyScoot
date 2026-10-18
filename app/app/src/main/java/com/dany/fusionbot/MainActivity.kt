package com.dany.fusionbot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dany.fusionbot.core.ClientState
import com.dany.fusionbot.core.LogBus
import com.dany.fusionbot.core.Prefs
import com.dany.fusionbot.core.ScooterClient
import com.dany.fusionbot.databinding.ActivityMainBinding
import com.dany.fusionbot.ui.ControlFragment
import com.dany.fusionbot.ui.DevicesFragment
import com.dany.fusionbot.ui.LogFragment
import com.dany.fusionbot.ui.SettingsFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fragments: Array<Fragment>
    private var currentTab = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            LogBus.warn("APP", "Разрешения не выданы: ${denied.joinToString()}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestPermissionsIfNeeded()
        setupFragments(savedInstanceState)
        binding.bottomNav.setOnItemSelectedListener { item ->
            val index = when (item.itemId) {
                R.id.tabDevices -> 0
                R.id.tabControl -> 1
                R.id.tabLog -> 2
                else -> 3
            }
            if (index != currentTab) showTab(index)
            true
        }
        observeClient()
        if (!ScooterClient.bluetoothSupported()) {
            Toast.makeText(this, getString(R.string.bt_not_supported), Toast.LENGTH_LONG).show()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val wanted = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wanted.add(Manifest.permission.BLUETOOTH_SCAN)
            wanted.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            wanted.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun setupFragments(savedInstanceState: Bundle?) {
        val manager = supportFragmentManager
        val tags = arrayOf("devices", "control", "log", "settings")
        fragments = Array(4) { i ->
            val existing = manager.findFragmentByTag(tags[i])
            val fragment = existing ?: when (i) {
                0 -> DevicesFragment()
                1 -> ControlFragment()
                2 -> LogFragment()
                else -> SettingsFragment()
            }
            if (existing == null) {
                manager.beginTransaction().add(R.id.container, fragment, tags[i]).hide(fragment).commitNow()
            }
            fragment
        }
        val start = savedInstanceState?.getInt(KEY_TAB) ?: 0
        showTab(start)
    }

    private fun showTab(index: Int) {
        currentTab = index
        val manager = supportFragmentManager
        val transaction = manager.beginTransaction()
        for (i in fragments.indices) {
            if (i == index) transaction.show(fragments[i]) else transaction.hide(fragments[i])
        }
        transaction.commitAllowingStateLoss()
        val menuId = TAB_MENU_IDS[index]
        if (binding.bottomNav.selectedItemId != menuId) {
            binding.bottomNav.selectedItemId = menuId
        }
        updateStatus()
    }

    fun showControlTab() {
        showTab(1)
    }

    private fun updateSpamBadge(sent: Int, active: Boolean) {
        val badge = binding.spamBadge
        if (active) {
            badge.text = "Спам: $sent"
            badge.visibility = View.VISIBLE
        } else {
            badge.text = ""
            badge.visibility = View.GONE
        }
    }

    private fun observeClient() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { ScooterClient.state.collect { updateStatus() } }
                launch { ScooterClient.spamActive.collect { updateSpamBadge(ScooterClient.spamSent.value, it) } }
                launch {
                    ScooterClient.spamSent.collect {
                        updateSpamBadge(it, ScooterClient.spamActive.value)
                    }
                }
            }
        }
    }

    private fun updateStatus() {
        val state = ScooterClient.state.value
        val name = ScooterClient.deviceName
        val stateText = when (state) {
            ClientState.IDLE -> getString(R.string.status_disconnected)
            ClientState.SCANNING -> "Сканирование"
            ClientState.CONNECTING -> getString(R.string.status_connecting)
            ClientState.DISCOVERING -> "Поиск сервисов"
            ClientState.AUTHENTICATING -> getString(R.string.status_authenticating)
            ClientState.READY -> getString(R.string.status_ready)
            ClientState.DEMO -> "Демо-режим"
            ClientState.ERROR -> getString(R.string.status_error)
        }
        binding.statusText.text = if (name.isEmpty()) stateText else "$name: $stateText"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            ScooterClient.stopScan()
        }
    }

    companion object {
        private const val KEY_TAB = "tab"
        private val TAB_MENU_IDS = intArrayOf(R.id.tabDevices, R.id.tabControl, R.id.tabLog, R.id.tabSettings)
    }
}
