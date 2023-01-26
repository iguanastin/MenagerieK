package com.github.iguanastin.app.menagerie.database.updates

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase

abstract class DatabaseUpdate {

    abstract fun sync(db: MenagerieDatabase): Int

}