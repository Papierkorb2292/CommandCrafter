package net.papierkorb2292.command_crafter.helper

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadFactory

class TrackingThreadFactory(private val delegate: ThreadFactory) : ThreadFactory {
    private val threads = ConcurrentHashMap.newKeySet<Thread>()

    override fun newThread(runnable: Runnable): Thread {
        val thread = delegate.newThread(runnable)
        threads.add(thread)
        return thread
    }

    fun assertCurrentThread() {
        check(Thread.currentThread() in threads) {
            "This method must be called from the workspace processing executor"
        }
    }
}
