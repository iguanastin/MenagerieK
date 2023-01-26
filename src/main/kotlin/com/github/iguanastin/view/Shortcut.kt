package com.github.iguanastin.view

import javafx.event.EventType
import javafx.scene.Node
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent

class Shortcut(
    val key: KeyCode,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val type: EventType<KeyEvent> = KeyEvent.KEY_PRESSED,
    val desc: String? = null,
    val context: String? = null,
    handler: (event: KeyEvent) -> Unit
) {

    private val _handler: (event: KeyEvent) -> Unit = { event ->
        if (event.code == key && event.isShortcutDown == ctrl && event.isAltDown == alt && event.isShiftDown == shift) {
            event.consume()
            handler(event)
        }
    }

    private val nodes: MutableList<Node> = mutableListOf()

    fun bindTo(node: Node) {
        node.addEventHandler(type, _handler)
    }

    fun unbindFrom(node: Node) {
        node.removeEventHandler(type, _handler)
    }

    fun unbindAll() {
        nodes.forEach { unbindFrom(it) }
    }

}

fun Node.bindShortcut(
    key: KeyCode,
    ctrl: Boolean = false,
    alt: Boolean = false,
    shift: Boolean = false,
    type: EventType<KeyEvent> = KeyEvent.KEY_PRESSED,
    desc: String? = null,
    context: String? = null,
    handler: (event: KeyEvent) -> Unit
): Shortcut {
    return Shortcut(key, ctrl, alt, shift, type, desc, context, handler).apply { bindTo(this@bindShortcut) }
}