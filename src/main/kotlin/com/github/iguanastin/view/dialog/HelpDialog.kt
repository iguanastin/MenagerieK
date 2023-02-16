package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.MyApp
import com.github.iguanastin.app.Styles
import com.github.iguanastin.view.nodes.TopEnabledStackPane
import com.github.iguanastin.view.onActionConsuming
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
                hyperlink(MyApp.githubURL) {
                    onActionConsuming {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop()
                                .isSupported(Desktop.Action.BROWSE)
                        ) Desktop.getDesktop().browse(URI(text))
                    }
                }
                hyperlink("Patch Notes") {
                    onActionConsuming { app.showPatchNotesDialog() }
                }
            }
        }
    }

    private fun TabPane.initHelpTab() {
        tab("Help") {
            isClosable = false
            scrollpane(fitToWidth = true) {
                padding = insets(5.0)
                vbox(spacing = 10.0) {
                    label("New to Menagerie?")
                    button("Take a tour") {
                        onActionConsuming {
                            this@HelpDialog.close()
                            app.root.startTour()
                        }
                    }

                    separator()
                    label("Overview") { addClass(Styles.helpHeader) }
                    label(
                        "something something something overview purpose and idk\n\n" +
                                "A warning about using Menagerie:\n" +
                                "Menagerie doesn't track file changes made outside of the app, so if files are moved or renamed, they won't be accessible from the app anymore."
                    ) { isWrapText = true }

                    separator()
                    label("Importing") { addClass(Styles.helpHeader) }
                    label(
                        "Files can be imported from your local filesystem (Ctrl+I, Ctrl+Shift+I) or from the web (by dragging and dropping images or urls to images from the browser).\n\n" +
                                "When importing from the local filesystem, files are left in place.\n" +
                                "When importing from the web, files are downloaded into the folder specified in the settings, and the filename is automatically incremented if applicable.\n\n" +
                                "Newly imported files are automatically checked for similar/duplicate files."
                    ) { isWrapText = true }

                    separator()
                    label("Tagging") { addClass(Styles.helpHeader) }
                    label(
                        "Tags cannot contain spaces. Separate tag adds/deletes with a space to do multiple edits at once. To remove a tag, prepend it with a dash (-)\n\n" +
                                "E.g. remove 'tagme', add 'person' and 'landscape':\n" +
                                "    -tagme person landscape\n\n" +
                                "When editing or searching tags, an autocomplete will show the most common tags. Select the first option by pressing Ctrl+Space, or navigate to a specific option with the up/down arrow keys."
                    ) { isWrapText = true }

                    separator()
                    label("Searching") { addClass(Styles.helpHeader) }
                    label(
                        "Searching also autocompletes tags, but additionally completes non-tag search terms.\n\n" +
                                "Special search terms:"
                    ) { isWrapText = true }
                    gridpane {
                        hgap = 10.0
                        paddingLeft = 10.0
                        row {
                            label("in:[{ID}|any]")
                            label("In a group with id {ID}, or any group") { isWrapText = true }
                        }
                        row {
                            label("type:{TYPE}")
                            label("Specific types (image, file, video, group)") { isWrapText = true }
                        }
                        row {
                            label("time:(<|>){MILLIS}")
                            label("Imported before (<), after (>), or at (no prefix) a given time in milliseconds since epoch") {
                                isWrapText = true
                            }
                        }
                        row {
                            label("id:(<|>){ID}")
                            label("IDs less than (<), greater than (>), or equal to (no prefix)") { isWrapText = true }
                        }
                    }
                    label("Examples: -id:>4321 -id:123 time:<1674867225628 type:image -in:any") {
                        style {
                            paddingBottom = 100.0
                        }
                    }
                }
            }
        }
    }

}

fun TopEnabledStackPane.helpDialog(
    onClose: () -> Unit = {},
    app: MyApp,
    op: HelpDialog.() -> Unit = {}
) =
    HelpDialog(onClose, app).attachTo(this, op)