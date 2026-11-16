package com.metrolist.shared.deviceconnect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceConnectPlaybackStatusTest {
    @Test
    fun summaryCountsConnectedPeersOnlyForQuickOutputSwitch() {
        val connectedPeer =
            DeviceConnectManager.PeerDevice(
                id = "phone-1",
                name = "Phone",
                host = "192.168.1.2",
                port = 8080,
                state = DeviceConnectManager.PeerState.CONNECTED,
                deviceType = DeviceConnectDeviceType.ANDROID_PHONE,
            )
        val discoveredPeer =
            connectedPeer.copy(
                id = "tablet-1",
                name = "Tablet",
                state = DeviceConnectManager.PeerState.DISCOVERED,
            )
        val managerState =
            DeviceConnectManager.State(
                enabled = true,
                localDeviceId = "pc-1",
                localDeviceType = DeviceConnectDeviceType.DESKTOP,
                peers = listOf(connectedPeer, discoveredPeer),
            )

        val idleSummary = summarizeDeviceConnectUi(managerState, DcSyncState(), "This device")
        assertEquals(1, idleSummary.connectedPeerCount)
        assertTrue(idleSummary.hasConnectedPeer)
        assertFalse(idleSummary.isActive)

        val disconnectedSummary =
            summarizeDeviceConnectUi(
                managerState.copy(peers = listOf(discoveredPeer)),
                DcSyncState(),
                "This device",
            )
        assertEquals(0, disconnectedSummary.connectedPeerCount)
        assertFalse(disconnectedSummary.hasConnectedPeer)

        val activeSummary =
            summarizeDeviceConnectUi(
                managerState,
                DcSyncState(sessionId = "s-1", targetId = "phone-1"),
                "This device",
            )
        assertTrue(activeSummary.isActive)
        assertTrue(activeSummary.isFollowing)
        assertEquals("Phone", activeSummary.activeDeviceName)
        assertEquals(DeviceConnectDeviceType.ANDROID_PHONE, activeSummary.activeDeviceType)
    }
}
