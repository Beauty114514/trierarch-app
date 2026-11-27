package app.trierarch.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ManifestTest {
    private val apkSha = "a".repeat(64)
    private val kwinSha = "b".repeat(64)
    private val aquaSha = "c".repeat(64)
    private val mesaSha = "d".repeat(64)

    private val defaultComponents = """
        [[components]]
        id = "kwin-compat"
        name = "KWin compatibility"
        version = "6.8.3-1"
        file = "kwin-compat.tar.zst"
        url = "https://example.com/kwin-compat.tar.zst"
        size = 10
        sha256 = "$kwinSha"

        [[components]]
        id = "aquamarine"
        name = "Aquamarine compatibility"
        file = "libaquamarine-compat.so"
        url = "https://example.com/libaquamarine-compat.so"
        size = 5
        sha256 = "$aquaSha"
    """.trimIndent()

    private fun manifest(
        appVersionCode: Long = 11,
        apkUrl: String = "https://example.com/Trierarch.apk",
        apkSha256: String = apkSha,
        notesUrl: String? = "https://example.com/notes",
        components: String = defaultComponents,
    ): String = buildString {
        appendLine("schema = 1")
        appendLine("[app]")
        appendLine("versionCode = $appVersionCode")
        appendLine("versionName = \"0.6.0\"")
        appendLine("apkUrl = \"$apkUrl\"")
        appendLine("apkSize = 100")
        appendLine("apkSha256 = \"$apkSha256\"")
        if (notesUrl != null) appendLine("notesUrl = \"$notesUrl\"")
        appendLine(components)
    }

    @Test
    fun parsesAppAndComponents() {
        val manifest = Manifest.parse(manifest())

        assertEquals(1L, manifest.schema)
        assertEquals(11L, manifest.app.versionCode)
        assertEquals("0.6.0", manifest.app.versionName)
        assertEquals("https://example.com/Trierarch.apk", manifest.app.apkUrl)
        assertEquals(100L, manifest.app.apkSize)
        assertEquals(apkSha, manifest.app.apkSha256)
        assertEquals("https://example.com/notes", manifest.app.notesUrl)
        assertEquals(listOf("kwin-compat", "aquamarine"), manifest.components.map { it.id })
        assertEquals("6.8.3-1", manifest.components[0].version)
        assertNull(manifest.components[1].version)
    }

    @Test
    fun versionAndNotesAreOptional() {
        val manifest = Manifest.parse(manifest(notesUrl = null))

        assertNull(manifest.app.notesUrl)
        assertNull(manifest.components[1].version)
    }

    @Test
    fun appUpdateRequiresANewerVersionCode() {
        val manifest = Manifest.parse(manifest())

        assertEquals(AppState.UPDATE_AVAILABLE, manifest.check(10, emptyMap()).app.state)
        assertEquals(AppState.UP_TO_DATE, manifest.check(11, emptyMap()).app.state)
        assertEquals(AppState.UP_TO_DATE, manifest.check(12, emptyMap()).app.state)
    }

    @Test
    fun componentStateUsesTheDownloadedHash() {
        val manifest = Manifest.parse(manifest())

        val initial = manifest.check(11, emptyMap()).components.associateBy { it.release.id }
        assertEquals(ComponentState.NOT_DOWNLOADED, initial.getValue("kwin-compat").state)

        val current = manifest.check(
            11,
            mapOf("kwin-compat" to kwinSha, "aquamarine" to aquaSha.uppercase()),
        ).components.associateBy { it.release.id }
        assertEquals(ComponentState.UP_TO_DATE, current.getValue("kwin-compat").state)
        assertEquals(ComponentState.UP_TO_DATE, current.getValue("aquamarine").state)

        val stale = manifest.check(
            11,
            mapOf("kwin-compat" to "d".repeat(64)),
        ).components.first { it.release.id == "kwin-compat" }
        assertEquals(ComponentState.UPDATE_AVAILABLE, stale.state)
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val error = assertThrows(ManifestException::class.java) {
            Manifest.parse(manifest().replace("schema = 1", "schema = 2"))
        }
        assertEquals("unsupported manifest schema: 2", error.message)
    }

    @Test
    fun rejectsMalformedToml() {
        assertThrows(ManifestException::class.java) { Manifest.parse("schema = ") }
    }

    @Test
    fun rejectsNonHttpsAndBadHashes() {
        assertThrows(ManifestException::class.java) {
            Manifest.parse(manifest(apkUrl = "http://example.com/Trierarch.apk"))
        }
        assertThrows(ManifestException::class.java) {
            Manifest.parse(manifest(apkSha256 = "abc"))
        }
    }

    @Test
    fun rejectsDuplicateComponentIds() {
        val duplicate = """
            [[components]]
            id = "kwin-compat"
            name = "KWin"
            file = "a.tar.zst"
            url = "https://example.com/a.tar.zst"
            size = 1
            sha256 = "$kwinSha"

            [[components]]
            id = "kwin-compat"
            name = "KWin again"
            file = "b.tar.zst"
            url = "https://example.com/b.tar.zst"
            size = 1
            sha256 = "$kwinSha"
        """.trimIndent()

        assertThrows(ManifestException::class.java) {
            Manifest.parse(manifest(components = duplicate))
        }
    }

    @Test
    fun rejectsFileNamesWithPathComponents() {
        val traversal = """
            [[components]]
            id = "kwin-compat"
            name = "KWin"
            file = "../kwin.tar.zst"
            url = "https://example.com/a.tar.zst"
            size = 1
            sha256 = "$kwinSha"
        """.trimIndent()

        assertThrows(ManifestException::class.java) {
            Manifest.parse(manifest(components = traversal))
        }
    }

    @Test
    fun parsesExplicitComponentInstallTypes() {
        val components = """
            [[components]]
            id = "kwin-compat"
            name = "KWin compatibility"
            file = "kwin-compat.tar.zst"
            url = "https://example.com/kwin-compat.tar.zst"
            size = 10
            sha256 = "$kwinSha"

            install = { type = "files" }

            [[components]]
            id = "mesa-27"
            name = "Mesa 27"
            file = "mesa-27.tar.xz"
            url = "https://example.com/mesa-27.tar.xz"
            size = 10
            sha256 = "$mesaSha"

            install = { type = "pacman", packages = ["mesa", "vulkan-freedreno"] }
        """.trimIndent()

        val manifest = Manifest.parse(manifest(components = components))

        assertEquals(ComponentInstallType.FILES, manifest.components[0].install.type)
        assertEquals(emptyList<ComponentPackage>(), manifest.components[0].install.packages)
        assertEquals(ComponentInstallType.PACMAN, manifest.components[1].install.type)
        assertEquals(
            listOf(ComponentPackage("mesa"), ComponentPackage("vulkan-freedreno")),
            manifest.components[1].install.packages,
        )
    }

    @Test
    fun defaultsMissingInstallTypeToFiles() {
        val manifest = Manifest.parse(manifest())
        assertEquals(ComponentInstallType.FILES, manifest.components[0].install.type)
    }

    @Test
    fun rejectsInvalidInstallDescriptions() {
        val pacmanWithoutPackages = """
            [[components]]
            id = "mesa-27"
            name = "Mesa 27"
            file = "mesa-27.tar.xz"
            url = "https://example.com/mesa-27.tar.xz"
            size = 10
            sha256 = "$mesaSha"

            install = { type = "pacman" }
        """.trimIndent()
        assertThrows(ManifestException::class.java) {
            Manifest.parse(manifest(components = pacmanWithoutPackages))
        }

        val filesWithPackages = """
            [[components]]
            id = "kwin-compat"
            name = "KWin compatibility"
            file = "kwin-compat.tar.zst"
            url = "https://example.com/kwin-compat.tar.zst"
            size = 10
            sha256 = "$kwinSha"

            install = { type = "files", packages = ["mesa"] }
        """.trimIndent()
        assertThrows(ManifestException::class.java) {
            Manifest.parse(manifest(components = filesWithPackages))
        }
    }

    @Test
    fun parsesPackageVersionsFromPacmanPackages() {
        val components = """
            [[components]]
            id = "mesa"
            name = "Mesa"
            version = "27.0.0-91"
            file = "mesa.tar.xz"
            url = "https://example.com/mesa.tar.xz"
            size = 10
            sha256 = "$mesaSha"
            install = { type = "pacman", packages = [
                { name = "mesa", version = "1:27.0.0-91" },
                { name = "vulkan-freedreno", version = "1:27.0.0-91" }
            ] }
        """.trimIndent()

        val manifest = Manifest.parse(manifest(components = components))

        assertEquals(
            listOf(
                ComponentPackage("mesa", "1:27.0.0-91"),
                ComponentPackage("vulkan-freedreno", "1:27.0.0-91"),
            ),
            manifest.components.single().install.packages,
        )
    }
}
