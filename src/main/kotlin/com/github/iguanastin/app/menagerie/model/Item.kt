package com.github.iguanastin.app.menagerie.model

import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.scene.image.Image
import tornadofx.*
import java.lang.ref.SoftReference

open class Item(val id: Int, val added: Long, val menagerie: Menagerie) {

    companion object {
        private const val defaultThumbPath: String = "/imgs/default-thumb.png"
        const val thumbnailSize: Double = 150.0
        val defaultThumbnail: Image by lazy { Image(Item::class.java.getResource(defaultThumbPath).toExternalForm(), thumbnailSize, thumbnailSize, true, true, true) }
    }

    private val _tags: ObservableList<Tag> = FXCollections.observableArrayList()
    val tags: ObservableList<Tag> = _tags.asUnmodifiable()

    val changeListeners: MutableSet<(ItemChangeBase) -> Unit> = mutableSetOf()

    @Volatile
    var invalidated: Boolean = false
        private set

    private var thumbnailCache: SoftReference<Thumbnail> = SoftReference(null)

    init {
        tags.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { tag -> tag.frequency-- }
                change.addedSubList.forEach { tag -> tag.frequency++ }

                val itemChange = ItemChange(this, if (change.wasAdded()) {
                    change.addedSubList
                } else {
                    null
                }, if (change.wasRemoved()) {
                    change.removed
                } else {
                    null
                })
                changeListeners.forEach { listener ->
                    listener(itemChange)
                }
            }
        })
    }


    fun getThumbnail(): Thumbnail {
        var img = thumbnailCache.get()
        if (img == null) {
            img = Thumbnail(this)
            thumbnailCache = SoftReference(img)
        }
        return img
    }

    fun invalidateThumbnail() {
        thumbnailCache.clear()
    }

    open fun loadThumbnail(): Image {
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

    fun hasTag(tag: Tag) = tag in _tags

    fun addTag(tag: Tag): Boolean {
        return if (!invalidated && !hasTag(tag)) {
            _tags.add(tag)
            true
        } else {
            false
        }
    }

    fun removeTag(tag: Tag): Boolean {
        return !invalidated && _tags.remove(tag)
    }

    open fun invalidate() {
        _tags.clear()
        changeListeners.clear()
        invalidated = true
    }

    override fun toString(): String {
        return id.toString()
    }

    open fun replace(with: Item, replaceTags: Boolean): Boolean {
        if (invalidated || with == this) return false

        if (replaceTags) {
            _tags.clear()
            _tags.addAll(with.tags)
        }

        with.menagerie.removeItem(with)

        invalidateThumbnail()

        return true
    }

}