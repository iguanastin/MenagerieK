package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.Histogram
import com.github.iguanastin.app.menagerie.ImageItem
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase

class DatabaseSetImageHistogram(private val itemID: Int, private val histogram: Histogram?): DatabaseUpdate() {

    constructor(item: ImageItem, histogram: Histogram? = item.histogram): this(item.id, histogram)


    override fun sync(db: MenagerieDatabase): Int {
        val ps = db.getPrepared("DatabaseSetImageHistogram", "UPDATE images SET hist_a=? hist_r=? hist_g=? hist_b=? WHERE id=?;")

        ps.setBinaryStream(1, histogram?.alphaToInputStream())
        ps.setBinaryStream(2, histogram?.redToInputStream())
        ps.setBinaryStream(3, histogram?.greenToInputStream())
        ps.setBinaryStream(4, histogram?.blueToInputStream())
        ps.setInt(5, itemID)

        return ps.executeUpdate()
    }

}