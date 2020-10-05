package com.github.iguanastin.app.menagerie.import

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.Channels

class RemoteImportJob private constructor(val url: String, file: File): ImportJob(file) {

    companion object {
        fun intoFile(url: String, file: File): RemoteImportJob {
            return RemoteImportJob(url, file)
        }

        fun intoDirectory(url: String, downloadsDir: File, incrementIfExists: Boolean = true): RemoteImportJob {
            val filename = URL(url).path.substringAfterLast('/')
            var file: File = downloadsDir.resolve(filename)

            var i = 1
            while (file.exists()) {
                file = downloadsDir.resolve(filename.substringBeforeLast('.') + " ($i)." + filename.substringAfterLast('.'))

                if (!incrementIfExists && file.exists()) throw FileAlreadyExistsException(file)
                i++
            }

            return RemoteImportJob(url, file)
        }
    }


    override fun import() {
        download(url, file)

        super.import()
    }

    private fun download(url: String, into: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.addRequestProperty("User-Agent", "Mozilla/4.0")
        Channels.newChannel(conn.inputStream).use { rbs ->
            FileOutputStream(into).use { fos ->
                val size: Long = conn.contentLengthLong
                val chunkSize: Long = 4096
                var i: Long = 0
                while (i < size) {
                    fos.channel.transferFrom(rbs, i, chunkSize)
                    i += chunkSize
                }
            }
        }
        conn.disconnect()
    }

}