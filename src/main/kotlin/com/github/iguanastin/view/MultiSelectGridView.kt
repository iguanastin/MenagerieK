package com.github.iguanastin.view

import com.github.iguanastin.app.Styles
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.EventTarget
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
    }

    constructor() : super()
    constructor(items: ObservableList<T>) : super(items)


    fun initSelectableCell(cell: GridCell<T>) {
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

    private fun selectUtility(item: T, event: MouseEvent) {
        val type: SelectionType = when {
            event.isShiftDown -> {
                SelectionType.FROM_TO
            }
            event.isShortcutDown -> {
                SelectionType.ADD
            }
            else -> {
                SelectionType.ONLY
            }
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
    }

}

inline fun <T> EventTarget.multiselectgridview(op: MultiSelectGridView<T>.() -> Unit = {}) = MultiSelectGridView<T>().attachTo(this, op)