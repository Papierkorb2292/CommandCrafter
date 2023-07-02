package net.papierkorb2292.command_crafter.editor.console

import com.mojang.logging.LogQueues
import com.mojang.logging.plugins.QueueLogAppender
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.helper.CallbackLinkedBlockingQueue
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.reflect.full.staticProperties
import kotlin.reflect.jvm.isAccessible

@Suppress("unused")
object PreLaunchLogListener : PreLaunchEntrypoint {

    private const val EDITOR_LOG_QUEUE = "CommandCrafter"
    private const val VANILLA_SERVER_QUEUE = "ServerGuiConsole"

    private var logQueue: CallbackLinkedBlockingQueue<String>? = null

    override fun onPreLaunch() {
        val logQueuesProperties = LogQueues::class.staticProperties
        logQueuesProperties.stream().filter { it.name == "QUEUE_LOCK" }.findFirst().ifPresentOrElse({ queueLock ->
            logQueuesProperties.stream().filter { it.name == "QUEUES" }.findFirst().ifPresentOrElse({ queues ->
                queueLock.isAccessible = true
                queues.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                try {
                    startSavingLog(
                        queueLock.getter.call() as ReentrantReadWriteLock,
                        queues.getter.call() as MutableMap<String, BlockingQueue<String>>
                    )
                } catch (e: Exception) {
                    CommandCrafter.LOGGER.error("Unable to get logged messages for the editor", e)
                }
            }, {
                CommandCrafter.LOGGER.error("Unable to get logged messages for the editor: QUEUES field not found")
            })
        }, {
            CommandCrafter.LOGGER.error("Unable to get logged messages for the editor: QUEUE_LOCK field not found")
        })
    }

    private fun startSavingLog(queueLock: ReentrantReadWriteLock, queues: MutableMap<String, BlockingQueue<String>>) {
        queueLock.readLock().lock()
        val logQueue = CallbackLinkedBlockingQueue<String>()
        this.logQueue = logQueue
        queues[EDITOR_LOG_QUEUE] = logQueue
        queueLock.readLock().unlock()

        val ctx: LoggerContext = LogManager.getContext(false) as LoggerContext
        val config: Configuration = ctx.configuration
        val logger = config.loggers[""]!!
        val serverQueueAppender = logger.appenders[VANILLA_SERVER_QUEUE]!!
        val layout = serverQueueAppender.layout

        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val appender: Appender = QueueLogAppender.createAppender(EDITOR_LOG_QUEUE, "false", layout, null, null)
        appender.start()
        config.addAppender(appender)
        logger.addAppender(appender, Level.INFO, null)
        ctx.updateLoggers()

        // There's technically a slight chance of messages being logged between the logger update
        // and the following iteration finishing, causing those messages to be logged twice.
        queues[VANILLA_SERVER_QUEUE]?.forEach { logQueue.offer(it) }
    }

    fun addLogListener(logListener: CallbackLinkedBlockingQueue.Callback<String>) {
        logQueue?.addCallback(logListener)
    }
}