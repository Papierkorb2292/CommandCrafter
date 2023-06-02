package net.papierkorb2292.command_crafter.helper

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class CallbackExecutorService(private val delegate: ExecutorService, private val callback: () -> Unit): ExecutorService {
    override fun execute(command: Runnable) {
        delegate.execute(wrapRunnable(command))
    }
    override fun shutdown() {
        delegate.shutdown()
    }
    override fun shutdownNow(): List<Runnable> = delegate.shutdownNow()
    override fun isShutdown(): Boolean = delegate.isShutdown
    override fun isTerminated(): Boolean = delegate.isTerminated
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = delegate.awaitTermination(timeout, unit)
    override fun <T> submit(task: Callable<T>): Future<T> = delegate.submit(wrapCallable(task))
    override fun <T> submit(task: Runnable, result: T): Future<T> = delegate.submit(wrapRunnable(task), result)
    override fun submit(task: Runnable): Future<*> = delegate.submit(wrapRunnable(task))
    override fun <T> invokeAll(tasks: Collection<Callable<T>>): MutableList<Future<T>> = delegate.invokeAll(wrapCallables(tasks))
    override fun <T> invokeAll(
        tasks: Collection<Callable<T>>,
        timeout: Long,
        unit: TimeUnit,
    ): MutableList<Future<T>> = delegate.invokeAll(wrapCallables(tasks), timeout, unit)
    override fun <T> invokeAny(tasks: Collection<Callable<T>>): T = delegate.invokeAny(wrapCallables(tasks))
    override fun <T> invokeAny(tasks: Collection<Callable<T>>, timeout: Long, unit: TimeUnit): T = delegate.invokeAny(wrapCallables(tasks), timeout, unit)

    private fun wrapRunnable(task: Runnable): Runnable = Runnable {
        try {
            task.run()
        } finally {
            callback()
        }
    }

    private fun <T> wrapCallable(task: Callable<T>): Callable<T> = Callable {
        try {
            return@Callable task.call()
        } finally {
            callback()
        }
    }

    private fun <T> wrapCallables(tasks: Collection<Callable<T>>): Collection<Callable<T>> {
        return tasks.stream().map { Callable<T> {
            try {
                return@Callable it.call()
            } finally {
                callback()
            }
        }}.toList()
    }
}