package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.ImageItem

class DatabaseCreateImage(item: ImageItem): DatabaseCreateFile(item) {

    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseCreateImage", "INSERT INTO images(id, no_similar, hist_a, hist_r, hist_g, hist_b) VALUES (?, ?, ?, ?, ?, ?);")

        item as ImageItem
        ps.setInt(1, item.id)
        ps.setBoolean(2, item.noSimilar)
        ps.setBinaryStream(3, item.histogram?.alphaToInputStream())
        ps.setBinaryStream(4, item.histogram?.redToInputStream())
        ps.setBinaryStream(5, item.histogram?.greenToInputStream())
        ps.setBinaryStream(6, item.histogram?.blueToInputStream())

        return super.sync(db) + ps.executeUpdate()
    }

    override fun toString(): String {
        return "CreateImage(item=${item.id}, noSimilar=${(item as ImageItem).noSimilar}, file=${item.file.absolutePath})"
    }

}