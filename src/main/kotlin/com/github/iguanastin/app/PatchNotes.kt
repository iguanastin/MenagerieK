package com.github.iguanastin.app

object PatchNotes {

    private val notes = mutableMapOf<String, String>()

    init {
        notes["1.1.0"] = "Features:\n" +
                "- Auto tagger implemented for some online sources\n" +
                "- Tag aliasing for new tags\n" +
                "- Purge temporary and unused tags from tag dialog\n" +
                "- Notify user of new version available on startup\n" +
                "- This patch notes dialog!\n" +
                "- Send files to trash instead of permanently deleting them\n" +
                "\n" +
                "Bug fixes:\n" +
                "- Tag list dialog reacts to tag changes properly\n" +
                "- Fix unreachable HTTP API server\n" +
                "- Fix API server not reflecting changes to port setting\n" +
                "- Fix long tag names clipping the frequency of all tags"
        notes["1.2.0"] = "Features:\n" +
                "- Video playback support\n" +
                "- Video thumbnails\n" +
                "\n" +
                "Bug fixes:\n" +
                "- Fixed unhandled errors in filter parsing\n" +
                "- Handle Http 429 responses better\n" +
                "- Improve autotagger stability\n" +
                "- Support JFIF image format"
        notes["1.2.1"] = "Bug fixes:\n" +
                "- Fix 'is:____' special search term error when used correctly"
    }

    fun get(version: String): String {
        return notes[version] ?: "No patch notes found for version $version"
    }

}