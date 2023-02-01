package com.github.iguanastin.app.menagerie.duplicates.remote

data class OnlineMatch(
    val sourceUrl: String,
    val thumbUrl: String,
    val topText: String? = null,
    val bottomText: String? = null
)
