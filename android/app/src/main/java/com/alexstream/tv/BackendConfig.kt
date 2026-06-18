package com.alexstream.tv

// Where the AlexStream Node backend (/api/*) is reachable. The frontend is
// bundled in the APK; only the API calls hit the network. Set this to your
// deployed Modal URL (no trailing slash). It's exposed to the bundled JS via
// AndroidBridge.backendBase() and used natively for subtitle fetching.
object BackendConfig {
    const val BASE_URL = "https://alexhasitbig--alexstream-serve.modal.run"

    fun normalizedBase(): String = BASE_URL.trimEnd('/')

    // Resolve an app-relative "/api/..." path, or pass through an absolute URL.
    fun resolve(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val suffix = if (path.startsWith("/")) path else "/$path"
        return normalizedBase() + suffix
    }
}
