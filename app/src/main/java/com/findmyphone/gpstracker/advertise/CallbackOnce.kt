package com.findmyphone.gpstracker.advertise

import java.util.concurrent.atomic.AtomicBoolean

internal class CallbackOnce(private val callback: () -> Unit) {
    private val hasInvoked = AtomicBoolean(false)

    fun invoke() {
        if (hasInvoked.compareAndSet(false, true)) {
            callback()
        }
    }
}
