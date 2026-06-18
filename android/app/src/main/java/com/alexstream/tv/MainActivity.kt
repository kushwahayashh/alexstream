package com.alexstream.tv

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val bridgeExecutor = Executors.newSingleThreadExecutor()

    // D-pad throttle state (mirrors ALEX: keeps fast remote scrolling from
    // overwhelming the WebView's focus handling).
    private var lastNavKeyCode: Int = KeyEvent.KEYCODE_UNKNOWN
    private var lastNavKeyAtMs: Long = 0L

    // In-app updater: id of the in-flight APK download and our completion receiver.
    private var downloadId: Long = -1L
    private var downloadReceiverRegistered = false

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != downloadId) return
            val ok = downloadSucceeded(id)
            if (ok) installApk()
            notifyUpdateStatus(ok, if (ok) "" else "Download failed")
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        enterImmersive()

        // Listen for the updater's APK download to finish, then launch the installer.
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onDownloadComplete, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(onDownloadComplete, filter)
        }
        downloadReceiverRegistered = true

        // Serve bundled assets over an https origin so ES modules (import/export)
        // load — file:// blocks module scripts. Registered at "/" so the
        // frontend's absolute paths ("/main.js", "/style.css") resolve.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            }
            webChromeClient = WebChromeClient()

            addJavascriptInterface(NativeBridge(), "AndroidBridge")
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(0xFF000000.toInt())
        }

        setContentView(webView)
        webView.loadUrl("https://appassets.androidplatform.net/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Let the web app's keyboard router handle Back as Escape first
                // (closes modals / returns to previous page). If web history has
                // somewhere to go, go there; otherwise exit.
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun enterImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    // ── In-app updater ───────────────────────────────────────────────────────
    // The downloaded APK lives in app-private external storage and is served to
    // the system installer through the FileProvider declared in the manifest.
    private fun updateApkFile(): File =
        File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")

    private fun downloadSucceeded(id: Long): Boolean {
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return status == DownloadManager.STATUS_SUCCESSFUL
        }
    }

    private fun installApk() {
        val file = updateApkFile()
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(intent)
    }

    // Tell the web UI how the download ended so the Update button can reset / show "Failed".
    private fun notifyUpdateStatus(ok: Boolean, message: String) {
        val js = "window.__onUpdateStatus && window.__onUpdateStatus($ok, ${JSONObject.quote(message)});"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (shouldThrottleNavKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun isThrottledNavKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> true
        else -> false
    }

    private fun shouldThrottleNavKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val keyCode = event.keyCode
        if (!isThrottledNavKey(keyCode)) return false

        val now = SystemClock.uptimeMillis()
        val sinceLast = now - lastNavKeyAtMs
        val sameDirectionBurst = keyCode == lastNavKeyCode && sinceLast < 120L
        val isRepeat = event.repeatCount > 0 || sameDirectionBurst
        val throttleMs = if (isRepeat) 80L else 16L
        val shouldThrottle = now - lastNavKeyAtMs < throttleMs

        if (!shouldThrottle) {
            lastNavKeyCode = keyCode
            lastNavKeyAtMs = now
        }
        return shouldThrottle
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onDestroy() {
        if (downloadReceiverRegistered) {
            unregisterReceiver(onDownloadComplete)
            downloadReceiverRegistered = false
        }
        webView.destroy()
        super.onDestroy()
    }

    private inner class NativeBridge {
        // Backend base URL exposed to the bundled JS (Modal server).
        @JavascriptInterface
        fun backendBase(): String = BackendConfig.normalizedBase()

        // GET any URL (TMDB or /api/* resolved by JS) and hand the raw body back
        // to JS via __nativeFetchResolve. Runs off the UI thread.
        @JavascriptInterface
        fun fetchJson(url: String, callbackId: String) {
            bridgeExecutor.execute {
                val (ok, payload) = try {
                    true to httpGet(url)
                } catch (e: Exception) {
                    false to (e.message ?: "Network error")
                }
                val js = "window.__nativeFetchResolve(" +
                    "${JSONObject.quote(callbackId)}, $ok, ${JSONObject.quote(payload)});"
                webView.post { webView.evaluateJavascript(js, null) }
            }
        }

        // Launch the native ExoPlayer for the chosen stream.
        @JavascriptInterface
        fun play(url: String, ext: String, title: String, fid: String) {
            if (url.isBlank()) return
            runOnUiThread {
                val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_STREAM_URL, url)
                    putExtra(PlayerActivity.EXTRA_TITLE, title)
                    putExtra(PlayerActivity.EXTRA_FID, fid)
                }
                startActivity(intent)
            }
        }

        // Download the latest APK and (on completion) launch the installer. The
        // button's "Downloading…" state is driven by JS; onDownloadComplete fires
        // installApk() and reports back via window.__onUpdateStatus.
        @JavascriptInterface
        fun updateApp(url: String) {
            if (url.isBlank()) return
            runOnUiThread {
                val file = updateApkFile()
                if (file.exists()) file.delete()

                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle("AlexStream Update")
                    setDescription("Downloading the latest version")
                    setDestinationInExternalFilesDir(
                        this@MainActivity, Environment.DIRECTORY_DOWNLOADS, "update.apk"
                    )
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                }
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadId = manager.enqueue(request)
            }
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
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IOException("HTTP $code $body")
            return body
        } finally {
            conn.disconnect()
        }
    }
}
