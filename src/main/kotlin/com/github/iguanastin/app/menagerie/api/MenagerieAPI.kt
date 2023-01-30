package com.github.iguanastin.app.menagerie.api

import com.github.iguanastin.app.context.MenagerieContext
import com.github.iguanastin.app.context.TagEdit
import com.github.iguanastin.app.menagerie.import.ImportJob
import com.github.iguanastin.app.menagerie.import.RemoteImportJob
import com.github.iguanastin.app.menagerie.model.*
import com.github.iguanastin.app.menagerie.search.FilterParseException
import com.github.iguanastin.app.menagerie.search.MenagerieSearch
import com.github.iguanastin.app.menagerie.search.filters.FilterFactory
import com.github.iguanastin.app.menagerie.search.filters.SearchFilter
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import mu.KotlinLogging
import org.h2.util.IOUtils
import org.json.JSONObject
import tornadofx.*
import java.io.*
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.activation.MimetypesFileTypeMap
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.math.ceil

private val log = KotlinLogging.logger {}

class MenagerieAPI(val context: MenagerieContext, var pageSize: Int) {

    companion object {
        private val HTTP_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
    }

    private var server: HttpServer? = null
    var port: Int = context.prefs.api.port.value
        private set


    /**
     * Starts the API server on the specific port
     *
     * @param port Port of the server
     */
    fun start(port: Int = this.port) {
        stop()

        log.info("Starting API server on port: $port")
        this.port = port
        try {
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()) {
                    thread(isDaemon = true, name = "API executor") { it.run() }
                }
                createContext("/").handler = HttpHandler { exchange: HttpExchange -> handleRequest(exchange) }
                start()
            }
        } catch (e: Exception) {
            log.error("Failed to start HTTP server on port: $port", e)
        }
        log.info("API server finished starting")

        //        try {
        //            // Set up the socket address
        //            InetSocketAddress address = new InetSocketAddress(port);
        //
        //            // Initialise the HTTPS server
        //            server = HttpsServer.create(address, 0);
        //            SSLContext sslContext = SSLContext.getInstance("TLS");
        //
        //            // Initialise the keystore
        //            char[] password = "simulator".toCharArray();
        //            KeyStore ks = KeyStore.getInstance("JKS");
        //            ks.load(getClass().getResourceAsStream("/lig.keystore"), password);
        //
        //            // Set up the key manager factory
        //            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        //            kmf.init(ks, password);
        //
        //            // Set up the trust manager factory
        //            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        //            tmf.init(ks);
        //
        //            // Set up the HTTPS context and parameters
        //            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        //            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
        //                public void configure(HttpsParameters params) {
        //                    try {
        //                        // Initialise the SSL context
        //                        SSLContext c = SSLContext.getDefault();
        //                        SSLEngine engine = c.createSSLEngine();
        //                        params.setNeedClientAuth(false);
        //                        params.setCipherSuites(engine.getEnabledCipherSuites());
        //                        params.setProtocols(engine.getEnabledProtocols());
        //
        //                        // Get the default parameters
        //                        SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
        //                        params.setSSLParameters(defaultSSLParameters);
        //                    } catch (Exception ex) {
        //                        ex.printStackTrace();
        //                    }
        //                }
        //            });
        //        } catch (Exception exception) {
        //            exception.printStackTrace();
        //        }
        //        server.createContext("/").handler = HttpHandler { exchange: HttpExchange -> handleRequest(exchange) }
        //        server.start()
    }

    fun stop() {
        log.info("Stopping API server...")
        server?.stop(0)
        server = null
    }

    /**
     * Attempts to route a server request. Sends an error response if the request cannot be routed to a handler.
     *
     * @param exchange Exchange data of the request
     */
    private fun handleRequest(exchange: HttpExchange) {
        try {
            val target = exchange.requestURI.path.substring(1).lowercase()
            log.info(exchange.remoteAddress.toString() + " requested: \"" + exchange.requestURI + "\"")
            when {
                target.startsWith("thumbs/") -> {
                    handleThumbnailRequest(exchange)
                }
                target == "search" -> {
                    handleSearchRequest(exchange)
                }
                target == "tags" -> {
                    handleTagsRequest(exchange)
                }
                target == "upload" -> {
                    handleUploadRequest(exchange)
                }
                target.startsWith("file/") -> {
                    handleFileRequest(exchange)
                }
                target.startsWith("edit_item/") -> {
                    handleEditItemRequest(exchange)
                }
                else -> {
                    sendErrorResponse(exchange, 404, "No such endpoint", "No endpoint found at specified path")
                }
            }
        } catch (e: Exception) {
            sendErrorResponse(exchange, 500, "Unexpected error", "Unexpected internal server error")
            e.printStackTrace()
        }
    }

    private fun handleEditItemRequest(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendErrorResponse(exchange, 400, "Invalid request method", "Method not allowed at this endpoint")
            return
        }

        // TODO allow multiple items to be edited with one call. Dash-separated ids?
        val idStr = exchange.requestURI.path.substring(11)
        val id: Int = try {
            idStr.toInt()
        } catch (e: NumberFormatException) {
            sendErrorResponse(exchange, 400, "Invalid ID", "ID must be an integer")
            return
        }

        // Get item
        val item: Item? = context.menagerie.getItem(id)
        if (item == null) {
            sendErrorResponse(exchange, 404, "404 not found", "No such item")
            return
        }
        val query = mapQuerys(exchange)
        val expandTags = "1".equals(query["expand_tags"], ignoreCase = true)
        val expandGroups = "1".equals(query["expand_groups"], ignoreCase = true)
        val tagEdit = query["tags"]
        if (tagEdit?.isNotBlank() == true) {
            val add = mutableListOf<Tag>()
            val remove = mutableListOf<Tag>()
            for (edit in tagEdit.split(Regex("\\s+"))) {
                if (edit.startsWith('-')) {
                    val tag = context.menagerie.getTag(edit.substring(1))
                    if (tag != null) remove.add(tag)
                } else {
                    val tag = context.menagerie.getTag(edit)
                    if (tag != null) add.add(tag)
                }
            }

            val edit = TagEdit(listOf(item), add, remove)
            if (!edit.perform()) {
                sendErrorResponse(exchange, 500, "Failed tag edit", "Edit was not applied")
            }
        }

        sendSimpleResponse(exchange, 200, encodeJSONItem(item, expandTags, expandGroups).toString())
    }

    private fun handleFileRequest(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendErrorResponse(exchange, 400, "Invalid request method", "Method not allowed at this endpoint")
            return
        }

        // Get and verify ID
        val idStr = exchange.requestURI.path.substring(6)
        val id: Int = try {
            idStr.toInt()
        } catch (e: NumberFormatException) {
            sendErrorResponse(exchange, 400, "Invalid ID", "ID must be an integer")
            return
        }

        // Get item
        val item: Item? = context.menagerie.getItem(id)
        if (item == null) {
            sendErrorResponse(exchange, 404, "404 not found", "No such item")
            return
        }

        // Ensure item has file
        if (item !is FileItem) {
            sendErrorResponse(exchange, 400, "Invalid request", "Item does not have a file")
            return
        }

        // Respond with item
        exchange.responseHeaders["Cache-Control"] = "max-age=86400"
        exchange.responseHeaders["ETag"] = "" + item.id
        exchange.responseHeaders["Last-Modified"] = HTTP_DATE_TIME_FORMATTER.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(item.file.lastModified()), ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("GMT")))
        sendFileResponse(exchange, 201, item.file)
    }

    /**
     * Handles posts to the upload endpoint
     *
     * @param exchange Exchange
     * @throws IOException When an IO exception occurs during the exchange
     */
    @Throws(IOException::class)
    private fun handleUploadRequest(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
            sendErrorResponse(exchange, 400, "Invalid request method", "Method not allowed at this endpoint")
            return
        }

        val query = mapQuerys(exchange)
        val filename = query["filename"]
        if (filename == null || filename.isEmpty()) {
            sendErrorResponse(exchange, 400, "Missing filename", "Filename parameter required")
            return
        }

        val folder = context.prefs.general.downloadFolder.value
        if (folder.isBlank()) {
            sendErrorResponse(exchange, 500, "Missing download folder", "Download folder is not specified")
            return
        } else {
            if (query.containsKey("url")) {
                val url = query["url"]
                if (url.isNullOrBlank()) {
                    sendErrorResponse(exchange, 400, "Blank URL", "URL is blank")
                    return
                } else {
                    if (filename.isNullOrBlank()) {
                        context.importer.enqueue(RemoteImportJob.intoDirectory(url, File(folder), incrementIfExists = true))
                    } else {
                        context.importer.enqueue(RemoteImportJob.intoFile(url, File(folder, filename)))
                    }
                }
            } else {
                val file = File(folder, filename)
                exchange.requestBody.use { body -> FileOutputStream(file).use { fos -> IOUtils.copy(body, fos) } }
                context.importer.enqueue(ImportJob(file))
            }
        }

        sendEmptyResponse(exchange, 201)
    }

    /**
     * Handles requests for the tags endpoint.
     *
     * @param exchange The exchange
     * @throws IOException When an IO exception occurs during the exchange
     */
    @Throws(IOException::class)
    private fun handleTagsRequest(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendErrorResponse(exchange, 400, "Invalid request method", "Method not allowed at this endpoint")
            return
        }
        val query = mapQuerys(exchange)
        val tags: MutableList<Tag> = ArrayList(context.menagerie.tags)
        if (query.containsKey("id")) {
            val id = query["id"]!!.toInt()
            tags.removeIf { tag: Tag -> tag.id != id }
        }
        if (query.containsKey("name")) {
            val name = query["name"]
            tags.removeIf { tag: Tag -> !tag.name.equals(name, true) }
        }
        if (query.containsKey("starts")) {
            val starts = query["starts"]!!.lowercase()
            tags.removeIf { tag: Tag -> !tag.name.startsWith(starts) }
        }
        if (query.containsKey("color")) {
            val color = query["color"]!!.lowercase()
            tags.removeIf { tag: Tag -> !color.equals(tag.color, ignoreCase = true) }
        }
        val json = JSONObject()
        tags.forEach { tag: Tag ->
            val j = JSONObject()
            j.put("id", tag.id)
            j.put("name", tag.name)
            j.put("color", tag.color)
            // TODO: j.put("notes", tag.notes)
            j.put("frequency", tag.frequency)
            json.append("tags", j)
        }
        exchange.setAttribute("Content-Type", "application/json")
        sendSimpleResponse(exchange, 200, json.toString())
    }

    /**
     * Handles requests for the search endpoint
     *
     * @param exchange The exchange
     * @throws IOException When an IO exception occurs during the exchange
     */
    @Throws(IOException::class)
    private fun handleSearchRequest(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendErrorResponse(exchange, 400, "Invalid request method", "Method not allowed at this endpoint")
            return
        }
        val query = mapQuerys(exchange)
        val terms = query["terms"] ?: ""
        val page = (query["page"] ?: "0").toInt()
        val descending = "1".equals(query["desc"], ignoreCase = true)
        val ungrouped = "1".equals(query["ungroup"], ignoreCase = true)
        val expandTags = "1".equals(query["expand_tags"], ignoreCase = true)
        val expandGroups = "1".equals(query["expand_groups"], ignoreCase = true)

        val filters: MutableList<SearchFilter> = try {
            FilterFactory.parseFilters(terms, context.menagerie, !ungrouped)
        } catch (e: FilterParseException) {
            sendErrorResponse(exchange, 400, "Failed to parse filters", e.message ?: "No message")
            return
        }

        val search = MenagerieSearch(context.menagerie, terms, descending, false, filters)
        search.bindTo(observableListOf())

        val total: Int = search.items!!.size
        val count = Integer.min(pageSize, total - page * pageSize)
        val json = JSONObject()
        json.put("page", page).put("count", count).put("total", total).put("page_size", pageSize).put("page_count", ceil(total.toDouble() / pageSize).toInt())
        for (i in page * pageSize until page * pageSize + count) {
            json.append("items", encodeJSONItem(search.items!![i], expandTags, expandGroups))
        }
        search.close()

        exchange.setAttribute("Content-Type", "application/json")
        sendSimpleResponse(exchange, 200, json.toString())
    }

    /**
     * Handles requests for the thumbnail endpoint
     *
     * @param exchange The exchange
     * @throws IOException When an IO exception occurs during the exchange
     */
    @Throws(IOException::class)
    private fun handleThumbnailRequest(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendErrorResponse(exchange, 400, "Invalid request method", "Method not allowed at this endpoint")
            return
        }
        val idStr = exchange.requestURI.path.substring(8)
        try {
            val id = idStr.toInt()
            val item: Item? = context.menagerie.getItem(id)
            if (item != null) {
                val thumb: Thumbnail = item.getThumbnail()
                val cdl = CountDownLatch(1)
                thumb.want(this) {
                    cdl.countDown()
                }

                if (cdl.await(5, TimeUnit.SECONDS)) {
                    var extension = "png"
                    if (item is FileItem) {
                        extension = item.file.extension
                        exchange.responseHeaders["Cache-Control"] = "max-age=86400"
                        exchange.responseHeaders["ETag"] = "" + item.id
                        exchange.responseHeaders["Last-Modified"] = HTTP_DATE_TIME_FORMATTER.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(item.file.lastModified()), ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("GMT")))
                    }
                    sendImageResponse(exchange, 200, thumb.image, extension)
                } else {
                    sendErrorResponse(exchange, 500, "Thumbnail load timeout", "Failed to load the thumbnail in the given time")
                }

                thumb.unWant(this)
            } else {
                sendErrorResponse(exchange, 404, "404 not found", "No such item with id: $id")
                return
            }
        } catch (e: NumberFormatException) {
            sendErrorResponse(exchange, 400, "Invalid query", "Invalid id format: $idStr")
        } catch (e: InterruptedException) {
            sendErrorResponse(exchange, 500, "Thumbnail error", "Failed to create/load thumbnail")
        }
    }

    /**
     * Sends a bodyless response to the client
     *
     * @param exchange Exchange
     * @param httpCode HTTP response code
     * @throws IOException When an IO exception occurs during the exchange
     */
    @Throws(IOException::class)
    private fun sendEmptyResponse(exchange: HttpExchange, httpCode: Int) {
        exchange.requestBody.close()
        exchange.sendResponseHeaders(httpCode, 0)
        exchange.responseBody.close()
    }

    /**
     * Sends a simple text response to the client
     *
     * @param exchange Exchange
     * @param httpCode HTTP response code
     * @param response Text content of the response
     * @throws IOException When an IO exception occurs during the exchange
     */
    @Throws(IOException::class)
    private fun sendSimpleResponse(exchange: HttpExchange, httpCode: Int, response: String) {
        exchange.requestBody.close()
        exchange.sendResponseHeaders(httpCode, response.toByteArray().size.toLong())
        val os = exchange.responseBody
        os.write(response.toByteArray())
        os.close()
    }

    /**
     * Sends a pretty, HTML error response
     *
     * @param exchange Exchange
     * @param httpCode HTTP response code
     * @param title    Title of the page
     * @param message  Message
     * @throws IOException When an IO exception occurs during the exchange
     */
    @Throws(IOException::class)
    private fun sendErrorResponse(exchange: HttpExchange, httpCode: Int = 400, title: String, message: String) {
        exchange.responseHeaders["Content-Type"] = "text/html"
        sendSimpleResponse(exchange, httpCode, "<!DOCTYPE html><html><head><title>" + httpCode + ": " + title + "</title></head><body style=\"text-align: center; margin: 5em; line-height: 1.5em;\"><h1>Response Code " + httpCode + "</h1><h2>" + title + "</h2><p>" + message + "<br>URI: " + exchange.requestURI + "<br>" + Date() + "</p></body></html>")
    }

    /**
     * Sends an image as a response for an exchange
     *
     * @param exchange The exchange
     * @param jfxImage Image to send to the client
     * @throws IOException When an IO exception occurs during the exchange
     */
    private fun sendImageResponse(exchange: HttpExchange, httpCode: Int = 201, jfxImage: Image?, extension: String) {
        exchange.requestBody.close()
        try {
            val bImage = SwingFXUtils.fromFXImage(jfxImage, null)
            val baos = ByteArrayOutputStream()
            ImageIO.write(bImage, extension, baos)
            baos.close()
            exchange.sendResponseHeaders(httpCode, baos.size().toLong())
            val os = exchange.responseBody
            exchange.setAttribute("Content-Type", MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(extension))
            os.write(baos.toByteArray())
            os.close()
        } catch (e: Exception) {
            sendErrorResponse(exchange, 500, "Transfer error", "Unexpected error sending image")
        }
    }

    private fun sendFileResponse(exchange: HttpExchange, httpCode: Int = 201, file: File) {
        exchange.responseHeaders["Content-Type"] = Files.probeContentType(file.toPath())
        exchange.sendResponseHeaders(httpCode, file.length())
        exchange.responseBody.use { os -> Files.copy(file.toPath(), os) }
    }

    /**
     * Encodes the metadata of a Menagerie item as JSON for a search response
     *
     * @param item         Menagerie item to encode
     * @param expandTags   Expand tag information for this item
     * @param expandGroups Expand group elements for this item
     * @return A JSON representation of the item metadata
     */
    private fun encodeJSONItem(item: Item, expandTags: Boolean, expandGroups: Boolean): JSONObject {
        val json = JSONObject()

        json.put("id", item.id)
        json.put("thumbnail", "/thumbs/" + item.id)
        json.put("type", when (item) {
            is ImageItem -> "image"
            is VideoItem -> "video"
            is FileItem -> "file"
            is GroupItem -> "group"
            else -> "unknown"
        })
        json.put("added", item.added)
        item.tags.forEach { tag ->
            if (expandTags) {
                val j = JSONObject()
                j.put("id", tag.id)
                j.put("name", tag.name)
                j.put("color", tag.color)
                // TODO: j.put("notes", tag.notes)
                j.put("frequency", tag.frequency)
                json.append("tags", j)
            } else {
                json.append("tags", tag.id)
            }
        }
        if (item is FileItem) {
            json.put("md5", item.md5)
            json.put("path", item.file.absolutePath)
            json.put("file", "/file/" + item.id)
            if (item.elementOf != null) {
                json.put("element_of", item.elementOf!!.id)
                json.put("element_index", item.elementOf!!.items.indexOf(item))
            }
        } else if (item is GroupItem) {
            item.items.forEach { element ->
                if (expandGroups) {
                    json.append("elements", encodeJSONItem(element, expandTags, false))
                } else {
                    json.append("elements", element.id)
                }
            }
            json.put("title", item.title)
        }
        return json
    }

    /**
     * Cleanly maps a query from a client into a map. A valueless parameter will be given a value of "1"
     *
     * @param exchange Exchange
     * @return A map of parameters and values
     */
    private fun mapQuerys(exchange: HttpExchange): Map<String, String> {
        val query: MutableMap<String, String> = HashMap()
        val queryStr = exchange.requestURI.rawQuery
        if (queryStr != null) {
            val split = queryStr.split("&").toTypedArray()
            for (s in split) {
                var param = s
                try {
                    param = URLDecoder.decode(s, StandardCharsets.UTF_8.toString())
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
                if (param.isNotEmpty()) {
                    if (param.contains("=")) {
                        query[param.substring(0, s.indexOf('='))] = param.substring(param.indexOf('=') + 1)
                    } else {
                        query[param] = "1"
                    }
                }
            }
        }
        return query
    }

}