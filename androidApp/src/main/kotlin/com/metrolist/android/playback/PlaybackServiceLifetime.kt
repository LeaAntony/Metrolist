package com.metrolist.android.playback

import com.metrolist.shared.listentogether.ListenTogetherConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal enum class TaskRemovedServiceAction {
    NONE,
    DEFER_TO_MEDIA_SESSION_SERVICE,
    STOP_LOCALLY_WITHOUT_REMOTE_PAUSE,
}

internal fun resolveTaskRemovedServiceAction(
    isPlaybackOngoing: Boolean,
    isSessionPlaying: Boolean,
    isFollowingDeviceConnect: Boolean,
): TaskRemovedServiceAction =
    when {
        !isFollowingDeviceConnect -> TaskRemovedServiceAction.DEFER_TO_MEDIA_SESSION_SERVICE
        !isPlaybackOngoing || !isSessionPlaying -> TaskRemovedServiceAction.STOP_LOCALLY_WITHOUT_REMOTE_PAUSE
        else -> TaskRemovedServiceAction.NONE
    }

internal fun shouldPauseSessionPlayersOnTaskClear(isFollowingDeviceConnect: Boolean): Boolean = !isFollowingDeviceConnect

internal fun roomNeedsPlaybackService(
    inRoom: Boolean,
    requestPending: Boolean,
    connection: ListenTogetherConnectionState,
): Boolean =
    inRoom || requestPending || connection == ListenTogetherConnectionState.CONNECTING ||
        connection == ListenTogetherConnectionState.RECONNECTING

internal suspend fun observePlaybackServiceLifetime(
    taskRemoved: Flow<Boolean>,
    playbackRequired: Flow<Boolean>,
    stopOnTaskClear: Flow<Boolean>,
    stop: suspend () -> Unit,
) {
    combine(taskRemoved, playbackRequired, stopOnTaskClear) { removed, required, stopOnClear ->
        (removed && (stopOnClear || !required)) to stopOnClear
    }.distinctUntilChanged().collectLatest { (shouldStop, stopOnClear) ->
        if (shouldStop) {
            // Allow focus recovery or a notification command to resume before releasing the player.
            if (!stopOnClear) delay(60_000)
            stop()
        }
    }
}
