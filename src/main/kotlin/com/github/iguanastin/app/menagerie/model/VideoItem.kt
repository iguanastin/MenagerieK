package com.github.iguanastin.app.menagerie.model

import javafx.scene.image.Image
import mu.KotlinLogging
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

class VideoItem(id: Int, added: Long, menagerie: Menagerie, md5: String, file: File) :
    FileItem(id, added, menagerie, md5, file) {

    companion object {

        private val thumbnailerMediaPlayer: MediaPlayer? = if (NativeDiscovery().discover()) MediaPlayerFactory(
            "--intf", "dummy",
            "--vout", "dummy",
            "--no-audio",
            "--no-osd",
            "--no-spu",
            "--no-video-title-show",
            "--no-stats",
            "--no-sub-autodetect-file",
            "--no-disable-screensaver",
            "--no-snapshot-preview",
            "--no-metadata-network-access"
        ).let {
            val player = it.mediaPlayers().newMediaPlayer()
            it.release()
            return@let player
        } else null

        val fileExtensions = listOf("webm", "mp4", "mov", "flv", "avi", "wmv", "3gp", "mpg", "m4v", "mkv")

        fun isVideo(file: File): Boolean {
            return fileExtensions.contains(file.extension.lowercase())
        }

        fun releaseThumbnailer() {
            thumbnailerMediaPlayer?.submit { thumbnailerMediaPlayer.release() }
        }

    }

    override fun loadThumbnail(): Image {
        val inPositionLatch = CountDownLatch(2)
        val snapshotLatch = CountDownLatch(1)
        var tempFile: File? = null
        var result: Image? = null

        val eventListener = object : MediaPlayerEventAdapter() {
            override fun positionChanged(mediaPlayer: MediaPlayer, newPosition: Float) {
                inPositionLatch.countDown()
            }

            override fun videoOutput(mediaPlayer: MediaPlayer, newCount: Int) {
                inPositionLatch.countDown()
            }

            override fun snapshotTaken(mediaPlayer: MediaPlayer, filename: String) {
                tempFile = File(filename)
                snapshotLatch.countDown()
            }
        }
        thumbnailerMediaPlayer?.events()?.addMediaPlayerEventListener(eventListener)

        try {
            if (thumbnailerMediaPlayer?.media()?.start(file.absolutePath) == true) {
                if (!inPositionLatch.await(2, TimeUnit.SECONDS)) return super.loadThumbnail()

                if (thumbnailerMediaPlayer.video().videoDimension() != null) {
                    val vidWidth = thumbnailerMediaPlayer.video().videoDimension().getWidth()
                    val vidHeight = thumbnailerMediaPlayer.video().videoDimension().getHeight()
                    var scale = thumbnailSize / vidWidth
                    if (scale * vidHeight > thumbnailSize) scale = thumbnailSize / vidHeight
                    val width = (scale * vidWidth).toInt()
                    val height = (scale * vidHeight).toInt()

                    try {
                        thumbnailerMediaPlayer.snapshots().save(width, height)
                        if (!snapshotLatch.await(2, TimeUnit.SECONDS)) return super.loadThumbnail()

                        result = Image(tempFile!!.toURI().toString())
                        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                        if (!tempFile!!.delete()) log.warn("Failed to delete tempfile: $tempFile")
                    } catch (e: RuntimeException) {
                        log.warn("Failed to get video snapshot of file: $file", e)
                    }
                }
            }
        } catch (t: Throwable) {
            log.warn("Error while trying to create video thumbnail: $file", t)
        } finally {
            thumbnailerMediaPlayer?.controls()?.stop()
            thumbnailerMediaPlayer?.events()?.removeMediaPlayerEventListener(eventListener)
        }

        return result ?: super.loadThumbnail()
    }

    override fun getThumbnailExtension(): String {
        return "png"
    }

    override fun replace(with: Item, replaceTags: Boolean): Boolean {
        if (with !is VideoItem) return false
        return super.replace(with, replaceTags)
    }

}