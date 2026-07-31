package DahRealPanda.plugins.colonyweb.support

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively

/**
 * Runs [block] against a directory of its own and deletes it afterwards.
 *
 * The auth store and the texture cache both take a data directory and write to it, so the tests
 * that cover them need a real one. Sharing a directory between tests would let one test's
 * `auth.json` decide another test's starting state.
 */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
fun <T> withTempDir(block: (Path) -> T): T {
    val dir = Files.createTempDirectory("colonyweb-test")
    try {
        return block(dir)
    } finally {
        dir.deleteRecursively()
    }
}
