package ru.dany.scoot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ru.dany.scoot.core.ClientState
import ru.dany.scoot.core.LogBus
import ru.dany.scoot.core.ScooterClient
import ru.dany.scoot.databinding.ActivityMainBinding
import ru.dany.scoot.ui.ControlFragment
import ru.dany.scoot.ui.DevicesFragment
import ru.dany.scoot.ui.GattFragment
import ru.dany.scoot.ui.LogFragment
import ru.dany.scoot.ui.SettingsFragment
import ru.dany.scoot.ui.applyEdgeToEdgeInsets
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fragments: Array<Fragment>
    private var currentTab = 0
    private var permissionsRequested = false
    private var settingsDialogShown = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            LogBus.warn("APP", "Разрешения не выданы: ${denied.joinToString()}")
            val permanent = denied.filter { !shouldShowRequestPermissionRationale(it) }
            if (permanent.isNotEmpty()) showPermissionSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppTheme.current(this).styleRes)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyEdgeToEdgeInsets(binding.bottomNav)
        setupFragments(savedInstanceState)
        handleIntent(intent)
        binding.aiButton.setOnClickListener {
            startActivity(Intent(this, AiActivity::class.java))
        }
        binding.bottomNav.setOnItemSelectedListener { item ->
            val index = when (item.itemId) {
                R.id.tabDevices -> 0
                R.id.tabControl -> 1
                R.id.tabGatt -> 2
                R.id.tabLog -> 3
                else -> 4
            }
            if (index != currentTab) showTab(index)
            true
        }
        observeClient()
        if (!ScooterClient.bluetoothSupported()) {
            Toast.makeText(this, getString(R.string.bt_not_supported), Toast.LENGTH_LONG).show()
        }
        requestPermissionsIfNeeded()
    }

    private fun requiredPermissions(): List<String> {
        val wanted = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wanted.add(Manifest.permission.BLUETOOTH_SCAN)
            wanted.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            wanted.add(Manifest.permission.ACCESS_FINE_LOCATION)
            wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return wanted
    }

    private fun missingPermissions(): List<String> = requiredPermissions().filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionsIfNeeded() {
        val missing = missingPermissions()
        if (missing.isEmpty()) return
        if (permissionsRequested) {
            val permanent = missing.filter { !shouldShowRequestPermissionRationale(it) }
            if (permanent.isNotEmpty()) showPermissionSettings()
            return
        }
        permissionsRequested = true
        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun showPermissionSettings() {
        if (settingsDialogShown || isFinishing) return
        settingsDialogShown = true
        AlertDialog.Builder(this)
            .setTitle(R.string.perm_denied_title)
            .setMessage(R.string.perm_denied_message)
            .setPositiveButton(R.string.perm_denied_open) { _, _ -> openAppSettings() }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { settingsDialogShown = false }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            LogBus.warn("APP", "Не удалось открыть настройки: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (!permissionsRequested) return
        val missing = missingPermissions()
        if (missing.isEmpty()) {
            settingsDialogShown = false
            return
        }
        val permanent = missing.filter { !shouldShowRequestPermissionRationale(it) }
        if (permanent.isNotEmpty()) showPermissionSettings()
    }

    private fun setupFragments(savedInstanceState: Bundle?) {
        val manager = supportFragmentManager
        val tags = arrayOf("devices", "control", "gatt", "log", "settings")
        fragments = Array(5) { i ->
            val existing = manager.findFragmentByTag(tags[i])
            val fragment = existing ?: when (i) {
                0 -> DevicesFragment()
                1 -> ControlFragment()
                2 -> GattFragment()
                3 -> LogFragment()
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

    fun showDevicesTab() {
        showTab(0)
    }

    fun showControlTab() {
        showTab(1)
    }

    fun showGattTab() {
        showTab(2)
    }

    private fun updateSpamBadge(sent: Int, active: Boolean) {
        val badge = binding.spamBadge
        if (active) {
            badge.text = getString(R.string.spam_badge, sent)
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
            ClientState.SCANNING -> getString(R.string.status_scanning)
            ClientState.CONNECTING -> getString(R.string.status_connecting)
            ClientState.DISCOVERING -> getString(R.string.status_discovering)
            ClientState.AUTHENTICATING -> getString(R.string.status_authenticating)
            ClientState.READY -> getString(R.string.status_ready)
            ClientState.ERROR -> getString(R.string.status_error)
        }
        binding.statusText.text = if (name.isEmpty()) stateText else "$name: $stateText"
        val color = when (state) {
            ClientState.IDLE -> R.color.status_idle
            ClientState.SCANNING,
            ClientState.CONNECTING,
            ClientState.DISCOVERING,
            ClientState.AUTHENTICATING,
            -> R.color.status_busy
            ClientState.READY -> R.color.status_ok
            ClientState.ERROR -> R.color.status_error
        }
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, color),
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val screen = intent?.getStringExtra(EXTRA_TAB) ?: return
        val index = when (screen) {
            "devices" -> 0
            "control" -> 1
            "gatt" -> 2
            "log" -> 3
            "settings" -> 4
            else -> return
        }
        showTab(index)
        intent.removeExtra(EXTRA_TAB)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            ScooterClient.stopScan()
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        private const val KEY_TAB = "tab"
        private val TAB_MENU_IDS = intArrayOf(
            R.id.tabDevices,
            R.id.tabControl,
            R.id.tabGatt,
            R.id.tabLog,
            R.id.tabSettings,
        )
    }
}