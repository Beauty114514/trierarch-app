package app.trierarch.feature.updates

import app.trierarch.update.AppRelease
import app.trierarch.update.ComponentRelease
import app.trierarch.update.ComponentStore
import app.trierarch.update.DownloadedComponent
import app.trierarch.update.FileDownloader
import app.trierarch.update.Manifest
import app.trierarch.update.UpdateSource
import app.trierarch.update.UpdateStatus
import app.trierarch.update.check
import java.io.File

data class UpdateCheck(
    val manifest: Manifest,
    val status: UpdateStatus,
    val installedVersionCode: Long,
    val installedVersionName: String,
)

/**
 * Update-center backend: manifest check, verified downloads and the component
 * store. It has no View dependencies; the ViewModel supplies the Android bits
 * (installed version, APK verification and the installer).
 */
class UpdatesService(
    private val components: ComponentStore,
    private val cacheDirectory: File,
    private val installedVersion: () -> Pair<Long, String>,
    private val fetchManifest: () -> String = { UpdateSource.fetch() },
    private val downloadFile: (
        url: String,
        destination: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (Long, Long) -> Unit,
    ) -> File = { url, destination, size, sha256, onProgress ->
        FileDownloader.download(url, destination, size, sha256, onProgress)
    },
    private val verifyApk: (File, Long) -> Unit,
    private val installApk: (File) -> Unit,
) {
    fun installed(): Pair<Long, String> = installedVersion()

    fun check(): UpdateCheck {
        val (code, name) = installedVersion()
        val manifest = Manifest.parse(fetchManifest())
        return UpdateCheck(
            manifest = manifest,
            status = manifest.check(code, downloadedHashes()),
            installedVersionCode = code,
            installedVersionName = name,
        )
    }

    fun downloadApk(
        app: AppRelease,
        onProgress: (Long, Long) -> Unit,
        onVerifying: () -> Unit,
    ): File {
        val destination = File(
            cacheDirectory,
            "updates/Trierarch-${safeName(app.versionName)}.apk",
        )
        val file = downloadFile(app.apkUrl, destination, app.apkSize, app.apkSha256, onProgress)
        onVerifying()
        verifyApk(file, app.versionCode)
        return file
    }

    fun install(file: File) = installApk(file)

    fun downloadComponent(
        component: ComponentRelease,
        onProgress: (Long, Long) -> Unit,
        onVerifying: () -> Unit,
    ): File {
        val destination = components.file(component.file)
        val file = downloadFile(component.url, destination, component.size, component.sha256, onProgress)
        onVerifying()
        components.record(component.id, component.file, component.sha256)
        return file
    }

    fun deleteComponent(id: String): DownloadedComponent? = components.remove(id)

    fun downloadedHashes(): Map<String, String> =
        components.downloaded().mapValues { it.value.sha256 }

    fun statusFor(manifest: Manifest, installedVersionCode: Long): UpdateStatus =
        manifest.check(installedVersionCode, downloadedHashes())

    private fun safeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
