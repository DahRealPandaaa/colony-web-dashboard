package DahRealPanda.plugins.colonyweb.web

import DahRealPanda.plugins.colonyweb.facade.AuthFacade
import DahRealPanda.plugins.colonyweb.facade.ColonyFacade
import DahRealPanda.plugins.colonyweb.facade.EventsFacade
import DahRealPanda.plugins.colonyweb.facade.MapFacade
import DahRealPanda.plugins.colonyweb.facade.TextureFacade
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.ByteArrayInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

data class ColonyWebServices(
    val authFacade: AuthFacade,
    val colonyFacade: ColonyFacade,
    val eventsFacade: EventsFacade,
    val textureFacade: TextureFacade,
    val mapFacade: MapFacade
)

class ColonyWebRouter(
    private val services: ColonyWebServices,
    bindAddress: String,
    port: Int
) : NanoHTTPD(bindAddress, port) {

    // ---- NanoHTTPD entry point ----

    override fun serve(session: IHTTPSession): Response {
        return try {
            dispatch(session)
        } catch (e: Exception) {
            val msg = e.message?.replace("\"", "\\\"") ?: "internal error"
            json(Status.INTERNAL_ERROR, """{"error":"$msg"}""")
        }
    }

    // ---- Routing ----

    private fun dispatch(session: IHTTPSession): Response {
        val path = session.uri.removePrefix("/").substringBefore("?")
        val segments = path.split("/").filter { it.isNotEmpty() }
        val method = session.method

        return when {
            method == Method.GET && segments.firstOrNull() == "auth"       -> handleAuthGet(session, segments)
            method == Method.POST && segments.firstOrNull() == "auth"      -> handleAuthPost(session, segments)
            segments.firstOrNull() == "api"                                -> handleApi(session, segments)
            method == Method.GET && segments.size == 1 && segments[0] == "events" -> handleEvents(session)
            method == Method.GET && isMapRequest(segments)                 -> handleMap(session, segments[1])
            method == Method.GET && isTextureRequest(segments)             -> handleTexture(session, segments[1])
            method == Method.GET                                           -> handleStatic(session)
            else -> json(Status.NOT_FOUND, """{"error":"Not found"}""")
        }
    }

    private fun isMapRequest(s: List<String>) = s.size == 2 && s[0] == "map" && s[1].endsWith(".png")
    private fun isTextureRequest(s: List<String>) = s.size == 2 && s[0] == "textures" && s[1].endsWith(".png")

    // ---- Helpers ----

    private fun sessionToken(session: IHTTPSession): String? =
        session.cookies.read("colonyweb_session")

    private fun setSessionCookie(response: Response, token: String, maxAge: Long) {
        response.addHeader("Set-Cookie",
            "colonyweb_session=$token; Path=/; HttpOnly; SameSite=Lax; Max-Age=$maxAge")
    }

    private fun clearSessionCookie(response: Response) {
        response.addHeader("Set-Cookie",
            "colonyweb_session=; Path=/; Max-Age=0")
    }

    private fun json(status: Status, body: String): Response {
        val resp = newFixedLengthResponse(status, "application/json", body)
        resp.addHeader("Cache-Control", "no-store")
        return resp
    }

    private fun jsonOk(body: String) = json(Status.OK, body)

    // ---- Auth: GET ----

    private fun handleAuthGet(session: IHTTPSession, segments: List<String>): Response {
        if (segments.size == 2 && segments[1] == "me") {
            val token = sessionToken(session)
            val resp = services.authFacade.checkSession(token)
            return jsonOk(JsonUtil.toJson(resp))
        }
        return json(Status.NOT_FOUND, """{"error":"Not found"}""")
    }

    // ---- Auth: POST ----

    private fun handleAuthPost(session: IHTTPSession, segments: List<String>): Response {
        try {
            session.parseBody(HashMap())
        } catch (e: Exception) {
            return json(Status.BAD_REQUEST, """{"error":"Could not read the request body"}""")
        }
        return when (segments.getOrNull(1)) {
            "login"  -> handleLogin(session)
            "logout" -> handleLogout(session)
            else     -> json(Status.NOT_FOUND, """{"error":"Not found"}""")
        }
    }

    private fun handleLogin(session: IHTTPSession): Response {
        val code = session.parms["code"] ?: ""
        val (token, loginResp) = services.authFacade.login(code)
        val status = if (loginResp.error != null) Status.FORBIDDEN else Status.OK
        val resp = json(status, JsonUtil.toJson(loginResp))
        if (token != null) setSessionCookie(resp, token, 7 * 24 * 3600)
        return resp
    }

    private fun handleLogout(session: IHTTPSession): Response {
        val token = sessionToken(session)
        services.authFacade.logout(token)
        val resp = jsonOk("{}")
        clearSessionCookie(resp)
        return resp
    }

    // ---- Colony API ----

    private fun handleApi(session: IHTTPSession, segments: List<String>): Response {
        val token = sessionToken(session)

        return when {
            // GET /api/colonies
            segments.size == 2 && segments[1] == "colonies" ->
                jsonOk(services.colonyFacade.listColonies(token))

            // GET /api/colony/{id}
            segments.size == 3 && segments[1] == "colony" ->
                jsonOk(services.colonyFacade.snapshot(idParam(segments[2]), token))

            // GET /api/colony/{id}/citizens
            segments.size == 4 && segments[1] == "colony" && segments[3] == "citizens" ->
                jsonOk(services.colonyFacade.citizens(idParam(segments[2]), token))

            // GET /api/colony/{id}/research
            segments.size == 4 && segments[1] == "colony" && segments[3] == "research" ->
                jsonOk(services.colonyFacade.research(idParam(segments[2]), token))

            // GET /api/colony/{id}/combat
            segments.size == 4 && segments[1] == "colony" && segments[3] == "combat" ->
                jsonOk(services.colonyFacade.combat(idParam(segments[2]), token))

            // GET /api/colony/{id}/map
            segments.size == 4 && segments[1] == "colony" && segments[3] == "map" ->
                jsonOk(services.colonyFacade.mapInfo(idParam(segments[2]), token))

            // GET /api/colony/{id}/citizen/{cid}
            segments.size == 5 && segments[1] == "colony" && segments[3] == "citizen" ->
                jsonOk(services.colonyFacade.citizenDetail(idParam(segments[2]), idParam(segments[4]), token))

            else -> json(Status.NOT_FOUND, """{"error":"Not found"}""")
        }
    }

    private fun idParam(s: String): Int = s.toIntOrNull() ?: -1

    // ---- Server-Sent Events (callback-based, no coroutines dependency) ----

    private fun handleEvents(session: IHTTPSession): Response {
        val token = sessionToken(session)
        val user = services.eventsFacade.authenticate(token)

        if (services.eventsFacade.isAuthEnabled() && user == null) {
            return json(Status.UNAUTHORIZED, """{"error":"Not signed in"}""")
        }

        val pipedOut = PipedOutputStream()
        val pipedIn = PipedInputStream(pipedOut, 4096)

        val unsubscribe = services.eventsFacade.subscribe { event ->
            val frame = buildString {
                if (event.event != null) append("event: ${event.event}\n")
                append("data: ${event.data}\n\n")
            }
            try {
                pipedOut.write(frame.toByteArray(Charsets.UTF_8))
                pipedOut.flush()
            } catch (_: Exception) { /* client disconnected */ }
        }

        val thread = Thread {
            try {
                pipedOut.write(": connected\n\n".toByteArray(Charsets.UTF_8))
                pipedOut.flush()
                // Keep the connection open with periodic heartbeats.
                while (!Thread.interrupted()) {
                    Thread.sleep(30_000)
                    try {
                        pipedOut.write(": heartbeat\n\n".toByteArray(Charsets.UTF_8))
                        pipedOut.flush()
                    } catch (_: Exception) { break }
                }
            } catch (_: Exception) { /* client disconnected */ }
            finally {
                unsubscribe()
                try { pipedOut.close() } catch (_: Exception) {}
            }
        }
        thread.isDaemon = true
        thread.name = "colonyweb-sse"
        thread.start()

        val resp = newChunkedResponse(Status.OK, "text/event-stream", pipedIn)
        resp.addHeader("Cache-Control", "no-cache, no-store")
        resp.addHeader("Connection", "keep-alive")
        return resp
    }

    // ---- Map PNG ----

    private fun handleMap(session: IHTTPSession, filename: String): Response {
        val colonyId = filename.removeSuffix(".png").toIntOrNull() ?: -1
        val token = sessionToken(session)
        val png = services.mapFacade.getMapImage(colonyId, token)
        return if (png != null) {
            val resp = newFixedLengthResponse(Status.OK, "image/png",
                ByteArrayInputStream(png), png.size.toLong())
            resp.addHeader("Cache-Control", "public, max-age=30")
            resp
        } else {
            newFixedLengthResponse(Status.NOT_FOUND, "image/png",
                ByteArrayInputStream(ByteArray(0)), 0)
        }
    }

    // ---- Texture PNG ----

    private fun handleTexture(session: IHTTPSession, filename: String): Response {
        // The client percent-encodes the key (Domum Ornamentum variants carry a "#"), and NanoHTTPD
        // already percent-decodes session.uri before serve() sees it, so `filename` is the decoded
        // key. Decoding again here would be a second pass over data that is no longer encoded.
        val key = filename.removeSuffix(".png")
        val png = services.textureFacade.getTexture(key)
        return if (png != null) {
            val resp = newFixedLengthResponse(Status.OK, "image/png",
                ByteArrayInputStream(png), png.size.toLong())
            resp.addHeader("Cache-Control", "public, max-age=604800")
            resp
        } else {
            newFixedLengthResponse(Status.NOT_FOUND, "image/png",
                ByteArrayInputStream(ByteArray(0)), 0)
        }
    }

    // ---- Static / SPA fallback ----

    private fun handleStatic(session: IHTTPSession): Response {
        val path = session.uri.removePrefix("/").let { p -> if (p.isEmpty()) "index.html" else p }
        val resourcePath = "/webroot/$path"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
        if (stream != null) {
            val bytes = stream.use { it.readAllBytes() }
            val mime = when {
                path.endsWith(".js") -> "application/javascript"
                path.endsWith(".css") -> "text/css"
                path.endsWith(".html") || !path.contains(".") -> "text/html"
                path.endsWith(".svg") -> "image/svg+xml"
                path.endsWith(".png") -> "image/png"
                else -> "application/octet-stream"
            }
            val resp = newFixedLengthResponse(Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
            if (mime in setOf("image/png", "image/svg+xml", "application/javascript", "text/css")) {
                resp.addHeader("Cache-Control", "public, max-age=3600, immutable")
            }
            return resp
        }
        // SPA fallback: serve index.html for client-side routing
        val indexStream = javaClass.classLoader.getResourceAsStream("/webroot/index.html")
        return if (indexStream != null) {
            val bytes = indexStream.use { it.readAllBytes() }
            newFixedLengthResponse(Status.OK, "text/html", ByteArrayInputStream(bytes), bytes.size.toLong())
        } else {
            newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }
}
