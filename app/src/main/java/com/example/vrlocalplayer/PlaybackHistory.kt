package com.example.vrlocalplayer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers recently played videos and where playback stopped in each one,
 * so a video resumes from its last position and shows up under "Continue watching".
 */
object PlaybackHistory {

    private const val PREFS_NAME = "playback_history"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 20

    // Closer than this to the end counts as finished, so the next play starts over.
    private const val FINISHED_MARGIN_MS = 5_000L

    data class Entry(
        val uri: String,
        val name: String,
        val positionMs: Long,
        val durationMs: Long,
        val lastPlayedAt: Long
    )

    fun all(context: Context): List<Entry> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Entry(
                    uri = o.getString("uri"),
                    name = o.optString("name", ""),
                    positionMs = o.optLong("positionMs", 0L),
                    durationMs = o.optLong("durationMs", 0L),
                    lastPlayedAt = o.optLong("lastPlayedAt", 0L)
                )
            }.sortedByDescending { it.lastPlayedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun find(context: Context, uri: String): Entry? = all(context).firstOrNull { it.uri == uri }

    /** Position to resume from, or 0 when the video is new or was watched to the end. */
    fun resumePosition(context: Context, uri: String): Long {
        val entry = find(context, uri) ?: return 0L
        if (entry.durationMs > 0 && entry.positionMs >= entry.durationMs - FINISHED_MARGIN_MS) return 0L
        return entry.positionMs
    }

    fun record(context: Context, uri: String, name: String, positionMs: Long, durationMs: Long) {
        val previous = find(context, uri)
        val entry = Entry(
            uri = uri,
            name = name.ifBlank { previous?.name ?: "" },
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = if (durationMs > 0) durationMs else previous?.durationMs ?: 0L,
            lastPlayedAt = System.currentTimeMillis()
        )
        val updated = listOf(entry) + all(context).filter { it.uri != uri }
        write(context, updated.take(MAX_ENTRIES))
    }

    fun remove(context: Context, uri: String) {
        write(context, all(context).filter { it.uri != uri })
    }

    fun displayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) return name
                    }
                }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun write(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject()
                    .put("uri", e.uri)
                    .put("name", e.name)
                    .put("positionMs", e.positionMs)
                    .put("durationMs", e.durationMs)
                    .put("lastPlayedAt", e.lastPlayedAt)
            )
        }
        prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
