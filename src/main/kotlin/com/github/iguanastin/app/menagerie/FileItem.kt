package com.github.iguanastin.app.menagerie

import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin
import java.io.File
import java.io.IOException

open class FileItem(id: Int, added: Long, var md5: String, var file: File): Item(id, added) {

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