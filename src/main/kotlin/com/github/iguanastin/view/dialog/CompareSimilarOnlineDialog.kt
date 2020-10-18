package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.duplicates.OnlineMatch
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.view.nodes.ItemDisplay
import com.github.iguanastin.view.nodes.PanZoomImageView
import com.github.iguanastin.view.nodes.itemdisplay
import com.github.iguanastin.view.nodes.panzoomimageview
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.image.Image
import tornadofx.*
import java.awt.Desktop
import java.net.URI

class CompareSimilarOnlineDialog(item: Item, match: OnlineMatch) : StackDialog() {

    private lateinit var itemDisplay: ItemDisplay
    private lateinit var onlineDisplay: PanZoomImageView

    init {
        addClass(Styles.dialogPane)

        center {
            splitpane {
                padding = insets(5.0)

                borderpane {
                    center {
                        itemDisplay = itemdisplay {
                            this.item = item
                        }
                    }
                }

                borderpane {
                    center {
                        onlineDisplay = panzoomimageview {
                            image = Image(match.sourceResourceUrl, true)
                        }
                    }
                }
            }
        }

        bottom {
            hbox(5.0) {
                alignment = Pos.CENTER
                padding = insets(10.0)

                button("Go to source") {
                    onAction = EventHandler { event ->
                        event.consume()
                        Desktop.getDesktop().browse(URI(match.sourceUrl))
                    }
                }
                button("Replace") {
                    isDisable = true
                    onAction = EventHandler { event ->
                        event.consume()
                        TODO("Unimplemented")
                    }
                }
            }
        }
    }

}