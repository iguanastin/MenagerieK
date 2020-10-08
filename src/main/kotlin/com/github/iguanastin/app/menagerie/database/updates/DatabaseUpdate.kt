package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import java.sql.Connection
import java.sql.PreparedStatement

abstract class DatabaseUpdate {

    abstract fun sync(db: MenagerieDatabase): Int

}