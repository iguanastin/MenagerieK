package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.VideoItem
import com.github.iguanastin.app.utils.toggle
import com.github.iguanastin.view.nodes.image.dynamicImageView
import com.github.iguanastin.view.onActionConsuming
import com.github.iguanastin.view.runOnUIThread
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleLongProperty
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Priority
import tornadofx.*
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Desktop
import java.net.URI
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

class VideoDisplay : ItemDisplay() {

    private val playImage = Image(VideoDisplay::class.java.getResource("/imgs/play.png")?.toExternalForm(), true)
    private val pauseImage = Image(VideoDisplay::class.java.getResource("/imgs/pause.png")?.toExternalForm(), true)
    private val volumeOffImage =
        Image(VideoDisplay::class.java.getResource("/imgs/volume_off.png")?.toExternalForm(), true)
    private val volumeUpImage =
        Image(VideoDisplay::class.java.getResource("/imgs/volume_up.png")?.toExternalForm(), true)

    //    private val volumeDownImage =
//        Image(VideoDisplay::class.java.getResource("/imgs/volume_down.png")?.toExternalForm(), true)
//    private val fullscreenImage =
//        Image(VideoDisplay::class.java.getResource("/imgs/fullscreen.png")?.toExternalForm(), true)
    private val repeatOnImage =
        Image(VideoDisplay::class.java.getResource("/imgs/repeat_on.png")?.toExternalForm(), true)
    private val repeatOffImage =
        Image(VideoDisplay::class.java.getResource("/imgs/repeat_off.png")?.toExternalForm(), true)

    private lateinit var videoSurface: ImageView

    private val mediaPlayer: EmbeddedMediaPlayer? =
        if (NativeDiscovery().discover()) MediaPlayerFactory("--no-metadata-network-access").let {
            val player = it.mediaPlayers().newEmbeddedMediaPlayer()
            it.release()
            return@let player
        } else null

    val repeatProperty = SimpleBooleanProperty(true)
    var isRepeat: Boolean
        get() = repeatProperty.value
        set(value) = repeatProperty.set(value)

    val pausedProperty = SimpleBooleanProperty(false)
    var isPaused: Boolean
        get() = pausedProperty.value
        set(value) = pausedProperty.set(value)

    val muteProperty = SimpleBooleanProperty(true)
    var isMuted: Boolean
        get() = muteProperty.value
        set(value) = muteProperty.set(value)

    private val timeProperty = SimpleLongProperty()
    private var time: Long
        get() = timeProperty.value
        set(value) = timeProperty.set(value)

    private val mediaLengthProperty = SimpleLongProperty()
    private var mediaLength: Long
        get() = mediaLengthProperty.value
        set(value) = mediaLengthProperty.set(value)

    init {
        if (mediaPlayer != null) {
            initWithVLCJ()
        } else {
            center = vbox(25) {
                alignment = Pos.CENTER
                label("Please install VLC version 3.x.x and restart Menagerie for video support")
                hyperlink("https://www.videolan.org/vlc/") {
                    onActionConsuming {
                        Desktop.getDesktop().browse(URI("https://www.videolan.org/vlc/"))
                    }
                }
            }
        }
    }

    private fun initWithVLCJ() {
        center = stackpane {
            videoSurface = dynamicImageView {
                addEventHandler(MouseEvent.MOUSE_CLICKED) { event ->
                    if (event.button == MouseButton.PRIMARY) {
                        event.consume()
                        pausedProperty.toggle()
                    }
                }
            }

            // Overlay
            borderpane {
                isPickOnBounds = false
                padding = insets(2.0)
                maxWidthProperty().bind(videoSurface.fitWidthProperty())
                maxHeightProperty().bind(videoSurface.fitHeightProperty())

                bottom = hbox(5.0) {
                    padding = insets(2.0)
                    alignment = Pos.CENTER
                    hgrow = Priority.ALWAYS
                    addClass(Styles.videoControls)
                    visibleWhen(this@VideoDisplay.hoverProperty())

                    button {
                        isFocusTraversable = false
                        tooltip {
                            textProperty().bind(pausedProperty.map { if (it) "Play" else "Pause" })
                        }
                        graphic = imageview {
                            imageProperty().bind(pausedProperty.map { if (it) playImage else pauseImage })
                        }
                        onActionConsuming { pausedProperty.toggle() }
                    }
                    slider(0.0, 1.0) {
                        isFocusTraversable = false
                        hgrow = Priority.ALWAYS
                        mediaPlayer?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                            override fun positionChanged(mediaPlayer: MediaPlayer?, newPosition: Float) {
                                runOnUIThread {
                                    if (!isValueChanging) {
                                        value = newPosition.toDouble()
                                    }
                                }
                            }
                        })
                        // Seek video
                        valueProperty().addListener { _, _, new ->
                            if (isValueChanging) mediaPlayer?.controls()?.setPosition(new.toFloat())
                        }
                        valueChangingProperty().addListener { _, _, new -> isPaused = new }

                        // Workaround to fix dragging with mouse when you DON'T click on the thumb to start the drag
                        addEventFilter(MouseEvent.MOUSE_PRESSED) { isValueChanging = true }
                        addEventFilter(MouseEvent.MOUSE_RELEASED) { isValueChanging = false }

                        // Seek bar smoothing
                        val millis = 33L
                        val timer = fixedRateTimer("Video player seek smoother", daemon = true, millis, millis) {
                            if (item != null && !isValueChanging && !isPaused) {
                                runOnUIThread { value += (millis.toDouble() / mediaLength).coerceIn(0.0, 1.0) }
                            }
                        }
                        sceneProperty().addListener { _ -> if (scene == null) timer.cancel() }
                    }
                    label {
                        val update = {
                            runOnUIThread { text = "${formatTime(time)} / ${formatTime(mediaLength)}" }
                        }
                        update()
                        timeProperty.addListener { _ -> update() }
                        mediaLengthProperty.addListener { _ -> update() }
                    }
                    // TODO show volume slider on hover
                    button {
                        isFocusTraversable = false
                        tooltip("Volume")
                        graphic = imageview {
                            imageProperty().bind(muteProperty.map { if (it) volumeOffImage else volumeUpImage })
                        }
                        onActionConsuming { muteProperty.toggle() }
                    }
                    button {
                        isFocusTraversable = false
                        tooltip("Repeat")
                        graphic = imageview {
                            imageProperty().bind(repeatProperty.map { if (it) repeatOnImage else repeatOffImage })
                        }
                        onActionConsuming { repeatProperty.toggle() }
                    }

                    // TODO fullscreen behavior?
    //                    button {
    //                        tooltip("Fullscreen")
    //                        graphic = imageview(fullscreenImage)
    //                        onActionConsuming {
    //                            // fullscreen it here
    //                        }
    //                    }
                }
            }
        }

        // Attach media player to surface
        mediaPlayer?.videoSurface()?.set(ImageViewVideoSurface(videoSurface))

        // Release VLCJ natives when removed from scene
        sceneProperty().addListener { _ ->
            if (scene == null) release()
        }

        initControls()

        itemProperty.addListener { _, _, item ->
            val file = (item as? VideoItem)?.file
            if (file != null) {
                time = 0
                mediaLength = 0
                mediaPlayer?.submit {
                    mediaPlayer.media().play(file.absolutePath)
                    mediaPlayer.audio().isMute = isMuted
                    isPaused = false
                }
            } else {
                mediaPlayer?.submit {
                    mediaPlayer.controls().stop()
                    mediaPlayer.media().prepare("") // Clear current media to stop it from holding a file lock
                    // VLC seems to do a long blocking operation the first time it's loading a file from a folder with thousands of files in it.
                    // I don't see why it's necessary for VLC to walk all the files in a folder when it has a direct path to a file.
                    videoSurface.image = null
                }
            }
        }
    }

    private fun initControls() {
        // Init time event handler
        mediaPlayer?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                mediaLength = newLength
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                time = newTime
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                mediaPlayer.submit { if (!isRepeat) isPaused = true }
            }

            override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
                mediaPlayer.submit { mediaPlayer.audio().isMute = isMuted }
            }
        })

        // Init simple controls
        repeatProperty.addListener { _, _, new -> mediaPlayer?.controls()?.repeat = new }
        pausedProperty.addListener { _, _, new ->
            if (!new && !isRepeat && mediaPlayer?.status()?.position() == -1f) {
                // Restart if paused at end of media
                mediaPlayer.controls().setPosition(0f)
                mediaPlayer.controls().play()
            } else {
                mediaPlayer?.controls()?.setPause(new)
            }
            if (!new) mediaPlayer?.audio()?.isMute = isMuted
        }
        mediaPlayer?.controls()?.apply {
            repeat = isRepeat
            setPause(isPaused)
        }
        muteProperty.addListener { _, _, new -> mediaPlayer?.audio()?.isMute = new }
        mediaPlayer?.audio()?.isMute = isMuted

        disabledProperty().addListener { _, _, new -> if (new) isPaused = true }
    }

    override fun canDisplay(item: Item?): Boolean {
        return item is VideoItem
    }

    private fun formatTime(millis: Long): String {
        val d = Duration.ofMillis(millis)
        val days = d.toDays()
        val hours = d.toHoursPart()
        val minutes = d.toMinutesPart()
        val seconds = if (d.toSecondsPart() >= 10) d.toSecondsPart() else "0${d.toSecondsPart()}"

        return if (days > 0) {
            "$days:$hours:$minutes:$seconds"
        } else if (hours > 0) {
            "$hours:$minutes:$seconds"
        } else {
            "$minutes:$seconds"
        }
    }

    fun release() {
        mediaPlayer?.controls()?.stop()
        mediaPlayer?.release()
    }

}

fun EventTarget.videodisplay(op: VideoDisplay.() -> Unit = {}) = VideoDisplay().attachTo(this, op)