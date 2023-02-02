package com.github.iguanastin.app.utils

object Versioning {

    private val regex = Regex("^v?[0-9]+\\.[0-9]+\\.[0-9]+$", RegexOption.IGNORE_CASE)

    fun compare(v1: String, v2: String): Int {
        if (!regex.matches(v1)) throw IllegalArgumentException("Invalid version format: $v1")
        if (!regex.matches(v2)) throw IllegalArgumentException("Invalid version format: $v2")

        val version1 = Version.fromString(v1)
        val version2 = Version.fromString(v2)

        return version1.compareTo(version2)
    }

}

private data class Version(val major: Int, val minor: Int, val patch: Int): Comparable<Version> {

    companion object {

        fun fromString(str: String): Version {
            val split = str.split(".")
            return Version(split[0].toInt(), split[1].toInt(), split[2].toInt())
        }

    }

    override fun compareTo(other: Version): Int {
        val major = this.major - other.major
        val minor = this.minor - other.minor
        val patch = this.patch - other.patch

        if (major == 0) {
            if (minor == 0) {
                return patch
            }
            return minor
        }
        return major
    }

}