package com.github.iguanastin.app.settings

import javafx.beans.property.*
import java.util.prefs.Preferences

abstract class Setting(val name: String, protected val prefs: Preferences) {
    abstract fun resetToDefault()
}

class StringSetting(name: String, val default: String?, prefs: Preferences) : Setting(name, prefs) {

    private val _property: StringProperty = SimpleStringProperty(default)
    val property: ReadOnlyStringProperty = _property
    var value: String?
        get() = prefs.get(name, default)
        set(value) {
            prefs.put(name, value)
        }

    init {
        prefs.addPreferenceChangeListener {
            if (it.key == name) _property.value = it.newValue
        }
    }

    override fun resetToDefault() {
        value = default
    }
}

class IntSetting(name: String, val default: Int, prefs: Preferences) : Setting(name, prefs) {

    private val _property: IntegerProperty = SimpleIntegerProperty(default)
    val property: ReadOnlyIntegerProperty = _property
    var value: Int
        get() = prefs.getInt(name, default)
        set(value) {
            prefs.putInt(name, value)
        }

    init {
        prefs.addPreferenceChangeListener {
            if (it.key == name) _property.value = it.newValue.toInt()
        }
    }

    override fun resetToDefault() {
        value = default
    }

}

class BoolSetting(name: String, val default: Boolean, prefs: Preferences) : Setting(name, prefs) {

    private val _property: BooleanProperty = SimpleBooleanProperty(default)
    val property: ReadOnlyBooleanProperty = _property
    var value: Boolean
        get() = prefs.getBoolean(name, default)
        set(value) {
            prefs.putBoolean(name, value)
        }

    init {
        prefs.addPreferenceChangeListener {
            if (it.key == name) _property.value = it.newValue.toBoolean()
        }
    }

    override fun resetToDefault() {
        value = default
    }

}

class DoubleSetting(name: String, val default: Double, prefs: Preferences) : Setting(name, prefs) {

    private val _property: DoubleProperty = SimpleDoubleProperty(default)
    val property: ReadOnlyDoubleProperty = _property
    var value: Double
        get() = prefs.getDouble(name, default)
        set(value) {
            prefs.putDouble(name, value)
        }

    init {
        prefs.addPreferenceChangeListener {
            if (it.key == name) _property.value = it.newValue.toDouble()
        }
    }

    override fun resetToDefault() {
        value = default
    }

}