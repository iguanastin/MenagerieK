package com.github.iguanastin.app.menagerie.model

import com.sun.jna.platform.FileUtils
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import java.io.File
import java.util.*

open class FileItem(id: Int, added: Long, menagerie: Menagerie, md5: String, file: File) : Item(id, added, menagerie) {

    val md5Property: StringProperty = SimpleStringProperty(md5)
    var md5: String
        get() = md5Property.get()
        set(value) = md5Property.set(value)

    val fileProperty: ObjectProperty<File> = SimpleObjectProperty(file)
    var file: File
        get() = fileProperty.get()
        set(value) = fileProperty.set(value)

    val elementOfProperty: ObjectProperty<GroupItem?> = SimpleObjectProperty(null)
    var elementOf: GroupItem?
        get() = elementOfProperty.get()
        set(value) = elementOfProperty.set(value)

    init {
        md5Property.addListener { _, old, new ->
            val change = FileItemChange(this, md5 = Change(old, new))
            changeListeners.forEach { listener -> listener(change) }
        }
        fileProperty.addListener { _, old, new ->
            val change = FileItemChange(this, file = Change(old, new))
            changeListeners.forEach { listener -> listener(change) }
            invalidateThumbnail()
        }
        elementOfProperty.addListener { _, old, new ->
            val change = FileItemChange(this, elementOf = Change(old, new))
            changeListeners.forEach { listener -> listener(change) }
        }
    }

    companion object {
        fun fileHash(file: File): String {
            return HexFormat.of().formatHex(MD5Hasher.hash(file))
        }

        fun bytesHash(bytes: ByteArray): String {
            return HexFormat.of().formatHex(MD5Hasher.hash(bytes))
        }
    }


    override fun similarityTo(other: Item): Double {
        val superSimilarity = super.similarityTo(other)
        if (superSimilarity == 1.0 || other !is FileItem) return superSimilarity

        return if (md5 == other.md5 || file == other.file) {
            1.0
        } else {
            0.0
        }
    }

    override fun invalidate() {
        elementOf?.removeItem(this)

        super.invalidate()
    }

    override fun replace(with: Item, replaceTags: Boolean): Boolean {
        if (with !is FileItem) return false
        if (!super.replace(with, replaceTags)) return false

        val oldFile = file

        md5 = with.md5
        file = with.file

        val fu = FileUtils.getInstance()
        if (fu.hasTrash()) {
            fu.moveToTrash(oldFile)
        } else {
            oldFile.delete()
        }

        return true
    }

}