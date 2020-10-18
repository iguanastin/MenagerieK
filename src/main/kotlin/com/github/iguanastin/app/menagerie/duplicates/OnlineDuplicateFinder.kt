package com.github.iguanastin.app.menagerie.duplicates

abstract class OnlineDuplicateFinder {

    abstract fun findMatches(set: OnlineMatchSet)

}