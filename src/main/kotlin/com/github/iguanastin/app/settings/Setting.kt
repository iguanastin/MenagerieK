package com.github.iguanastin.app.settings

import com.github.iguanastin.app.menagerie.model.Tag
import java.util.prefs.Preferences

abstract class Setting<T>(val key: String, val label: String?, val default: T, private val prefs: Preferences) {

    var value: T
        get() = stringToObject(prefs.get(key, objectToString(default)))
        set(value) {
            prefs.put(key, objectToString(filterValue(value)))
        }

    val changeListeners: MutableList<(T) -> Unit> = mutableListOf()

    init {
        prefs.addPreferenceChangeListener { change ->
            if (change.key == key) {
                val obj = stringToObject(change.newValue)
                changeListeners.forEach { listener -> listener.invoke(obj) }
            }
        }
    }

    protected open fun filterValue(newValue: T): T {
        return newValue
    }

    fun resetToDefault() {
        value = default
    }

    protected abstract fun stringToObject(string: String): T

    protected open fun objectToString(obj: T): String {
        return obj.toString()
    }

}

class TagColorizerSetting(
    key: String,
    label: String? = null,
    default: List<TagColorRule> = listOf(),
    prefs: Preferences
) :
    Setting<List<TagColorRule>>(key, label, default, prefs) {

    override fun stringToObject(string: String): List<TagColorRule> {
        return mutableListOf<TagColorRule>().apply {
            if (string.isBlank()) return@apply
            string.split(";").forEach { add(TagColorRule.fromString(it)) }
        }
    }

    override fun objectToString(obj: List<TagColorRule>): String {
        return obj.joinToString(";")
    }

    fun applyRulesTo(tag: Tag): Boolean {
        for (rule in value) {
            if (rule.regex.containsMatchIn(tag.name)) {
                tag.color = rule.color
                return true
            }
        }

        return false
    }

}

class TagColorRule(val regex: Regex, val color: String) {

    override fun toString(): String {
        return "${regex.pattern} $color"
    }

    companion object {
        fun fromString(str: String): TagColorRule {
            val split = str.split(" ")
            return TagColorRule(Regex(split[0], RegexOption.IGNORE_CASE), split[1])
        }
    }

}

class StringSetting(
    key: String,
    label: String? = null,
    default: String,
    prefs: Preferences,
    val type: Type = Type.PLAIN_TEXT
) :
    Setting<String>(key, label, default, prefs) {

    enum class Type {
        PLAIN_TEXT,
        FILE_PATH,
        FOLDER_PATH
    }

    override fun stringToObject(string: String): String {
        return string
    }
}

class BoolSetting(key: String, label: String? = null, default: Boolean, prefs: Preferences) :
    Setting<Boolean>(key, label, default, prefs) {
    override fun stringToObject(string: String): Boolean {
        return string.toBoolean()
    }
}

class IntSetting(
    key: String,
    label: String? = null,
    default: Int,
    prefs: Preferences,
    val min: Int = Int.MIN_VALUE,
    val max: Int = Int.MAX_VALUE
) :
    Setting<Int>(key, label, default, prefs) {
    override fun stringToObject(string: String): Int {
        return string.toInt()
    }

    override fun filterValue(newValue: Int): Int {
        return newValue.coerceIn(min, max)
    }
}

class DoubleSetting(
    key: String,
    label: String? = null,
    default: Double,
    prefs: Preferences,
    val min: Double = Double.MIN_VALUE,
    val max: Double = Double.MAX_VALUE
) :
    Setting<Double>(key, label, default, prefs) {
    override fun stringToObject(string: String): Double {
        return string.toDouble()
    }

    override fun filterValue(newValue: Double): Double {
        return newValue.coerceIn(min, max)
    }
}