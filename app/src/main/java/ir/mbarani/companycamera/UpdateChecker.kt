package ir.mbarani.companycamera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val tagName: String,
    val apkUrl: String,
    val releaseNotes: String
)

object UpdateChecker {
    private const val RELEASES_API =
        "https://api.github.com/repos/sepahna/CompanyCameraAndroid/releases/latest"
    private const val USER_AGENT = "CompanyCameraAndroid"

    fun currentVersionCode(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    fun currentVersionName(context: Context): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    }

    suspend fun fetchLatest(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tagName = json.optString("tag_name").ifBlank { return@withContext null }
            val versionCode = parseVersionCode(tagName, json.optString("body"))
            val versionName = parseVersionName(tagName, json.optString("name"), json.optString("body"))
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name").lowercase()
                val url = asset.optString("browser_download_url")
                if (name.endsWith(".apk") && url.isNotBlank()) {
                    apkUrl = url
                    break
                }
            }
            if (apkUrl.isBlank() || versionCode <= 0) return@withContext null
            AppUpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                tagName = tagName,
                apkUrl = apkUrl,
                releaseNotes = json.optString("body").orEmpty()
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseVersionCode(tagName: String, body: String): Int {
        Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("""^v?(\d+)$""", RegexOption.IGNORE_CASE)
            .find(tagName.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("""v?(\d+)\.\d+\.\d+""", RegexOption.IGNORE_CASE)
            .find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        return -1
    }

    private fun parseVersionName(tagName: String, releaseName: String, body: String): String {
        Regex("""versionName\s*[:=]\s*([0-9.]+)""", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.getOrNull(1)?.let { return it }
        Regex("""\d+\.\d+\.\d+""").find(releaseName)?.value?.let { return it }
        Regex("""\d+\.\d+\.\d+""").find(tagName)?.value?.let { return it }
        return tagName.removePrefix("v")
    }

    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val outFile = File(dir, "company-camera-update.apk")
        if (outFile.exists()) outFile.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 60000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("دانلود ناموفق: HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    var downloaded = 0L
                    var lastProgress = -1
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val progress = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }
            onProgress(100)
            outFile
        } finally {
            connection.disconnect()
        }
    }

    fun canRequestInstallPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownSourcesSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
