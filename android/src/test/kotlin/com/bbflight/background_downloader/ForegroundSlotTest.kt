package com.bbflight.background_downloader

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A process has one WorkManager foreground service. When several concurrent tasks each try to
 * promote themselves into it, the promotions fail and WorkManager cancels those workers -
 * observed as every task dying seconds after starting, retrying, and the queue thrashing without
 * finishing anything. [ForegroundSlot] admits one holder so the rest run as ordinary workers,
 * protected by the process staying foreground for the one that holds it.
 */
class ForegroundSlotTest {

    @After
    fun tearDown() = ForegroundSlot.releaseAll()

    @Test
    fun firstClaimWins() {
        assertTrue(ForegroundSlot.claim("task-1"))
    }

    @Test
    fun secondTaskIsRefusedWhileTheFirstHolds() {
        ForegroundSlot.claim("task-1")

        assertFalse(ForegroundSlot.claim("task-2"))
        assertFalse(ForegroundSlot.claim("task-3"))
    }

    @Test
    fun theHolderMayReclaimItsOwnSlot() {
        ForegroundSlot.claim("task-1")

        // determineRunInForeground runs again on resume and on each content-length update.
        assertTrue(ForegroundSlot.claim("task-1"))
    }

    @Test
    fun releasingHandsTheSlotToTheNextTask() {
        ForegroundSlot.claim("task-1")
        ForegroundSlot.release("task-1")

        assertTrue(ForegroundSlot.claim("task-2"))
    }

    @Test
    fun aNonHolderCannotReleaseTheSlot() {
        ForegroundSlot.claim("task-1")

        // A task that never held the slot finishing must not evict the one that does.
        ForegroundSlot.release("task-2")

        assertFalse(ForegroundSlot.claim("task-3"))
        assertTrue(ForegroundSlot.claim("task-1"))
    }
}
