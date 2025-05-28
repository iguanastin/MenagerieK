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

class Import(val url: String? = null, val file: File, val group: ImportGroup? = null, val addTags: List<Tag>? = null) {

    enum class Status {
        READY,
        DOWNLOADING,
        IMPORTING,
        SUCCESS,
        FAILED
    }

    val status: ObjectProperty<Status> = objectProperty(Status.READY)

    init {
        if (url != null) require(!file.exists())
    }

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
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.addRequestProperty("User-Agent", "Mozilla/4.0")
            conn.contentType
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

}