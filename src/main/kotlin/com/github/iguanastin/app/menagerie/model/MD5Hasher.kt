package com.github.iguanastin.app.menagerie.model

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

object MD5Hasher {

    private val digest: MessageDigest by lazy { MessageDigest.getInstance("md5") }

    fun hash(file: File): ByteArray {
        if (!file.exists()) throw NoSuchFileException(file)

        return digest.digest(Files.readAllBytes(file.toPath()))
    }

    fun hash(bytes: ByteArray): ByteArray {
        return digest.digest(bytes)
    }

}