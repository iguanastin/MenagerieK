package com.github.iguanastin.app.menagerie.model

/**
 * An object representing a pair and their similarity.
 *
 * @param <T> Object type.
</T> */
class SimilarPair<T>(val obj1: T, val obj2: T, val similarity: Double) {

    init {
        require(obj1 != obj2) { "Objects must not be equal" }
        require(!(similarity < 0 || similarity > 1)) { "Similarity must be between 0 and 1, inclusive: $similarity" }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is SimilarPair<*>) return false
        return if (obj1 == other.obj1 && obj2 == other.obj2) {
            true
        } else {
            obj1 == other.obj2 && obj2 == other.obj1
        }
    }

    override fun hashCode(): Int {
        return obj1.hashCode() + obj2.hashCode()
    }
}