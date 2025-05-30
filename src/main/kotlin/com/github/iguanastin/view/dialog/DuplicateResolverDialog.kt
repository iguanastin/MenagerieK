package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.MyApp
import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.context.MenagerieContext
import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.SimilarPair
import com.github.iguanastin.app.menagerie.model.Tag
import com.github.iguanastin.app.utils.copyTagsToClipboard
import com.github.iguanastin.app.utils.pasteTagsFromClipboard
import com.github.iguanastin.view.factories.TagCellFactory
import com.github.iguanastin.view.nodes.multitypeitemdisplay
import com.github.iguanastin.view.onActionConsuming
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ObservableList
import javafx.collections.SetChangeListener
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import tornadofx.*

class DuplicateResolverDialog(val pairs: ObservableList<SimilarPair<Item>>, val context: MenagerieContext?) :
    StackDialog() {

    val displayingProperty: ObjectProperty<SimilarPair<Item>> = SimpleObjectProperty()
    var displaying: SimilarPair<Item>?
        get() = displayingProperty.get()
        set(value) = displayingProperty.set(value)

    private lateinit var notDuplicateButton: ToggleButton
    private lateinit var leftTags: ListView<Tag>
    private lateinit var rightTags: ListView<Tag>
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private lateinit var topPane: SplitPane

    private val leftTagCellFactory = object : TagCellFactory() {
        override fun call(listView: ListView<Tag>?): ListCell<Tag> {
            return super.call(listView).apply {
                contextmenu {
                    item("Clone to Other") { onActionConsuming { displaying?.obj2?.addTag(item) } }
                    item("Remove") { onActionConsuming { displaying?.obj1?.removeTag(item) } }
                }
            }
        }
    }
    private val rightTagCellFactory = object : TagCellFactory() {
        override fun call(listView: ListView<Tag>?): ListCell<Tag> {
            return super.call(listView).apply {
                contextmenu {
                    item("Clone to Other") { onActionConsuming { displaying?.obj1?.addTag(item) } }
                    item("Remove") { onActionConsuming { displaying?.obj2?.removeTag(item) } }
                }
            }
        }
    }

    init {
        addClass(Styles.dialogPane)

        center {
            topPane = splitpane()
            // Create then apply so it's topPane is usable for initialization of children
            topPane.apply {
                stackpane {
                    multitypeitemdisplay {
                        itemProperty.bind(displayingProperty.map { it?.obj1 })
                        contextmenu {
                            item("Clone Tags from Other") {
                                onActionConsuming { displaying?.obj2?.tags?.forEach { displaying?.obj1?.addTag(it) } }
                            }
                            item("Copy Tags") {
                                onActionConsuming { displaying?.obj1?.copyTagsToClipboard() }
                            }
                            item("Paste Tags") {
                                onActionConsuming { displaying?.obj1?.pasteTagsFromClipboard(context) }
                            }
                            separator()
                            item("No Duplicates") {
                                onActionConsuming { markNoDuplicates(displaying!!, displaying!!.obj1) }
                            }
                        }
                    }
                    borderpane {
                        isPickOnBounds = false
                        right {
                            leftTags = listview {
                                addClass(Styles.duplicatesTagList)
                                cellFactory = leftTagCellFactory
                                visibleWhen(topPane.hoverProperty())
                            }
                        }
                    }
                }
                stackpane {
                    multitypeitemdisplay {
                        itemProperty.bind(displayingProperty.map { it?.obj2 })
                        contextmenu {
                            item("Clone Tags from Other") {
                                onActionConsuming { displaying?.obj1?.tags?.forEach { displaying?.obj2?.addTag(it) } }
                            }
                            item("Copy Tags") {
                                onActionConsuming { displaying?.obj2?.copyTagsToClipboard() }
                            }
                            item("Paste Tags") {
                                onActionConsuming { displaying?.obj2?.pasteTagsFromClipboard(context) }
                            }
                            separator()
                            item("No Duplicates") {
                                onActionConsuming { markNoDuplicates(displaying!!, displaying!!.obj2) }
                            }
                        }
                    }
                    borderpane {
                        isPickOnBounds = false
                        left {
                            rightTags = listview {
                                addClass(Styles.duplicatesTagList)
                                cellFactory = rightTagCellFactory
                                visibleWhen(topPane.hoverProperty())
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
                    hbox(5) {
                        button("Delete") {
                            onActionConsuming { delete(displaying ?: return@onActionConsuming, true) }
                        }
                        button("Replace") {
                            onActionConsuming {
                                replace(displaying ?: return@onActionConsuming, true)
                            }
                        }
                    }
                }
                center {
                    vbox(5.0) {
                        alignment = Pos.CENTER
                        label {
                            textProperty().bind(displayingProperty.stringBinding(pairs) {
                                if (it == null) "N/A" else "${
                                    pairs.indexOf(it) + 1
                                }/${pairs.size}: %.2f%%".format(it.similarity * 100)
                            })
                        }
                        hbox(5.0) {
                            alignment = Pos.CENTER
                            prevButton = button("◄") {
                                disableWhen(displayingProperty.booleanBinding(pairs) {
                                    pairs.indexOf(it) <= 0
                                })
                                onActionConsuming { previous() }
                            }
                            notDuplicateButton = togglebutton("Not a duplicate", selectFirst = false) {
                                displayingProperty.addListener { _, _, new -> isSelected = new?.obj1?.menagerie?.hasNonDupe(new) == true }
                                onActionConsuming {
                                    val pair = displaying ?: return@onActionConsuming
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
                                disableWhen(displayingProperty.booleanBinding(pairs) {
                                    pairs.indexOf(it) >= pairs.size - 1
                                })
                                onActionConsuming { next() }
                            }
                        }
                    }
                }
                right {
                    hbox(5) {
                        button("Replace") {
                            onActionConsuming {
                                replace(displaying ?: return@onActionConsuming, false)
                            }
                        }
                        button("Delete") {
                            onActionConsuming { delete(displaying ?: return@onActionConsuming, false) }
                        }
                    }
                }
            }
        }

        initListeners()

        if (pairs.isNotEmpty()) displaying = pairs.first()
    }

    private fun replace(pair: SimilarPair<Item>, replaceLeftWithRight: Boolean) {
        val replace = if (replaceLeftWithRight) pair.obj1 else pair.obj2
        val with = if (replaceLeftWithRight) pair.obj2 else pair.obj1

        displayNextForRemovingItem(pair, with)

        replace.replace(with, false)
    }

    private fun delete(pair: SimilarPair<Item>, left: Boolean) {
        val item = if (left) pair.obj1 else pair.obj2

        displayNextForRemovingItem(pair, item)

        item.menagerie.removeItem(item)
        if (item is FileItem) item.file.delete()
    }

    private fun displayNextForRemovingItem(pair: SimilarPair<Item>, removing: Item) {
        var foundNext = false
        // Find forwards in list
        for (i in (pairs.indexOf(pair) + 1) until pairs.size) {
            if (!pairs[i].contains(removing)) {
                displaying = pairs[i]
                foundNext = true
                break
            }
        }
        // Find backwards in list
        if (!foundNext) {
            for (i in (pairs.indexOf(pair) - 1) downTo 0) {
                if (!pairs[i].contains(removing)) {
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
    }

    private fun markNoDuplicates(pair: SimilarPair<Item>, item: Item) {
        displayNextForRemovingItem(pair, item)

        val toRemove = mutableListOf<SimilarPair<Item>>()
        pairs.forEach {
            if (it.contains(item)) {
                it.obj1.menagerie.addNonDupe(it)
                toRemove.add(it)
            }
        }

        toRemove.forEach { it.obj1.menagerie.removeSimilarity(it) }
    }

    private fun initListeners() {
        @Suppress("RemoveExplicitTypeArguments")
        val leftTagsListener = SetChangeListener<Tag> { change ->
            leftTags.items.apply {
                if (change.wasRemoved()) remove(change.elementRemoved)
                if (change.wasAdded()) add(change.elementAdded)
                sortWith(MyApp.displayTagSorter)
            }
        }

        @Suppress("RemoveExplicitTypeArguments")
        val rightTagsListener = SetChangeListener<Tag> { change ->
            rightTags.items.apply {
                if (change.wasRemoved()) remove(change.elementRemoved)
                if (change.wasAdded()) add(change.elementAdded)
                sortWith(MyApp.displayTagSorter)
            }
        }

        displayingProperty.addListener { _, oldValue, new ->
            if (oldValue != null) {
                leftTags.items.clear()
                rightTags.items.clear()
                oldValue.obj1.tags.removeListener(leftTagsListener)
                oldValue.obj2.tags.removeListener(rightTagsListener)
            }

            if (new != null) {
                leftTags.items.apply {
                    addAll(new.obj1.tags)
                    sortWith(MyApp.displayTagSorter)
                }
                rightTags.items.apply {
                    addAll(new.obj2.tags)
                    sortWith(MyApp.displayTagSorter)
                }
                new.obj1.tags.addListener(leftTagsListener)
                new.obj2.tags.addListener(rightTagsListener)
            }
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
        if (pairs.isEmpty()) return
        val i = pairs.indexOf(displaying)
        displaying = pairs[(i + 1).coerceAtMost(pairs.size - 1)]
    }

    private fun previous() {
        if (pairs.isEmpty()) return
        val i = pairs.indexOf(displaying)
        displaying = pairs[(i - 1).coerceAtLeast(0)]
    }

    override fun requestFocus() {
        notDuplicateButton.requestFocus()
    }

}