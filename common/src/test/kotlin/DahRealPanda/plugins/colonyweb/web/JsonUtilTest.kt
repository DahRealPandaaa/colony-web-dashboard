package DahRealPanda.plugins.colonyweb.web

import DahRealPanda.plugins.colonyweb.api.response.AuthSessionResponse
import DahRealPanda.plugins.colonyweb.api.response.ErrorResponse
import DahRealPanda.plugins.colonyweb.model.ColonySummary
import com.google.gson.JsonParser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Every API response goes out through [JsonUtil], so its configuration is part of the wire
 * contract the dashboard is written against. In particular Gson is left on its defaults, which
 * means null fields are omitted rather than serialised as `null` — the browser code relies on
 * that, and a stray `serializeNulls()` would change every payload at once.
 */
class JsonUtilTest : DescribeSpec({

    describe("serialising API payloads") {
        it("writes a colony summary with every field the dashboard reads") {
            val json = JsonParser.parseString(
                JsonUtil.toJson(
                    ColonySummary(
                        id = 3, name = "Test Colony", dimension = "minecraft:overworld",
                        owner = "Ann", x = 10, y = 64, z = -20,
                        buildingCount = 5, builderCount = 2, activeWorkOrders = 1
                    )
                )
            ).asJsonObject

            json["id"].asInt shouldBe 3
            json["name"].asString shouldBe "Test Colony"
            json["dimension"].asString shouldBe "minecraft:overworld"
            json["owner"].asString shouldBe "Ann"
            json["z"].asInt shouldBe -20
            json["buildingCount"].asInt shouldBe 5
            json["builderCount"].asInt shouldBe 2
            json["activeWorkOrders"].asInt shouldBe 1
        }

        it("omits null fields instead of writing them out") {
            val json = JsonUtil.toJson(AuthSessionResponse(authenticated = false, authEnabled = true, user = null))

            json shouldContain "\"authenticated\":false"
            json shouldContain "\"authEnabled\":true"
            json shouldNotContain "user"
        }

        it("serialises an empty list as an empty array rather than dropping it") {
            JsonUtil.toJson(emptyList<ColonySummary>()) shouldBe "[]"
        }

        it("serialises an empty object") {
            JsonUtil.toJson(Any()) shouldBe "{}"
        }
    }

    describe("escaping") {
        it("escapes quotes and backslashes in a colony name") {
            val name = "Ann's \"Best\" \\ Colony"
            val json = JsonUtil.toJson(ErrorResponse(name))

            JsonParser.parseString(json).asJsonObject["error"].asString shouldBe name
        }

        it("escapes a newline rather than emitting a raw one") {
            JsonUtil.toJson(ErrorResponse("line one\nline two")) shouldNotContain "\n"
        }

        // Colony and citizen names are player-supplied and reach the browser inside a script
        // context, so Gson's HTML escaping is load-bearing rather than cosmetic.
        it("escapes angle brackets so a colony name cannot close a script tag") {
            val json = JsonUtil.toJson(ErrorResponse("</script><img src=x onerror=alert(1)>"))

            json shouldNotContain "</script>"
            json shouldNotContain "<img"
            JsonParser.parseString(json).asJsonObject["error"].asString shouldBe
                    "</script><img src=x onerror=alert(1)>"
        }

        it("round-trips non-ASCII names") {
            val name = "Vílagë ⛏"
            JsonParser.parseString(JsonUtil.toJson(ErrorResponse(name))).asJsonObject["error"].asString shouldBe name
        }
    }
})
