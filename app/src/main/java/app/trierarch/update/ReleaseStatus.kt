package app.trierarch.update

/** The live package state of one target, read through pacman. */
data class PackageSnapshot(
    val installed: Map<String, String>,
    val requiredBy: Map<String, List<String>> = emptyMap(),
)

/** A receipt recording which package versions Trierarch installed. */
data class InstallReceipt(
    val target: String,
    val componentId: String,
    val releaseVersion: String?,
    val packages: Map<String, String>,
)

enum class ReleasePhase {
    NOT_DOWNLOADED,
    DOWNLOADED,
    PARTIAL,
    MISMATCH,
    INSTALLED,
    EXTERNAL,
    UNKNOWN,
}

data class ReleaseStatus(
    val phase: ReleasePhase,
    val downloaded: Boolean,
    val installed: Boolean,
    val owned: Boolean,
    val removable: Boolean,
    val requiredBy: Set<String>,
    val installedVersions: Map<String, String>,
    val canInstall: Boolean,
    val canUninstall: Boolean,
)

/**
 * Pure release-state resolver. It never mutates storage or the target; callers
 * provide the downloaded flag, receipt, and current pacman snapshot.
 */
object ReleaseStatusResolver {
    fun resolve(
        release: ComponentRelease,
        downloaded: Boolean,
        receipt: InstallReceipt?,
        snapshot: PackageSnapshot?,
    ): ReleaseStatus {
        val expected = release.install.packages
        if (!downloaded) {
            return ReleaseStatus(
                phase = ReleasePhase.NOT_DOWNLOADED,
                downloaded = false,
                installed = false,
                owned = false,
                removable = false,
                requiredBy = emptySet(),
                installedVersions = emptyMap(),
                canInstall = false,
                canUninstall = false,
            )
        }
        if (snapshot == null || expected.isEmpty()) {
            return ReleaseStatus(
                phase = if (snapshot == null) ReleasePhase.UNKNOWN else ReleasePhase.DOWNLOADED,
                downloaded = true,
                installed = false,
                owned = false,
                removable = false,
                requiredBy = emptySet(),
                installedVersions = emptyMap(),
                canInstall = snapshot != null,
                canUninstall = false,
            )
        }
        val versions = expected.associate { it.name to (snapshot.installed[it.name] ?: "") }
        val present = expected.all { snapshot.installed.containsKey(it.name) }
        val versionsMatch = expected.all { packageSpec ->
            val actual = snapshot.installed[packageSpec.name] ?: return@all false
            packageSpec.version == null || packageSpec.version == actual
        }
        val owned = receipt != null &&
            receipt.componentId == release.id &&
            receipt.releaseVersion == release.version &&
            expected.all { packageSpec ->
                receipt.packages[packageSpec.name] == snapshot.installed[packageSpec.name]
            }
        val requiredBy = expected.flatMap { packageSpec ->
            snapshot.requiredBy[packageSpec.name].orEmpty()
        }.toSet()
        val phase = when {
            !present && versions.values.any { it.isNotEmpty() } -> ReleasePhase.PARTIAL
            !present -> ReleasePhase.DOWNLOADED
            !versionsMatch -> ReleasePhase.MISMATCH
            owned -> ReleasePhase.INSTALLED
            else -> ReleasePhase.EXTERNAL
        }
        val installed = phase == ReleasePhase.INSTALLED || phase == ReleasePhase.EXTERNAL
        return ReleaseStatus(
            phase = phase,
            downloaded = true,
            installed = installed,
            owned = owned,
            removable = phase == ReleasePhase.INSTALLED && requiredBy.isEmpty(),
            requiredBy = requiredBy,
            installedVersions = versions.filterValues { it.isNotEmpty() },
            canInstall = phase != ReleasePhase.INSTALLED && phase != ReleasePhase.EXTERNAL,
            canUninstall = phase == ReleasePhase.INSTALLED && requiredBy.isEmpty(),
        )
    }
}
