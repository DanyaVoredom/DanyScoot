package ru.dany.scoot.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import ru.dany.scoot.BuildConfig

object ReportUploader {

    private const val TAG = "REPORT"
    private const val MIN_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 5000L
    private const val MAX_QUEUE = 10
    private const val QUEUE_DIR = "report_queue"

    sealed interface Status {
        data object Idle : Status
        data object Waiting : Status
        data object Uploading : Status
        data class Success(val id: String) : Status
        data class Failed(val message: String) : Status
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun enqueue(context: Context, report: DiagnosticsReport.Report): String? {
        val app = context.applicationContext
        val prefs = Prefs.get(app)
        if (!prefs.reportUploadEnabled) return app.getString(ru.dany.scoot.R.string.report_upload_disabled)
        if (prefs.reportServerUrl.isBlank()) return app.getString(ru.dany.scoot.R.string.report_upload_no_url)
        val dir = queueDir(app).apply { mkdirs() }
        File(dir, "${report.createdAt}.json").writeText(report.json, Charsets.UTF_8)
        val queued = dir.listFiles { file -> file.name.endsWith(".json") }
            ?.sortedBy { it.name }
            .orEmpty()
        queued.take((queued.size - MAX_QUEUE).coerceAtLeast(0)).forEach { it.delete() }
        scope.launch { drain(app) }
        return null
    }

    fun pendingCount(context: Context): Int =
        queueDir(context.applicationContext).listFiles { file -> file.name.endsWith(".json") }?.size ?: 0

    fun retry(context: Context) {
        val app = context.applicationContext
        scope.launch { drain(app) }
    }

    private suspend fun drain(app: Context) = mutex.withLock {
        val prefs = Prefs.get(app)
        val file = queueDir(app).listFiles { f -> f.name.endsWith(".json") }?.minByOrNull { it.name }
        if (file == null) {
            _status.value = Status.Idle
            return@withLock
        }
        if (!prefs.reportUploadEnabled || prefs.reportServerUrl.isBlank()) {
            _status.value = Status.Failed(app.getString(ru.dany.scoot.R.string.report_upload_disabled))
            return@withLock
        }
        if (prefs.reportWifiOnly && !isWifi(app)) {
            _status.value = Status.Waiting
            return@withLock
        }
        if (System.currentTimeMillis() - prefs.reportLastUpload < MIN_INTERVAL_MS) {
            _status.value = Status.Waiting
            return@withLock
        }
        _status.value = Status.Uploading
        var error = ""
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                val id = post(prefs.reportServerUrl, file.readText(Charsets.UTF_8), prefs.reportToken)
                prefs.reportLastUpload = System.currentTimeMillis()
                prefs.reportLastId = id
                file.delete()
                LogBus.ok(TAG, "Отчёт загружен, ID $id")
                _status.value = Status.Success(id)
                return@withLock
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                LogBus.warn(TAG, "Загрузка отчёта, попытка $attempt: $error")
                if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS * attempt)
            }
        }
        _status.value = Status.Failed(error)
    }

    private fun post(urlText: String, bodyText: String, token: String): String {
        var url = urlText.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "DanyScoot/${BuildConfig.VERSION_NAME}")
        if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
        return try {
            connection.outputStream.use { it.write(bodyText.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code")
            if (response.isBlank()) throw IOException("Пустой ответ сервера")
            val json = JSONObject(response)
            json.optString("id").ifEmpty { json.optString("report_id") }
                .ifEmpty { throw IOException("В ответе нет id") }
        } finally {
            connection.disconnect()
        }
    }

    private fun isWifi(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun queueDir(context: Context): File = File(context.filesDir, QUEUE_DIR)
}
