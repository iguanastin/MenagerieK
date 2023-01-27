package com.github.iguanastin.app.context

import com.github.iguanastin.app.menagerie.model.GroupItem

class GroupRenameEdit(val group: GroupItem, val newName: String): Edit() {

    val oldName: String = group.title

    override fun _perform(): Boolean {
        group.title = newName
        return true
    }

    override fun _undo(): Boolean {
        group.title = oldName
        return true
    }

    override fun toString(): String {
        return "Rename group: \"${oldName}\" -> \"$newName\""
    }

}