package net.papierkorb2292.command_crafter.helper

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class SizeLimitedCallbackLinkedBlockingQueue<T : Any>(val sizeLimit: Int = 256) : LinkedBlockingQueue<T>() {
    private val callbacks: MutableList<Callback<T>> = mutableListOf()

    fun addCallback(callback: Callback<T>) {
        for(e in this) {
            callback.onElementAdded(e)
        }
        callbacks.add(callback)
    }

    override fun put(e: T) {
        super.put(e)
        alertCallbacks(e)
        reduceToSizeLimit()
    }
    
    override fun offer(e: T): Boolean {
        val successful = super.offer(e)
        if(successful) {
            alertCallbacks(e)
            reduceToSizeLimit()
        }
        return successful
    }

    override fun offer(e: T, timeout: Long, unit: TimeUnit): Boolean {
        val successful = super.offer(e, timeout, unit)
        if(successful) {
            alertCallbacks(e)
            reduceToSizeLimit()
        }
        return successful
    }

    private fun alertCallbacks(e: T) {
        val iterator = callbacks.iterator()
        while(iterator.hasNext()) {
            val callback = iterator.next()
            if(callback.shouldRemoveCallback()) {
                iterator.remove()
                continue
            }
            callback.onElementAdded(e)
        }
    }

    private fun reduceToSizeLimit() {
        while(size > sizeLimit) {
            poll()
        }
    }

    interface Callback<T> {
        fun onElementAdded(e: T)
        fun shouldRemoveCallback(): Boolean
    }
}