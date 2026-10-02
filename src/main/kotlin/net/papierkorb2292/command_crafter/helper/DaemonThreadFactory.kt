package net.papierkorb2292.command_crafter.helper

import java.util.concurrent.ThreadFactory

class DaemonThreadFactory(private val threadName: String) : ThreadFactory {
    private var threadNumber = 1
    @Synchronized // Synchronized to access threadNumber
    override fun newThread(r: Runnable): Thread {
        val thread = Thread(r, "$threadName-${threadNumber++}")
        thread.isDaemon = true
        return thread
    }
}