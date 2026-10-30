package com.zeroflux.cprcoach

import android.content.Context
import com.zeroflux.cprcoach.engine.PoseFrame

/** Release has no CSV reader, synthetic arm, intent handling, or replay scheduler. */
internal object PractiseReplay {
    @Suppress("UNUSED_PARAMETER")
    fun requestedFile(context: Context): String? = null

    @Suppress("UNUSED_PARAMETER")
    suspend fun play(context: Context, name: String, onFrame: (PoseFrame) -> Unit): Nothing =
        error("Replay is unavailable in release builds")
}
