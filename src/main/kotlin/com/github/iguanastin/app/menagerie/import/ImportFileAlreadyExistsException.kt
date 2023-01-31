package com.github.iguanastin.app.menagerie.import

class ImportFileAlreadyExistsException: Exception {
    constructor(): super()
    constructor(message: String): super(message)
}