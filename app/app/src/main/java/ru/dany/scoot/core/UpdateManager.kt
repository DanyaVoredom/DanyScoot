package ru.dany.scoot.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.dany.scoot.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data object UpToDate : Status
        data class Available(val version: String, val size: Long) : Status
        data class Downloading(val version: String, val percent: Int) : Status
        data class Ready(val version: String, val file: File) : Status
        data class Failed(val message: String) : Status
    }

    private const val TAG = "UPDATE"
    private const val API = "https://codeberg.org/api/v1/repos/DanyaVoredom/DanyScoot/releases/latest"
    private const val APK_TYPE = "application/vnd.android.package-archive"
    private const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class Release(val version: String, val url: String, val size: Long)

    fun autoCheck(context: Context) {
        val app = context.applicationContext
        val prefs = Prefs.get(app)
        if (!prefs.autoUpdate) return
        restoreCached(app)
        val now = System.currentTimeMillis()
        if (now - prefs.lastUpdateCheck < AUTO_CHECK_INTERVAL_MS) return
        if (!isIdle()) return
        prefs.lastUpdateCheck = now
        check(app)
    }

    fun check(context: Context) {
        val app = context.applicationContext
        val current = _status.value
        if (current is Status.Checking || current is Status.Downloading) return
        scope.launch {
            _status.value = Status.Checking
            try {
                val release = fetchRelease()
                if (release == null) {
                    _status.value = Status.UpToDate
                    LogBus.info(TAG, "Релизов с APK нет")
                    return@launch
                }
                if (!isNewer(release.version, BuildConfig.VERSION_NAME)) {
                    _status.value = Status.UpToDate
                    LogBus.info(TAG, "Версия ${BuildConfig.VERSION_NAME} актуальна")
                    return@launch
                }
                LogBus.info(TAG, "Доступно обновление ${release.version}")
                _status.value = Status.Available(release.version, release.size)
                val file = download(app, release)
                _status.value = Status.Ready(release.version, file)
                LogBus.ok(TAG, "Обновление ${release.version} скачано")
            } catch (e: Exception) {
                val message = e.message ?: "неизвестная ошибка"
                _status.value = Status.Failed(message)
                LogBus.warn(TAG, "Обновление: $message")
            }
        }
    }

    fun install(context: Context, ready: Status.Ready): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            LogBus.warn(TAG, "Нужно разрешение на установку APK")
            return false
        }
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.files", ready.file)
        } catch (e: Exception) {
            _status.value = Status.Failed("Файл обновления недоступен")
            LogBus.err(TAG, "FileProvider: ${e.message}")
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            LogBus.info(TAG, "Открыт установщик ${ready.version}")
            true
        } catch (e: Exception) {
            _status.value = Status.Failed("Нет установщика APK")
            LogBus.err(TAG, "Установщик: ${e.message}")
            false
        }
    }

    private fun isIdle(): Boolean {
        val current = _status.value
        return current is Status.Idle || current is Status.UpToDate || current is Status.Failed
    }

    private fun restoreCached(context: Context) {
        if (_status.value is Status.Ready) return
        val dir = File(context.cacheDir, "updates")
        val apk = dir.listFiles { file -> file.isFile && file.name.endsWith(".apk", true) }
            ?.maxByOrNull { it.lastModified() } ?: return
        val version = Regex("DanyScoot-(.+)\\.apk").find(apk.name)?.groupValues?.getOrNull(1) ?: return
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return
        _status.value = Status.Ready(version, apk)
        LogBus.info(TAG, "Найден скачанный APK ${version}")
    }

    private fun fetchRelease(): Release? {
        val connection = URL(API).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "DanyScoot/${BuildConfig.VERSION_NAME}")
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (code !in 200..299) throw IOException("HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val version = normalizeVersion(json.optString("tag_name").ifEmpty { json.optString("name") })
            if (version.isEmpty()) return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url")
                if (url.isEmpty()) continue
                return Release(version, url, asset.optLong("size"))
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun download(context: Context, release: Release): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "DanyScoot-${release.version}.apk")
        if (target.isFile && target.length() > 0 && (release.size <= 0 || target.length() == release.size)) {
            return target
        }
        val temp = File(dir, "update.tmp")
        if (temp.exists()) temp.delete()
        val connection = URL(release.url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/octet-stream")
        connection.setRequestProperty("User-Agent", "DanyScoot/${BuildConfig.VERSION_NAME}")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val total = connection.contentLength.toLong().takeIf { it > 0 } ?: release.size
            var copied = 0L
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = (copied * 100 / total).toInt().coerceIn(0, 100)
                            _status.value = Status.Downloading(release.version, percent)
                        }
                    }
                }
            }
            if (copied <= 0) throw IOException("пустой файл")
        } finally {
            connection.disconnect()
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return target
    }

    private fun normalizeVersion(raw: String): String {
        val match = Regex("\\d+(\\.\\d+)*").find(raw.trim()) ?: return ""
        return match.value
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val left = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val right = current.split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val a = left.getOrElse(i) { 0 }
            val b = right.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
