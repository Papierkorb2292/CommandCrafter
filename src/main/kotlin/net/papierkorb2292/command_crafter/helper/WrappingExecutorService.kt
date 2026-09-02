package net.papierkorb2292.command_crafter.helper

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class WrappingExecutorService(private val delegate: ExecutorService, private val wrapper: Wrapper): ExecutorService {
    companion object {
        fun withFinishedCallback(delegate: ExecutorService, callback: () -> Unit) = WrappingExecutorService(delegate, object : Wrapper {
            override fun <T> wrapTask(task: Callable<T>): Callable<T> {
                return Callable {
                    try {
                        task.call()
                    } finally {
                        callback()
                    }
                }
            }
        })

        fun withErrorCallback(delegate: ExecutorService, callback: (Exception) -> Unit) = WrappingExecutorService(delegate, object : Wrapper {
            override fun <T> wrapTask(task: Callable<T>): Callable<T> {
                return Callable {
                    try {
                        task.call()
                    } catch (e: Exception) {
                        callback(e)
                        throw e
                    }
                }
            }
        })
    }

    override fun execute(command: Runnable) {
        delegate.execute(this@WrappingExecutorService.wrapRunnable(command))
    }
    override fun shutdown() {
        delegate.shutdown()
    }
    override fun shutdownNow(): List<Runnable> = delegate.shutdownNow()
    override fun isShutdown(): Boolean = delegate.isShutdown
    override fun isTerminated(): Boolean = delegate.isTerminated
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = delegate.awaitTermination(timeout, unit)
    override fun <T> submit(task: Callable<T>): Future<T> = delegate.submit(wrapCallable(task))
    override fun <T> submit(task: Runnable, result: T): Future<T> = delegate.submit(this@WrappingExecutorService.wrapRunnable(task), result)
    override fun submit(task: Runnable): Future<*> = delegate.submit(this@WrappingExecutorService.wrapRunnable(task))
    override fun <T> invokeAll(tasks: Collection<Callable<T>>): MutableList<Future<T>> = delegate.invokeAll(wrapCallables(tasks))
    override fun <T> invokeAll(
        tasks: Collection<Callable<T>>,
        timeout: Long,
        unit: TimeUnit,
    ): MutableList<Future<T>> = delegate.invokeAll(wrapCallables(tasks), timeout, unit)
    override fun <T> invokeAny(tasks: Collection<Callable<T>>): T = delegate.invokeAny(wrapCallables(tasks))
    override fun <T> invokeAny(tasks: Collection<Callable<T>>, timeout: Long, unit: TimeUnit): T = delegate.invokeAny(wrapCallables(tasks), timeout, unit)

    private fun wrapRunnable(task: Runnable): Runnable =
        wrapper.wrapRunnable(task)

    private fun <T> wrapCallable(task: Callable<T>): Callable<T> =
        wrapper.wrapTask(task)

    private fun <T> wrapCallables(tasks: Collection<Callable<T>>): Collection<Callable<T>> =
        tasks.map(wrapper::wrapTask)

    interface Wrapper {
        fun <T> wrapTask(task: Callable<T>): Callable<T>
        fun wrapRunnable(task: Runnable): Runnable {
            val wrapped = wrapTask { task.run() }
            return Runnable { wrapped.call() }
        }
    }
}