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
    val hidden: HiddenSettingGroup
    val tags: TagSettingGroup

    override val groups: List<SettingGroup>

    init {
        groups = mutableListOf()

        general = GeneralSettingGroup(prefs).also { groups.add(it) }
        database = DatabaseSettingGroup(prefs).also { groups.add(it) }
        duplicate = DuplicateSettingGroup(prefs).also { groups.add(it) }
        api = APISettingGroup(prefs).also { groups.add(it) }
        tags = TagSettingGroup(prefs).also { groups.add(it) }
        hidden = HiddenSettingGroup(prefs).also { groups.add(it) }
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