package com.github.iguanastin.app.menagerie.duplicates.remote

import mu.KotlinLogging
import org.apache.http.client.HttpResponseException
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors

private val log = KotlinLogging.logger {}

object SourceTagParser {

    private const val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36"

    private val gelRegex = Regex("^https://gelbooru\\.com/.+page=post.+")
    private val danRegex = Regex("^https://danbooru\\.donmai\\.us/posts?/.+")
    private val yanRegex = Regex("^https://yande\\.re/post/.+")
    private val sanRegex = Regex("^https://chan\\.sankakucomplex\\.com/post/.+")
    private val e62Regex = Regex("^https://e621\\.net/post/.+")

    private const val artistPrefix = "a:"
    private const val characterPrefix = "c:"
    private const val copyrightPrefix = "s:"


    fun canGetTagsFrom(url: String): Boolean {
        return gelRegex.matches(url) || danRegex.matches(url) || yanRegex.matches(url) || sanRegex.matches(url) || e62Regex.matches(
            url
        )
    }

    fun getTags(url: String, client: CloseableHttpClient): List<String> {
        if (!canGetTagsFrom(url)) return emptyList()

        val doc = try {
            get(url, client)
        } catch (e: HttpResponseException) {
            if (e.statusCode == 451 || e.statusCode == 403) {
                log.warn("Failed to get tags from source: $url", e)
                return emptyList()
            }
            throw e
        }

        return if (gelRegex.matches(url)) {
            parseGelTags(doc)
        } else if (danRegex.matches(url)) {
            parseDanTags(doc)
        } else if (yanRegex.matches(url)) {
            parseYanTags(doc)
        } else if (sanRegex.matches(url)) {
            parseSanTags(doc)
        } else if (e62Regex.matches(url)) {
            parseE62Tags(doc)
        } else {
            emptyList()
        }
    }

    private fun processTag(
        tag: String,
        list: MutableList<String>,
        optionalPrefix: String? = null,
        splitParentheses: Boolean = false
    ) {
        val name = tag.trim().replace(" ", "_")
        list.add(name)

        if (optionalPrefix != null) list.add(optionalPrefix + name)
        if (splitParentheses && name.contains("_(")) {
            val one = name.substring(0, name.indexOf("_("))
            list.add(one)
            if (optionalPrefix != null) list.add(optionalPrefix + one)

            val two = name.substring(name.indexOf("_(") + 2, name.length - 1)
            list.add(two)
            if (optionalPrefix != null) list.add(optionalPrefix + two)
        }
    }

    private fun parseSanTags(doc: Document): List<String> {
        if (doc.selectFirst("#post-view") == null) return emptyList()

        val tags = mutableListOf<String>()
        doc.apply {
            select(".tag-type-artist > a").forEach {
                processTag(it.ownText(), tags, artistPrefix, true)
            }
            select(".tag-type-character > a").forEach {
                processTag(it.ownText(), tags, characterPrefix)
            }
            select(".tag-type-copyright > a").forEach {
                processTag(it.ownText(), tags, copyrightPrefix)
            }
            select(".tag-type-general > a").forEach {
                processTag(it.ownText(), tags)
            }
            select(".tag-type-medium > a").forEach {
                processTag(it.ownText(), tags)
            }
            select(".tag-type-meta > a").forEach {
                processTag(it.ownText(), tags)
            }
            select(".tag-type-genre > a").forEach {
                processTag(it.ownText(), tags)
            }
        }
        return tags
    }

    private fun parseYanTags(doc: Document): List<String> {
        if (doc.selectFirst("#image") == null) return emptyList()

        val tags = mutableListOf<String>()
        doc.apply {
            select(".tag-type-artist > a:nth-child(2)").forEach {
                processTag(it.ownText(), tags, artistPrefix, true)
            }
            select(".tag-type-character > a:nth-child(2)").forEach {
                processTag(it.ownText(), tags, characterPrefix)
            }
            select(".tag-type-copyright > a:nth-child(2)").forEach {
                processTag(it.ownText(), tags, copyrightPrefix)
            }
            select(".tag-type-general > a:nth-child(2)").forEach {
                processTag(it.ownText(), tags)
            }
        }
        return tags
    }

    private fun parseE62Tags(doc: Document): List<String> {
        if (doc.selectFirst("#image") == null) return emptyList()

        val tags = mutableListOf<String>()
        doc.apply {
            select(".artist-tag-list .search-tag").forEach {
                processTag(it.ownText(), tags, artistPrefix, true)
            }
            select(".character-tag-list .search-tag").forEach {
                processTag(it.ownText(), tags, characterPrefix)
            }
            select(".copyright-tag-list .search-tag").forEach {
                processTag(it.ownText(), tags, copyrightPrefix)
            }
            select(".species-tag-list .search-tag").forEach {
                processTag(it.ownText(), tags)
            }
            select(".general-tag-list .search-tag").forEach {
                processTag(it.ownText(), tags)
            }
            select(".meta-tag-list .search-tag").forEach {
                processTag(it.ownText(), tags)
            }
        }
        return tags
    }

    private fun parseGelTags(doc: Document): List<String> {
        if (doc.selectFirst("div.mainBodyPadding") == null) return emptyList()

        val tags = mutableListOf<String>()
        doc.apply {
            select(".tag-type-artist > a").forEach {
                processTag(it.ownText(), tags, artistPrefix, true)
            }
            select(".tag-type-character > a").forEach {
                processTag(it.ownText(), tags, characterPrefix)
            }
            select(".tag-type-copyright > a").forEach {
                processTag(it.ownText(), tags, copyrightPrefix)
            }
            select(".tag-type-general > a").forEach {
                processTag(it.ownText(), tags)
            }
            select(".tag-type-metadata > a").forEach {
                processTag(it.ownText(), tags)
            }
        }
        return tags
    }

    private fun parseDanTags(doc: Document): List<String> {
        if (doc.selectFirst("section.image-container") == null) return emptyList()

        val tags = mutableListOf<String>()
        doc.apply {
            select(".artist-tag-list .search-tag").forEach {
                processTag(it.ownText(), tags, artistPrefix, true)
            }
            select(".character-tag-list .search-tag").forEach {
                processTag(it.ownText(), tags, characterPrefix)
            }
            select(".copyright-tag-list .seach-tag").forEach {
                processTag(it.ownText(), tags, copyrightPrefix)
            }
            select(".general-tag-list .search-tag").forEach {
                processTag(it.ownText(), tags)
            }
            select(".meta-tag-list .search-tag").forEach {
                processTag(it.ownText(), tags)
            }
        }
        return tags
    }

    private fun get(url: String, client: CloseableHttpClient): Document {
        val get = HttpGet(url)
        get.addHeader("User-Agent", userAgent)
        client.execute(get).use { response ->
            if (response.statusLine.statusCode != 200) {
                throw HttpResponseException(response.statusLine.statusCode, response.statusLine.reasonPhrase)
            }

            response.entity.content.use { content ->
                val result = BufferedReader(InputStreamReader(content)).lines().collect(Collectors.joining("\n"))
                return Jsoup.parse(result)
            }
        }
    }

}