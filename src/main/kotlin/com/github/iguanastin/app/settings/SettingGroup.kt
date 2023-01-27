package com.github.iguanastin.app.settings

import java.util.prefs.Preferences

abstract class SettingGroup(val title: String) {
    abstract val settings: List<Setting<out Any>>

    fun resetToDefaults() {
        settings.forEach { it.resetToDefault() }
    }
}

class GeneralSettingGroup(title: String, prefs: Preferences) : SettingGroup(title) {

    override val settings: List<Setting<out Any>>

    val downloadFolder: StringSetting

    init {
        settings = mutableListOf()

        downloadFolder = StringSetting("downloads", "Downloads folder", "", prefs, StringSetting.Type.FOLDER_PATH).also { settings.add(it) }
    }

}

class DatabaseSettingGroup(title: String, prefs: Preferences) : SettingGroup(title) {

    override val settings: List<Setting<out Any>>

    val url: StringSetting
    val user: StringSetting
    val pass: StringSetting

    init {
        settings = mutableListOf()

        url = StringSetting("db_url", "Database URL", "~/menagerie", prefs).also { settings.add(it) }
        user = StringSetting("db_user", "Database username", "sa", prefs).also { settings.add(it) }
        pass = StringSetting("db_pass", "Database password", "", prefs).also { settings.add(it) }
    }

}

class DuplicateSettingGroup(title: String, prefs: Preferences) : SettingGroup(title) {

    override val settings: List<Setting<out Any>>

    val confidence: DoubleSetting
    val enableCuda: BoolSetting

    init {
        settings = mutableListOf()

        confidence = DoubleSetting("confidence", "Similarity confidence", 0.95, prefs, min = 0.5, max = 1.0).also { settings.add(it) }
        enableCuda = BoolSetting("cuda", "CUDA GPU acceleration", false, prefs).also { settings.add(it) }
    }

}

class APISettingGroup(title: String, prefs: Preferences) : SettingGroup(title) {

    override val settings: List<Setting<out Any>>

    val enabled: BoolSetting
    val port: IntSetting
    val pageSize: IntSetting

    init {
        settings = mutableListOf()

        enabled = BoolSetting("api", "Host HTTP API server", false, prefs).also { settings.add(it) }
        port = IntSetting("api_port", "HTTP API port", 54321, prefs, min = 0, max = 65536).also { settings.add(it) }
        pageSize = IntSetting("api_pagesize", "Number of items per page", 100, prefs, 10, 1000).also { settings.add(it) }
    }

}

class UISettingGroup(title: String, prefs: Preferences) : SettingGroup(title) {

    override val settings: List<Setting<out Any>>

    val maximized: BoolSetting
    val width: DoubleSetting
    val height: DoubleSetting
    val x: DoubleSetting
    val y: DoubleSetting

    init {
        settings = mutableListOf()

        maximized = BoolSetting(key = "maximized", default = false, prefs = prefs).also { settings.add(it) }
        width = DoubleSetting(key = "width", default = 600.0, prefs = prefs).also { settings.add(it) }
        height = DoubleSetting(key = "height", default = 400.0, prefs = prefs).also { settings.add(it) }
        x = DoubleSetting(key = "x", default = 200.0, prefs = prefs).also { settings.add(it) }
        y = DoubleSetting(key = "y", default = 200.0, prefs = prefs).also { settings.add(it) }
    }

}