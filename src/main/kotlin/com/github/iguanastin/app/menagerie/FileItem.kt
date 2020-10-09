package com.github.iguanastin.app.menagerie

import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import java.io.File
import java.io.IOException

open class FileItem(id: Int, added: Long, menagerie: Menagerie, md5: String, file: File): Item(id, added, menagerie) {

    val md5Property: StringProperty = SimpleStringProperty(md5)
    var md5: String
        get() = md5Property.get()
        set(value) = md5Property.set(value)

    val fileProperty: ObjectProperty<File> = SimpleObjectProperty(file)
    var file: File
        get() = fileProperty.get()
        set(value) = fileProperty.set(value)

    companion object {
        fun fileHash(file: File): String {
            try {
                return HexBin.encode(MD5Hasher.hash(file))
            } catch (e: IOException) {
                e.printStackTrace()
                TODO("Better error handling")
            }
        }
    }


    override fun similarityTo(other: Item): Double {
        if (other !is FileItem) return super.similarityTo(other)

        return if (md5 == other.md5 || file == other.file) {
            1.0
        } else {
            0.0
        }
    }

}