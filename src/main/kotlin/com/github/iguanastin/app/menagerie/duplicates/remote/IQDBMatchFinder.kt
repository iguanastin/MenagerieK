package com.github.iguanastin.app.menagerie.duplicates.remote

import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.ImageItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Thumbnail
import javafx.embed.swing.SwingFXUtils
import org.apache.http.client.HttpResponseException
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.FormBodyPartBuilder
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.stream.Collectors
import javax.imageio.ImageIO


class IQDBMatchFinder(client: CloseableHttpClient? = null) : OnlineMatchFinder() {

    private val url = "https://iqdb.org"

    private var client: CloseableHttpClient? = client
        get() {
            if (field == null) field = HttpClientBuilder.create().build()
            return field
        }


    override fun findMatches(set: OnlineMatchSet) {
        if (set.isFinished) return

        val doc = post(set.item as FileItem)
        if (doc == null) {
            set.isFinished = true
            return
        }
        val matches = findMatchesInDocument(doc)

        set.matches = matches
        set.isFinished = true
    }

    private fun findMatchesInDocument(doc: Document): List<OnlineMatch> {
        val matches = mutableListOf<OnlineMatch>()

        var first = true
        for (element in doc.select("#pages > div")) {
            if (first) {
                first = false
                continue
            }

            val rows = element.select("tbody > tr")
            if (rows.first().selectFirst("th").ownText() !in arrayOf("Possible match", "Best match")) continue

            val sources = element.select("a").map { fixLink(it.attr("href")) }
            val thumbUrl = url + element.selectFirst("img")?.attr("src")

            val res = rows[rows.lastIndex - 1].selectFirst("td").ownText().substringBefore(' ').replace('×', 'x')
            val sim = rows[rows.lastIndex].selectFirst("td").ownText().substringBefore(' ')
            val details = "$res - $sim"

            for (source in sources) {
                val sourceName = source.substring(8).substringBefore('/')

                matches.add(OnlineMatch(source, thumbUrl, sourceName, details))
            }
        }

        return matches
    }

    private fun fixLink(url: String): String {
        return if (url.startsWith("//")) {
            "https:$url"
        } else {
            url
        }
    }

    private fun post(item: FileItem): Document? {
        if (item.file.extension.toLowerCase() !in ImageItem.fileExtensions) throw IllegalArgumentException("Cannot process this type of file")
        val img = acquireThumbnail(item) ?: return null

        ByteArrayOutputStream().use { baos ->
            ImageIO.write(img, item.file.extension, baos)

            val post = HttpPost(url)
            ByteArrayInputStream(baos.toByteArray()).use { bais ->
                post.entity = MultipartEntityBuilder.create().addPart(FormBodyPartBuilder.create("file", InputStreamBody(bais, item.file.name)).build()).build()
            }

            client!!.execute(post).use { response ->
                if (response.statusLine.statusCode == 200) {
                    response.entity.content.use { content ->
                        val result = BufferedReader(InputStreamReader(content)).lines().collect(Collectors.joining("\n"))
                        return Jsoup.parse(result)
                    }
                } else {
                    throw HttpResponseException(response.statusLine.statusCode, "Bad response code")
                }
            }
        }
    }

    private fun acquireThumbnail(item: FileItem): BufferedImage? {
        val latch = CountDownLatch(1)
        var thumb: Thumbnail? = null
        item.getThumbnail().want(this) {
            thumb = it
            latch.countDown()
        }
        latch.await()

        return processThumbnail(thumb!!)
    }

    private fun processThumbnail(thumb: Thumbnail): BufferedImage? {
        if (thumb.item is FileItem && thumb.item.file.extension.equals("gif", true)) {
            val img: BufferedImage = ImageIO.read(thumb.item.file)
            var width = img.width
            var height = img.height
            if (width > Item.thumbnailSize) {
                height = (height * Item.thumbnailSize / width).toInt()
                width = Item.thumbnailSize.toInt()
            }
            if (height > Item.thumbnailSize) {
                width = (width * Item.thumbnailSize / height).toInt()
                height = Item.thumbnailSize.toInt()
            }
            val image = BufferedImage(width, height, img.type)
            val g: Graphics2D = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(img, 0, 0, width, height, 0, 0, img.width, img.height, null)
            g.dispose()

            return image
        } else {
            return SwingFXUtils.fromFXImage(thumb.image, null)
        }
    }

    override fun close() {
        client?.close()
        super.close()
    }

}