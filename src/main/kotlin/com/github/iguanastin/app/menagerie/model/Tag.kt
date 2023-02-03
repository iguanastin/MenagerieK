package com.github.iguanastin.app.menagerie.model

import javafx.beans.property.IntegerProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty

class Tag(val id: Int, name: String, val menagerie: Menagerie, color: String? = null, frequency: Int = 0, val temporary: Boolean = false) {

    val name: String = name.lowercase()

    val colorProperty: StringProperty = SimpleStringProperty(color)
    var color: String?
        get() = colorProperty.get()
        set(value) = colorProperty.set(value)

    val frequencyProperty: IntegerProperty = SimpleIntegerProperty(frequency.coerceAtLeast(0))
    var frequency: Int
        get() = frequencyProperty.get()
        set(value) = frequencyProperty.set(value)

    val changeListeners: MutableSet<(TagChange) -> Unit> = mutableSetOf()


    init {
        colorProperty.addListener { _, old, new ->
            val change = TagChange(this, Change(old, new))
            changeListeners.forEach { it(change) }
        }
    }


    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Tag) return false

        return id == other.id
    }

    override fun toString(): String {
        return "($id) $name"
    }

}