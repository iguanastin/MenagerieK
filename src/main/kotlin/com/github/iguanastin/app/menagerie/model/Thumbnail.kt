package com.github.iguanastin.app.menagerie.model

import javafx.scene.image.Image
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


class Thumbnail(val item: Item) {

    companion object {
        private val imageQueue: BlockingQueue<Thumbnail> = LinkedBlockingQueue()

        init {
            thread(start = true, name = "Image thumbnailer", isDaemon = true) {
                while (true) {
                    val thumb = imageQueue.take()
                    if (!thumb.isWanted()) continue

                    thumb.load()
                }
            }
        }
    }

    private val imageLock: Any = Any()
    var image: Image? = null
        get() {
            synchronized(imageLock) { return field }
        }
        private set(value) {
            synchronized(imageLock) { field = value }
        }
    val isLoaded: Boolean
        get() {
            synchronized(imageLock) { return image != null }
        }

    private val wanting: MutableMap<Any, (Thumbnail) -> Unit> = mutableMapOf()


    private fun load() {
        image = item.loadThumbnail()
        synchronized(wanting) {
            wanting.forEach { (_, onReady) ->
                onReady(this)
            }
            wanting.clear()
        }
    }

    fun want(you: Any, onReady: (Thumbnail) -> Unit = {}) {
        if (isLoaded) {
            onReady(this)
        } else {
            synchronized(wanting) { wanting[you] = onReady }
            if (this !in imageQueue) imageQueue.put(this)
        }
    }

    fun unWant(you: Any) {
        synchronized(wanting) { wanting.remove(you) }
    }

    fun isWanted(): Boolean {
        synchronized(wanting) { return wanting.isNotEmpty() }
    }

}
