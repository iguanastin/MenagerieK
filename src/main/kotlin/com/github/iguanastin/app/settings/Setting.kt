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

open class StringPairListSetting(
    key: String,
    label: String? = null,
    val firstPrompt: String? = null,
    val secondPrompt: String? = null,
    default: List<Pair<String, String>> = listOf(),
    prefs: Preferences
) : Setting<List<Pair<String, String>>>(key, label, default, prefs) {

    override fun stringToObject(string: String): List<Pair<String, String>> {
        val split = string.split(" ")
        val list = mutableListOf<Pair<String, String>>()

        split.forEachIndexed { i, s ->
            if (i % 2 == 1) list.add(Pair(split[i - 1], s))
        }

        return list
    }

    override fun objectToString(obj: List<Pair<String, String>>): String {
        return obj.joinToString(" ") { "${it.first} ${it.second}" }
    }

}

class TagAliasSetting(
    key: String,
    label: String? = null,
    default: List<Pair<String, String>> = listOf(),
    prefs: Preferences
) :
    StringPairListSetting(key, label, firstPrompt = "Alias", secondPrompt = "Target tag", default = default, prefs = prefs) {

    fun apply(tag: String): String {
        value.forEach {
            if (it.first.lowercase() == tag) return it.second.lowercase()
        }

        return tag
    }

}

class TagColorizerSetting(
    key: String,
    label: String? = null,
    default: List<Pair<String, String>> = listOf(),
    prefs: Preferences
) :
    StringPairListSetting(key, label, "Tag regex", "Color", default, prefs) {

    fun applyRulesTo(tag: Tag): Boolean {
        for (rule in value) {
            if (Regex(rule.first).containsMatchIn(tag.name)) {
                tag.color = rule.second
                return true
            }
        }

        return false
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