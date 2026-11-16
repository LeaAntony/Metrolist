package com.metrolist.shared.deviceconnect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.device_connect_controllers_count
import com.metrolist.shared.generated.resources.device_connect_device_type_and_model
import com.metrolist.shared.generated.resources.device_connect_device_type_android_auto
import com.metrolist.shared.generated.resources.device_connect_device_type_android_phone
import com.metrolist.shared.generated.resources.device_connect_device_type_desktop
import com.metrolist.shared.generated.resources.device_connect_device_type_ios
import com.metrolist.shared.generated.resources.device_connect_device_type_unknown
import com.metrolist.shared.generated.resources.device_connect_device_type_wear_os
import com.metrolist.shared.generated.resources.device_connect_playing_on
import com.metrolist.shared.generated.resources.device_connect_sync_conflict
import com.metrolist.shared.generated.resources.device_connect_sync_stale_queue
import com.metrolist.shared.generated.resources.device_connect_sync_too_large
import com.metrolist.shared.generated.resources.device_connect_this_device_label
import com.metrolist.shared.generated.resources.device_connect_transferring
import com.metrolist.shared.generated.resources.device_connect_transferring_to
import com.metrolist.shared.generated.resources.device_connect_unshareable_queue
import com.metrolist.shared.generated.resources.listen_together_blocked_by_host
import com.metrolist.shared.generated.resources.listen_together_error
import com.metrolist.shared.generated.resources.unavailable
import com.metrolist.shared.ui.icons.MetrolistIcons
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

fun deviceConnectDeviceIcon(deviceType: DeviceConnectDeviceType): ImageVector =
    when (deviceType) {
        DeviceConnectDeviceType.ANDROID_PHONE -> MetrolistIcons.Outlined.PhoneAndroid
        DeviceConnectDeviceType.DESKTOP -> MetrolistIcons.Outlined.Computer
        DeviceConnectDeviceType.ANDROID_AUTO -> MetrolistIcons.Outlined.DirectionsCar
        DeviceConnectDeviceType.WEAR_OS -> MetrolistIcons.Outlined.Watch
        DeviceConnectDeviceType.IOS -> MetrolistIcons.Outlined.PhoneIphone
        DeviceConnectDeviceType.UNKNOWN -> MetrolistIcons.Outlined.DeviceUnknown
    }

fun DeviceConnectManager.State?.localDisplayName(fallback: String): String = this?.localDeviceName?.takeIf { it.isNotBlank() } ?: fallback

@Immutable
data class DeviceConnectUiSummary(
    val enabled: Boolean = false,
    val isActive: Boolean = false,
    val isFollowing: Boolean = false,
    val isTransferring: Boolean = false,
    val activeDeviceName: String = "",
    val activeDeviceType: DeviceConnectDeviceType = DeviceConnectDeviceType.UNKNOWN,
    val connectedPeerCount: Int = 0,
) {
    val hasConnectedPeer: Boolean
        get() = connectedPeerCount > 0
}

@Composable
fun rememberDeviceConnectUiSummary(
    managerState: DeviceConnectManager.State,
    syncState: DcSyncState,
): DeviceConnectUiSummary = summarizeDeviceConnectUi(managerState, syncState, stringResource(Res.string.device_connect_this_device_label))

internal fun summarizeDeviceConnectUi(
    managerState: DeviceConnectManager.State,
    syncState: DcSyncState,
    thisDeviceLabel: String,
): DeviceConnectUiSummary {
    val localId = managerState.localDeviceId.takeIf { it.isNotBlank() }
    val connectedPeerCount = managerState.peers.count { it.state == DeviceConnectManager.PeerState.CONNECTED }
    val isTransferring = syncState.transferringToId != null
    val isFollowing = syncState.sessionId != null && syncState.targetId != null && syncState.targetId != localId
    val isActive = syncState.sessionId != null || isTransferring
    val lookupId = syncState.transferringToId ?: syncState.targetId
    val (deviceName, deviceType) =
        when {
            lookupId == null -> {
                "" to DeviceConnectDeviceType.UNKNOWN
            }

            lookupId == localId -> {
                managerState.localDisplayName(thisDeviceLabel) to managerState.localDeviceType
            }

            else -> {
                val peer = managerState.peers.firstOrNull { it.id == lookupId }
                val paired = managerState.pairedDevices.firstOrNull { it.deviceId == lookupId }
                val name = peer?.name?.ifBlank { peer.id } ?: paired?.deviceName?.ifBlank { paired.deviceId }.orEmpty()
                val type = peer?.deviceType ?: paired?.deviceType ?: DeviceConnectDeviceType.UNKNOWN
                name to type
            }
        }
    return DeviceConnectUiSummary(
        enabled = managerState.enabled,
        isActive = isActive,
        isFollowing = isFollowing,
        isTransferring = isTransferring,
        activeDeviceName = deviceName,
        activeDeviceType = deviceType,
        connectedPeerCount = connectedPeerCount,
    )
}

@Composable
fun DeviceConnectDeviceTypeIcon(
    deviceType: DeviceConnectDeviceType,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val icon = deviceConnectDeviceIcon(deviceType)
    Surface(
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = tint,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
        }
    }
}

@Composable
fun deviceConnectDeviceMetadata(
    deviceType: DeviceConnectDeviceType,
    model: String = "",
): String {
    val type =
        stringResource(
            when (deviceType) {
                DeviceConnectDeviceType.ANDROID_PHONE -> Res.string.device_connect_device_type_android_phone
                DeviceConnectDeviceType.DESKTOP -> Res.string.device_connect_device_type_desktop
                DeviceConnectDeviceType.ANDROID_AUTO -> Res.string.device_connect_device_type_android_auto
                DeviceConnectDeviceType.WEAR_OS -> Res.string.device_connect_device_type_wear_os
                DeviceConnectDeviceType.IOS -> Res.string.device_connect_device_type_ios
                DeviceConnectDeviceType.UNKNOWN -> Res.string.device_connect_device_type_unknown
            },
        )
    return if (
        model.isBlank() ||
        deviceType == DeviceConnectDeviceType.DESKTOP ||
        model.equals(type, ignoreCase = true)
    ) {
        type
    } else {
        stringResource(Res.string.device_connect_device_type_and_model, type, model)
    }
}

@Composable
fun DeviceConnectPlaybackStatus(
    state: DcSyncState,
    targetName: String? = null,
) {
    val errorResource =
        when (state.error) {
            DcSyncError.CONFLICT -> Res.string.device_connect_sync_conflict
            DcSyncError.UNSHAREABLE_QUEUE -> Res.string.device_connect_unshareable_queue
            DcSyncError.NOT_ALLOWED -> Res.string.listen_together_blocked_by_host
            DcSyncError.STALE_QUEUE -> Res.string.device_connect_sync_stale_queue
            DcSyncError.TOO_LARGE -> Res.string.device_connect_sync_too_large
            DcSyncError.UNAVAILABLE -> Res.string.unavailable
            DcSyncError.INVALID_MESSAGE -> Res.string.listen_together_error
            null -> null
        }
    val text =
        when {
            errorResource != null -> {
                stringResource(errorResource)
            }

            state.transferringToId != null && !targetName.isNullOrBlank() -> {
                stringResource(Res.string.device_connect_transferring_to, targetName)
            }

            state.transferringToId != null -> {
                stringResource(Res.string.device_connect_transferring)
            }

            state.sessionId != null && !state.playbackAvailable -> {
                stringResource(Res.string.unavailable)
            }

            state.controllerIds.isNotEmpty() -> {
                pluralStringResource(
                    Res.plurals.device_connect_controllers_count,
                    state.controllerIds.size,
                    state.controllerIds.size,
                )
            }

            else -> {
                return
            }
        }
    val color =
        when {
            state.error != null -> MaterialTheme.colorScheme.error
            state.transferringToId != null -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun DeviceConnectOutputPill(
    deviceName: String,
    deviceType: DeviceConnectDeviceType = DeviceConnectDeviceType.UNKNOWN,
    isTransferring: Boolean = false,
    isRemote: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isRemote) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val contentColor =
        if (isRemote) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isTransferring) {
                LoadingIndicator(
                    modifier = Modifier.size(16.dp),
                    color = contentColor,
                )
            } else {
                Icon(
                    imageVector = deviceConnectDeviceIcon(deviceType),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor,
                )
            }
            Text(
                text =
                    when {
                        isTransferring && deviceName.isNotBlank() -> {
                            stringResource(Res.string.device_connect_transferring_to, deviceName)
                        }

                        isTransferring -> {
                            stringResource(Res.string.device_connect_transferring)
                        }

                        else -> {
                            stringResource(Res.string.device_connect_playing_on, deviceName)
                        }
                    },
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!isTransferring) {
                Icon(
                    imageVector = MetrolistIcons.Outlined.Equalizer,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun deviceConnectSegmentedShapes(
    index: Int,
    count: Int,
): ListItemShapes {
    val shapes = ListItemDefaults.segmentedShapes(index, count)
    val groupedShape = if (count == 1) shapes.selectedShape else shapes.shape
    return shapes.copy(
        shape = groupedShape,
        selectedShape = groupedShape,
        pressedShape = groupedShape,
        focusedShape = groupedShape,
        hoveredShape = groupedShape,
        draggedShape = groupedShape,
    )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun deviceConnectSegmentedColors(
    connected: Boolean = false,
    offline: Boolean = false,
    error: Boolean = false,
): ListItemColors {
    val containerColor =
        when {
            connected -> MaterialTheme.colorScheme.primaryContainer
            error -> MaterialTheme.colorScheme.errorContainer
            offline -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val contentColor =
        when {
            connected -> MaterialTheme.colorScheme.onPrimaryContainer
            error -> MaterialTheme.colorScheme.onErrorContainer
            offline -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        }
    val supportingContentColor =
        when {
            connected -> MaterialTheme.colorScheme.onPrimaryContainer
            error -> MaterialTheme.colorScheme.onErrorContainer
            offline -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val disabledContentColor =
        if (offline) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else contentColor
    return ListItemDefaults.segmentedColors(
        containerColor = containerColor,
        contentColor = contentColor,
        leadingContentColor = supportingContentColor,
        trailingContentColor = supportingContentColor,
        supportingContentColor = supportingContentColor,
        disabledContainerColor = containerColor,
        disabledContentColor = disabledContentColor,
        disabledLeadingContentColor = supportingContentColor,
        disabledTrailingContentColor = supportingContentColor,
        disabledSupportingContentColor = supportingContentColor,
        selectedContainerColor = containerColor,
        selectedContentColor = contentColor,
        selectedLeadingContentColor = supportingContentColor,
        selectedTrailingContentColor = supportingContentColor,
        selectedSupportingContentColor = supportingContentColor,
    )
}
