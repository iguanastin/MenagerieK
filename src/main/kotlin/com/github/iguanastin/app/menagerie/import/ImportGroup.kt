package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Menagerie
import tornadofx.*

class ImportGroup(val title: String, id: Int) {

    val id = intProperty(id)

    fun getRealGroup(menagerie: Menagerie): GroupItem {
        if (id < 0) {
            val g = menagerie.createGroup(title)
            id.value = g.id
        }

        var item = menagerie.getItem(id.value)
        if (item !is GroupItem) {
            id.value = menagerie.reserveImportTempGroupID()
            item = menagerie.createGroup(title)
            id.value = item.id
        }

        return item
    }

}