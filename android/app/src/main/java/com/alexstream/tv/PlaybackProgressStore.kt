package com.alexstream.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PlaybackProgressStore {
    private const val PREFS_NAME = "playback_progress"
    private const val KEY_ENTRIES = "entries"
    private const val MIN_TRACKED_POSITION_MS = 5_000L
    private const val MIN_RESUME_POSITION_MS = 30_000L
    private const val MIN_REMAINING_FOR_RESUME_MS = 30_000L
    private const val COMPLETION_REMAINING_MS = 60_000L
    private const val COMPLETION_RATIO = 0.95

    data class Entry(
        val mediaPath: String,
        val title: String,
        val positionMs: Long,
        val durationMs: Long,
        val updatedAt: Long
    ) {
        fun progressFraction(): Double {
            if (durationMs <= 0L) return 0.0
            return (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
        }

        fun shouldResume(): Boolean {
            if (positionMs < MIN_RESUME_POSITION_MS) return false
            if (durationMs <= 0L) return false
            return durationMs - positionMs > MIN_REMAINING_FOR_RESUME_MS
        }
    }

    data class Summary(
        val count: Int,
        val lastUpdatedAt: Long
    )

    fun getResumePositionMs(context: Context, mediaPath: String): Long {
        return getEntry(context, mediaPath)?.takeIf { it.shouldResume() }?.positionMs ?: 0L
    }

    fun saveProgress(
        context: Context,
        mediaPath: String,
        title: String,
        positionMs: Long,
        durationMs: Long
    ) {
        if (mediaPath.isBlank()) return

        if (shouldClearEntry(positionMs, durationMs)) {
            clearProgress(context, mediaPath)
            return
        }

        if (positionMs < MIN_TRACKED_POSITION_MS) {
            return
        }

        val entries = readEntries(context)
        entries.put(
            mediaPath,
            JSONObject().apply {
                put("mediaPath", mediaPath)
                put("title", title)
                put("positionMs", positionMs.coerceAtLeast(0L))
                put("durationMs", durationMs.coerceAtLeast(0L))
                put("updatedAt", System.currentTimeMillis())
            }
        )
        writeEntries(context, entries)
    }

    fun clearProgress(context: Context, mediaPath: String) {
        if (mediaPath.isBlank()) return
        val entries = readEntries(context)
        entries.remove(mediaPath)
        writeEntries(context, entries)
    }

    fun clearAll(context: Context) {
        writeEntries(context, JSONObject())
    }

    fun getHistorySummaryPayload(context: Context): String {
        val summary = getSummary(context)
        return JSONObject().apply {
            put("count", summary.count)
            put("lastUpdatedAt", summary.lastUpdatedAt)
            put("hasHistory", summary.count > 0)
        }.toString()
    }

    fun getProgressPayload(context: Context, pathsJson: String): String {
        val result = JSONObject()
        val paths = try {
            JSONArray(pathsJson)
        } catch (_: Exception) {
            JSONArray()
        }

        for (i in 0 until paths.length()) {
            val mediaPath = paths.optString(i)
            if (mediaPath.isBlank()) continue
            val entry = getEntry(context, mediaPath) ?: continue
            result.put(
                mediaPath,
                JSONObject().apply {
                    put("title", entry.title)
                    put("positionMs", entry.positionMs)
                    put("durationMs", entry.durationMs)
                    put("updatedAt", entry.updatedAt)
                    put("progress", entry.progressFraction())
                    put("shouldResume", entry.shouldResume())
                }
            )
        }

        return result.toString()
    }

    private fun getEntry(context: Context, mediaPath: String): Entry? {
        if (mediaPath.isBlank()) return null
        val entry = readEntries(context).optJSONObject(mediaPath) ?: return null
        return Entry(
            mediaPath = mediaPath,
            title = entry.optString("title"),
            positionMs = entry.optLong("positionMs"),
            durationMs = entry.optLong("durationMs"),
            updatedAt = entry.optLong("updatedAt")
        )
    }

    private fun getSummary(context: Context): Summary {
        val entries = readEntries(context)
        var count = 0
        var lastUpdatedAt = 0L

        val keys = entries.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = entries.optJSONObject(key) ?: continue
            count += 1
            lastUpdatedAt = maxOf(lastUpdatedAt, entry.optLong("updatedAt"))
        }

        return Summary(count = count, lastUpdatedAt = lastUpdatedAt)
    }

    private fun shouldClearEntry(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val remainingMs = durationMs - positionMs
        val ratio = if (durationMs > 0L) positionMs.toDouble() / durationMs.toDouble() else 0.0
        return remainingMs <= COMPLETION_REMAINING_MS || ratio >= COMPLETION_RATIO
    }

    private fun readEntries(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun writeEntries(context: Context, entries: JSONObject) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, entries.toString())
            .apply()
    }
}
