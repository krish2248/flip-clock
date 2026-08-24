package dev.flip.clock.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/** Wall-clock-aligned tick flow (spec §6.9). Never polls faster than 1 Hz. */
fun tickerFlow(intervalMs: Long = 1000): Flow<Long> = flow {
    while (currentCoroutineContext().isActive) {
        val now = System.currentTimeMillis()
        emit(now)
        val next = ((now / intervalMs) + 1) * intervalMs
        delay((next - System.currentTimeMillis()).coerceAtLeast(1L))
    }
}
