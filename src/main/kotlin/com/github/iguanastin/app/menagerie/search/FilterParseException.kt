package com.github.iguanastin.app.menagerie.search

@Suppress("unused")
class FilterParseException : Exception {
    constructor() : super()
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
    constructor(cause: Throwable) : super(cause)
    constructor(msg: String, cause: Throwable, enableSuppression: Boolean, writableStackTrace: Boolean) : super(msg, cause, enableSuppression, writableStackTrace)
}