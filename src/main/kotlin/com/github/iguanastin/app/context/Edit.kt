package com.github.iguanastin.app.context

abstract class Edit {

    var state = State.NotApplied
        private set

    enum class State {
        NotApplied,
        Applied,
        Undone // Edits are currently not "redo" able
    }

    // TODO: Make edits "redo" able

    fun perform(): Boolean {
        if (state != State.NotApplied) return false

        if (!_perform()) return false

        state = State.Applied
        return true
    }

    fun undo(): Boolean {
        if (state != State.Applied) return false

        if (!_undo()) return false

        state = State.Undone
        return true
    }

    protected abstract fun _perform(): Boolean
    protected abstract fun _undo(): Boolean

}