package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.SimilarPair

class DatabaseCreateSimilar(val pair: SimilarPair<Item>) : DatabaseUpdate() {

    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseCreateSimilar", "INSERT INTO similar(id1, id2, similarity) VALUES (?, ?, ?);")

        ps.setInt(1, pair.obj1.id)
        ps.setInt(2, pair.obj2.id)
        ps.setDouble(3, pair.similarity)

        return ps.executeUpdate()
    }

    override fun toString(): String {
        return "CreateSimilar(id1=${pair.obj1.id}, id2=${pair.obj2.id}, similarity=${pair.similarity})"
    }

}