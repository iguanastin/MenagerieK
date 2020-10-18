package com.github.iguanastin.app.menagerie.duplicates

import com.github.iguanastin.app.menagerie.model.Item
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty

class OnlineMatchSet(val item: Item, var matches: List<OnlineMatch> = emptyList()) {

    val finishedProperty: BooleanProperty = SimpleBooleanProperty(false)
    var isFinished: Boolean
        get() = finishedProperty.get()
        set(value) = finishedProperty.set(value)


    fun reset() {
        matches = emptyList()
        isFinished = false
    }

}