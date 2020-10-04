package com.github.iguanastin.app.menagerie

class Tag(val id: Int, val name: String, var color: String? = null, var frequency: Int = 0) {

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