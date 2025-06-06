package com.github.iguanastin.app.menagerie.model

import javafx.beans.InvalidationListener
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.image.Image
import java.io.File
import java.lang.ref.SoftReference

class ImageItem(id: Int, added: Long, menagerie: Menagerie, md5: String, file: File, noSimilar: Boolean = false, histogram: Histogram? = null): FileItem(id, added, menagerie, md5, file) {

    val noSimilarProperty: BooleanProperty = SimpleBooleanProperty(noSimilar)
    var noSimilar: Boolean
        get() = noSimilarProperty.get()
        set(value) = noSimilarProperty.set(value)

    val histogramProperty: ObjectProperty<Histogram?> = SimpleObjectProperty(histogram)
    var histogram: Histogram?
        get() = histogramProperty.get()
        set(value) = histogramProperty.set(value)

    var cachedImage = SoftReference<Image>(null)

    init {
        noSimilarProperty.addListener { _, old, new ->
            val change = ImageItemChange(this, noSimilar = Change(old, new))
            changeListeners.forEach { listener -> listener(change) }
        }
        histogramProperty.addListener { _, old, new ->
            val change = ImageItemChange(this, histogram = Change(old, new))
            changeListeners.forEach { listener -> listener(change) }
        }

        fileProperty.addListener(InvalidationListener { cachedImage.clear() })
    }

    companion object {
        const val noSimilarMax: Double = 0.9
        val fileExtensions = listOf("png", "jpg", "jpeg", "gif", "bmp", "jfif")

        fun isImage(file: File): Boolean {
            return fileExtensions.contains(file.extension.lowercase())
        }
    }

    override fun invalidate() {
        super.invalidate()
        cachedImage.clear()
    }

    override fun loadThumbnail(): Image {
        return Image(file.toURI().toString(), thumbnailSize, thumbnailSize, true, true)
    }

    override fun getThumbnailExtension(): String {
        return file.extension
    }

    override fun similarityTo(other: Item): Double {
        val superSimilarity = super.similarityTo(other)
        if (superSimilarity == 1.0 || other !is ImageItem || histogram == null || other.histogram == null) return superSimilarity

        return histogram!!.similarityTo(other.histogram!!)
    }

    override fun replace(with: Item, replaceTags: Boolean): Boolean {
        if (with !is ImageItem) return false
        if (!super.replace(with, replaceTags)) return false

        histogram = with.histogram
        noSimilar = with.noSimilar

        return true
    }

    fun buildHistogram(image: Image): Histogram? {
        val histogram = Histogram.from(image)

        noSimilar = true
        if (histogram != null) {
            menagerie.items.forEach { i ->
                if (i is ImageItem && (i.histogram?.similarityTo(histogram) ?: 0.0) > noSimilarMax) {
                    noSimilar = false
                    i.noSimilar = false
                }
            }
        }

        this.histogram = histogram
        return histogram
    }

}