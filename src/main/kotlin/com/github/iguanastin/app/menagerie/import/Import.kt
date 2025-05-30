package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.database.StatusFilter
import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.Menagerie
import com.github.iguanastin.app.menagerie.model.Tag
import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.Channels

private val log = KotlinLogging.logger {}

class Import(val id: Int, val url: String? = null, var file: File, val group: ImportGroup? = null, val addTags: List<Tag>? = null): Thread() {

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

        val runningStates = listOf(Status.IMPORTING, Status.DOWNLOADING)
        val finishedStates = listOf(Status.FAILED, Status.SUCCESS, Status.CANCELLED)

        fun fromWeb(id: Int, url: String, intoDirectory: File, tags: List<Tag>? = null): Import {
            return Import(id, url, intoDirectory, addTags = tags)
        }

        fun fromLocal(id: Int, file: File, intoGroup: ImportGroup? = null, tags: List<Tag>? = null): Import {
            return Import(id, file = file, group = intoGroup, addTags = tags)
        }
    }

    private lateinit var menagerie: Menagerie

    val status = objectProperty(Status.READY)
    val progress = doubleProperty()

    private var downloaded = false
    private var imported = false
    var item: FileItem? = null

    @Volatile
    private var cancelled = false


    fun start(menagerie: Menagerie) {
        if (status.value != Status.READY) return
        this.menagerie = menagerie
        start()
    }

    override fun run() {
        try {
            if (url != null) {
                updateStatus(Status.DOWNLOADING) { "Downloading file from URL: $url" }
                download()
                downloaded = true
            }
            if (checkCancel()) return

            updateStatus(Status.IMPORTING) { "Importing file: ${file.absolutePath}" }
            progress.value = -1.0

            item = menagerie.createFileItem(file, skipAdding = true)
            imported = true
            if (checkCancel()) return

            group?.getRealGroup(menagerie)?.addItem(item!!)

            addTags?.forEach { tag -> item!!.addTag(tag) }

            menagerie.addItem(item!!)
            menagerie.findSimilarToSingle(item!!)

            updateStatus(Status.SUCCESS) { "Successfully imported file: ${file.absolutePath}" }
            if (checkCancel()) return
            progress.value = 1.0
        } catch (e: Exception) {
            status.value = Status.FAILED
            undoImport()
            throw e
        }
    }

    private fun checkCancel(): Boolean {
        if (cancelled) {
            undoImport()
            updateStatus(Status.CANCELLED) { "Cancelled import: ${file.absolutePath}" }
            progress.value = 0.0
        }
        return cancelled
    }

    fun cancel() {
        if (status.value in finishedStates) return

        cancelled = true
        status.value = Status.CANCELLED
    }

    private fun updateStatus(stat: Status, msg: () -> Any) {
        status.value = stat
        log.info(msg)
    }

    private fun undoImport() {
        item?.also { item ->
            if (imported) {
                item.menagerie.removeItem(item)
                log.info { "Removed item ($item) from menagerie" }
            }
        }
        if (downloaded) {
            file.delete()
            log.info { "Deleted downloaded file: $file" }
        }

        imported = false
        downloaded = false
        item = null
        status.value = Status.CANCELLED
        cancelled = true
    }

    private fun download() {
        require(url != null)
        progress.value = 0.0

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.addRequestProperty("User-Agent", "Mozilla/4.0")
            patchFileName(conn.contentType)

            Channels.newChannel(conn.inputStream).use { rbs ->
                FileOutputStream(file).use { fos ->
                    val size: Long = conn.contentLengthLong
                    val chunkSize: Long = 4096
                    var i: Long = 0

                    val filter = StatusFilter<Double>({ progress.value = it }, 50)
                    while (i < size) {
                        fos.channel.transferFrom(rbs, i, chunkSize)
                        i += chunkSize
                        filter.trySend { i.toDouble()/size }
                    }
                }
            }

            progress.value = 1.0
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
        if (ext.lowercase() in mimeExtensions[mime]!!) return

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

    override fun equals(other: Any?): Boolean {
        return other is Import && other.id == id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun isReadyOrRunning(): Boolean {
        return isReady() || isRunning()
    }

    fun isRunning(): Boolean {
        return status.value in listOf(Status.DOWNLOADING, Status.IMPORTING)
    }

    fun isReady(): Boolean {
        return status.value == Status.READY
    }

}