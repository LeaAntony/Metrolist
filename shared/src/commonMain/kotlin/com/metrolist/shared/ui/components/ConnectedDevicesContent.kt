@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.shared.cast.CastConnectionStatus
import com.metrolist.shared.cast.CastDevice
import com.metrolist.shared.cast.CastState
import com.metrolist.shared.deviceconnect.DcSyncState
import com.metrolist.shared.deviceconnect.DeviceConnectManager
import com.metrolist.shared.deviceconnect.DeviceConnectPlaybackStatus
import com.metrolist.shared.deviceconnect.deviceConnectDeviceIcon
import com.metrolist.shared.deviceconnect.deviceConnectDeviceMetadata
import com.metrolist.shared.deviceconnect.localDisplayName
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.available_devices
import com.metrolist.shared.generated.resources.close
import com.metrolist.shared.generated.resources.connected_devices
import com.metrolist.shared.generated.resources.device_connect_active_output_summary
import com.metrolist.shared.generated.resources.device_connect_current_output
import com.metrolist.shared.generated.resources.device_connect_default_device
import com.metrolist.shared.generated.resources.device_connect_default_device_description
import com.metrolist.shared.generated.resources.device_connect_enable
import com.metrolist.shared.generated.resources.device_connect_grant_permission
import com.metrolist.shared.generated.resources.device_connect_open_settings
import com.metrolist.shared.generated.resources.device_connect_pair_hint
import com.metrolist.shared.generated.resources.device_connect_switch_output
import com.metrolist.shared.generated.resources.device_connect_tap_to_switch
import com.metrolist.shared.generated.resources.device_connect_this_device_label
import com.metrolist.shared.generated.resources.device_connect_transferring
import com.metrolist.shared.generated.resources.disconnect_cast
import com.metrolist.shared.generated.resources.no_compatible_devices
import com.metrolist.shared.generated.resources.no_device_connected
import com.metrolist.shared.generated.resources.no_device_found
import com.metrolist.shared.generated.resources.no_other_devices_nearby
import com.metrolist.shared.generated.resources.none
import com.metrolist.shared.generated.resources.scrobble_connecting
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.utils.toPolygonShape
import com.metrolist.shared.ui.utils.verticalScrollWithDrag
import org.jetbrains.compose.resources.stringResource

@Composable
fun ConnectedDevicesContent(
    devices: List<CastDevice>,
    castState: CastState,
    onDeviceClick: (CastDevice) -> Unit,
    onDisconnectCast: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    deviceState: DeviceConnectManager.State? = null,
    syncState: DcSyncState = DcSyncState(),
    isPlaying: Boolean = true,
    onEnableDeviceConnect: (() -> Unit)? = null,
    onTransferTo: (String) -> Unit = {},
    permissionRequired: Boolean = false,
    onOpenIntegrations: (() -> Unit)? = null,
    closeButtonModifier: Modifier = Modifier,
) {
    val castEnabled = Settings.shared.castEnabled
    val activeCast = castState.device.takeIf { castEnabled && castState.status != CastConnectionStatus.DISCONNECTED }
    val availableCast = devices.filter { castEnabled && it.id != activeCast?.id }
    val peers = deviceState?.peers.orEmpty().filter { it.state == DeviceConnectManager.PeerState.CONNECTED }
    val localDeviceId = deviceState?.localDeviceId?.takeIf { it.isNotBlank() }
    val isFollowingPeer = syncState.sessionId != null && syncState.targetId != null && syncState.targetId != localDeviceId
    val activePeer = if (isFollowingPeer) peers.firstOrNull { it.id == syncState.targetId } else null
    val availablePeers = peers.filterNot { it.id == activePeer?.id }
    val showThisDeviceAsCurrent = deviceState != null && activeCast == null && !isFollowingPeer && localDeviceId != null
    val canSwitchToThisDevice = isFollowingPeer && localDeviceId != null
    val empty =
        activeCast == null && activePeer == null && !showThisDeviceAsCurrent &&
            availableCast.isEmpty() && availablePeers.isEmpty()
    val connectingLabel = stringResource(Res.string.scrobble_connecting)
    val thisDeviceFallback = stringResource(Res.string.device_connect_this_device_label)
    val localDisplayName = deviceState.localDisplayName(thisDeviceFallback)
    val transferringToId = syncState.transferringToId
    val transferringTargetName =
        when {
            transferringToId == null -> null
            transferringToId == localDeviceId -> localDisplayName
            else -> peers.firstOrNull { it.id == transferringToId }?.name
        }
    Column(
        modifier.fillMaxWidth().verticalScrollWithDrag(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    Res.string.connected_devices,
                ),
                style = MaterialTheme.typography.headlineSmall,
                modifier =
                    Modifier.weight(1f).semantics {
                        heading()
                    },
            )
            FilledTonalIconButton(onClick = onClose, modifier = closeButtonModifier, shapes = IconButtonDefaults.shapes()) {
                Icon(MetrolistIcons.Outlined.Close, stringResource(Res.string.close))
            }
        }
        if (empty) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DeviceEmblem(large = true)
                Text(
                    stringResource(Res.string.no_device_found),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(Res.string.no_compatible_devices),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            DeviceSectionTitle(stringResource(Res.string.device_connect_current_output))
            if (activeCast == null && activePeer == null && !showThisDeviceAsCurrent) {
                EmptyDeviceSection(stringResource(Res.string.no_device_connected))
            } else {
                val activeCount =
                    (if (activeCast != null) 1 else 0) +
                        (if (activePeer != null) 1 else 0) +
                        (if (showThisDeviceAsCurrent) 1 else 0)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    var activeIndex = 0
                    activeCast?.let { device ->
                        ConnectedDeviceRow(
                            name = device.name,
                            subtitle =
                                if (castState.status == CastConnectionStatus.CONNECTING) {
                                    connectingLabel
                                } else {
                                    protocolName(device.protocol)
                                },
                            icon = MetrolistIcons.Outlined.Cast,
                            index = activeIndex++,
                            count = activeCount,
                            onClick = onDisconnectCast,
                            active = true,
                            showDisconnectAction = true,
                            connecting = castState.status == CastConnectionStatus.CONNECTING,
                        )
                    }
                    activePeer?.let { peer ->
                        ConnectedDeviceRow(
                            name = peer.name.ifBlank { peer.id },
                            subtitle = deviceConnectDeviceMetadata(peer.deviceType, peer.model),
                            icon = deviceConnectDeviceIcon(peer.deviceType),
                            index = activeIndex++,
                            count = activeCount,
                            onClick = null,
                            active = true,
                            isPlaying = isPlaying,
                            connecting = transferringToId == peer.id,
                        )
                    }
                    if (showThisDeviceAsCurrent) {
                        val localMetadata = deviceConnectDeviceMetadata(deviceState.localDeviceType)
                        ConnectedDeviceRow(
                            name = localDisplayName,
                            subtitle = stringResource(Res.string.device_connect_active_output_summary, localMetadata),
                            icon = deviceConnectDeviceIcon(deviceState.localDeviceType),
                            index = activeIndex,
                            count = activeCount,
                            onClick = null,
                            active = true,
                            isPlaying = isPlaying,
                            connecting = transferringToId == localDeviceId,
                        )
                    }
                }
            }
            val switchTitle =
                if (deviceState != null) Res.string.device_connect_switch_output else Res.string.available_devices
            DeviceSectionTitle(stringResource(switchTitle))
            val switchCount =
                (if (canSwitchToThisDevice) 1 else 0) + availableCast.size + availablePeers.size
            if (switchCount == 0) {
                EmptyDeviceSection(stringResource(Res.string.no_other_devices_nearby))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    var switchIndex = 0
                    if (localDeviceId != null && isFollowingPeer) {
                        val isTransferringHere = transferringToId == localDeviceId
                        ConnectedDeviceRow(
                            name = localDisplayName,
                            subtitle =
                                stringResource(
                                    if (isTransferringHere) {
                                        Res.string.device_connect_transferring
                                    } else {
                                        Res.string.device_connect_tap_to_switch
                                    },
                                ),
                            icon = deviceConnectDeviceIcon(deviceState.localDeviceType),
                            index = switchIndex++,
                            count = switchCount,
                            onClick = { onTransferTo(localDeviceId) },
                            connecting = isTransferringHere,
                            showSwitchAffordance = !isTransferringHere,
                            enabled = transferringToId == null,
                        )
                    }
                    availablePeers.forEach { peer ->
                        val isTransferringHere = transferringToId == peer.id
                        ConnectedDeviceRow(
                            name = peer.name.ifBlank { peer.id },
                            subtitle =
                                if (isTransferringHere) {
                                    stringResource(Res.string.device_connect_transferring)
                                } else {
                                    deviceConnectDeviceMetadata(peer.deviceType, peer.model)
                                },
                            icon = deviceConnectDeviceIcon(peer.deviceType),
                            index = switchIndex++,
                            count = switchCount,
                            onClick = { onTransferTo(peer.id) },
                            connecting = isTransferringHere,
                            showSwitchAffordance = !isTransferringHere,
                            enabled =
                                !castState.isConnected &&
                                    castState.status != CastConnectionStatus.CONNECTING &&
                                    transferringToId != peer.id,
                        )
                    }
                    availableCast.forEach { device ->
                        ConnectedDeviceRow(
                            name = device.name,
                            subtitle = protocolName(device.protocol),
                            icon = MetrolistIcons.Outlined.Cast,
                            index = switchIndex++,
                            count = switchCount,
                            onClick = { onDeviceClick(device) },
                            enabled = castState.status != CastConnectionStatus.CONNECTING,
                        )
                    }
                }
            }
        }
        castState.error
            ?.takeIf {
                castEnabled
            }?.let { Text(stringResource(it.stringResource), color = MaterialTheme.colorScheme.error) }
        if (
            syncState.error != null ||
            syncState.transferringToId != null ||
            (syncState.sessionId != null && !syncState.playbackAvailable) ||
            syncState.controllerIds.isNotEmpty()
        ) {
            DeviceConnectPlaybackStatus(syncState, targetName = transferringTargetName)
        }
        if (deviceState != null) {
            val pairedDevices = deviceState.pairedDevices
            if (pairedDevices.isNotEmpty()) {
                var defaultDeviceId by Settings.shared::deviceConnectDefaultDeviceId
                var showDefaults by remember { mutableStateOf(false) }
                DeviceSectionTitle(stringResource(Res.string.device_connect_default_device))
                Text(
                    stringResource(Res.string.device_connect_default_device_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    TextButton(onClick = { showDefaults = true }, shapes = ButtonDefaults.shapes()) {
                        Text(pairedDevices.firstOrNull { it.deviceId == defaultDeviceId }?.deviceName ?: stringResource(Res.string.none))
                    }
                    DropdownMenu(expanded = showDefaults, onDismissRequest = { showDefaults = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.none)) },
                            onClick = {
                                defaultDeviceId = ""
                                showDefaults = false
                            },
                        )
                        pairedDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.deviceName) },
                                onClick = {
                                    defaultDeviceId = device.deviceId
                                    showDefaults = false
                                },
                            )
                        }
                    }
                }
            }
            if (!deviceState.enabled && onEnableDeviceConnect != null) {
                Button(onClick = onEnableDeviceConnect, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                    Text(
                        stringResource(
                            if (permissionRequired) Res.string.device_connect_grant_permission else Res.string.device_connect_enable,
                        ),
                    )
                }
            }
            if (onOpenIntegrations != null) {
                TextButton(
                    onClick = onOpenIntegrations,
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(Res.string.device_connect_open_settings))
                }
            } else {
                Text(
                    stringResource(Res.string.device_connect_pair_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DeviceSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier.semantics {
                heading()
            },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceEmblem(large: Boolean = false) {
    Box(
        Modifier
            .size(
                if (large) 136.dp else 56.dp,
            ).background(MaterialTheme.colorScheme.secondaryContainer, MaterialShapes.Cookie7Sided.toPolygonShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            MetrolistIcons.Outlined.Devices,
            null,
            Modifier.size(if (large) 40.dp else 20.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun EmptyDeviceSection(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceEmblem()
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun ConnectedDeviceRow(
    name: String,
    subtitle: String,
    icon: ImageVector,
    index: Int,
    count: Int,
    onClick: (() -> Unit)?,
    active: Boolean = false,
    isPlaying: Boolean = true,
    showDisconnectAction: Boolean = false,
    showSwitchAffordance: Boolean = false,
    connecting: Boolean = false,
    enabled: Boolean = true,
) {
    val shape =
        RoundedCornerShape(
            topStart = if (index == 0) 20.dp else 5.dp,
            topEnd = if (index == 0) 20.dp else 5.dp,
            bottomStart = if (index == count - 1) 20.dp else 5.dp,
            bottomEnd = if (index == count - 1) 20.dp else 5.dp,
        )
    val containerColor =
        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest
    val contentColor =
        if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val subtitleColor =
        if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val badgeColor =
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val badgeTint =
        if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer

    val rowContent: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(badgeColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = badgeTint)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (connecting) {
                LoadingIndicator(
                    modifier = Modifier.size(20.dp),
                    color = if (active) contentColor else MaterialTheme.colorScheme.primary,
                )
            } else if (showDisconnectAction && onClick != null) {
                IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
                    Icon(MetrolistIcons.Outlined.Close, stringResource(Res.string.disconnect_cast), tint = contentColor)
                }
            } else if (active) {
                NowPlayingBars(
                    isPlaying = isPlaying,
                    color = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            } else if (showSwitchAffordance) {
                Icon(
                    MetrolistIcons.Filled.PlayArrow,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
        ) {
            rowContent()
        }
    } else {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
        ) {
            rowContent()
        }
    }
}
