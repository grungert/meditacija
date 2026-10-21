package com.grungert.nebo.ml

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps something heavy (a loaded speech model) for [keepMs] after its last use, then lets it go with [close].
 *
 * The release waits for [lock], and a release that came due while the model was being loaded or used would get the
 * lock right after (the lock is fair) and free the model just loaded. So it checks, under the lock, when the model was
 * last used, and leaves it alone unless it has really sat idle for [keepMs]. [touch] is called under [lock].
 */
internal class KeepAlive(
    private val scope: CoroutineScope, private val lock: Mutex, private val keepMs: Long,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }, private val close: () -> Unit,
) {
    private var lastUse = Long.MIN_VALUE / 2   // under the lock
    private var release: Job? = null

    /** The model was just loaded or used. */
    fun touch() { lastUse = now() }

    /** Lets it go [keepMs] from now, unless it is used again before then. */
    @Synchronized
    fun later() {
        release?.cancel()
        release = scope.launch {
            delay(keepMs)
            lock.withLock { if (now() - lastUse >= keepMs) close() }
        }
    }
}
