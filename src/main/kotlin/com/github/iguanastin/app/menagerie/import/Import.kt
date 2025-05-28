package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.Menagerie
import com.github.iguanastin.app.menagerie.model.Tag
import javafx.beans.property.ObjectProperty
import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.Channels

private val log = KotlinLogging.logger {}

class Import(val url: String? = null, var file: File, val group: ImportGroup? = null, val addTags: List<Tag>? = null) {

    enum class Status {
        READY,
        DOWNLOADING,
        IMPORTING,
        SUCCESS,
        FAILED,
        CANCELLED
    }

    companion object {
        val mimeExtensions = mapOf(
            Pair("image/jpeg", listOf(".jpg", ".jpeg", ".jfif")),
            Pair("image/png", listOf(".png")),
            Pair("image/gif", listOf(".gif")),
            Pair("image/bmp", listOf(".bmp")),

            Pair("video/webm", listOf(".webm")),
            Pair("video/mp4", listOf(".mp4", ".m4v")),
            Pair("video/quicktime", listOf(".mov")),
            Pair("video/x-flv", listOf(".flv")),
            Pair("video/x-msvideo", listOf(".avi")),
            Pair("video/x-ms-wmv", listOf(".wmv")),
            Pair("video/3gpp", listOf(".3gp")),
            Pair("video/mpeg", listOf(".mpg")),
            Pair("video/x-matroska", listOf(".mkv")),
        )
    }

    val status: ObjectProperty<Status> = objectProperty(Status.READY)


    fun import(menagerie: Menagerie): FileItem {
        try {
            if (url != null) {
                status.value = Status.DOWNLOADING
                log.info { "Downloading file from URL: $url" }
                download()
            }

            status.value = Status.IMPORTING
            log.info { "Importing file: ${file.path}" }
            val item = menagerie.createFileItem(file)

            group?.getRealGroup(menagerie)?.addItem(item)

            addTags?.forEach { tag -> item.addTag(tag) }

            status.value = Status.SUCCESS
            log.info { "Successfully imported file: ${file.path}" }
            return item
        } catch (e: Exception) {
            status.value = Status.FAILED
            throw e
        }
    }

    private fun download() {
        require(url != null)

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.addRequestProperty("User-Agent", "Mozilla/4.0")
            patchFileName(conn.contentType)

            Channels.newChannel(conn.inputStream).use { rbs ->
                FileOutputStream(file).use { fos ->
                    val size: Long = conn.contentLengthLong
                    val chunkSize: Long = 4096
                    var i: Long = 0
                    while (i < size) {
                        fos.channel.transferFrom(rbs, i, chunkSize)
                        i += chunkSize
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun patchFileName(mime: String) {
        require(url != null) { "Can't patch local file imports" }

        if (file.isDirectory) {
            // Build a filename from url
            val name = url.substringBefore('?').substringAfterLast('/')
            file = File("${file.parent}${File.separator}$name")
        }

        patchFileExtension(mime)

        while (file.exists()) {
            incrementFileName()
        }
    }

    private fun patchFileExtension(mime: String) {
        if (mime !in mimeExtensions) return

        val ext = if (file.extension.isEmpty()) "" else ".${file.extension}"
        if (ext in mimeExtensions[mime]!!) return

        file = File("${file.parent}${File.separator}${file.nameWithoutExtension}${mimeExtensions[mime]!![0]}")
    }

    private fun incrementFileName() {
        val ext = if (file.extension.isEmpty()) "" else ".${file.extension}"
        var name = file.nameWithoutExtension

        if (Regex(".+\\s\\([0-9]+\\)$").matches(name)) {
            val n = name.substringAfterLast('(').substringBefore(')').toInt()
            name = "${name.substringBeforeLast('(')}(${n+1})"

            file = File("${file.parent}${File.separator}$name$ext")
        } else {
            file =  File("${file.parent}${File.separator}$name (1)$ext")
        }
    }

}