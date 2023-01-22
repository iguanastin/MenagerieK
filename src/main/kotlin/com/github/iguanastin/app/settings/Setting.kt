package com.github.iguanastin.app.settings

import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleObjectProperty
import java.util.prefs.Preferences

abstract class Setting<T>(val key: String, val label: String?, val default: T, private val prefs: Preferences) {
    private val _property: ObjectProperty<T> = SimpleObjectProperty()
    val property: ReadOnlyObjectProperty<T> = _property
    var value: T
        get() = stringToObject(prefs.get(key, default.toString()))
        set(value) {
            prefs.put(key, filterValue(value).toString())
        }

    init {
        prefs.addPreferenceChangeListener {
            if (it.key == key) _property.value = stringToObject(it.newValue)
        }
    }

    protected open fun filterValue(newValue: T): T {
        return newValue
    }

    fun resetToDefault() {
        value = default
    }

    protected abstract fun stringToObject(string: String): T

}

class StringSetting(key: String, label: String? = null, default: String, prefs: Preferences, val type: Type = Type.PLAIN_TEXT) :
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

class IntSetting(key: String, label: String? = null, default: Int, prefs: Preferences, val min: Int = Int.MIN_VALUE, val max: Int = Int.MAX_VALUE) :
    Setting<Int>(key, label, default, prefs) {
    override fun stringToObject(string: String): Int {
        return string.toInt()
    }

    override fun filterValue(newValue: Int): Int {
        return newValue.coerceIn(min, max)
    }
}

class DoubleSetting(key: String, label: String? = null, default: Double, prefs: Preferences, val min: Double = Double.MIN_VALUE, val max: Double = Double.MAX_VALUE) :
    Setting<Double>(key, label, default, prefs) {
    override fun stringToObject(string: String): Double {
        return string.toDouble()
    }

    override fun filterValue(newValue: Double): Double {
        return newValue.coerceIn(min, max)
    }
}