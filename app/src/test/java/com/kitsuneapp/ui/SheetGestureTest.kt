package com.kitsuneapp.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
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
    fun nestedScrollBlockerConsumesOnlyRemainingVerticalScrollAndFling() = runBlocking {
        val postScroll = BlockSheetNestedScroll.onPostScroll(
            consumed = Offset(10f, 20f),
            available = Offset(35f, 120f),
            source = NestedScrollSource.UserInput,
        )
        assertEquals(Offset(0f, 120f), postScroll)

        val postFling = BlockSheetNestedScroll.onPostFling(
            consumed = Velocity(50f, 100f),
            available = Velocity(250f, 900f),
        )
        assertEquals(Velocity(0f, 900f), postFling)
    }
}
