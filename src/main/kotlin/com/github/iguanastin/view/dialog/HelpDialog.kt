package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.MyApp
import com.github.iguanastin.view.nodes.TopEnabledStackPane
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.TabPane
import javafx.scene.text.FontWeight
import tornadofx.*
import java.awt.Desktop
import java.net.URI

class HelpDialog(onClose: () -> Unit = {}, val app: MyApp) : StackDialog(onClose) {

    init {
        root.graphic = tabpane {
            prefWidth = 600.0
            prefHeight = 400.0
            initHelpTab()
            initAboutTab()
            initShortcutsTab()
        }
    }

    private fun TabPane.initShortcutsTab() {
        tab("Shortcuts") {
            isClosable = false
            scrollpane {
                gridpane {
                    padding = insets(10.0)
                    vgap = 5.0
                    hgap = 25.0
                    var curContext: String? = null

                    MyApp.shortcuts.sortedBy { it.context }.forEach {
                        if (it.context != curContext) {
                            curContext = it.context
                            row {
                                label(it.context ?: "Generic") {
                                    style { fontWeight = FontWeight.EXTRA_BOLD }
                                }
                            }
                        }

                        val parts = mutableListOf<String>()
                        if (it.ctrl) parts.add("Ctrl")
                        if (it.alt) parts.add("Alt")
                        if (it.shift) parts.add("Shift")
                        parts.add(it.key.name)
                        row {
                            label(parts.joinToString("+")) {
                                paddingLeft = 10.0
                            }
                            label(it.desc ?: "Unspecified")
                        }
                    }
                }
            }
        }
    }

    private fun TabPane.initAboutTab() {
        tab("About") {
            isClosable = false
            vbox(10.0) {
                padding = insets(5.0)
                alignment = Pos.CENTER

                label("MenagerieK") {
                    style {
                        fontWeight = FontWeight.BOLD
                    }
                }
                label("Local file organizer")
                label("Version: ${MyApp.VERSION}")
                hyperlink("https://github.com/iguanastin/menageriek") {
                    onAction = EventHandler { event ->
                        event.consume()
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop()
                                .isSupported(Desktop.Action.BROWSE)
                        ) Desktop.getDesktop().browse(URI(text))
                    }
                }
            }
        }
    }

    private fun TabPane.initHelpTab() {
        tab("Help") {
            isClosable = false
            scrollpane(fitToWidth = true) {
                padding = insets(5.0)
                // TODO write up an introduction and overview
                vbox(spacing = 10.0) {
                    label("This is a test sentence that is hopefully going to be long enough to cause a wrap because I need to test that feature\n" +
                            "\n" +
                            "Plus newlines :)") {
                        isWrapText = true
                    }
                    button("Take a tour") {
                        onAction = EventHandler { event ->
                            event.consume()
                            this@HelpDialog.close()
                            app.root.startTour()
                        }
                    }
                }
            }
        }
    }

}

fun TopEnabledStackPane.helpDialog(onClose: () -> Unit = {}, app: MyApp, op: HelpDialog.() -> Unit = {}) = HelpDialog(onClose, app).attachTo(this, op)