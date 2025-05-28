package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Menagerie

class ImportGroup(val title: String, var id: Int) {

    fun getRealGroup(menagerie: Menagerie): GroupItem {
        if (id < 0) {
            val g = menagerie.createGroup(title)
            id = g.id
        }
        return menagerie.getItem(id) as GroupItem
    }

}