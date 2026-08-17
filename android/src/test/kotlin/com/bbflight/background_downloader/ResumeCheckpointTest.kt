package com.bbflight.background_downloader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Resume data is written when a task pauses, times out or is re-enqueued. A process kill gets none
 * of those, so the partial file is orphaned and the next run starts at byte 0 and re-downloads
 * everything - observed as a download dropping from 12% back to 2% after the app was force-stopped.
 *
 * [ResumeCheckpoint.startByteFor] decides what a cold-started worker may trust of a checkpoint
 * written mid-transfer.
 */
class ResumeCheckpointTest {

    @Test
    fun resumesFromTheCheckpointWhenTheFileBacksIt() {
        assertEquals(1_000L, ResumeCheckpoint.startByteFor(checkpointBytes = 1_000, tempFileLength = 1_000))
    }

    @Test
    fun resumesFromTheCheckpointWhenTheFileRanAhead() {
        // Bytes written after the last checkpoint are discarded rather than assumed: the file is
        // truncated back to the checkpoint, which costs at most one checkpoint interval.
        assertEquals(1_000L, ResumeCheckpoint.startByteFor(checkpointBytes = 1_000, tempFileLength = 1_600))
    }

    @Test
    fun restartsWhenTheFileIsShorterThanTheCheckpoint() {
        // The checkpoint outran the bytes that reached disk, so it cannot be trusted.
        assertEquals(0L, ResumeCheckpoint.startByteFor(checkpointBytes = 1_000, tempFileLength = 999))
    }

    @Test
    fun restartsWhenThereIsNoCheckpoint() {
        assertEquals(0L, ResumeCheckpoint.startByteFor(checkpointBytes = 0, tempFileLength = 5_000))
        assertEquals(0L, ResumeCheckpoint.startByteFor(checkpointBytes = -1, tempFileLength = 5_000))
    }

    @Test
    fun restartsWhenTheFileIsMissing() {
        assertEquals(0L, ResumeCheckpoint.startByteFor(checkpointBytes = 1_000, tempFileLength = 0))
    }

    @Test
    fun checkpointIntervalIsDueOnlyAfterAFullInterval() {
        assertEquals(false, ResumeCheckpoint.isDue(bytesSinceLastCheckpoint = 0))
        assertEquals(false, ResumeCheckpoint.isDue(bytesSinceLastCheckpoint = ResumeCheckpoint.intervalBytes - 1))
        assertEquals(true, ResumeCheckpoint.isDue(bytesSinceLastCheckpoint = ResumeCheckpoint.intervalBytes))
    }
}
