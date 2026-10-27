package app.trierarch.config

import android.content.Context
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import java.io.File

/** Direct filesystem access for profile documents; no index or database exists. */
class ProfileStore(context: Context) {
    private val directory = File(context.filesDir, "config/profiles")

    fun list(): List<File> = directory
        .listFiles { file -> file.isFile && file.extension == EXTENSION }
        ?.sortedBy { it.name }
        .orEmpty()

    fun read(file: File): String = file.readText()

    fun save(text: String, replacing: File? = null): File {
        val id = validate(text)
        check(directory.isDirectory || directory.mkdirs()) {
            "Unable to create profile directory"
        }
        val destination = File(directory, "$id.$EXTENSION")
        require(destination == replacing || !destination.exists()) {
            "A profile named '$id' already exists"
        }

        val temporary = File(directory, ".${id}.${System.nanoTime()}.tmp")
        temporary.writeText(text)
        check(temporary.renameTo(destination)) { "Unable to save profile '$id'" }
        if (replacing != null && replacing != destination && replacing.exists()) {
            check(replacing.delete()) { "Saved profile but could not remove its former file" }
        }
        return destination
    }

    fun delete(file: File) {
        require(file.parentFile == directory) { "Profile is outside the profile directory" }
        check(file.delete()) { "Unable to delete ${file.name}" }
    }

    /** Validates TOML syntax plus the stable filename identity required by the book. */
    fun validate(text: String): String {
        val parsed = Toml.parse(text)
        require(!parsed.hasErrors()) {
            parsed.errors().joinToString("\n") { error -> error.toString() }
        }
        val id = parsed.getString("id")?.trim().orEmpty()
        require(ID_PATTERN.matches(id)) {
            "id must contain only lowercase letters, digits, and hyphens"
        }
        val runtime = parsed.getString("runtime")?.trim().orEmpty()
        require(runtime.isNotEmpty()) { "runtime is required" }
        val display = parsed.getString("display.type")?.trim().orEmpty().ifEmpty { DISPLAY_NONE }
        require(display == DISPLAY_NONE || display == DISPLAY_X11 || display == DISPLAY_WAYLAND) {
            "display.type must be '$DISPLAY_NONE', '$DISPLAY_X11', or '$DISPLAY_WAYLAND'"
        }
        val graphics = graphicsProfile(parsed, display)
        compatibilityProfile(parsed)
        val audioBackend = audioBackend(parsed)
        require(audioBackend == AUDIO_OFF || audioBackend == AUDIO_PIPEWIRE) {
            "audio.backend must be '$AUDIO_OFF' or '$AUDIO_PIPEWIRE'"
        }
        if (audioBackend == AUDIO_PIPEWIRE) {
            require(runtime == RUNTIME_DROIDSPACES || runtime == RUNTIME_PROOT || runtime == RUNTIME_CHROOT) {
                "audio.backend = '$AUDIO_PIPEWIRE' is currently supported by DroidSpaces, PRoot, and chroot"
            }
        }
        if (graphics.renderer == GRAPHICS_VIRGL || graphics.renderer == GRAPHICS_VENUS) {
            require(runtime == RUNTIME_DROIDSPACES || runtime == RUNTIME_PROOT || runtime == RUNTIME_CHROOT) {
                "graphics.renderer '${graphics.renderer}' is currently supported by droidspaces, proot, and chroot"
            }
            require(display != DISPLAY_NONE) {
                "graphics.renderer '${graphics.renderer}' requires display.type 'x11' or 'wayland'"
            }
        }
        if (graphics.renderer == GRAPHICS_ADRENO) {
            require(runtime == RUNTIME_DROIDSPACES || runtime == RUNTIME_CHROOT) {
                "graphics.renderer '$GRAPHICS_ADRENO' is currently supported by droidspaces and chroot"
            }
            require(display == DISPLAY_WAYLAND) {
                "graphics.renderer '$GRAPHICS_ADRENO' currently requires display.type 'wayland'"
            }
        }
        launchArgv(parsed)
        if (runtime == RUNTIME_PROOT || runtime == RUNTIME_CHROOT) {
            val rootfs = parsed.getString("rootfs")?.trim().orEmpty()
            require(rootfs.isNotEmpty()) {
                "rootfs is required for this runtime"
            }
            require(rootfs.startsWith('/')) { "rootfs must be an absolute path" }
            val shell = parsed.getString("shell")?.trim().orEmpty()
            require(shell.startsWith('/') && shell.split('/').none { it == ".." }) {
                "shell must be an absolute path inside the rootfs"
            }
        }
        if (runtime == RUNTIME_DROIDSPACES) {
            require(parsed.getString("container")?.trim().isNullOrEmpty().not()) {
                "container is required"
            }
        }
        return id
    }

    fun runtime(text: String): String = Toml.parse(text).getString("runtime")?.trim().orEmpty()

    /** The display is optional; omitted means the terminal-only default. */
    fun display(text: String): String = Toml.parse(text)
        .getString("display.type")
        ?.trim()
        .orEmpty()
        .ifEmpty { DISPLAY_NONE }

    /** Reads the minimal, explicit contract for one PRoot session. */
    fun prootProfile(text: String): ProotProfile {
        val parsed = parse(text)
        require(parsed.getString("runtime")?.trim() == RUNTIME_PROOT) {
            "runtime must be '$RUNTIME_PROOT'"
        }
        val rootfsPath = required(parsed.getString("rootfs"), "rootfs")
        val shell = required(parsed.getString("shell"), "shell")
        require(shell.startsWith('/') && shell.split('/').none { it == ".." }) {
            "shell must be an absolute path inside the rootfs"
        }
        val rootfs = File(rootfsPath)
        require(rootfs.isDirectory) { "rootfs is not an accessible directory: $rootfsPath" }
        require(File(rootfs, shell.removePrefix("/")).isFile) {
            "configured shell does not exist in rootfs: $shell"
        }
        return ProotProfile(
            id = required(parsed.getString("id"), "id"),
            rootfs = rootfs,
            shell = shell,
            display = parsed.getString("display.type")?.trim().orEmpty().ifEmpty { DISPLAY_NONE },
            launchArgv = launchArgv(parsed),
            graphics = graphicsProfile(parsed, parsed.getString("display.type")?.trim().orEmpty().ifEmpty { DISPLAY_NONE }),
            compatibility = compatibilityProfile(parsed),
            audioBackend = audioBackend(parsed),
        )
    }

    /**
     * A chroot rootfs is checked only after elevation: its host directory may
     * intentionally be unreadable by the ordinary Android app UID.
     */
    fun chrootProfile(text: String): ChrootProfile {
        val parsed = parse(text)
        require(parsed.getString("runtime")?.trim() == RUNTIME_CHROOT) {
            "runtime must be '$RUNTIME_CHROOT'"
        }
        val rootfs = required(parsed.getString("rootfs"), "rootfs")
        val shell = required(parsed.getString("shell"), "shell")
        require(rootfs.startsWith('/')) { "rootfs must be an absolute path" }
        require(shell.startsWith('/') && shell.split('/').none { it == ".." }) {
            "shell must be an absolute path inside the rootfs"
        }
        return ChrootProfile(
            id = required(parsed.getString("id"), "id"),
            rootfs = rootfs,
            shell = shell,
            display = parsed.getString("display.type")?.trim().orEmpty().ifEmpty { DISPLAY_NONE },
            launchArgv = launchArgv(parsed),
            graphics = graphicsProfile(parsed, parsed.getString("display.type")?.trim().orEmpty().ifEmpty { DISPLAY_NONE }),
            compatibility = compatibilityProfile(parsed),
            audioBackend = audioBackend(parsed),
        )
    }

    /** A container DroidSpaces has deployed; Trierarch owns this profile's session start. */
    fun droidspacesProfile(text: String): DroidspacesProfile {
        val parsed = parse(text)
        require(parsed.getString("runtime")?.trim() == RUNTIME_DROIDSPACES) {
            "runtime must be '$RUNTIME_DROIDSPACES'"
        }
        return DroidspacesProfile(
            id = required(parsed.getString("id"), "id"),
            container = required(parsed.getString("container"), "container"),
            user = parsed.getString("user")?.trim().takeUnless { it.isNullOrEmpty() } ?: "root",
            display = parsed.getString("display.type")?.trim().orEmpty().ifEmpty { DISPLAY_NONE },
            launchArgv = launchArgv(parsed),
            graphics = graphicsProfile(parsed, parsed.getString("display.type")?.trim().orEmpty().ifEmpty { DISPLAY_NONE }),
            compatibility = compatibilityProfile(parsed),
            audioBackend = audioBackend(parsed),
        )
    }

    private fun parse(text: String) = Toml.parse(text).also { parsed ->
        require(!parsed.hasErrors()) {
            parsed.errors().joinToString("\n") { error -> error.toString() }
        }
    }

    private fun required(value: String?, key: String): String = value?.trim().takeUnless { it.isNullOrEmpty() }
        ?: throw IllegalArgumentException("$key is required")

    /**
     * Rendering policy is deliberately small for now.  The app resolves it to
     * concrete environment variables before crossing the native boundary, so
     * every runtime receives the same guest-facing contract.
     */
    private fun graphicsProfile(parsed: TomlParseResult, display: String): GraphicsProfile {
        val renderer = parsed.getString("graphics.renderer")?.trim().orEmpty().ifEmpty { GRAPHICS_AUTO }
        require(
            renderer == GRAPHICS_AUTO || renderer == GRAPHICS_LLVMPIPE ||
                renderer == GRAPHICS_VIRGL || renderer == GRAPHICS_VENUS ||
                renderer == GRAPHICS_ADRENO,
        ) {
            "graphics.renderer must be '$GRAPHICS_AUTO', '$GRAPHICS_LLVMPIPE', " +
                "'$GRAPHICS_VIRGL', '$GRAPHICS_VENUS', or '$GRAPHICS_ADRENO'"
        }
        val qtQuickBackend = parsed.getString("graphics.qt_quick_backend")?.trim().orEmpty()
            .ifEmpty {
                if (display == DISPLAY_NONE || renderer == GRAPHICS_VIRGL || renderer == GRAPHICS_VENUS) {
                    GRAPHICS_AUTO
                } else {
                    QT_QUICK_SOFTWARE
                }
            }
        require(qtQuickBackend == GRAPHICS_AUTO || qtQuickBackend == QT_QUICK_SOFTWARE) {
            "graphics.qt_quick_backend must be '$GRAPHICS_AUTO' or '$QT_QUICK_SOFTWARE'"
        }
        val buffer = parsed.getString("graphics.buffer")?.trim().orEmpty().ifEmpty { BUFFER_AUTO }
        require(buffer == BUFFER_AUTO || buffer == BUFFER_SHM || buffer == BUFFER_DMABUF) {
            "graphics.buffer must be '$BUFFER_AUTO', '$BUFFER_SHM', or '$BUFFER_DMABUF'"
        }
        // The buffer path only exists for a Wayland parent compositor; X11 has
        // no dmabuf contract, so a non-auto value there would be silently
        // meaningless rather than effective.
        require(display == DISPLAY_WAYLAND || buffer == BUFFER_AUTO) {
            "graphics.buffer is only supported when display.type is '$DISPLAY_WAYLAND'"
        }
        // virpipe/vtest cannot allocate or export a local dma-buf: the guest
        // has no DRM device behind the GL context.  Advertising dmabuf for it
        // is exactly the failure mode that made KWin abort before its first
        // buffer, so reject the combination instead of starting a black screen.
        val usesVtestTransport = renderer == GRAPHICS_VIRGL || renderer == GRAPHICS_VENUS
        require(!(buffer == BUFFER_DMABUF && usesVtestTransport)) {
            "graphics.buffer '$BUFFER_DMABUF' is not supported by graphics.renderer '$renderer'"
        }
        return GraphicsProfile(renderer, qtQuickBackend, buffer)
    }

    /**
     * Guest compatibility shims are intentionally runtime-neutral.  `auto`
     * leaves a real host service alone and only falls back when Android has
     * denied a guest operation such as a udev netlink monitor.
     */
    private fun compatibilityProfile(parsed: TomlParseResult): CompatibilityProfile {
        val udevMonitor = parsed.getString("compat.udev_monitor")?.trim().orEmpty().ifEmpty { COMPAT_AUTO }
        require(udevMonitor == COMPAT_AUTO || udevMonitor == COMPAT_OFF) {
            "compat.udev_monitor must be '$COMPAT_AUTO' or '$COMPAT_OFF'"
        }
        return CompatibilityProfile(udevMonitor)
    }

    private fun audioBackend(parsed: TomlParseResult): String =
        parsed.getString("audio.backend")?.trim().orEmpty().ifEmpty { AUDIO_OFF }

    /** Optional direct program invocation; absent keeps the runtime's interactive-shell default. */
    private fun launchArgv(parsed: TomlParseResult): List<String>? {
        if (!parsed.contains("launch.argv")) return null
        require(parsed.isArray("launch.argv")) { "launch.argv must be an array of strings" }
        val array = checkNotNull(parsed.getArray("launch.argv"))
        require(array.size() > 0) { "launch.argv must not be empty" }
        return (0 until array.size()).map { index ->
            val value = array.getString(index)
                ?: throw IllegalArgumentException("launch.argv[$index] must be a string")
            require(value.isNotEmpty() && !value.contains('\u0000')) {
                "launch.argv[$index] must not be empty or contain a NUL byte"
            }
            value
        }
    }

    data class ProotProfile(
        val id: String,
        val rootfs: File,
        val shell: String,
        val display: String,
        val launchArgv: List<String>?,
        val graphics: GraphicsProfile,
        val compatibility: CompatibilityProfile,
        val audioBackend: String,
    )

    data class ChrootProfile(
        val id: String,
        val rootfs: String,
        val shell: String,
        val display: String,
        val launchArgv: List<String>?,
        val graphics: GraphicsProfile,
        val compatibility: CompatibilityProfile,
        val audioBackend: String,
    )

    data class DroidspacesProfile(
        val id: String,
        val container: String,
        val user: String,
        val display: String,
        val launchArgv: List<String>?,
        val graphics: GraphicsProfile,
        val compatibility: CompatibilityProfile,
        val audioBackend: String,
    )

    data class GraphicsProfile(
        val renderer: String,
        val qtQuickBackend: String,
        val buffer: String = BUFFER_AUTO,
    ) {
        /**
         * Buffer path the host should advertise for this profile.
         *
         * `auto` keeps the historical behaviour (dmabuf) for renderers that
         * own a local DRM device, and falls back to shm for the vtest
         * transports: virpipe/venus only render on the host, so the guest has
         * no device that could back a dmabuf.
         */
        val resolvedBuffer: String
            get() = when {
                buffer != BUFFER_AUTO -> buffer
                renderer == GRAPHICS_VIRGL || renderer == GRAPHICS_VENUS -> BUFFER_SHM
                else -> BUFFER_DMABUF
            }

        fun environment(): List<String> = buildList {
            // The guest-side launch scripts use these to gate the render-node
            // report and to pick the compositor's software path.  They also
            // make session logs self-describing.
            add("TRIERARCH_BUFFER=$resolvedBuffer")
            add("TRIERARCH_RENDERER=$renderer")
            if (renderer == GRAPHICS_LLVMPIPE) {
                add("LIBGL_ALWAYS_SOFTWARE=1")
                add("GALLIUM_DRIVER=llvmpipe")
                add("MESA_LOADER_DRIVER_OVERRIDE=llvmpipe")
            }
            if (renderer == GRAPHICS_VIRGL) {
                // virpipe is a Gallium driver reached through the software DRI
                // frontend; MESA_LOADER_DRIVER_OVERRIDE=virpipe is not a valid
                // loader name and makes eglInitialize() fail outright.
                add("LIBGL_ALWAYS_SOFTWARE=1")
                add("GALLIUM_DRIVER=virpipe")
            }
            if (renderer == GRAPHICS_VENUS) {
                add("VN_DEBUG=vtest")
            }
            if (renderer == GRAPHICS_ADRENO) {
                add("MESA_LOADER_DRIVER_OVERRIDE=kgsl")
                add("TURNIP_KMD=kgsl")
                add("GALLIUM_DRIVER=freedreno")
                add("FD_FORCE_KGSL=1")
            }
            if (resolvedBuffer == BUFFER_SHM) {
                // KWin still ships the QPainter compositor up to 6.7; without
                // this it prefers OpenGL, finds no usable render device and
                // aborts before its first buffer.  KWin 6.8 dropped QPainter
                // entirely, so KWIN_COMPOSE=Q there fails loudly instead of
                // leaving a black screen (the profile should not choose shm
                // for it in the first place).
                add("KWIN_COMPOSE=Q")
                // wlroots compositors must not try GLES2 when the host only
                // offers wl_shm.
                add("WLR_RENDERER=pixman")
            }
            if (qtQuickBackend == QT_QUICK_SOFTWARE) add("QT_QUICK_BACKEND=software")
        }
    }

    data class CompatibilityProfile(val udevMonitor: String) {
        val enablesUdevMonitorShim: Boolean get() = udevMonitor == COMPAT_AUTO
    }

    companion object {
        private const val EXTENSION = "toml"
        private val ID_PATTERN = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        const val RUNTIME_INTERNAL_SHELL = "internal-shell"
        const val RUNTIME_PROOT = "proot"
        const val RUNTIME_CHROOT = "chroot"
        const val RUNTIME_DROIDSPACES = "droidspaces"
        const val DISPLAY_NONE = "none"
        const val DISPLAY_X11 = "x11"
        const val DISPLAY_WAYLAND = "wayland"
        const val GRAPHICS_AUTO = "auto"
        const val GRAPHICS_LLVMPIPE = "llvmpipe"
        const val GRAPHICS_VIRGL = "virgl"
        const val GRAPHICS_VENUS = "venus"
        const val GRAPHICS_ADRENO = "adreno"
        const val BUFFER_AUTO = "auto"
        const val BUFFER_SHM = "shm"
        const val BUFFER_DMABUF = "dmabuf"
        const val QT_QUICK_SOFTWARE = "software"
        const val AUDIO_OFF = "off"
        const val AUDIO_PIPEWIRE = "pipewire"
        const val COMPAT_AUTO = "auto"
        const val COMPAT_OFF = "off"
        const val EXTRA_RUNTIME = "app.trierarch.config.RUNTIME"
    }
}
