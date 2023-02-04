package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.menagerie.duplicates.remote.*
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.view.bindShortcut
import com.github.iguanastin.view.runOnUIThread
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import tornadofx.*

class FindOnlineChooseMatcherDialog(private val items: List<Item>, var onCancel: () -> Unit = {}): StackDialog() {

    private lateinit var sauceNAOButton: Button
    private lateinit var sauceNAOAutoTagButton: Button
    private lateinit var iqdbButton: Button
    private lateinit var iqdbAutoTagButton: Button

    private val tagIcon = Image(
        FindOnlineChooseMatcherDialog::class.java.getResource("/imgs/tag.png")?.toExternalForm(),
        true
    )

    private val matchSets: List<OnlineMatchSet> = items.map { OnlineMatchSet(it) }


    init {
        root.graphic = VBox(10.0).apply {
            padding = insets(25.0)
            minWidth = 300.0
            alignment = Pos.CENTER

            hbox {
                alignment = Pos.CENTER_LEFT
                label("Find Online With:") {
                    style {
                        fontSize = 18.px
                    }
                }
            }

            vbox(10.0) {
                hbox(5.0) {
                    sauceNAOButton = button("SauceNAO") {
                        maxWidth = Double.MAX_VALUE
                        hgrow = Priority.ALWAYS
                        style { padding = box(10.px) }
                        onAction = EventHandler { event ->
                            val parent = this@FindOnlineChooseMatcherDialog.parent
                            event.consume()
                            close()
                            parent.add(SimilarOnlineDialog(matchSets, SauceNAOMatchFinder()))
                        }
                    }
                    sauceNAOAutoTagButton = button {
                        graphic = ImageView(tagIcon)
                        onAction = EventHandler { event ->
                            event.consume()
                            autoTag(SauceNAOMatchFinder())
                        }
                        prefHeightProperty().bind(sauceNAOButton.heightProperty())
                    }
                }
                hbox(5.0) {
                    iqdbButton = button("IQDB") {
                        maxWidth = Double.MAX_VALUE
                        hgrow = Priority.ALWAYS

                        style { padding = box(10.px) }
                        onAction = EventHandler { event ->
                            val parent = this@FindOnlineChooseMatcherDialog.parent
                            event.consume()
                            close()
                            parent.add(SimilarOnlineDialog(matchSets, IQDBMatchFinder()))
                        }
                    }
                    iqdbAutoTagButton = button {
                        graphic = ImageView(tagIcon)
                        onAction = EventHandler { event ->
                            event.consume()
                            autoTag(IQDBMatchFinder())
                        }
                        prefHeightProperty().bind(iqdbButton.heightProperty())
                    }
                }
            }

            hbox {
                alignment = Pos.CENTER_RIGHT
                button("Cancel") {
                    onAction = EventHandler { event ->
                        event.consume()
                        close()
                        onCancel()
                    }
                }
            }
        }

        bindShortcut(KeyCode.DIGIT1) {
            sauceNAOButton.fire()
        }
        bindShortcut(KeyCode.DIGIT2) {
            iqdbButton.fire()
        }

        bindShortcut(KeyCode.DIGIT1, ctrl = true) {
            sauceNAOAutoTagButton.fire()
        }
        bindShortcut(KeyCode.DIGIT2, ctrl = true) {
            iqdbAutoTagButton.fire()
        }
    }

    private fun autoTag(source: OnlineMatchFinder) {
        val parent = this@FindOnlineChooseMatcherDialog.parent
        close()

        val progress = ProgressDialog(header = "Fetching tags", "0/${items.size}").also { parent.add(it) }
        var i = 0
        AutoTagger(items, source, onFoundTagsForItem = {
            i++
            runOnUIThread {
                progress.progress = i.toDouble() / items.size
                progress.message = "$i/${items.size}"
            }
        }, onFinished = {
            source.close()
            runOnUIThread { progress.close() }
        }).start()
    }

}