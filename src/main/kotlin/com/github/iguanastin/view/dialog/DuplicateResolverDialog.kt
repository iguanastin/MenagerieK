package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.SimilarPair
import com.github.iguanastin.app.menagerie.model.Tag
import com.github.iguanastin.view.TagCellFactory
import com.github.iguanastin.view.nodes.ItemDisplay
import com.github.iguanastin.view.nodes.itemdisplay
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import tornadofx.*

class DuplicateResolverDialog(val pairs: ObservableList<SimilarPair<Item>>) : StackDialog() {

    val displayingProperty: ObjectProperty<SimilarPair<Item>> = SimpleObjectProperty()
    var displaying: SimilarPair<Item>?
        get() = displayingProperty.get()
        set(value) = displayingProperty.set(value)

    private lateinit var deleteLeftButton: Button
    private lateinit var deleteRightButton: Button
    private lateinit var prevButton: Button
    private lateinit var notDuplicateButton: ToggleButton
    private lateinit var nextButton: Button
    private lateinit var countSimilarityLabel: Label
    private lateinit var leftDisplay: ItemDisplay
    private lateinit var rightDisplay: ItemDisplay
    private lateinit var leftTags: ListView<Tag>
    private lateinit var rightTags: ListView<Tag>
    private lateinit var topPane: SplitPane

    init {
        addClass(Styles.dialogPane)

        center {
            topPane = splitpane {
                stackpane {
                    leftDisplay = itemdisplay()
                    borderpane {
                        isPickOnBounds = false
                        right {
                            leftTags = listview {
                                opacity = 0.75
                                isFocusTraversable = false
                                cellFactory = TagCellFactory.factory
                                maxWidth = 200.0
                                minWidth = 200.0
                            }
                        }
                    }
                }
                stackpane {
                    rightDisplay = itemdisplay()
                    borderpane {
                        isPickOnBounds = false
                        left {
                            rightTags = listview {
                                opacity = 0.75
                                isFocusTraversable = false
                                cellFactory = TagCellFactory.factory
                                maxWidth = 200.0
                                minWidth = 200.0
                            }
                        }
                    }
                }
            }
        }
        bottom {
            borderpane {
                padding = insets(10.0)
                left {
                    deleteLeftButton = button("Delete") {
                        onAction = EventHandler { event ->
                            val pair = displaying ?: return@EventHandler
                            event.consume()
                            delete(pair, true)
                        }
                    }
                }
                center {
                    vbox(5.0) {
                        alignment = Pos.CENTER
                        countSimilarityLabel = label("N/A")
                        hbox(5.0) {
                            alignment = Pos.CENTER
                            prevButton = button("◄") {
                                onAction = EventHandler { event ->
                                    event.consume()
                                    previous()
                                }
                            }
                            notDuplicateButton = togglebutton("Not a duplicate", selectFirst = false) {
                                onAction = EventHandler { event ->
                                    val pair = displaying ?: return@EventHandler
                                    event.consume()
                                    if (pair.obj1.menagerie.hasNonDupe(pair)) {
                                        pair.obj1.menagerie.removeNonDupe(pair)
                                        isSelected = false
                                    } else {
                                        pair.obj1.menagerie.addNonDupe(pair)
                                        isSelected = true
                                    }
                                }
                            }
                            nextButton = button("►") {
                                onAction = EventHandler { event ->
                                    event.consume()
                                    next()
                                }
                            }
                        }
                    }
                }
                right {
                    deleteRightButton = button("Delete") {
                        onAction = EventHandler { event ->
                            val pair = displaying ?: return@EventHandler
                            event.consume()
                            delete(pair, false)
                        }
                    }
                }
            }
        }

        initListeners()

        if (pairs.isNotEmpty()) displaying = pairs.first()
    }

    private fun delete(pair: SimilarPair<Item>, left: Boolean) {
        val item = if (left) pair.obj1 else pair.obj2

        item.menagerie.removeItem(item)
        if (item is FileItem) item.file.delete()

        var foundNext = false
        // Find forwards in list
        for (i in pairs.indexOf(pair) + 1 until pairs.size) {
            if (!pairs[i].contains(item)) {
                displaying = pairs[i]
                foundNext = true
                break
            }
        }
        // Find backwards in list
        if (!foundNext) {
            for (i in pairs.indexOf(pair) - 1 downTo 0) {
                if (!pairs[i].contains(item)) {
                    displaying = pairs[i]
                    foundNext = true
                    break
                }
            }
        }
        if (!foundNext) {
            displaying = null
            close()
        }

        pairs.removeIf { it.contains(item) }
    }

    private fun initListeners() {
        topPane.addEventHandler(MouseEvent.MOUSE_ENTERED) {
            leftTags.show()
            rightTags.show()
        }
        topPane.addEventHandler(MouseEvent.MOUSE_EXITED) {
            leftTags.hide()
            rightTags.hide()
        }

        val leftTagsListener = ListChangeListener<Tag> { change ->
            while (change.next()) {
                leftTags.items.apply {
                    removeAll(change.removed)
                    addAll(change.addedSubList)
                    sortBy { it.name }
                }
            }
        }
        val rightTagsListener = ListChangeListener<Tag> { change ->
            while (change.next()) {
                rightTags.items.apply {
                    removeAll(change.removed)
                    addAll(change.addedSubList)
                    sortBy { it.name }
                }
            }
        }
        displayingProperty.addListener { _, oldValue, newValue ->
            if (oldValue != null) {
                leftTags.items.clear()
                rightTags.items.clear()
                oldValue.obj1.tags.removeListener(leftTagsListener)
                oldValue.obj2.tags.removeListener(rightTagsListener)
            }

            if (newValue != null) {
                leftTags.items.apply {
                    addAll(newValue.obj1.tags)
                    sortBy { it.name }
                }
                rightTags.items.apply {
                    addAll(newValue.obj2.tags)
                    sortBy { it.name }
                }
                newValue.obj1.tags.addListener(leftTagsListener)
                newValue.obj2.tags.addListener(rightTagsListener)
            }

            notDuplicateButton.isSelected = newValue?.obj1?.menagerie?.knownNonDupes?.contains(newValue) == true

            countSimilarityLabel.text = if (newValue == null) {
                "N/a"
            } else {
                "${pairs.indexOf(newValue) + 1}/${pairs.size}: %.2f%%".format(newValue.similarity * 100)
            }
            leftDisplay.item = newValue?.obj1
            rightDisplay.item = newValue?.obj2
        }

        addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            when (event.code) {
                KeyCode.LEFT -> {
                    event.consume()
                    previous()
                }
                KeyCode.RIGHT -> {
                    event.consume()
                    next()
                }
                KeyCode.HOME -> {
                    event.consume()
                    displaying = pairs.first()
                }
                KeyCode.END -> {
                    event.consume()
                    displaying = pairs.last()
                }
                else -> { /* Do nothing */
                }
            }
        }
    }

    private fun next() {
        val i = pairs.indexOf(displaying)
        displaying = pairs[(i + 1).coerceAtMost(pairs.size - 1)]
    }

    private fun previous() {
        val i = pairs.indexOf(displaying)
        displaying = pairs[(i - 1).coerceAtLeast(0)]
    }

    override fun requestFocus() {
        notDuplicateButton.requestFocus()
    }

}