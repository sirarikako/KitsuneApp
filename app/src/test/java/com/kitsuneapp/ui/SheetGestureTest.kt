package com.kitsuneapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetGestureTest {
    @Test
    fun effectiveDismissDistanceUsesDefaultForNormalSheetsAndAdaptsOnShortSheets() {
        assertEquals(300f, effectiveSheetDismissDistance(defaultDistancePx = 300f, sheetHeightPx = 900, minDistancePx = 168f), 0.01f)
        assertEquals(240f, effectiveSheetDismissDistance(defaultDistancePx = 300f, sheetHeightPx = 480, minDistancePx = 168f), 0.01f)
        assertEquals(168f, effectiveSheetDismissDistance(defaultDistancePx = 300f, sheetHeightPx = 240, minDistancePx = 168f), 0.01f)
        assertEquals(300f, effectiveSheetDismissDistance(defaultDistancePx = 300f, sheetHeightPx = 0, minDistancePx = 168f), 0.01f)
    }

    @Test
    fun dismissDecisionRequiresDistanceOrClearDownwardFling() {
        val distance = 100f
        val flingVelocity = 800f
        val minFlingDistance = 24f

        assertFalse(shouldDismissSheet(40f, 150f, distance, flingVelocity, minFlingDistance))
        assertFalse(shouldDismissSheet(99f, 300f, distance, flingVelocity, minFlingDistance))
        assertTrue(shouldDismissSheet(100f, 50f, distance, flingVelocity, minFlingDistance))
        assertTrue(shouldDismissSheet(140f, 0f, distance, flingVelocity, minFlingDistance))

        assertTrue(shouldDismissSheet(48f, 950f, distance, flingVelocity, minFlingDistance))
        assertFalse(shouldDismissSheet(12f, 1500f, distance, flingVelocity, minFlingDistance))

        assertFalse(shouldDismissSheet(120f, -900f, distance, flingVelocity, minFlingDistance))
        assertFalse(shouldDismissSheet(0f, -400f, distance, flingVelocity, minFlingDistance))
    }

    @Test
    fun fastPullFollowedByPauseBeforeReleaseDoesNotDismissBelowDistanceThreshold() {
        val distance = 100f
        val flingVelocity = 800f
        val minFlingDistance = 24f

        // Genuine fast flick (minimal gap between last movement and release) still dismisses.
        assertTrue(shouldDismissSheet(50f, 1400f, distance, flingVelocity, minFlingDistance, pauseBeforeReleaseMs = 20L))
        // Pausing before release clears stale fling velocity, so pulls below distance threshold snap back.
        assertFalse(shouldDismissSheet(50f, 1400f, distance, flingVelocity, minFlingDistance, pauseBeforeReleaseMs = SheetDismissMaxFlingPauseMs))
        assertFalse(shouldDismissSheet(80f, 1800f, distance, flingVelocity, minFlingDistance, pauseBeforeReleaseMs = 250L))
        // Pulling past the distance threshold still dismisses even after a pause.
        assertTrue(shouldDismissSheet(120f, 1400f, distance, flingVelocity, minFlingDistance, pauseBeforeReleaseMs = 300L))
    }
}
