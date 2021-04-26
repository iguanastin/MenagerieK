package com.github.iguanastin.app.menagerie.duplicates.remote

import com.github.iguanastin.app.menagerie.model.Item
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty

class OnlineMatchSet(val item: Item, var matches: List<OnlineMatch> = emptyList()) {

    enum class State {
        WAITING,
        LOADING,
        FINISHED,
        FAILED
    }

    val stateProperty: ObjectProperty<State> = SimpleObjectProperty(State.WAITING)
    var state: State
        get() = stateProperty.get()
        set(value) = stateProperty.set(value)

    var error: Throwable? = null


    fun reset() {
        matches = emptyList()
        error = null
        state = State.WAITING
    }

}