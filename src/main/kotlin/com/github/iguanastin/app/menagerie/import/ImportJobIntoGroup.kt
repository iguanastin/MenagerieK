package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.FileItem
import com.github.iguanastin.app.menagerie.GroupItem
import com.github.iguanastin.app.menagerie.Menagerie
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty

class ImportJobIntoGroup private constructor(job: ImportJob, val group: ObjectProperty<GroupItem>, val groupTitle: String): ImportJob(job.file, null, null) {

    companion object {
        fun asGroup(jobs: Iterable<ImportJob>, groupTitle: String): List<ImportJob> {
            val group = SimpleObjectProperty<GroupItem>(null)

            val result = mutableListOf<ImportJob>()
            for (job in jobs) {
                result.add(ImportJobIntoGroup(job, group, groupTitle))
            }

            return result
        }
    }


    override fun import(menagerie: Menagerie): FileItem {
        var g = group.get()
        if (g == null) {
            g = GroupItem(menagerie.reserveItemID(), System.currentTimeMillis(), menagerie, groupTitle)
            menagerie.addItem(g)
        }

        val item = super.import(menagerie)
        g.addItem(item)

        return item
    }

}