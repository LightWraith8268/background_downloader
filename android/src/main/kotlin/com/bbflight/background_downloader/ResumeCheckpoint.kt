package com.bbflight.background_downloader

import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Periodic mid-transfer resume points, so a killed process does not restart a download from zero.
 *
 * Resume data is otherwise written only when a task pauses, times out, or is re-enqueued for a
 * WiFi-requirement change. A process kill - force-stop, or the system reclaiming memory - reaches
 * none of those paths, so the partial file is orphaned and the replacement worker starts at byte 0.
 * On a part-downloaded film that discards gigabytes and re-fetches them.
 *
 * Checkpoints are kept under their own preferences key rather than the resume-data map the Dart
 * side pops, so a checkpoint can never be mistaken for a pause the app asked for.
 */
object ResumeCheckpoint {
    private const val TAG = "ResumeCheckpoint"

    const val keyCheckpointMap = "com.bbflight.background_downloader.resumeCheckpointMap.v1"

    /**
     * Bytes between checkpoints. A kill costs at most this much re-downloading, against one small
     * preferences write per interval.
     */
    const val intervalBytes = 16L shl 20 // 16 MB

    fun isDue(bytesSinceLastCheckpoint: Long) = bytesSinceLastCheckpoint >= intervalBytes

    /**
     * The byte offset a cold-started worker may resume from.
     *
     * Returns 0 - restart - unless the temp file on disk is at least as long as the checkpoint
     * claims. Bytes written past the checkpoint are discarded rather than trusted, because nothing
     * recorded that they arrived intact.
     */
    fun startByteFor(checkpointBytes: Long, tempFileLength: Long): Long {
        if (checkpointBytes <= 0) return 0
        if (tempFileLength < checkpointBytes) return 0
        return checkpointBytes
    }

    /** Records [resumeData] for [taskId], replacing any earlier checkpoint. */
    fun write(taskId: String, resumeData: ResumeData, prefs: SharedPreferences) {
        try {
            val map = read(prefs).toMutableMap()
            map[taskId] = Json.encodeToString(resumeData)
            prefs.edit().putString(keyCheckpointMap, Json.encodeToString(map)).apply()
        } catch (e: Exception) {
            // A checkpoint is an optimisation; failing to store one must not fail the download.
            Log.d(TAG, "Could not store resume checkpoint for $taskId: ${e.message}")
        }
    }

    /** The checkpoint for [taskId], or null when there is none or it cannot be read. */
    fun take(taskId: String, prefs: SharedPreferences): ResumeData? {
        return try {
            val encoded = read(prefs)[taskId] ?: return null
            Json.decodeFromString<ResumeData>(encoded)
        } catch (e: Exception) {
            Log.d(TAG, "Could not read resume checkpoint for $taskId: ${e.message}")
            null
        }
    }

    /** Drops the checkpoint for [taskId]. Call when the task reaches a terminal state. */
    fun clear(taskId: String, prefs: SharedPreferences) {
        try {
            val map = read(prefs).toMutableMap()
            if (map.remove(taskId) != null) {
                prefs.edit().putString(keyCheckpointMap, Json.encodeToString(map)).apply()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not clear resume checkpoint for $taskId: ${e.message}")
        }
    }

    /** Length of [path] on disk, or 0 when it is absent. */
    fun lengthOf(path: String): Long {
        if (path.isEmpty()) return 0
        val file = File(path)
        return if (file.exists()) file.length() else 0
    }

    private fun read(prefs: SharedPreferences): Map<String, String> {
        val encoded = prefs.getString(keyCheckpointMap, null) ?: return emptyMap()
        return Json.decodeFromString<Map<String, String>>(encoded)
    }
}
