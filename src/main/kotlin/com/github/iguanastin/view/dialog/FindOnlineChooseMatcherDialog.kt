package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.menagerie.duplicates.remote.IQDBMatchFinder
import com.github.iguanastin.app.menagerie.duplicates.remote.OnlineMatchSet
import com.github.iguanastin.app.menagerie.duplicates.remote.SauceNAOMatchFinder
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.layout.VBox
import tornadofx.*

class FindOnlineChooseMatcherDialog(private val matches: List<OnlineMatchSet>, var onCancel: () -> Unit = {}): StackDialog() {

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
                button("SauceNAO") {
                    maxWidth = Double.MAX_VALUE
                    style { padding = box(10.px) }
                    onAction = EventHandler { event ->
                        val parent = this@FindOnlineChooseMatcherDialog.parent
                        event.consume()
                        close()
                        parent.add(SimilarOnlineDialog(matches, SauceNAOMatchFinder()))
                    }
                }
                button("IQDB") {
                    maxWidth = Double.MAX_VALUE
                    style { padding = box(10.px) }
                    onAction = EventHandler { event ->
                        val parent = this@FindOnlineChooseMatcherDialog.parent
                        event.consume()
                        close()
                        parent.add(SimilarOnlineDialog(matches, IQDBMatchFinder()))
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
    }

}