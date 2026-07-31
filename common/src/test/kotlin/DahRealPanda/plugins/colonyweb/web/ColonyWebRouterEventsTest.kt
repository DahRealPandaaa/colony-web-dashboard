package DahRealPanda.plugins.colonyweb.web

import DahRealPanda.plugins.colonyweb.Config
import DahRealPanda.plugins.colonyweb.auth.AuthService
import DahRealPanda.plugins.colonyweb.facade.AuthFacade
import DahRealPanda.plugins.colonyweb.facade.ColonyFacade
import DahRealPanda.plugins.colonyweb.facade.EventsFacade
import DahRealPanda.plugins.colonyweb.facade.MapFacade
import DahRealPanda.plugins.colonyweb.facade.TextureFacade
import DahRealPanda.plugins.colonyweb.service.BuildingService
import DahRealPanda.plugins.colonyweb.service.CitizenService
import DahRealPanda.plugins.colonyweb.service.ColonyMapService
import DahRealPanda.plugins.colonyweb.service.CombatService
import DahRealPanda.plugins.colonyweb.service.ResearchService
import DahRealPanda.plugins.colonyweb.service.SseService
import DahRealPanda.plugins.colonyweb.service.TextureService
import DahRealPanda.plugins.colonyweb.support.ConfigReset
import DahRealPanda.plugins.colonyweb.support.withTempDir
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Path

/**
 * Regression cover for issue #38: the dashboard's live-update stream never delivered an event.
 *
 * NanoHTTPD compresses any response whose MIME type contains `text/`, and for a streaming
 * response it does that by wrapping the body in a `GZIPOutputStream` that is only finished when
 * the body ends. An event stream never ends, so every frame sat in the compressor and the browser
 * held a connection that produced nothing — which is what the reported `NS_BINDING_ABORTED`
 * looked like from the network tab, as the browser gave up and retried.
 *
 * That is only visible over a real socket: the frames are written correctly, and it is the
 * encoding of the response around them that swallows them. So this test speaks HTTP to a running
 * router rather than calling into it.
 */
class ColonyWebRouterEventsTest : DescribeSpec({

    beforeTest { ConfigReset.applyDefaults() }

    /** How long to wait for a frame that should arrive as soon as it is written. */
    val readTimeoutMs = 10_000

    fun freePort(): Int = ServerSocket(0).use { it.localPort }

    fun servicesFor(dir: Path, sse: SseService): ColonyWebServices {
        val auth = AuthService(dir)
        val maps = mockk<ColonyMapService>()
        return ColonyWebServices(
            authFacade = AuthFacade(auth),
            colonyFacade = ColonyFacade(
                BuildingService(), CitizenService(), CombatService(), ResearchService(), maps, auth
            ),
            eventsFacade = EventsFacade(sse, auth),
            textureFacade = TextureFacade(mockk<TextureService>()),
            mapFacade = MapFacade(maps, auth)
        )
    }

    /**
     * Opens `/events` the way a browser's EventSource does — including offering gzip, which is
     * what triggered the bug — and hands the live connection to [block].
     */
    fun openEventStream(sse: SseService, block: (headers: List<String>, body: BufferedReader) -> Unit) {
        withTempDir { dir ->
            Config.authEnabled = false
            val port = freePort()
            val router = ColonyWebRouter(servicesFor(dir, sse), "127.0.0.1", port)
            router.start()
            try {
                Socket("127.0.0.1", port).use { socket ->
                    socket.soTimeout = readTimeoutMs
                    socket.getOutputStream().write(
                        buildString {
                            append("GET /events HTTP/1.1\r\n")
                            append("Host: 127.0.0.1:$port\r\n")
                            append("Accept: text/event-stream\r\n")
                            append("Accept-Encoding: gzip, deflate\r\n")
                            append("Connection: keep-alive\r\n\r\n")
                        }.toByteArray(Charsets.UTF_8)
                    )
                    socket.getOutputStream().flush()

                    val body = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    val headers = mutableListOf<String>()
                    while (true) {
                        val line = body.readLine() ?: break
                        if (line.isEmpty()) break
                        headers.add(line)
                    }
                    block(headers, body)
                }
            } finally {
                router.stop()
            }
        }
    }

    /** Reads until [expected] shows up, or fails rather than hanging for the rest of the run. */
    fun readUntil(body: BufferedReader, expected: String): String {
        val seen = StringBuilder()
        try {
            while (!seen.contains(expected)) {
                val line = body.readLine() ?: break
                seen.append(line).append('\n')
            }
        } catch (e: SocketTimeoutException) {
            throw AssertionError(
                "waited ${readTimeoutMs}ms for \"$expected\" on the event stream and it never " +
                        "arrived. Received so far:\n$seen", e
            )
        }
        return seen.toString()
    }

    describe("GET /events") {
        it("answers with an event stream") {
            openEventStream(SseService()) { headers, _ ->
                headers.first() shouldContain "200 OK"
                headers.any { it.lowercase().startsWith("content-type: text/event-stream") } shouldBe true
            }
        }

        // The heart of the bug: gzip on a stream that never ends means the browser never sees a
        // byte of it.
        it("does not compress the stream, even though the browser offered to accept gzip") {
            openEventStream(SseService()) { headers, _ ->
                headers.any { it.lowercase().startsWith("content-encoding") } shouldBe false
            }
        }

        it("sends its opening comment straight away rather than buffering it") {
            openEventStream(SseService()) { _, body ->
                readUntil(body, ": connected") shouldContain ": connected"
            }
        }

        it("delivers a colony update as an SSE data frame") {
            val sse = SseService()
            openEventStream(sse) { _, body ->
                readUntil(body, ": connected")

                sse.broadcast("""{"type":"colony","id":1}""")

                val received = readUntil(body, "data:")
                received shouldContain "event: update"
                received shouldContain """data: {"type":"colony","id":1}"""
            }
        }

        it("delivers every update, not just the first") {
            val sse = SseService()
            openEventStream(sse) { _, body ->
                readUntil(body, ": connected")

                sse.broadcast("""{"type":"colonies"}""")
                readUntil(body, "colonies") shouldContain "colonies"

                sse.broadcast("""{"type":"colony","id":7}""")
                readUntil(body, "id\":7") shouldContain """"id":7"""
            }
        }

        it("counts the browser as a connected client while the stream is open") {
            val sse = SseService()
            openEventStream(sse) { _, body ->
                readUntil(body, ": connected")

                sse.clientCount shouldBe 1
            }
        }
    }

    // The decision itself, so the reason the stream is exempt does not get lost the next time
    // somebody looks at the compression settings.
    describe("which responses may be compressed") {
        it("never compresses an event stream") {
            ColonyWebRouter.compressible(ColonyWebRouter.MIME_EVENT_STREAM) shouldBe false
            ColonyWebRouter.compressible("text/event-stream;charset=UTF-8") shouldBe false
        }

        it("still allows compression for the documents the dashboard is made of") {
            ColonyWebRouter.compressible("application/json") shouldBe true
            ColonyWebRouter.compressible("text/html") shouldBe true
            ColonyWebRouter.compressible("application/javascript") shouldBe true
            ColonyWebRouter.compressible("image/png") shouldBe true
            ColonyWebRouter.compressible(null) shouldBe true
        }
    }
})
