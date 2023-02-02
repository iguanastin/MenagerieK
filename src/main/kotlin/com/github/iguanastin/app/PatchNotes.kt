package com.github.iguanastin.app

object PatchNotes {

    private val notes = mutableMapOf<String, String>()

    init {
        notes["1.1.0"] = "- Auto tagger implemented for gelbooru, danbooru, and yande.re\n" +
                "- Purge temporary and unused tags from tag dialog\n" +
                "- Notify user of new version available on startup\n" +
                "- This patch notes dialog!"
    }

    fun get(version: String): String {
        return notes[version] ?: "No patch notes found for version $version"
    }

}