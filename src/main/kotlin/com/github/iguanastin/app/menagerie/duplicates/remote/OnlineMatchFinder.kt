package com.github.iguanastin.app.menagerie.duplicates.remote

import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder

abstract class OnlineMatchFinder {

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

    open fun close() {
        client?.close()
        isClosed = true
    }

}