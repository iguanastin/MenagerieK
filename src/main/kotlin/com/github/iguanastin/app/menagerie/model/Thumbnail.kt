package com.github.iguanastin.app.menagerie.model

import javafx.scene.image.Image
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


class Thumbnail(val item: Item) {

    companion object {
        private val normalQueue = LinkedBlockingQueue<Thumbnail>()
        private val videoQueue = LinkedBlockingQueue<Thumbnail>()

        init {
            thread(start = true, name = "Image thumbnailer", isDaemon = true) {
                while (true) {
                    val thumb = normalQueue.take()
                    if (!thumb.isWanted()) continue

                    thumb.load()
                }
            }
            thread(start = true, name = "Video thumbnailer", isDaemon = true) {
                while (true) {
                    val thumb = videoQueue.take()
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
            val queue = if (item is VideoItem) videoQueue else normalQueue
            if (this !in queue) queue.put(this)
        }
    }

    fun unWant(you: Any) {
        synchronized(wanting) { wanting.remove(you) }
    }

    fun isWanted(): Boolean {
        synchronized(wanting) { return wanting.isNotEmpty() }
    }

}
