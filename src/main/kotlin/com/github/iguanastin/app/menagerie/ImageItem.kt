package com.github.iguanastin.app.menagerie

import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.image.Image
import java.io.File

class ImageItem(id: Int, added: Long, menagerie: Menagerie, md5: String, file: File, noSimilar: Boolean = false, histogram: Histogram? = null): FileItem(id, added, menagerie, md5, file) {

    val noSimilarProperty: BooleanProperty = SimpleBooleanProperty(noSimilar)
    var noSimilar: Boolean
        get() = noSimilarProperty.get()
        set(value) = noSimilarProperty.set(value)

    val histogramProperty: ObjectProperty<Histogram?> = SimpleObjectProperty(histogram)
    var histogram: Histogram?
        get() = histogramProperty.get()
        set(value) = histogramProperty.set(value)

    init {
        noSimilarProperty.addListener { _, old, new ->
            val change = ImageItemChange(this, noSimilar = Change(old, new))
            changeListeners.forEach { listener -> listener(change) }
        }
        histogramProperty.addListener { _, old, new ->
            val change = ImageItemChange(this, histogram = Change(old, new))
            changeListeners.forEach { listener -> listener(change) }
        }
    }

    companion object {
        val fileExtensions = listOf("png", "jpg", "jpeg", "gif", "bmp")

        fun isImage(file: File): Boolean {
            return fileExtensions.contains(file.extension.toLowerCase())
        }
    }

    override fun loadThumbnail(): Image {
        return Image(file.toURI().toString(), thumbnailSize, thumbnailSize, true, true, true)
    }

    override fun similarityTo(other: Item): Double {
        if (other !is ImageItem || histogram == null || other.histogram == null) return super.similarityTo(other)

        return histogram!!.similarityTo(other.histogram!!)
    }

}