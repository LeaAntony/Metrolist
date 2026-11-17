package com.metrolist.desktop.state.app

import com.metrolist.desktop.ui.components.buildLyricsPreviewLines
import com.metrolist.desktop.ui.components.findActiveLyricIndex
import com.metrolist.shared.model.LyricsEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopNowPlayingPanelTest {
    @Test
    fun dockedVisibilityTracksPreferenceTrackPresenceAndOtherPanels() {
        assertFalse(
            resolveDesktopNowPlayingVisibility(
                hasTrack = false,
                isOverlayLayout = false,
                showDockedPreference = true,
                showNarrowOverlay = true,
                showQueue = false,
                showLyrics = false,
                showDeviceConnect = false,
            ),
        )

        assertTrue(
            resolveDesktopNowPlayingVisibility(
                hasTrack = true,
                isOverlayLayout = false,
                showDockedPreference = true,
                showNarrowOverlay = false,
                showQueue = false,
                showLyrics = false,
                showDeviceConnect = false,
            ),
        )

        assertFalse(
            resolveDesktopNowPlayingVisibility(
                hasTrack = true,
                isOverlayLayout = false,
                showDockedPreference = true,
                showNarrowOverlay = false,
                showQueue = true,
                showLyrics = false,
                showDeviceConnect = false,
            ),
        )
        assertFalse(
            resolveDesktopNowPlayingVisibility(
                hasTrack = true,
                isOverlayLayout = false,
                showDockedPreference = true,
                showNarrowOverlay = false,
                showQueue = false,
                showLyrics = true,
                showDeviceConnect = false,
            ),
        )
        assertFalse(
            resolveDesktopNowPlayingVisibility(
                hasTrack = true,
                isOverlayLayout = false,
                showDockedPreference = true,
                showNarrowOverlay = false,
                showQueue = false,
                showLyrics = false,
                showDeviceConnect = true,
            ),
        )
    }

    @Test
    fun narrowOverlayNeverAutoCoversContentAndPreservesDockedPreference() {
        assertFalse(
            resolveDesktopNowPlayingVisibility(
                hasTrack = true,
                isOverlayLayout = true,
                showDockedPreference = true,
                showNarrowOverlay = false,
                showQueue = false,
                showLyrics = false,
                showDeviceConnect = false,
            ),
        )

        val opened =
            toggleDesktopNowPlayingState(
                isOverlayLayout = true,
                showDockedPreference = true,
                showNarrowOverlay = false,
                showQueue = true,
                showLyrics = false,
                showDeviceConnect = false,
            )
        assertTrue(opened.showDockedPreference)
        assertTrue(opened.showNarrowOverlay)
        assertFalse(opened.showQueue)
        assertFalse(opened.showLyrics)
        assertFalse(opened.showDeviceConnect)

        val closed =
            toggleDesktopNowPlayingState(
                isOverlayLayout = true,
                showDockedPreference = true,
                showNarrowOverlay = true,
                showQueue = false,
                showLyrics = false,
                showDeviceConnect = false,
            )
        assertTrue(closed.showDockedPreference)
        assertFalse(closed.showNarrowOverlay)
    }

    @Test
    fun togglingDockedNowPlayingReplacesBlockingPanelsInsteadOfHiding() {
        val revealed =
            toggleDesktopNowPlayingState(
                isOverlayLayout = false,
                showDockedPreference = true,
                showNarrowOverlay = false,
                showQueue = true,
                showLyrics = true,
                showDeviceConnect = false,
            )
        assertTrue(revealed.showDockedPreference)
        assertFalse(revealed.showQueue)
        assertFalse(revealed.showLyrics)
        assertFalse(revealed.showDeviceConnect)

        val collapsed =
            toggleDesktopNowPlayingState(
                isOverlayLayout = false,
                showDockedPreference = true,
                showNarrowOverlay = false,
                showQueue = false,
                showLyrics = false,
                showDeviceConnect = false,
            )
        assertFalse(collapsed.showDockedPreference)
    }

    @Test
    fun lyricsPreviewPicksActiveAndUpcomingLines() {
        val entries =
            listOf(
                LyricsEntry(time = 1_000L, text = "First line"),
                LyricsEntry(time = 5_000L, text = ""),
                LyricsEntry(time = 8_000L, text = "Second line"),
                LyricsEntry(time = 12_000L, text = "Third line"),
            )
        val activeIndex = findActiveLyricIndex(entries, positionMs = 9_000L)
        assertEquals(2, activeIndex)
        assertEquals(
            listOf("Second line", "Third line"),
            buildLyricsPreviewLines(entries = entries, rawLyrics = null, activeIndex = activeIndex),
        )
        assertEquals(
            listOf("Plain line one", "Plain line two", "Plain line three"),
            buildLyricsPreviewLines(
                entries = emptyList(),
                rawLyrics = "[00:01.00]Ignored tag line\nPlain line one\nPlain line two\nPlain line three\nPlain line four",
                activeIndex = -1,
            ),
        )
    }
}
