package com.github.iguanastin.view

import com.github.iguanastin.app.Styles
import com.sun.javafx.scene.control.skin.VirtualFlow
import impl.org.controlsfx.skin.GridViewSkin
import javafx.beans.InvalidationListener
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.EventTarget
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.util.Callback
import org.controlsfx.control.GridCell
import org.controlsfx.control.GridView
import tornadofx.*
import java.lang.Integer.max
import java.lang.Integer.min

class MultiSelectGridView<T> : GridView<T> {

    private enum class SelectionType {
        ONLY,
        ADD,
        FROM_TO
    }

    val selected: ObservableList<T> = observableListOf()

    init {
        items.addListener(ListChangeListener { change ->
            while (change.next()) {
                selected.removeAll(change.removed)
            }
        })

        cellFactory = Callback { grid ->
            object : GridCell<T>() {
                init {
                    if (grid is MultiSelectGridView) {
                        grid.initSelectableCell(this)
                    }
                }
            }
        }

        addEventHandler(MouseEvent.MOUSE_CLICKED) { event ->
            if (event.button == MouseButton.PRIMARY) {
                selected.clear()
            }
        }
        addEventHandler(KeyEvent.KEY_PRESSED) { event ->
            if (event.isShortcutDown) {
                if (event.code == KeyCode.A) {
                    if (selected.containsAll(items)) {
                        selected.clear()
                    } else {
                        selected.clear()
                        selected.addAll(items)
                    }
                    event.consume()
                }
            }

            var current: Int = items.indexOf(selected.lastOrNull())
            if (current == -1) current = items.indexOf(items.firstOrNull())
            val row = current / getItemsInRow()

            if (items.isEmpty()) return@addEventHandler
            when (event.code) {
                KeyCode.LEFT -> {
                    if (current <= 0) selectUtility(items[0], event)
                    else selectUtility(items[current - 1], event)
                    event.consume()
                }
                KeyCode.RIGHT -> {
                    if (current >= items.lastIndex) selectUtility(items.last(), event)
                    else selectUtility(items[current + 1], event)
                    event.consume()
                }
                KeyCode.UP -> {
                    if (row <= 0) selectUtility(items[0], event)
                    else selectUtility(items[current - getItemsInRow()], event)
                    event.consume()
                }
                KeyCode.DOWN -> {
                    if (row >= items.size / getItemsInRow()) selectUtility(items.last(), event)
                    else selectUtility(items[current + getItemsInRow()], event)
                    event.consume()
                }
                KeyCode.HOME -> {
                    selectUtility(items.first(), event)
                }
                KeyCode.END -> {
                    selectUtility(items.last(), event)
                }
                else -> {
                    // Do nothing
                }
            }
        }
    }

    constructor() : super()
    constructor(items: ObservableList<T>) : super(items)


    fun initSelectableCell(cell: GridCell<T>) {
        cell.itemProperty().addListener(InvalidationListener {
            if (cell.item in selected) {
                runOnUIThread { if (!cell.hasClass(Styles.selected)) cell.addClass(Styles.selected) }
            } else if (cell.item in selected) {
                runOnUIThread { cell.removeClass(Styles.selected) }
            }
        })

        selected.addListener(ListChangeListener { change ->
            while (change.next()) {
                if (cell.item in change.addedSubList) {
                    runOnUIThread { if (!cell.hasClass(Styles.selected)) cell.addClass(Styles.selected) }
                } else if (cell.item in change.removed) {
                    runOnUIThread { cell.removeClass(Styles.selected) }
                }
            }
        })

        cell.setOnMouseClicked { event ->
            if (cell.item != null && event.button == MouseButton.PRIMARY) {
                selectUtility(cell.item, event)
                event.consume()
            }
        }
    }

    fun select(item: T) {
        selected.clear()
        selected.add(item)
    }

    private fun selectUtility(item: T, event: KeyEvent) {
        selectUtility(item, when {
            event.isShiftDown -> SelectionType.FROM_TO
            event.isShortcutDown -> SelectionType.ADD
            else -> SelectionType.ONLY
        })
    }

    private fun selectUtility(item: T, event: MouseEvent) {
        val type: SelectionType = when {
            event.isShiftDown -> SelectionType.FROM_TO
            event.isShortcutDown -> SelectionType.ADD
            else -> SelectionType.ONLY
        }

        selectUtility(item, type)
    }

    private fun selectUtility(item: T, type: SelectionType = SelectionType.ONLY) {
        when (type) {
            SelectionType.ONLY -> {
                if (item in selected) {
                    if (selected.size == 1) {
                        selected.clear()
                    } else {
                        selected.clear()
                        selected.add(item)
                    }
                } else {
                    selected.apply {
                        clear()
                        add(item)
                    }
                }
            }
            SelectionType.ADD -> {
                if (item in selected) {
                    selected.remove(item)
                } else {
                    selected.add(item)
                }
            }
            SelectionType.FROM_TO -> {
                if (selected.isEmpty()) {
                    selected.add(item)
                } else {
                    val first: T = selected[0]

                    val i1 = items.indexOf(first)
                    val i2 = items.indexOf(item)

                    selected.apply {
                        clear()
                        val list = items.subList(min(i1, i2), max(i1, i2) + 1)
                        addAll(if (i2 < i1) {
                            list.asReversed()
                        } else {
                            list
                        })
                    }
                }
            }
        }

        ensureVisible(item)
    }

    private fun ensureVisible(item: T) {
        // Gross workaround. Couldn't find any other solution
        for (n in children) {
            if (n is VirtualFlow<*>) {
                n.show(items.indexOf(item) / getItemsInRow())
                break
            }
        }
    }

    fun getItemsInRow(): Int = (skin as GridViewSkin<*>).computeMaxCellsInRow()

}

inline fun <T> EventTarget.multiselectgridview(op: MultiSelectGridView<T>.() -> Unit = {}) = MultiSelectGridView<T>().attachTo(this, op)