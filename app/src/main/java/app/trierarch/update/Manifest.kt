package app.trierarch.update

import org.tomlj.Toml
import org.tomlj.TomlTable

/**
 * The manifest published next to the app releases. It is the only source the
 * update screen uses to answer "is there a newer app/component"; the app never
 * reads release tags or GitHub's API directly.
 */
data class Manifest(
    val schema: Long,
    val app: AppRelease,
    val components: List<ComponentRelease>,
) {
    companion object {
        const val SUPPORTED_SCHEMA = 1L

        fun parse(text: String): Manifest = try {
            parseToml(text)
        } catch (error: ManifestException) {
            throw error
        } catch (error: Exception) {
            throw ManifestException("invalid manifest: ${error.message}", error)
        }
    }
}

data class AppRelease(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val apkSize: Long,
    val apkSha256: String,
    val notesUrl: String?,
)

enum class ComponentInstallType { FILES, PACMAN }

data class ComponentInstall(
    val type: ComponentInstallType = ComponentInstallType.FILES,
    val packages: List<String> = emptyList(),
)

data class ComponentRelease(
    val id: String,
    val name: String,
    /** Optional while components are still being developed. */
    val version: String?,
    val file: String,
    val url: String,
    val size: Long,
    val sha256: String,
    val install: ComponentInstall = ComponentInstall(),
)

enum class AppState { UP_TO_DATE, UPDATE_AVAILABLE }

enum class ComponentState { NOT_DOWNLOADED, UP_TO_DATE, UPDATE_AVAILABLE }

data class AppStatus(val release: AppRelease, val state: AppState)

data class ComponentStatus(val release: ComponentRelease, val state: ComponentState)

data class UpdateStatus(val app: AppStatus, val components: List<ComponentStatus>)

/**
 * Compares the manifest with the installed app and the components this app has
 * downloaded. Component updates are decided by SHA-256, not by version strings.
 */
fun Manifest.check(
    installedVersionCode: Long,
    downloadedSha256: Map<String, String>,
): UpdateStatus {
    val appState = if (app.versionCode > installedVersionCode) {
        AppState.UPDATE_AVAILABLE
    } else {
        AppState.UP_TO_DATE
    }
    val componentStatuses = components.map { component ->
        val downloaded = downloadedSha256[component.id]
        val state = when {
            downloaded == null -> ComponentState.NOT_DOWNLOADED
            !downloaded.equals(component.sha256, ignoreCase = true) ->
                ComponentState.UPDATE_AVAILABLE
            else -> ComponentState.UP_TO_DATE
        }
        ComponentStatus(component, state)
    }
    return UpdateStatus(AppStatus(app, appState), componentStatuses)
}

class ManifestException(message: String, cause: Throwable? = null) : Exception(message, cause)

private val SHA256 = Regex("[0-9a-f]{64}")
private val COMPONENT_ID = Regex("[a-z0-9][a-z0-9-]*")

private fun parseToml(text: String): Manifest {
    val parsed = Toml.parse(text)
    if (parsed.hasErrors()) {
        throw ManifestException(
            "manifest is not valid TOML: " +
                parsed.errors().joinToString("; ") { it.toString() },
        )
    }
    val schema = parsed.getLong("schema")
        ?: throw ManifestException("manifest has no schema")
    if (schema != Manifest.SUPPORTED_SCHEMA) {
        throw ManifestException("unsupported manifest schema: $schema")
    }
    val app = parsed.getTable("app")
        ?: throw ManifestException("manifest has no app section")
    val release = AppRelease(
        versionCode = requiredPositiveLong(app, "versionCode", "app"),
        versionName = requiredString(app, "versionName", "app"),
        apkUrl = requiredUrl(app, "apkUrl", "app"),
        apkSize = requiredPositiveLong(app, "apkSize", "app"),
        apkSha256 = requiredSha256(app, "apkSha256", "app"),
        notesUrl = optionalUrl(app, "notesUrl", "app"),
    )
    val array = parsed.getArray("components")
    val components = if (array == null) {
        emptyList()
    } else {
        (0 until array.size()).map { index ->
            val value = array.getTable(index)
                ?: throw ManifestException("components[$index] is not a table")
            parseComponent(value, index)
        }
    }
    val ids = components.map { it.id }
    if (ids.size != ids.toSet().size) {
        throw ManifestException("duplicate component id in manifest")
    }
    return Manifest(schema, release, components)
}

private fun parseComponent(value: TomlTable, index: Int): ComponentRelease {
    val owner = "components[$index]"
    val id = requiredString(value, "id", owner)
    if (!COMPONENT_ID.matches(id)) {
        throw ManifestException("$owner.id must use lowercase letters, digits and hyphens")
    }
    val file = requiredString(value, "file", owner)
    if (file == "." || file == ".." || file.contains('/') || file.contains('\\')) {
        throw ManifestException("$owner.file must be a plain file name")
    }
    return ComponentRelease(
        id = id,
        name = requiredString(value, "name", owner),
        version = value.getString("version")?.trim()?.ifEmpty { null },
        file = file,
        url = requiredUrl(value, "url", owner),
        size = requiredPositiveLong(value, "size", owner),
        sha256 = requiredSha256(value, "sha256", owner),
        install = parseInstall(value, owner),
    )
}

private fun requiredString(value: TomlTable, key: String, owner: String): String {
    val text = value.getString(key)?.trim()
    if (text.isNullOrEmpty()) throw ManifestException("$owner.$key is missing")
    return text
}

private fun requiredPositiveLong(value: TomlTable, key: String, owner: String): Long {
    val number = value.getLong(key)
    if (number == null || number <= 0) {
        throw ManifestException("$owner.$key must be positive")
    }
    return number
}

private fun requiredUrl(value: TomlTable, key: String, owner: String): String {
    val url = requiredString(value, key, owner)
    if (!url.startsWith("https://")) throw ManifestException("$owner.$key must be HTTPS")
    return url
}

private fun optionalUrl(value: TomlTable, key: String, owner: String): String? {
    val url = value.getString(key)?.trim()?.ifEmpty { null } ?: return null
    if (!url.startsWith("https://")) throw ManifestException("$owner.$key must be HTTPS")
    return url
}

private fun requiredSha256(value: TomlTable, key: String, owner: String): String {
    val text = requiredString(value, key, owner).lowercase()
    if (!SHA256.matches(text)) {
        throw ManifestException("$owner.$key must be a lowercase SHA-256 hex digest")
    }
    return text
}

private fun parseInstall(value: TomlTable, owner: String): ComponentInstall {
    val table = value.getTable("install") ?: return ComponentInstall()
    val type = table.getString("type")?.trim()?.ifEmpty { null } ?: "files"
    val packages = table.getArray("packages")?.let { array ->
        (0 until array.size()).map { index ->
            (array.getString(index) ?: throw ManifestException(
                "$owner.install.packages[$index] must be a string"
            )).trim()
        }
    }.orEmpty()
    if (packages.any { it.isEmpty() || it.contains('/') || it.contains('\\') }) {
        throw ManifestException("$owner.install.packages contains an invalid package name")
    }
    return when (type) {
        "files" -> {
            if (packages.isNotEmpty()) {
                throw ManifestException("$owner.install.type = files cannot list packages")
            }
            ComponentInstall(ComponentInstallType.FILES)
        }
        "pacman" -> {
            if (packages.isEmpty()) {
                throw ManifestException("$owner.install.type = pacman requires packages")
            }
            ComponentInstall(ComponentInstallType.PACMAN, packages)
        }
        else -> throw ManifestException("$owner.install.type is unsupported: $type")
    }
}
