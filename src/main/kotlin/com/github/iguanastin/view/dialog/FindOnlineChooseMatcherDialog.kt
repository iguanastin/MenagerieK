package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.context.MenagerieContext
import com.github.iguanastin.app.menagerie.duplicates.remote.*
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.view.bindShortcut
import com.github.iguanastin.view.onActionConsuming
import com.github.iguanastin.view.runOnUIThread
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import mu.KotlinLogging
import org.apache.http.client.HttpResponseException
import tornadofx.*

private val log = KotlinLogging.logger {}

class FindOnlineChooseMatcherDialog(private val context: MenagerieContext, private val items: List<Item>, var onCancel: () -> Unit = {}): StackDialog() {

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
                    iqdbButton = button("IQDB") {
                        maxWidth = Double.MAX_VALUE
                        hgrow = Priority.ALWAYS

                        style { padding = box(10.px) }
                        onActionConsuming {
                            val parent = this@FindOnlineChooseMatcherDialog.parent
                            close()
                            parent.add(SimilarOnlineDialog(matchSets, IQDBMatchFinder()))
                        }
                    }
                    iqdbAutoTagButton = button {
                        graphic = ImageView(tagIcon)
                        onActionConsuming { autoTag(IQDBMatchFinder()) }
                        prefHeightProperty().bind(iqdbButton.heightProperty())
                    }
                }
                hbox(5.0) {
                    sauceNAOButton = button("SauceNAO") {
                        maxWidth = Double.MAX_VALUE
                        hgrow = Priority.ALWAYS
                        style { padding = box(10.px) }
                        onActionConsuming {
                            val parent = this@FindOnlineChooseMatcherDialog.parent
                            close()
                            parent.add(SimilarOnlineDialog(matchSets, SauceNAOMatchFinder()))
                        }
                    }
                    sauceNAOAutoTagButton = button {
                        graphic = ImageView(tagIcon)
                        onActionConsuming { autoTag(SauceNAOMatchFinder()) }
                        prefHeightProperty().bind(sauceNAOButton.heightProperty())
                    }
                }
            }

            hbox {
                alignment = Pos.CENTER_RIGHT
                button("Cancel") {
                    onActionConsuming {
                        close()
                        onCancel()
                    }
                }
            }
        }

        bindShortcut(KeyCode.DIGIT1) {
            iqdbButton.fire()
        }
        bindShortcut(KeyCode.DIGIT2) {
            sauceNAOButton.fire()
        }

        bindShortcut(KeyCode.DIGIT1, ctrl = true) {
            iqdbAutoTagButton.fire()
        }
        bindShortcut(KeyCode.DIGIT2, ctrl = true) {
            sauceNAOAutoTagButton.fire()
        }
    }

    private fun autoTag(source: OnlineMatchFinder) {
        val parent = this@FindOnlineChooseMatcherDialog.parent
        close()

        val progress = ProgressDialog(header = "Fetching tags", "").also { parent.add(it) }
        updateProgress(progress, 0)

        var i = 0
        val tagger = AutoTagger(context, items, source, finishedCheckingItem = {
            updateProgress(progress, i++)
        }, onFinished = {
            source.close()
            runOnUIThread { progress.close() }
        }, onError = { error ->
            log.error("Auto tagger error", error)
            if (error is HttpResponseException && error.statusCode == 429) {
                runOnUIThread { progress.message = "Too many requests, cooling down for 30 seconds..." }
                Thread.sleep(30000)
            } else {
                runOnUIThread {
                    parent.add(
                        InfoStackDialog(
                            header = "Auto tagger error",
                            message = error.message ?: "Unknown error"
                        )
                    )
                }
            }
        }).apply { start() }

        progress.onClose = {
            tagger.close()
        }
    }

    private fun updateProgress(progress: ProgressDialog, i: Int) {
        runOnUIThread {
            progress.progress = i.toDouble() / items.size
            progress.message = "$i/${items.size}"
        }
    }

}