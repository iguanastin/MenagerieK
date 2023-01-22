package com.github.iguanastin.app.settings

import java.util.prefs.Preferences

abstract class SettingGroup(val title: String) {
    abstract val settings: List<Setting>

    fun resetToDefaults() {
        settings.forEach { it.resetToDefault() }
    }
}

class GeneralSettingGroup(title: String, prefs: Preferences) : SettingGroup(title) {

    override val settings: List<Setting>

    val downloadFolder: StringSetting

    init {
        settings = mutableListOf()

        downloadFolder = StringSetting("downloads", null, prefs).also { settings.add(it) }
    }

}

class DatabaseSettingGroup(title: String, prefs: Preferences) : SettingGroup(title) {

    override val settings: List<Setting>

    val url: StringSetting
    val user: StringSetting
    val pass: StringSetting

    init {
        settings = mutableListOf()

        url = StringSetting("db_url", "~/menagerie", prefs).also { settings.add(it) }
        user = StringSetting("db_user", "sa", prefs).also { settings.add(it) }
        pass = StringSetting("db_pass", "", prefs).also { settings.add(it) }
    }

}

class DuplicateSettingGroup(title: String, prefs: Preferences) : SettingGroup(title) {

    override val settings: List<Setting>

    val confidence: DoubleSetting
    val enableCuda: BoolSetting

    init {
        settings = mutableListOf()

        confidence = DoubleSetting("confidence", 0.95, prefs).also { settings.add(it) }
        enableCuda = BoolSetting("cuda", false, prefs).also { settings.add(it) }
    }

}

class APISettingGroup(title: String, prefs: Preferences) : SettingGroup(title) {

    override val settings: List<Setting>

    val enabled: BoolSetting
    val port: IntSetting

    init {
        settings = mutableListOf()

        enabled = BoolSetting("api", false, prefs).also { settings.add(it) }
        port = IntSetting("api_port", 54321, prefs).also { settings.add(it) }
    }

}

class UISettingGroup(title: String, prefs: Preferences) : SettingGroup(title) {

    override val settings: List<Setting>

    val maximized: BoolSetting
    val width: DoubleSetting
    val height: DoubleSetting
    val x: DoubleSetting
    val y: DoubleSetting

    init {
        settings = mutableListOf()

        maximized = BoolSetting("maximized", false, prefs).also { settings.add(it) }
        width = DoubleSetting("width", 600.0, prefs).also { settings.add(it) }
        height = DoubleSetting("height", 400.0, prefs).also { settings.add(it) }
        x = DoubleSetting("x", 200.0, prefs).also { settings.add(it) }
        y = DoubleSetting("y", 200.0, prefs).also { settings.add(it) }
    }

}