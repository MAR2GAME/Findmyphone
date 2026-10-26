package com.findmyphone.gpstracker.advertise

import java.util.concurrent.atomic.AtomicBoolean

internal class InterstitialRequestGate {
    private val inProgress = AtomicBoolean(false)

    fun tryAcquire(): Boolean = inProgress.compareAndSet(false, true)

    fun release() {
        inProgress.set(false)
    }
}
