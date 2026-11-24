package app.trierarch.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Refuses an APK that is not this app, not the manifest's version, not newer
 * than the installed app, or not signed with the same certificate.
 */
internal object ApkVerifier {
    fun verify(context: Context, apk: File, expectedVersionCode: Long) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IllegalArgumentException("downloaded file is not a valid APK")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        if (archive.packageName != context.packageName) {
            throw IllegalArgumentException(
                "APK package ${archive.packageName} does not match ${context.packageName}",
            )
        }
        val archiveCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archive.versionCode.toLong()
        }
        val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            installed.versionCode.toLong()
        }
        if (archiveCode != expectedVersionCode) {
            throw IllegalArgumentException(
                "APK version $archiveCode does not match the manifest $expectedVersionCode",
            )
        }
        if (archiveCode <= installedCode) {
            throw IllegalArgumentException(
                "APK version $archiveCode is not newer than the installed $installedCode",
            )
        }
        val archiveSigners = signerHashes(archive)
        val installedSigners = signerHashes(installed)
        if (archiveSigners.isEmpty() || archiveSigners != installedSigners) {
            throw IllegalArgumentException("APK signature does not match the installed app")
        }
    }

    private fun signerHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty()
        }
        return signatures.mapTo(mutableSetOf()) { signature ->
            sha256Hex(signature.toByteArray())
        }
    }
}
