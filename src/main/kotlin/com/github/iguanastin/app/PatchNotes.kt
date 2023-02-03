package com.github.iguanastin.app

object PatchNotes {

    private val notes = mutableMapOf<String, String>()

    init {
        notes["1.1.0"] = "Features:\n" +
                "- Auto tagger implemented for gelbooru, danbooru, and yande.re, sankakucomplex\n" +
                "- Purge temporary and unused tags from tag dialog\n" +
                "- Notify user of new version available on startup\n" +
                "- This patch notes dialog!\n" +
                "- Send files to trash instead of permanently deleting them\n" +
                "\n" +
                "Bug fixes:\n" +
                "- Tag list dialog reacts to tag changes properly\n" +
                "- Fix unreachable HTTP API server\n" +
                "- Fix API server not reflecting changes to port setting"
    }

    fun get(version: String): String {
        return notes[version] ?: "No patch notes found for version $version"
    }

}