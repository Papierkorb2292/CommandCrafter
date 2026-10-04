package net.papierkorb2292.command_crafter.editor.workspace

import net.papierkorb2292.command_crafter.CommandCrafter
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A thread pool for running tasks in the background. Will use a maximum of [maxWorkers] threads, and will queue tasks if all threads are busy.
 * [maxSlowWorkers] determines how many threads are allowed to work on slow tasks, while the rest can quickly go through faster tasks.
 * So if a task runs for too long and there's already [maxSlowWorkers] threads working on slow tasks,
 * the task will be stopped and queued to run on a slow worker thread later.
 */
class WorkspaceAnalyzingScheduler(
    private val threadsName: String,
    private val maxWorkers: Int,
    private val maxSlowWorkers: Int,
) {
    companion object {
        val DROP_TO_SLOW_THRESHOLD_NS = TimeUnit.SECONDS.toNanos(5)
        val PROMOTE_TO_FAST_THRESHOLD_NS = TimeUnit.SECONDS.toNanos(1)
    }

    private val taskQueue: BlockingQueue<Task> = LinkedBlockingQueue()
    private val slowTaskQueue: BlockingQueue<Task> = LinkedBlockingQueue()
    // Only use with 'this' as lock
    private var currentWorkerCount: Int = 0
    // Only use with 'this' as lock
    private var currentSlowWorkerCount: Int = 0
    // Only use with 'this' as lock
    private var hasIdlingWorker: Boolean = false

    @Synchronized
    fun schedule(task: Task, isKnownSlow: Boolean = false) {
        if(tryStartWorker(task)) return

        if(isKnownSlow)
            slowTaskQueue.put(task)
        else
            taskQueue.put(task)
    }

    @Synchronized
    private fun tryStartWorker(task: Task): Boolean {
        if(currentWorkerCount >= maxWorkers)
            return false
        if(hasIdlingWorker)
            return false // No need to start a new thread if there's already a worker waiting for new tasks
        currentWorkerCount++
        val thread = Thread({
            Worker(task).start()
        }, "$threadsName-$currentWorkerCount")
        thread.isDaemon = true
        thread.start()
        return true
    }

    inner class Worker(initialTask: Task) {
        var currentTask = initialTask
        var isSlow = false

        fun pollNext(timeoutSeconds: Long): Boolean {
            if(!isSlow && slowTaskQueue.isNotEmpty()) {
                synchronized(this@WorkspaceAnalyzingScheduler) {
                    if(currentSlowWorkerCount < maxSlowWorkers) {
                        // Switch to slow if there are slow tasks but the slow worker count is below the limit
                        val polled = slowTaskQueue.poll()
                        if(polled != null) {
                            currentTask = polled
                            isSlow = true
                            currentSlowWorkerCount++
                            return true
                        }
                    }
                }
            }
            var polled =
                if(isSlow) slowTaskQueue.poll(timeoutSeconds, TimeUnit.SECONDS)
                else taskQueue.poll(timeoutSeconds, TimeUnit.SECONDS)

            if(polled == null && isSlow) {
                // Go back to fast if there are no more slow tasks
                synchronized(this@WorkspaceAnalyzingScheduler) {
                    polled = slowTaskQueue.poll()
                    if(polled == null) {
                        // Still no new slow tasks
                        currentSlowWorkerCount--
                        isSlow = false
                    }
                }
                if(polled == null)
                    polled = taskQueue.poll(timeoutSeconds, TimeUnit.SECONDS)
            }
            if(polled != null) {
                currentTask = polled
                return true
            }
            return false
        }

        fun start() {
            while(true) {
                do {
                    try {
                        val timer = TaskTimer(this)
                        val completed = currentTask.run(timer)
                        if(!completed) {
                            // Enqueue task as slow instead
                            slowTaskQueue.put(currentTask)
                        }
                    } catch (e: Exception) {
                        CommandCrafter.LOGGER.error("Workspace analyzer task threw error", e)
                    }
                } while(pollNext(1))

                synchronized(this@WorkspaceAnalyzingScheduler) {
                    // Try to poll again, in case a new task was added during synchronized
                    if(pollNext(0)) {
                        continue
                    }
                    // Always keep at least one worker alive, so we don't have to create a new thread every time a file is saved
                    if(currentWorkerCount > 1) {
                        // No more tasks, exit the thread
                        currentWorkerCount--
                        if(isSlow) currentSlowWorkerCount--
                        return
                    }

                    hasIdlingWorker = true
                }

                while(!pollNext(1)) {
                    // Wait for new tasks
                }

                synchronized(this@WorkspaceAnalyzingScheduler) {
                    hasIdlingWorker = false
                }
            }
        }

        fun tryConvertToSlowWorkerForRunningTask(): Boolean {
            synchronized(this@WorkspaceAnalyzingScheduler) {
                if(currentSlowWorkerCount >= maxSlowWorkers)
                    return false
                currentSlowWorkerCount++
                isSlow = true
                return true
            }
        }
    }

    fun interface Task {
        /**
         * Runs the task. Returns true if the task was completed, false if it ended early due the being canceled by the [TaskTimer]
         */
        fun run(timer: TaskTimer): Boolean
    }

    class TaskTimer(private val worker: Worker) {
        private val startNS = System.nanoTime()

        fun shouldStop(): Boolean {
            if(worker.isSlow)
                return false
            if(System.nanoTime() - startNS < DROP_TO_SLOW_THRESHOLD_NS)
                return false
            // Don't need to cancel the task if the slow worker limit is not reached yet
            return !worker.tryConvertToSlowWorkerForRunningTask()
        }

        fun wasSlow(): Boolean {
            // If the task is known to be slow, the threshold is lower, so tasks can't jump between fast and slow
            val limit = if(worker.isSlow) PROMOTE_TO_FAST_THRESHOLD_NS else DROP_TO_SLOW_THRESHOLD_NS
            return System.nanoTime() - startNS >= limit
        }
    }
}