package com.vishal.sfxeditor

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Debounced project autosave controller.
 *
 * The screen supplies a fresh SavedProject snapshot. Repeated edits reset the
 * pending timer, so only the latest state is written after the quiet period.
 *
 * Call schedule() whenever project state changes, and flush() before leaving
 * the editor or starting an export.
 */
class ProjectAutosaveController(
    context: Context,
    private val debounceMs: Long = 1_500L,
    private val onSaved: (() -> Unit)? = null,
    private val onError: ((Throwable) -> Unit)? = null
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var pendingSnapshot: (() -> SavedProject?)? = null

    private var pendingTask: Runnable? = null
    private var closed = false

    init {
        require(debounceMs >= 250L) {
            "Autosave debounce must be at least 250 ms"
        }
    }

    @Synchronized
    fun schedule(snapshot: () -> SavedProject?) {
        if (closed) return

        pendingSnapshot = snapshot
        pendingTask?.let(handler::removeCallbacks)

        val task = Runnable {
            writePendingSnapshot()
        }
        pendingTask = task
        handler.postDelayed(task, debounceMs)
    }

    /**
     * Immediately saves the latest pending snapshot, if one exists.
     * Safe to call when there is no pending change.
     */
    @Synchronized
    fun flush() {
        if (closed) return
        pendingTask?.let(handler::removeCallbacks)
        pendingTask = null
        writePendingSnapshot()
    }

    @Synchronized
    fun cancelPending() {
        pendingTask?.let(handler::removeCallbacks)
        pendingTask = null
        pendingSnapshot = null
    }

    @Synchronized
    fun close(flushPending: Boolean = true) {
        if (closed) return
        if (flushPending) {
            flush()
        } else {
            cancelPending()
        }
        closed = true
    }

    private fun writePendingSnapshot() {
        val snapshotFactory = synchronized(this) {
            pendingTask = null
            val current = pendingSnapshot
            pendingSnapshot = null
            current
        } ?: return

        try {
            val snapshot = snapshotFactory() ?: return
            ProjectLibrary.upsert(appContext, snapshot)
            onSaved?.invoke()
        } catch (error: Throwable) {
            onError?.invoke(error)
        }
    }
}
