package com.github.iguanastin.app.menagerie

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.image.Image
import java.lang.ref.WeakReference

open class Item(val id: Int, val added: Long) {

    companion object {
        private const val defaultThumbPath: String = "/imgs/default-thumb.png"
        const val thumbnailWidth: Double = 150.0
        const val thumbnailHeight: Double = 150.0
        val defaultThumbnail: Image by lazy { Image(Item::class.java.getResource(defaultThumbPath).toExternalForm(), thumbnailWidth, thumbnailHeight, true, true, true) }
    }

    val tags: ObservableList<Tag> = FXCollections.observableArrayList()

    private var thumbnailCache: WeakReference<Image> = WeakReference(null)


    fun getThumbnail(): Image {
        var img = thumbnailCache.get()
        if (img == null) {
            img = loadThumbnail()
            thumbnailCache = WeakReference(img)
        }
        return img
    }

    protected open fun loadThumbnail(): Image {
        return defaultThumbnail
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Item) return false

        if (id != other.id) return false

        return true
    }

    open fun similarityTo(other: Item): Double {
        return if (id == other.id) {
            1.0
        } else {
            0.0
        }
    }

}