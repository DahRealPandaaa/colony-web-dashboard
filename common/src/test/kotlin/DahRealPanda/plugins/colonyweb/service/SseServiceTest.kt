package DahRealPanda.plugins.colonyweb.service

import com.google.gson.JsonObject
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [SseService] holds one listener per open browser tab, and those listeners are written to from
 * the scan thread while browsers come and go. The behaviour that matters is what happens to a
 * listener whose client has vanished: it must be dropped, and it must not stop the others from
 * being delivered to.
 */
class SseServiceTest : DescribeSpec({

    describe("delivery") {
        it("delivers a broadcast to a subscriber, tagged as an update") {
            val sse = SseService()
            val received = mutableListOf<SseEvent>()
            sse.subscribe { received.add(it) }

            sse.broadcast("{\"type\":\"colony\",\"id\":1}")

            received.single().data shouldBe "{\"type\":\"colony\",\"id\":1}"
            received.single().event shouldBe "update"
        }

        it("delivers to every subscriber") {
            val sse = SseService()
            val first = mutableListOf<SseEvent>()
            val second = mutableListOf<SseEvent>()
            sse.subscribe { first.add(it) }
            sse.subscribe { second.add(it) }

            sse.broadcast("{}")

            first.size shouldBe 1
            second.size shouldBe 1
        }

        it("serialises a JSON object before sending it") {
            val sse = SseService()
            val received = mutableListOf<SseEvent>()
            sse.subscribe { received.add(it) }

            sse.broadcast(JsonObject().apply { addProperty("type", "colony"); addProperty("id", 3) })

            received.single().data shouldBe "{\"type\":\"colony\",\"id\":3}"
        }

        // A heartbeat is an SSE comment, not an update: it exists to keep proxies from closing an
        // idle connection, and the browser must not treat it as data.
        it("sends a heartbeat as a comment frame with no event name") {
            val sse = SseService()
            val received = mutableListOf<SseEvent>()
            sse.subscribe { received.add(it) }

            sse.heartbeat()

            received.single().data shouldBe ": ping"
            received.single().event.shouldBeNull()
        }

        it("broadcasting with nobody listening is harmless") {
            val sse = SseService()

            sse.broadcast("{}")
            sse.heartbeat()

            sse.clientCount shouldBe 0
        }
    }

    describe("subscribers coming and going") {
        it("counts the connected clients") {
            val sse = SseService()
            sse.clientCount shouldBe 0

            val first = sse.subscribe { }
            sse.subscribe { }
            sse.clientCount shouldBe 2

            first()
            sse.clientCount shouldBe 1
        }

        it("stops delivering to a listener that unsubscribed") {
            val sse = SseService()
            val received = mutableListOf<SseEvent>()
            val unsubscribe = sse.subscribe { received.add(it) }

            unsubscribe()
            sse.broadcast("{}")

            received.size shouldBe 0
        }

        it("tolerates the same listener unsubscribing twice") {
            val sse = SseService()
            val unsubscribe = sse.subscribe { }

            unsubscribe()
            unsubscribe()

            sse.clientCount shouldBe 0
        }

        it("drops every listener on shutdown, so a restart does not inherit them") {
            val sse = SseService()
            val received = mutableListOf<SseEvent>()
            sse.subscribe { received.add(it) }

            sse.closeAll()
            sse.broadcast("{}")

            sse.clientCount shouldBe 0
            received.size shouldBe 0
        }
    }

    describe("a client that has gone away") {
        // A listener only throws once its socket is closed, and the browser that opened it is in no
        // position to unsubscribe. Keeping it would make every later broadcast pay its exception
        // cost, and the queue would grow for as long as the server runs.
        it("drops a listener that throws") {
            val sse = SseService()
            sse.subscribe { throw IllegalStateException("socket closed") }

            sse.broadcast("{}")

            sse.clientCount shouldBe 0
        }

        it("still delivers to the healthy listeners in the same broadcast") {
            val sse = SseService()
            val delivered = mutableListOf<String>()
            sse.subscribe { delivered.add("first") }
            sse.subscribe { throw IllegalStateException("socket closed") }
            sse.subscribe { delivered.add("third") }

            sse.broadcast("{}")

            delivered shouldContainExactly listOf("first", "third")
            sse.clientCount shouldBe 2
        }

        it("drops the failing listener only once, not on every later broadcast") {
            val sse = SseService()
            var calls = 0
            sse.subscribe { calls++; throw IllegalStateException("socket closed") }

            sse.broadcast("{}")
            sse.broadcast("{}")
            sse.broadcast("{}")

            calls shouldBe 1
        }
    }

    // Browsers connect and disconnect on the request threads while the scan scheduler broadcasts
    // from its own; the listener queue has to survive that without losing events or throwing.
    describe("concurrent use") {
        it("delivers every broadcast while clients are subscribing and unsubscribing") {
            val sse = SseService()
            val received = CopyOnWriteArrayList<SseEvent>()
            val steady = sse.subscribe { received.add(it) }
            val churnDone = CountDownLatch(1)

            val churn = Thread {
                repeat(200) {
                    val unsubscribe = sse.subscribe { }
                    unsubscribe()
                }
                churnDone.countDown()
            }
            churn.start()
            repeat(200) { sse.broadcast("{}") }
            churnDone.await(10, TimeUnit.SECONDS) shouldBe true
            churn.join()

            received.size shouldBe 200
            steady()
            sse.clientCount shouldBe 0
        }
    }
})
