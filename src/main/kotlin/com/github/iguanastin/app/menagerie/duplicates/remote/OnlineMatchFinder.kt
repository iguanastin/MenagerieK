package com.github.iguanastin.app.menagerie.duplicates.remote

abstract class OnlineMatchFinder {

    @Volatile
    var isClosed: Boolean = false
        private set


    abstract fun findMatches(set: OnlineMatchSet)

    open fun close() {
        isClosed = true
    }

}