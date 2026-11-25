package app.trierarch.feature.updates

import app.trierarch.update.AppState
import app.trierarch.update.ComponentState
import app.trierarch.update.ComponentStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdatesServiceTest {
    private val apkSha = "a".repeat(64)
    private val componentSha = "b".repeat(64)

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun manifest(): String = """
        schema = 1
        [app]
        versionCode = 11
        versionName = "0.6.0"
        apkUrl = "https://example.com/app.apk"
        apkSize = 100
        apkSha256 = "$apkSha"

        [[components]]
        id = "kwin-compat"
        name = "KWin compatibility"
        file = "kwin.tar.zst"
        url = "https://example.com/kwin.tar.zst"
        size = 10
        sha256 = "$componentSha"
    """.trimIndent()

    private fun service(
        installedCode: Long = 10,
        fetch: () -> String = { manifest() },
        onVerify: (File, Long) -> Unit = { _, _ -> },
        onInstall: (File) -> Unit = {},
    ): UpdatesService {
        val root = temporaryFolder.newFolder()
        return UpdatesService(
            components = ComponentStore(File(root, "components")),
            cacheDirectory = File(root, "cache"),
            installedVersion = { installedCode to "0.5.1" },
            fetchManifest = fetch,
            downloadFile = { _, destination, _, _, onProgress ->
                destination.parentFile?.mkdirs()
                destination.writeText("downloaded")
                onProgress(5, 5)
                destination
            },
            verifyApk = onVerify,
            installApk = onInstall,
        )
    }

    @Test
    fun checkReportsAnAppUpdateAndAnUndownloadedComponent() {
        val check = service().check()

        assertEquals(11L, check.manifest.app.versionCode)
        assertEquals(AppState.UPDATE_AVAILABLE, check.status.app.state)
        assertEquals(ComponentState.NOT_DOWNLOADED, check.status.components.single().state)
        assertEquals(10L, check.installedVersionCode)
    }

    @Test
    fun downloadingAComponentStoresAndRecordsIt() {
        val service = service()
        val component = service.check().manifest.components.single()

        val file = service.downloadComponent(component, onProgress = { _, _ -> }, onVerifying = {})

        assertTrue(file.isFile)
        assertEquals(componentSha, service.downloadedHashes()["kwin-compat"])
        assertEquals(
            ComponentState.UP_TO_DATE,
            service.statusFor(service.check().manifest, 10).components.single().state,
        )
        assertEquals("kwin.tar.zst", service.deleteComponent("kwin-compat")?.file)
        assertTrue(service.downloadedHashes().isEmpty())
    }

    @Test
    fun downloadingTheApkVerifiesAndInstallsIt() {
        var verified = false
        var installed: File? = null
        val service = service(
            onVerify = { _, code -> verified = code == 11L },
            onInstall = { installed = it },
        )
        val app = service.check().manifest.app

        val file = service.downloadApk(app, onProgress = { _, _ -> }, onVerifying = {})
        assertTrue(file.isFile)
        assertTrue(verified)

        service.install(file)
        assertEquals(file, installed)
    }
}
