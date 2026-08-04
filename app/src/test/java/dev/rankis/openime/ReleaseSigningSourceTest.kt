package dev.rankis.openime

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ReleaseSigningSourceTest {
    @Test
    fun releaseBuildRequiresSigningProperties() {
        val source = String(Files.readAllBytes(buildSourcePath()))

        assertTrue(source.contains("gradle.taskGraph.whenReady"))
        assertTrue(source.contains("OPENIME_RELEASE_STORE_FILE"))
        assertTrue(source.contains("Release signing is not configured"))
    }

    private fun buildSourcePath(): Path {
        val userDir = Paths.get(System.getProperty("user.dir"))
        val modulePath = userDir.resolve(Paths.get("app", "build.gradle.kts"))
        if (Files.exists(modulePath)) {
            return modulePath
        }
        return userDir.resolve("build.gradle.kts")
    }
}
