package com.github.iguanastin.app.menagerie.duplicates.remote

import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import java.io.Closeable

abstract class OnlineMatchFinder: Closeable {

    @Volatile
    var isClosed: Boolean = false
        private set

    var client: CloseableHttpClient? = null
        get() {
            if (field == null) field = HttpClientBuilder.create().build()
            return field
        }
        protected set


    abstract fun findMatches(set: OnlineMatchSet)

    override fun close() {
        client?.close()
        isClosed = true
    }

}