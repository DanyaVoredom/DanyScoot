package ru.dany.scoot.core

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import ru.dany.scoot.BuildConfig
import ru.dany.scoot.ble.BleProfile
import ru.dany.scoot.ble.BleProfiles
import ru.dany.scoot.ble.DeviceKind
import ru.dany.scoot.ble.ScooterCodec
import ru.dany.scoot.ble.shortUuid

object DiagnosticsReport {

    const val MAX_LOG_LINES = 800
    const val MAX_ERROR_LINES = 60

    class Report(
        val text: String,
        val json: String,
        val createdAt: Long,
    )

    fun environment(context: Context): String {
        val prefs = Prefs.get(context.applicationContext)
        val secrets = secretsOf(prefs)
        val kind = ScooterClient.activeKind
        val profile = BleProfiles.forKind(kind)
        val text = StringBuilder()
        text.append("[app]\n")
        text.append("version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        text.append("package: ").append(context.packageName).append('\n')
        text.append("verbose_log: ").append(prefs.verboseLog).append('\n')
        text.append('\n')
        text.append("[system]\n")
        text.append("android: ").append(Build.VERSION.RELEASE).append(" (sdk ").append(Build.VERSION.SDK_INT).append(")\n")
        text.append("manufacturer: ").append(Build.MANUFACTURER).append('\n')
        text.append("model: ").append(Build.MODEL).append('\n')
        text.append("device: ").append(Build.DEVICE).append('\n')
        text.append("abi: ").append(Build.SUPPORTED_ABIS.joinToString(",")).append('\n')
        text.append('\n')
        text.append("[ble]\n")
        text.append("supported: ").append(ScooterClient.bluetoothSupported()).append('\n')
        text.append("enabled: ").append(ScooterClient.bluetoothEnabled()).append('\n')
        text.append("state: ").append(ScooterClient.state.value.name).append('\n')
        text.append("kind: ").append(kind.name).append('\n')
        text.append("device: ").append(Redactor.text(ScooterClient.deviceName, secrets)).append('\n')
        val address = ScooterClient.deviceAddress
        text.append("address: ").append(if (address.isBlank()) "none" else Redactor.address(address)).append('\n')
        text.append("profile_service: ").append(shortUuid(profile.service)).append('\n')
        text.append("profile_write: ").append(shortUuid(profile.write)).append('\n')
        text.append("profile_notify: ").append(shortUuid(profile.notify)).append('\n')
        text.append("mtu: ").append(profile.mtu).append('\n')
        text.append("link: ").append(ScooterClient.hasLink).append('\n')
        text.append("authenticated: ").append(ScooterClient.isReady).append('\n')
        text.append("gatt_services: ").append(ScooterClient.gatt.value?.services?.size ?: 0).append('\n')
        ScooterClient.remote.value?.let { remote ->
            text.append("remote_model: ").append(Redactor.text(remote.model, secrets)).append('\n')
            text.append("remote_serial: ").append(if (remote.serial.isEmpty()) "none" else MASK_VALUE).append('\n')
            text.append("remote_firmware: ").append(Redactor.text(remote.firmware, secrets)).append('\n')
            text.append("remote_battery: ").append(remote.batteryPercent ?: -1).append('\n')
        }
        return text.toString()
    }

    fun build(context: Context, source: List<LogLine>? = null): Report {
        val app = context.applicationContext
        val prefs = Prefs.get(app)
        val secrets = secretsOf(prefs)
        val lines = source ?: LogBus.lines.value
        val logLines = if (lines.size > MAX_LOG_LINES) lines.takeLast(MAX_LOG_LINES) else lines
        val errors = logLines.filter { it.level == LogLevel.ERR || it.level == LogLevel.WARN }
            .takeLast(MAX_ERROR_LINES)
        val created = System.currentTimeMillis()
        val env = environment(app)

        val text = StringBuilder()
        text.append("DanyScoot diagnostic report\n")
        text.append("created: ").append(stamp(created)).append("\n\n")
        text.append(env).append('\n')
        text.append("[errors]\n")
        if (errors.isEmpty()) text.append("none\n") else errors.forEach { text.append(Redactor.text(format(it), secrets)).append('\n') }
        text.append('\n')
        text.append("[log]\n")
        logLines.forEach { text.append(Redactor.text(format(it), secrets)).append('\n') }

        val json = JSONObject()
            .put("schema", 1)
            .put("created_at", created)
            .put("environment", env.trim())
            .put("errors", JSONArray(errors.map { Redactor.text(format(it), secrets) }))
            .put("log", JSONArray().apply {
                logLines.forEach {
                    put(
                        JSONObject()
                            .put("time", it.timeMs)
                            .put("level", it.level.name)
                            .put("tag", it.tag)
                            .put("text", Redactor.text(it.text, secrets)),
                    )
                }
            })
        return Report(text.toString(), json.toString(), created)
    }

    fun format(line: LogLine): String = "${stamp(line.timeMs)} ${line.level.name} ${line.tag}: ${line.text}"

    fun stamp(timeMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date(timeMs))

    fun secretsOf(prefs: Prefs): List<String> =
        listOf(prefs.bleKey, prefs.aiApiKey, prefs.reportToken)
            .filter { it.isNotBlank() }
            .plus(ScooterCodec.KEY_PRESETS)

    private const val MASK_VALUE = "***"
}
