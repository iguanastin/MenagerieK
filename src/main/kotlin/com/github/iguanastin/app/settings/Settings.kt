package com.github.iguanastin.app.settings

import java.util.prefs.Preferences

abstract class BaseSettings(prefsPath: String) {

    protected val prefs: Preferences = Preferences.userRoot().node(prefsPath)

    abstract val groups: List<SettingGroup>

    fun resetToDefaults() {
        groups.forEach { it.resetToDefaults() }
    }

}

class AppSettings: BaseSettings("iguanastin/MenagerieK/context") {

    val general: GeneralSettingGroup
    val database: DatabaseSettingGroup
    val duplicate: DuplicateSettingGroup
    val api: APISettingGroup

    override val groups: List<SettingGroup>

    init {
        groups = mutableListOf()

        general = GeneralSettingGroup("General", prefs).also { groups.add(it) }
        database = DatabaseSettingGroup("Database", prefs).also { groups.add(it) }
        duplicate = DuplicateSettingGroup("Duplicates", prefs).also { groups.add(it) }
        api = APISettingGroup("HTTP API", prefs).also { groups.add(it) }
    }

}

class WindowSettings: BaseSettings("iguanastin/MenagerieK/ui") {

    override val groups: List<SettingGroup>

    val ui: UISettingGroup

    init {
        groups = mutableListOf()

        ui = UISettingGroup("Window", prefs).also { groups.add(it) }
    }

}