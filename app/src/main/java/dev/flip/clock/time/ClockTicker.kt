package dev.flip.clock.time

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Wall-clock-aligned tick flow (spec §6.9). Emits immediately, then on every boundary of
 * [intervalMs]. Never polls faster than 1 Hz. [clock] is injectable for tests.
 */
fun tickerFlow(
    intervalMs: Long = 1000,
    clock: () -> Long = System::currentTimeMillis,
    sleep: suspend (Long) -> Unit = { delay(it) }
): Flow<Long> = flow {
    while (currentCoroutineContext().isActive) {
        val now = clock()
        emit(now)
        val next = ((now / intervalMs) + 1) * intervalMs
        // +2 ms guard so we never wake a hair *before* the boundary and re-emit the old second.
        sleep((next - clock() + 2).coerceAtLeast(1L))
    }
}
