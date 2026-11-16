package com.metrolist.shared.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerControlGroupLayoutTest {
    @Test
    fun connectedEndFollowsTheLastButtonThatFitsBesideOverflow() {
        assertEquals(3, lastVisible(198f))
        assertEquals(2, lastVisible(196f))
        assertEquals(1, lastVisible(195f))
        assertEquals(1, lastVisible(146f))
        assertEquals(0, lastVisible(145f))
        assertEquals(0, lastVisible(96f))
        assertEquals(-1, lastVisible(95f))
    }

    @Test
    fun unconstrainedAndSingleButtonGroupsKeepTheirOuterCorners() {
        assertEquals(3, lastVisible(0f))
        assertEquals(0, playerControlLastVisibleIndex(48f, 1, 48f, 2f, 48f))
        assertEquals(-1, playerControlLastVisibleIndex(48f, 0, 48f, 2f, 48f))
    }

    @Test
    fun connectedDeviceConnectStaysAdjacentToVolumeOutsideOverflowWhenTight() {
        val actions = listOf("queue", "lyrics", "shuffle", "repeat", "volume", "device_connect", "miniplayer", "fullscreen")
        val promoted =
            promoteAdjacentControlActions(
                actions = actions,
                lastVisibleIndex = 4,
                primaryIndex = actions.indexOf("volume"),
                secondaryIndex = actions.indexOf("device_connect"),
            )
        assertEquals(
            listOf("queue", "lyrics", "shuffle", "volume", "device_connect", "repeat", "miniplayer", "fullscreen"),
            promoted,
        )
        assertEquals(actions, promoteAdjacentControlActions(actions, 5, 4, 5))
        assertEquals(actions, promoteAdjacentControlActions(actions, 0, 4, 5))
    }

    @Test
    fun compactBottomPlayerFitsVolumeAndDeviceConnectWithoutDisplacingRepeat() {
        val actions = listOf("queue", "lyrics", "shuffle", "repeat", "volume", "device_connect", "miniplayer", "fullscreen")
        val itemWidth = 44f
        val overflowWidth = 36f
        val spacing = 2f

        // 5 items * 44 + 5 * 2 + 36 = 266 fits [queue..volume] beside overflow; volume is index 4 (trailing corner).
        assertEquals(4, playerControlLastVisibleIndex(266f, actions.size, itemWidth, spacing, overflowWidth))
        // 6 items * 44 + 6 * 2 + 36 = 312 fits [queue..device_connect] beside overflow without moving repeat.
        val lastConnected = playerControlLastVisibleIndex(312f, actions.size, itemWidth, spacing, overflowWidth)
        assertEquals(5, lastConnected)
        // At 125% DPI when overflow compresses by one gap (390 = 6 * 55 + 6 * 3 + 42), all 6 items still fit.
        assertEquals(5, playerControlLastVisibleIndex(390f, actions.size, 55f, 3f, 45f))
        assertEquals(
            actions,
            promoteAdjacentControlActions(
                actions = actions,
                lastVisibleIndex = lastConnected,
                primaryIndex = actions.indexOf("volume"),
                secondaryIndex = actions.indexOf("device_connect"),
            ),
        )
    }

    @Test
    fun variableItemWidthsUseMeasuredButtonSizesForOverflowCutoff() {
        val widths = listOf(44f, 44f, 44f, 76f, 44f, 44f)
        // First 4 items = 44+44+44+76 = 208; gaps before item 3 = 6; overflow = 36 -> 250.
        assertEquals(3, playerControlLastVisibleIndex(250f, widths, 2f, 36f))
        assertEquals(2, playerControlLastVisibleIndex(249f, widths, 2f, 36f))
    }

    private fun lastVisible(width: Float): Int = playerControlLastVisibleIndex(width, 4, 48f, 2f, 48f)
}
