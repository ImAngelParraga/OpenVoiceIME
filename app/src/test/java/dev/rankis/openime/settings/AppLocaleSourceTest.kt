package dev.rankis.openime.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class AppLocaleSourceTest {
    @Test
    fun imeLocaleLoadDoesNotOpenSecureSettings() {
        val source = String(Files.readAllBytes(sourcePath()))

        assertTrue(source.contains("loadAppLanguageChoice()"))
        assertFalse(source.contains(".load().appLanguageChoice"))
    }

    private fun sourcePath(): Path {
        val userDir = Paths.get(System.getProperty("user.dir"))
        val relativePath = Paths.get(
            "src",
            "main",
            "java",
            "dev",
            "rankis",
            "openime",
            "settings",
            "AppLocale.kt",
        )
        val modulePath = userDir.resolve(relativePath)
        if (Files.exists(modulePath)) {
            return modulePath
        }
        return userDir.resolve("app").resolve(relativePath)
    }
}
