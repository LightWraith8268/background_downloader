package com.bbflight.background_downloader

import java.util.concurrent.atomic.AtomicReference

/**
 * Admits one task at a time to the process's WorkManager foreground service.
 *
 * A process has exactly one `SystemForegroundService`. With `runInForeground` enabled and more
 * than one task running concurrently, every task calls `setForeground`; the surplus promotions
 * fail and WorkManager cancels those workers. The task then reports `waitingToRetry`, the retry
 * arrives as a fresh task id, and the queue churns without finishing anything.
 *
 * Admitting a single holder avoids that. The holder anchors the process in the foreground, which
 * is what keeps downloads alive once the app is backgrounded, and the other tasks run as ordinary
 * workers inside that same process rather than fighting for a service they cannot have.
 */
object ForegroundSlot {
    private val holder = AtomicReference<String?>(null)

    /**
     * Whether [taskId] may run in the foreground. True for the first caller, and thereafter only
     * for that same task, which re-asks on every resume and content-length update.
     */
    fun claim(taskId: String): Boolean = holder.compareAndSet(null, taskId) || holder.get() == taskId

    /**
     * Releases the slot if [taskId] holds it. A task that never held it finishing is a no-op, so a
     * short download completing cannot evict the long one currently anchoring the process.
     */
    fun release(taskId: String) {
        holder.compareAndSet(taskId, null)
    }

    /** Test seam: drops any holder. */
    fun releaseAll() {
        holder.set(null)
    }
}
