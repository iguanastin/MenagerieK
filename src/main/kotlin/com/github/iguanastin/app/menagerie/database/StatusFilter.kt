package com.github.iguanastin.app.menagerie.database

class StatusFilter<T>(val callback: ((T) -> Unit)? = null, val interval: Long = 100) {

    private var lastSend: Long = 0
    var markTime: Long = -1

    fun trySend(msgGen: () -> T): Boolean {
        if (System.currentTimeMillis() - lastSend > interval) {
            force(msgGen())
            return true
        }
        return false
    }

    fun force(msg: T, mark: Boolean = false) {
        callback?.invoke(msg)
        lastSend = System.currentTimeMillis()

        if (mark) markNow()
    }

    fun markNow() {
        markTime = System.currentTimeMillis()
    }

    fun secondsSinceMark(): Double {
        return (System.currentTimeMillis() - markTime) / 1000.0
    }

    fun sinceMarkStr(): String {
        return "%.2fs".format(secondsSinceMark())
    }

}