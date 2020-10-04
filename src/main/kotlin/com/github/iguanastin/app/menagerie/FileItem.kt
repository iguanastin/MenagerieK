package com.github.iguanastin.app.menagerie

import java.io.File

open class FileItem(id: Int, added: Long, var md5: String, var file: File): Item(id, added) {


    override fun similarityTo(other: Item): Double {
        if (other !is FileItem) return super.similarityTo(other)

        return if (md5 == other.md5 || file == other.file) {
            1.0
        } else {
            0.0
        }
    }

}