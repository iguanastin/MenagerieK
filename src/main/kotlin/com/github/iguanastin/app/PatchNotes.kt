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
        notes["1.2.2"] = "Bug Fixes:\n" +
                "- Incorrect pan bounds on image previews (#9)\n" +
                "- TEMP tag label changing to tag count in some circumstances (#4)"
        notes["1.2.3"] = "Features:\n" +
                "- Similar items are now kept through a restart (#8)\n" +
                "- Imports are now kept through a restart (#14)\n" +
                "- Text file previewing (#16)\n" +
                "- Faster image imports\n" +
                "- File extensions are automatically fixed for web imports where possible\n" +
                "- Cached image previews for slightly snappier experience\n" +
                "- Added a menu bar at the top (#22)\n" +
                "Bug fixes:\n" +
                "- Old file hashes did not match new file hashes (#20)\n" +
                "- Fixed grid rows breaking and showing the wrong items (#12)"
    }

    fun get(version: String): String {
        return notes[version] ?: "No patch notes found for version $version"
    }

}