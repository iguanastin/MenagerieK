package com.github.iguanastin.app.menagerie

import javafx.beans.property.IntegerProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty

class Tag(val id: Int, val name: String, color: String? = null, frequency: Int = 0) {

    val color: StringProperty = SimpleStringProperty(color)
    val frequency: IntegerProperty = SimpleIntegerProperty(frequency.coerceAtLeast(0))

    // TODO tag notes


    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Tag

        if (id != other.id) return false

        return true
    }

}