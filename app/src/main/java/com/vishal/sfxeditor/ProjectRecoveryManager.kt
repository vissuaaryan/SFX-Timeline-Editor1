package com.vishal.sfxeditor

import android.content.Context

/**
 * Stores one crash-recovery snapshot using the existing ProjectLibrary
 * serializer, so the recovery copy includes the complete project, events,
 * asset links, video URI and mix settings.
 *
 * The reserved ID is filtered from the normal project list in the
 * ProjectLibrary integration step.
 */
class ProjectRecoveryManager(context: Context) {
    private val appContext = context.applicationContext

    fun saveCheckpoint(snapshot: SavedProject): Boolean {
        return runCatching {
            val checkpoint = snapshot.copy(
                id = RECOVERY_ID,
                name = RECOVERY_NAME,
                savedAt = System.currentTimeMillis()
            )
            ProjectLibrary.upsert(appContext, checkpoint)
            true
        }.getOrDefault(false)
    }

    fun getCheckpoint(): SavedProject? {
        return runCatching {
            ProjectLibrary.load(appContext)
                .firstOrNull { it.id == RECOVERY_ID }
        }.getOrNull()
    }

    fun hasCheckpoint(): Boolean = getCheckpoint() != null

    fun clearCheckpoint() {
        ProjectLibrary.delete(appContext, RECOVERY_ID)
    }

    /**
     * Restores the checkpoint as a normal, independently saved project.
     * The recovery checkpoint remains until clearCheckpoint() is called,
     * allowing the caller to clear it only after the restored project is
     * successfully opened.
     */
    fun restoreAsProject(newName: String? = null): SavedProject? {
        val checkpoint = getCheckpoint() ?: return null
        val restored = checkpoint.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = newName?.trim()?.takeIf { it.isNotEmpty() }
                ?: checkpoint.name.removeSuffix(" (Recovery)"),
            savedAt = System.currentTimeMillis()
        )
        return runCatching {
            ProjectLibrary.upsert(appContext, restored)
            restored
        }.getOrNull()
    }

    companion object {
        const val RECOVERY_ID = "__sfx_editor_recovery_checkpoint__"
        const val RECOVERY_NAME = "Unsaved Recovery Checkpoint"
    }
}
