package com.hereliesaz.ideaz.git

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitIndexLockTest {
    @Test
    fun freshLockIsNotStale() {
        assertFalse(GitManager.isStaleLock(lastModifiedMs = 1_000_000L, nowMs = 1_005_000L))
    }

    @Test
    fun oldLockIsStale() {
        assertTrue(GitManager.isStaleLock(1_000_000L, 1_000_000L + GitManager.STALE_LOCK_MS))
    }

    @Test
    fun unknownModifiedTimeIsNeverStale() {
        assertFalse(GitManager.isStaleLock(0L, Long.MAX_VALUE))
    }
}
