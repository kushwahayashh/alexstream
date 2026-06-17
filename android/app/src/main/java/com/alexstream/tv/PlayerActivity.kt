package com.alexstream.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import com.alexstream.tv.ui.screens.PlayerScreen
import com.alexstream.tv.ui.screens.SubtitleSpec
import com.alexstream.tv.ui.theme.VibeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val fid = intent.getStringExtra(EXTRA_FID).orEmpty()
        if (streamUrl.isBlank()) {
            finish()
            return
        }

        setContent {
            VibeTheme {
                // Subtitles load asynchronously; playback starts immediately and
                // tracks are sideloaded once /api/subtitles resolves.
                var subtitles by remember { mutableStateOf<List<SubtitleSpec>>(emptyList()) }

                LaunchedEffect(fid) {
                    if (fid.isBlank()) return@LaunchedEffect
                    subtitles = fetchSubtitles(fid)
                }

                PlayerScreen(
                    streamUrl = streamUrl,
                    mediaPath = "", // resume disabled — no Continue Watching row in the UI yet
                    title = title,
                    initialResumePositionMs = 0L,
                    subtitles = subtitles,
                    onClose = { finish() }
                )
            }
        }
    }

    private suspend fun fetchSubtitles(fid: String): List<SubtitleSpec> = withContext(Dispatchers.IO) {
        try {
            val body = httpGet(BackendConfig.resolve("/api/subtitles?fid=$fid"))
            val arr = JSONObject(body).optJSONArray("subtitles") ?: return@withContext emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val rel = o.optString("url")
                if (rel.isBlank()) return@mapNotNull null
                SubtitleSpec(
                    url = BackendConfig.resolve(rel),
                    label = o.optString("langName").ifBlank { "English" },
                    language = o.optString("lang").ifBlank { "en" }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IOException("HTTP $code")
            return text
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_FID = "fid"
    }
}
