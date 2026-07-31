package DahRealPanda.plugins.colonyweb.repository

import DahRealPanda.plugins.colonyweb.auth.StoredSession
import DahRealPanda.plugins.colonyweb.auth.WebUser
import DahRealPanda.plugins.colonyweb.support.withTempDir
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/**
 * `auth.json` is the mod's only piece of persistent state, and it is rewritten on every sync,
 * login and logout. A read that throws would take the server's startup with it, so this store is
 * written to fail into "no accounts" instead — which is exactly the behaviour worth pinning.
 */
class AuthStoreTest : DescribeSpec({

    fun userNamed(name: String) = WebUser(
        uuid = "00000000-0000-0000-0000-00000000000$name".take(36),
        name = name,
        colonies = mutableSetOf(1, 2),
        granted = mutableSetOf(7),
        admin = true,
        syncedAt = 1234L,
        sessions = mutableListOf(StoredSession("hash", 1L, 2L))
    )

    describe("round trip") {
        it("reads back everything it wrote") {
            withTempDir { dir ->
                val file = dir.resolve("auth.json")
                val ann = userNamed("a")
                AuthStore(file).save(mapOf(ann.uuid to ann))

                val loaded = AuthStore(file).load()[ann.uuid].shouldNotBeNull()
                loaded.name shouldBe "a"
                loaded.admin shouldBe true
                loaded.syncedAt shouldBe 1234L
                loaded.colonies shouldContainExactlyInAnyOrder listOf(1, 2)
                loaded.granted shouldContainExactlyInAnyOrder listOf(7)
                loaded.sessions.single().tokenHash shouldBe "hash"
                loaded.sessions.single().expiresAt shouldBe 2L
            }
        }

        it("writes a file that is valid, readable JSON") {
            withTempDir { dir ->
                val file = dir.resolve("auth.json")
                val ann = userNamed("a")
                AuthStore(file).save(mapOf(ann.uuid to ann))

                Files.readString(file).trimStart().startsWith("{") shouldBe true
            }
        }

        it("creates the data directory if the server has never written one") {
            withTempDir { dir ->
                val file = dir.resolve("nested").resolve("deeper").resolve("auth.json")

                AuthStore(file).save(mapOf("a" to userNamed("a")))

                Files.isRegularFile(file) shouldBe true
            }
        }

        it("leaves no temporary file behind, since the write goes through one") {
            withTempDir { dir ->
                AuthStore(dir.resolve("auth.json")).save(mapOf("a" to userNamed("a")))

                Files.list(dir).use { entries ->
                    entries.map { it.fileName.toString() }.toList() shouldContainExactlyInAnyOrder listOf("auth.json")
                }
            }
        }

        it("replaces the previous contents rather than appending to them") {
            withTempDir { dir ->
                val file = dir.resolve("auth.json")
                val store = AuthStore(file)
                store.save(mapOf("a" to userNamed("a"), "b" to userNamed("b")))
                store.save(mapOf("a" to userNamed("a")))

                store.load().keys shouldContainExactlyInAnyOrder listOf("a")
            }
        }

        it("writes an empty file for an empty account list, and reads it back as one") {
            withTempDir { dir ->
                val file = dir.resolve("auth.json")
                AuthStore(file).save(emptyMap())

                AuthStore(file).load().shouldBeEmpty()
            }
        }
    }

    describe("recovering from a bad file") {
        it("starts with no accounts when the file has never been written") {
            withTempDir { dir ->
                AuthStore(dir.resolve("auth.json")).load().shouldBeEmpty()
            }
        }

        it("starts with no accounts rather than throwing when the JSON is corrupt") {
            withTempDir { dir ->
                val file = dir.resolve("auth.json")
                Files.writeString(file, "{ this is not json")

                AuthStore(file).load().shouldBeEmpty()
            }
        }

        it("starts with no accounts when the file is empty") {
            withTempDir { dir ->
                val file = dir.resolve("auth.json")
                Files.writeString(file, "")

                AuthStore(file).load().shouldBeEmpty()
            }
        }

        it("starts with no accounts when the file holds JSON of the wrong shape") {
            withTempDir { dir ->
                val file = dir.resolve("auth.json")
                Files.writeString(file, "[1, 2, 3]")

                AuthStore(file).load().shouldBeEmpty()
            }
        }

        it("starts with no accounts when the path is a directory") {
            withTempDir { dir ->
                val asDirectory = Files.createDirectory(dir.resolve("auth.json"))

                AuthStore(asDirectory).load().shouldBeEmpty()
            }
        }

        // A failed write must not take the server down with it: the accounts stay in memory and
        // the next save gets another chance.
        it("does not throw when the data directory cannot be created") {
            withTempDir { dir ->
                val blockedParent = Files.writeString(dir.resolve("colonyweb"), "not a directory")

                AuthStore(blockedParent.resolve("auth.json")).save(mapOf("a" to userNamed("a")))
            }
        }
    }
})
