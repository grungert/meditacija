package com.grungert.nebo.ml

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class KeepAliveTest {
    /**
     * The race behind the speech model loading twice: a release set at start-up comes due while the model is loading,
     * waits for the lock, and used to free the model the moment it was ready.
     */
    @Test
    fun aReleaseDueDuringLoadingLeavesTheFreshModel() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lock = Mutex()
        val closed = AtomicInteger()
        val keep = KeepAlive(scope, lock, keepMs = 200) { closed.incrementAndGet() }
        keep.later()                                   // set at start-up, due in 200 ms
        delay(120)
        val load = scope.launch {
            lock.withLock { delay(250); keep.touch() } // loading spans the moment the release comes due
            keep.later()
        }
        load.join()
        delay(60)
        assertEquals("the model just loaded is still there", 0, closed.get())
        delay(300)
        assertEquals("a minute (here 200 ms) without use, it goes", 1, closed.get())
        scope.cancel()
    }

    @Test
    fun usedAgainItStays() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lock = Mutex()
        val closed = AtomicInteger()
        val keep = KeepAlive(scope, lock, keepMs = 150) { closed.incrementAndGet() }
        lock.withLock { keep.touch() }; keep.later()
        delay(100)
        lock.withLock { keep.touch() }; keep.later()
        delay(100)
        assertEquals(0, closed.get())
        delay(150)
        assertEquals(1, closed.get())
        scope.cancel()
    }
}
