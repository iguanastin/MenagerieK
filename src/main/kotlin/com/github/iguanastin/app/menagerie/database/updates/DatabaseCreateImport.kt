package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.import.Import
import java.sql.Types

class DatabaseCreateImport(val import: Import): DatabaseUpdate() {

    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseCreateImport", "INSERT INTO imports(id, url, file, group_title, group_id, tags) VALUES (?,?,?,?,?,?);")

        ps.setInt(1, import.id)
        ps.setNString(2, import.url)
        ps.setNString(3, import.file.absolutePath)
        ps.setNString(4, import.group?.title)
        if (import.group != null) {
            ps.setInt(5, import.group.id)
        } else {
            ps.setNull(5, Types.INTEGER)
        }
        ps.setNClob(6, import.addTags?.joinToString(separator = ",") { t -> t.name }?.reader())

        return ps.executeUpdate()
    }

    override fun toString(): String {
        return "CreateImport(id=${import.id}, url=${import.url}, file=${import.file}, group_title=${import.group?.title}, group_id=${import.group?.id}, tags=${import.addTags})"
    }

}