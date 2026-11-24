package app.trierarch.ui.gui

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.net.Uri
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.trierarch.core.Command
import app.trierarch.core.CommandOutcome
import app.trierarch.core.Core
import app.trierarch.core.CoreEvent
import app.trierarch.core.DocumentContent
import app.trierarch.core.ProfileDocument
import app.trierarch.core.ProfileService
import app.trierarch.core.SessionPhase
import app.trierarch.core.SessionSnapshot
import app.trierarch.update.ApkInstaller
import app.trierarch.update.ApkVerifier
import app.trierarch.update.AppRelease
import app.trierarch.update.AppState
import app.trierarch.update.ComponentRelease
import app.trierarch.update.ComponentState
import app.trierarch.update.ComponentStatus
import app.trierarch.update.ComponentStore
import app.trierarch.update.FileDownloader
import app.trierarch.update.Manifest
import app.trierarch.update.UpdateSource
import app.trierarch.update.UpdateStatus
import app.trierarch.update.check
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The in-app GUI: a view over [Core]. It never owns sessions or writes profile
 * files; every action goes through the core and every refresh re-reads it.
 */
class GuiScreen(
    private val context: Context,
    private val core: Core,
    private val onClose: () -> Unit,
    private val onFollowSession: () -> Unit,
    private val onPickArchive: (String) -> Unit,
) {
    private val root = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        // Opaque and clickable: neither touches nor keys may reach the runtime
        // surface underneath.
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
    }
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(16))
    }
    private val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val errorLabel = TextView(context).apply {
        setTextColor(errorText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, dp(8), 0, 0)
        visibility = View.GONE
    }
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private var screen: Screen = Screen.List
    private var editor: EditText? = null
    private var imeInset = 0
    private var updatesState: UpdatesState = UpdatesState.NotChecked
    private var apkState: ApkDownloadState = ApkDownloadState.Idle
    private val componentStore = ComponentStore(File(context.filesDir, "components"))
    private val componentDownloads = mutableMapOf<String, ComponentDownloadState>()
    private var released = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val updatesExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "trierarch-updates").apply { isDaemon = true }
    }

    private val listener: (CoreEvent) -> Unit = { event ->
        if (screen !is Screen.Editor && screen !is Screen.Logs &&
            (event == CoreEvent.DocumentsChanged || event == CoreEvent.SessionsChanged)
        ) {
            render()
        }
    }

    init {
        column.addView(header)
        column.addView(errorLabel)
        column.addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(
                    content,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        root.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        core.addListener(listener)
        render()
    }

    val view: View get() = root

    /** Keeps the editor above the keyboard on every display surface. */
    fun setImeInset(inset: Int) {
        if (inset == imeInset) return
        imeInset = inset
        column.setPadding(dp(20), dp(16), dp(20), dp(16) + inset)
    }

    /** Re-reads the core; never touches an editor with unsaved text. */
    fun refresh() {
        if (screen is Screen.Editor) return
        render()
    }

    /** Returns true when it navigated inside the GUI; false at the root list. */
    fun handleBack(): Boolean = when (val current = screen) {
        Screen.List -> false
        is Screen.Editor -> {
            editor = null
            hideError()
            screen = Screen.Details(current.id)
            render()
            true
        }
        is Screen.Details -> {
            hideError()
            screen = Screen.List
            render()
            true
        }
        is Screen.Logs -> {
            hideError()
            screen = Screen.Details(current.id)
            render()
            true
        }
        Screen.Updates -> {
            hideError()
            screen = Screen.List
            render()
            true
        }
    }

    fun release() {
        released = true
        updatesExecutor.shutdownNow()
        core.removeListener(listener)
    }

    private fun render() {
        header.removeAllViews()
        content.removeAllViews()
        when (val current = screen) {
            Screen.List -> renderList()
            is Screen.Details -> renderDetails(current.id)
            is Screen.Editor -> Unit
            is Screen.Logs -> renderLogs(current.id)
            Screen.Updates -> renderUpdates()
        }
    }

    private fun renderList() {
        header.addView(
            title("Trierarch"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(headerButton("Create") { promptCreate() })
        header.addView(headerButton("Import") { promptImport() })
        header.addView(headerButton("Terminal") { onClose() })

        val documents = core.documents()
        val snapshot = core.sessions()
        content.addView(updatesEntry())
        content.addView(sectionLabel("ENVIRONMENTS · ${documents.size}"))
        if (documents.isEmpty()) {
            content.addView(emptyState())
            return
        }
        val busy = snapshot.pending != null
        documents.forEach { document ->
            content.addView(environmentRow(document, snapshot.sessions[document.id], busy))
        }
    }

    private fun updatesEntry(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { openUpdates() }
        }
        val labels = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(context).apply {
            setText("Updates")
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        })
        labels.addView(TextView(context).apply {
            setText(updatesSummary())
            setTextColor(if (updatesHaveAvailable()) pendingText else secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(context).apply {
            setText("›")
            setTextColor(secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        })
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(
                View(context).apply { setBackgroundColor(dividerColor) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)),
            )
        }
    }

    private fun openUpdates() {
        hideError()
        editor = null
        screen = Screen.Updates
        render()
        if (updatesState is UpdatesState.NotChecked) checkUpdates()
    }

    private fun checkUpdates() {
        if (updatesState is UpdatesState.Checking) return
        if (apkState is ApkDownloadState.Downloading || apkState is ApkDownloadState.Verifying) return
        if (componentDownloads.values.any {
                it is ComponentDownloadState.Downloading || it === ComponentDownloadState.Verifying
            }
        ) {
            return
        }
        apkState = ApkDownloadState.Idle
        componentDownloads.clear()
        updatesState = UpdatesState.Checking
        render()
        updatesExecutor.execute {
            val result = runCatching {
                val (code, name) = installedAppVersion()
                val manifest = Manifest.parse(UpdateSource.fetch())
                UpdatesState.Ready(
                    manifest = manifest,
                    status = manifest.check(
                        code,
                        componentStore.downloaded().mapValues { it.value.sha256 },
                    ),
                    installedVersionCode = code,
                    installedVersionName = name,
                    checkedAt = System.currentTimeMillis(),
                )
            }
            mainHandler.post {
                if (released) return@post
                updatesState = result.getOrElse {
                    UpdatesState.Failed(it.message ?: "update check failed")
                }
                if (screen == Screen.List || screen is Screen.Updates) render()
            }
        }
    }

    private fun renderUpdates() {
        header.addView(
            title("Updates"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            headerButton(if (updatesState is UpdatesState.Checking) "Checking…" else "Check") {
                checkUpdates()
            },
        )
        header.addView(headerButton("Back") {
            screen = Screen.List
            render()
        })

        content.addView(sectionLabel("APP"))
        content.addView(info("current", currentVersionText()))
        when (val state = updatesState) {
            UpdatesState.NotChecked -> content.addView(info("latest", "not checked"))
            UpdatesState.Checking -> content.addView(info("latest", "checking…"))
            is UpdatesState.Failed -> content.addView(TextView(context).apply {
                setText(state.message)
                setTextColor(errorText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(10), 0, 0)
            })
            is UpdatesState.Ready -> {
                content.addView(
                    info(
                        "latest",
                        "${state.manifest.app.versionName} (${state.manifest.app.versionCode})",
                    ),
                )
                val appState = state.status.app.state
                content.addView(
                    statusText(
                        if (appState == AppState.UPDATE_AVAILABLE) "Update available" else "Up to date",
                        if (appState == AppState.UPDATE_AVAILABLE) pendingText else runningText,
                    ),
                )
                if (appState == AppState.UPDATE_AVAILABLE) {
                    content.addView(apkControls(state.manifest.app))
                }
                content.addView(sectionLabel("COMPONENTS"))
                state.status.components.forEach { componentStatus ->
                    content.addView(componentRow(componentStatus))
                }
                content.addView(TextView(context).apply {
                    setText("Last checked: ${timeText(state.checkedAt)}")
                    setTextColor(secondaryText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, dp(18), 0, 0)
                })
            }
        }
    }

    private fun apkControls(app: AppRelease): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        when (val state = apkState) {
            ApkDownloadState.Idle -> {
                box.addView(MaterialButton(context).apply {
                    minWidth = 0
                    text = "Download"
                    setOnClickListener { downloadApk() }
                })
                box.addView(TextView(context).apply {
                    setText("${megabytes(app.apkSize)} · ${app.versionName}")
                    setTextColor(secondaryText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, dp(4), 0, 0)
                })
            }
            is ApkDownloadState.Downloading -> {
                box.addView(statusText(downloadText(state.downloaded, state.total), secondaryText))
            }
            ApkDownloadState.Verifying -> {
                box.addView(statusText("Verifying…", secondaryText))
            }
            is ApkDownloadState.Ready -> {
                box.addView(MaterialButton(context).apply {
                    minWidth = 0
                    text = "Install"
                    setOnClickListener { installApk() }
                })
                box.addView(statusText("Downloaded ${state.versionName}", runningText))
            }
            is ApkDownloadState.Failed -> {
                box.addView(TextView(context).apply {
                    setText(state.message)
                    setTextColor(errorText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                })
                box.addView(MaterialButton(context).apply {
                    minWidth = 0
                    text = "Retry"
                    setOnClickListener { downloadApk() }
                })
            }
        }
        return box
    }

    private fun downloadText(downloaded: Long, total: Long): String {
        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        return "Downloading… $percent% (${megabytes(downloaded)} / ${megabytes(total)})"
    }

    private fun downloadApk() {
        val ready = updatesState as? UpdatesState.Ready ?: return
        val app = ready.manifest.app
        if (ready.status.app.state != AppState.UPDATE_AVAILABLE) return
        if (apkState is ApkDownloadState.Downloading || apkState is ApkDownloadState.Verifying) return
        apkState = ApkDownloadState.Downloading(0, app.apkSize)
        render()
        updatesExecutor.execute {
            val destination = File(
                context.cacheDir,
                "updates/Trierarch-${safeName(app.versionName)}.apk",
            )
            var lastPercent = -1
            val result = runCatching {
                FileDownloader.download(
                    app.apkUrl,
                    destination,
                    app.apkSize,
                    app.apkSha256,
                ) { downloaded, total ->
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                    if (percent == lastPercent) return@download
                    lastPercent = percent
                    mainHandler.post {
                        if (released) return@post
                        if (apkState is ApkDownloadState.Downloading) {
                            apkState = ApkDownloadState.Downloading(downloaded, total)
                            if (screen is Screen.Updates) render()
                        }
                    }
                }
                mainHandler.post {
                    if (released) return@post
                    apkState = ApkDownloadState.Verifying
                    if (screen is Screen.Updates) render()
                }
                ApkVerifier.verify(context, destination, app.versionCode)
                destination
            }
            mainHandler.post {
                if (released) return@post
                apkState = result.fold(
                    onSuccess = { ApkDownloadState.Ready(it, app.versionName) },
                    onFailure = { ApkDownloadState.Failed(it.message ?: "update failed") },
                )
                if (screen is Screen.Updates) render()
            }
        }
    }

    private fun installApk() {
        val ready = apkState as? ApkDownloadState.Ready ?: return
        runCatching { ApkInstaller.install(context, ready.file) }
            .onFailure { showError(it.message ?: "unable to start the installer") }
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun megabytes(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

    private fun componentRow(status: ComponentStatus): View {
        val component = status.release
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        box.addView(TextView(context).apply {
            setText(component.name + (component.version?.let { " · $it" } ?: ""))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        when (val download = componentDownloads[component.id]) {
            is ComponentDownloadState.Downloading -> {
                box.addView(
                    statusText(downloadText(download.downloaded, download.total), secondaryText),
                )
            }
            ComponentDownloadState.Verifying -> {
                box.addView(statusText("Verifying…", secondaryText))
            }
            is ComponentDownloadState.Failed -> {
                box.addView(statusText(download.message, errorText))
                box.addView(headerButton("Retry") { downloadComponent(component) })
            }
            null -> when (status.state) {
                ComponentState.NOT_DOWNLOADED, ComponentState.UPDATE_AVAILABLE -> {
                    box.addView(
                        statusText(
                            componentStateText(status.state),
                            componentStateColor(status.state),
                        ),
                    )
                    box.addView(headerButton("Download") { downloadComponent(component) })
                }
                ComponentState.UP_TO_DATE -> {
                    box.addView(statusText("Downloaded", runningText))
                    box.addView(TextView(context).apply {
                        setText("files/components/${component.file}")
                        setTextColor(secondaryText)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setPadding(0, dp(2), 0, 0)
                    })
                    box.addView(textAction("Delete", errorText) {
                        confirmRemoveComponent(component)
                    })
                }
            }
        }
        return box
    }

    private fun confirmRemoveComponent(component: ComponentRelease) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Delete ${component.name}?")
            .setMessage(
                "Removes files/components/${component.file}. " +
                    "A copy already placed in a rootfs is not touched.",
            )
            .setPositiveButton("Delete") { _, _ ->
                val removed = componentStore.remove(component.id)
                if (removed == null) {
                    showError("nothing to delete for ${component.id}")
                    return@setPositiveButton
                }
                Toast.makeText(context, "Deleted ${removed.file}", Toast.LENGTH_SHORT).show()
                refreshComponentStatuses()
                if (screen is Screen.Updates) render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadComponent(component: ComponentRelease) {
        val current = componentDownloads[component.id]
        if (current is ComponentDownloadState.Downloading ||
            current === ComponentDownloadState.Verifying
        ) {
            return
        }
        componentDownloads[component.id] = ComponentDownloadState.Downloading(0, component.size)
        render()
        updatesExecutor.execute {
            val destination = componentStore.file(component.file)
            var lastPercent = -1
            val result = runCatching {
                FileDownloader.download(
                    component.url,
                    destination,
                    component.size,
                    component.sha256,
                ) { downloaded, total ->
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                    if (percent == lastPercent) return@download
                    lastPercent = percent
                    mainHandler.post {
                        if (released) return@post
                        if (componentDownloads[component.id] is ComponentDownloadState.Downloading) {
                            componentDownloads[component.id] =
                                ComponentDownloadState.Downloading(downloaded, total)
                            if (screen is Screen.Updates) render()
                        }
                    }
                }
                mainHandler.post {
                    if (released) return@post
                    componentDownloads[component.id] = ComponentDownloadState.Verifying
                    if (screen is Screen.Updates) render()
                }
                componentStore.record(component.id, component.file, component.sha256)
                destination
            }
            mainHandler.post {
                if (released) return@post
                if (result.isSuccess) {
                    componentDownloads.remove(component.id)
                    refreshComponentStatuses()
                    Toast.makeText(
                        context,
                        "Saved to files/components/${component.file}",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    componentDownloads[component.id] = ComponentDownloadState.Failed(
                        result.exceptionOrNull()?.message ?: "download failed",
                    )
                }
                if (screen is Screen.Updates) render()
            }
        }
    }

    private fun refreshComponentStatuses() {
        val ready = updatesState as? UpdatesState.Ready ?: return
        updatesState = ready.copy(
            status = ready.manifest.check(
                ready.installedVersionCode,
                componentStore.downloaded().mapValues { it.value.sha256 },
            ),
        )
    }

    private fun installedAppVersion(): Pair<Long, String> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        return code to (info.versionName ?: "")
    }

    private fun currentVersionText(): String = runCatching {
        val (code, name) = installedAppVersion()
        "$name ($code)"
    }.getOrDefault("unknown")

    private fun componentStateText(state: ComponentState): String = when (state) {
        ComponentState.NOT_DOWNLOADED -> "Not downloaded"
        ComponentState.UP_TO_DATE -> "Downloaded"
        ComponentState.UPDATE_AVAILABLE -> "Update available"
    }

    private fun componentStateColor(state: ComponentState): Int = when (state) {
        ComponentState.NOT_DOWNLOADED -> secondaryText
        ComponentState.UP_TO_DATE -> runningText
        ComponentState.UPDATE_AVAILABLE -> pendingText
    }

    private fun updatesHaveAvailable(): Boolean {
        val state = updatesState as? UpdatesState.Ready ?: return false
        return state.status.app.state == AppState.UPDATE_AVAILABLE ||
            state.status.components.any { it.state == ComponentState.UPDATE_AVAILABLE }
    }

    private fun updatesSummary(): String = when (val state = updatesState) {
        UpdatesState.NotChecked -> "Check for app and component updates"
        UpdatesState.Checking -> "Checking…"
        is UpdatesState.Failed -> "Check failed"
        is UpdatesState.Ready ->
            if (updatesHaveAvailable()) "Update available" else "Up to date"
    }

    private fun statusText(message: String, color: Int): TextView = TextView(context).apply {
        setText(message)
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, dp(4), 0, 0)
    }

    private fun timeText(millis: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))

    private fun renderDetails(id: String) {
        val document = core.documents().firstOrNull { it.id == id }
        if (document == null) {
            screen = Screen.List
            render()
            return
        }
        header.addView(
            title(document.id),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(headerButton("Back") {
            hideError()
            screen = Screen.List
            render()
        })

        content.addView(sectionLabel("PROFILE"))
        content.addView(info("runtime", document.runtime.ifEmpty { "—" }))
        content.addView(
            info(
                "display",
                if (document.display == ProfileService.DISPLAY_NONE) "terminal" else document.display,
            ),
        )
        content.addView(info("renderer", document.renderer ?: "—"))
        val snapshot = core.sessions()
        val session = snapshot.sessions[document.id]
        content.addView(info("state", phaseText(session)))

        document.error?.let { message ->
            content.addView(TextView(context).apply {
                text = message
                setTextColor(errorText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(12), 0, 0)
            })
        }

        val primary = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, 0)
        }
        val busy = snapshot.pending != null
        when (session?.phase ?: SessionPhase.STOPPED) {
            SessionPhase.STOPPED -> primary.addView(
                primaryAction("Start", busy) { submit(Command.Session.Run(document.id)) },
            )
            SessionPhase.RUNNING -> {
                primary.addView(
                    primaryAction("Open", busy) { submit(Command.Session.Open(document.id)) },
                )
                primary.addView(
                    secondaryAction("Stop", busy) { submit(Command.Session.Stop(document.id)) },
                )
            }
            SessionPhase.STARTING, SessionPhase.STOPPING -> primary.addView(
                primaryAction("…", busy = true) {},
            )
        }
        content.addView(primary)

        val manage = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        manage.addView(textAction("Edit") { openEditor(document.id) })
        manage.addView(textAction("Logs") { openLogs(document.id) })
        manage.addView(textAction("Delete", errorText) { confirmDelete(document) })
        content.addView(manage)
    }

    private fun renderEditor(document: DocumentContent) {
        header.removeAllViews()
        content.removeAllViews()
        header.addView(
            title("Edit ${document.id}"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(headerButton("Save") { saveEditor() })
        header.addView(headerButton("Cancel") { cancelEditor() })

        val field = EditText(context).apply {
            setText(document.text)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setBackgroundColor(fieldColor)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.TOP or Gravity.START
            minLines = 18
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        editor = field
        content.addView(field, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        content.addView(TextView(context).apply {
            text = "Raw TOML. Save checks that the file has not changed since it was opened."
            setTextColor(secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(10), 0, 0)
        })
    }

    private fun environmentRow(
        document: ProfileDocument,
        session: SessionSnapshot?,
        busy: Boolean,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { openDetails(document.id) }
        }
        val line = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        details.addView(TextView(context).apply {
            text = document.id
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.MONOSPACE
        })
        if (document.error == null) {
            val display = if (document.display == ProfileService.DISPLAY_NONE) {
                "terminal"
            } else {
                document.display
            }
            val badges = listOf(document.runtime, display, document.renderer)
                .filterNotNull()
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            details.addView(TextView(context).apply {
                text = badges
                setTextColor(secondaryText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(4), 0, 0)
            })
        }
        val phase = session?.phase ?: SessionPhase.STOPPED
        details.addView(TextView(context).apply {
            text = phaseText(session) + if (session?.active == true) " · shown" else ""
            setTextColor(
                when (phase) {
                    SessionPhase.STARTING, SessionPhase.STOPPING -> pendingText
                    SessionPhase.RUNNING -> runningText
                    SessionPhase.STOPPED -> secondaryText
                },
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, 0)
        })
        document.error?.let { message ->
            details.addView(TextView(context).apply {
                text = message
                setTextColor(errorText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(4), 0, 0)
            })
        }
        line.addView(
            details,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        rowActions(document, phase, busy)?.let { actions ->
            line.addView(
                actions,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(12) },
            )
        }
        row.addView(line)
        row.addView(
            View(context).apply { setBackgroundColor(dividerColor) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(14)
            },
        )
        return row
    }

    private fun rowActions(
        document: ProfileDocument,
        phase: SessionPhase,
        busy: Boolean,
    ): View? {
        if (document.error != null) return null
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        when (phase) {
            SessionPhase.STOPPED -> actions.addView(
                primaryAction("Start", busy) { submit(Command.Session.Run(document.id)) },
            )
            SessionPhase.RUNNING -> {
                actions.addView(
                    primaryAction("Open", busy) { submit(Command.Session.Open(document.id)) },
                )
                actions.addView(
                    secondaryAction("Stop", busy) { submit(Command.Session.Stop(document.id)) },
                )
            }
            SessionPhase.STARTING, SessionPhase.STOPPING -> actions.addView(
                primaryAction("…", busy = true) {},
            )
        }
        return actions
    }

    private fun openDetails(id: String) {
        hideError()
        editor = null
        screen = Screen.Details(id)
        render()
    }

    private fun openEditor(id: String) {
        hideError()
        val document = core.read(id)
        if (document == null) {
            screen = Screen.List
            showError("profile does not exist: $id")
            render()
            return
        }
        screen = Screen.Editor(id, document.revision)
        renderEditor(document)
    }

    private fun openLogs(id: String) {
        hideError()
        editor = null
        screen = Screen.Logs(id)
        render()
    }

    private fun renderLogs(id: String) {
        header.addView(
            title("Logs · $id"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(headerButton("Refresh") { render() })
        header.addView(headerButton("Back") {
            screen = Screen.Details(id)
            render()
        })
        val text = core.logs(id, 400)
        content.addView(TextView(context).apply {
            setText(text ?: "No session log yet.")
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, dp(14), 0, 0)
        })
    }

    private fun saveEditor() {
        val field = editor ?: return
        val current = screen as? Screen.Editor ?: return
        submit(
            Command.Document.Save(current.id, field.text.toString(), current.revision),
            afterSuccess = {
                editor = null
                screen = Screen.List
            },
        )
    }

    private fun cancelEditor() {
        val current = screen as? Screen.Editor ?: return
        editor = null
        hideError()
        screen = Screen.Details(current.id)
        render()
    }

    private fun promptCreate() {
        val input = EditText(context).apply {
            hint = "profile id"
            setSingleLine()
            typeface = Typeface.MONOSPACE
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("New environment")
            .setMessage("Creates the profile template; edit it before running.")
            .setView(input)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val id = input.text.toString().trim()
                if (id.isEmpty()) {
                    showError("profile id is empty")
                    return@setOnClickListener
                }
                dialog.dismiss()
                submit(Command.Document.Create(id), afterSuccess = { screen = Screen.List })
            }
        }
        dialog.show()
    }

    private fun promptImport() {
        val input = EditText(context).apply {
            hint = "rootfs name"
            setSingleLine()
            typeface = Typeface.MONOSPACE
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Import rootfs")
            .setMessage("Choose a .tar.xz archive next. The name becomes files/rootfs/<name>.")
            .setView(input)
            .setPositiveButton("Choose archive") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    showError("rootfs name is empty")
                } else {
                    onPickArchive(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Copies the SAF stream to a real .xz file, then hands it to the core. */
    fun importArchive(uri: Uri, name: String) {
        showStatus("Reading archive…")
        Thread {
            val archive = File(context.cacheDir, "import-${System.nanoTime()}.tar.xz")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    archive.outputStream().use { output -> input.copyTo(output) }
                } ?: error("unable to open archive")
                archive
            }
            mainHandler.post {
                copied.onSuccess { file ->
                    showStatus("Importing $name…")
                    core.submit(Command.Document.Import(file.absolutePath, name)) { outcome ->
                        file.delete()
                        when (outcome) {
                            is CommandOutcome.Failure -> showError(outcome.error.message)
                            is CommandOutcome.Success -> {
                                hideError()
                                Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }.onFailure { showError(it.message ?: "unable to read archive") }
            }
        }.start()
    }

    private fun confirmDelete(document: ProfileDocument) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Delete ${document.id}?")
            .setMessage("The profile file is removed. A running session is not stopped.")
            .setPositiveButton("Delete") { _, _ ->
                val current = core.read(document.id)
                if (current == null) {
                    showError("profile does not exist: ${document.id}")
                    return@setPositiveButton
                }
                submit(
                    Command.Document.Delete(current.id, current.revision),
                    afterSuccess = { screen = Screen.List },
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submit(command: Command, afterSuccess: (() -> Unit)? = null) {
        hideError()
        core.submit(command) { outcome ->
            when (outcome) {
                is CommandOutcome.Failure -> showError(outcome.error.message)
                is CommandOutcome.Success -> {
                    if (command is Command.Session.Run || command is Command.Session.Open) {
                        // Follow the session and let its display host own focus.
                        onFollowSession()
                    }
                    outcome.afterReply?.invoke()
                    afterSuccess?.invoke()
                }
            }
        }
    }

    private fun showStatus(message: String) {
        errorLabel.setTextColor(secondaryText)
        errorLabel.text = message
        errorLabel.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        errorLabel.setTextColor(errorText)
        errorLabel.text = message
        errorLabel.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorLabel.visibility = View.GONE
    }

    private fun phaseText(session: SessionSnapshot?): String = when (session?.phase ?: SessionPhase.STOPPED) {
        SessionPhase.STOPPED -> "stopped"
        SessionPhase.STARTING -> "starting"
        SessionPhase.RUNNING -> "running"
        SessionPhase.STOPPING -> "stopping"
    }

    private fun title(text: String): TextView = TextView(context).apply {
        setText(text)
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
    }

    private fun headerButton(label: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(context).apply {
            minWidth = 0
            text = label
            setOnClickListener { onClick() }
        }

    private fun sectionLabel(text: String): TextView = TextView(context).apply {
        setText(text)
        setTextColor(secondaryText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        letterSpacing = 0.08f
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun info(label: String, value: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, 0)
        addView(TextView(context).apply {
            setText(label)
            setTextColor(secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            width = dp(88)
        })
        addView(
            TextView(context).apply {
                setText(value)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.MONOSPACE
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
    }

    private fun primaryAction(label: String, busy: Boolean, onClick: () -> Unit) =
        MaterialButton(context).apply {
            minWidth = 0
            text = label
            isEnabled = !busy
            setOnClickListener { onClick() }
        }

    private fun secondaryAction(label: String, busy: Boolean, onClick: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextColor(errorText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            isEnabled = !busy
            alpha = if (busy) 0.4f else 1f
            setOnClickListener { onClick() }
        }

    private fun textAction(label: String, color: Int = secondaryText, onClick: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun emptyState(): View = TextView(context).apply {
        text = "No environments yet.\n\nUse Create to add one, or from the terminal:\n\n" +
            "  trierarch --create arch-demo\n" +
            "  trierarch --config arch-demo"
        setTextColor(secondaryText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.MONOSPACE
        setPadding(0, dp(16), 0, 0)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()

    private sealed interface Screen {
        data object List : Screen
        data class Details(val id: String) : Screen
        data class Editor(val id: String, val revision: String) : Screen
        data class Logs(val id: String) : Screen
        data object Updates : Screen
    }

    private sealed interface ComponentDownloadState {
        data class Downloading(val downloaded: Long, val total: Long) : ComponentDownloadState
        data object Verifying : ComponentDownloadState
        data class Failed(val message: String) : ComponentDownloadState
    }

    private sealed interface ApkDownloadState {
        data object Idle : ApkDownloadState
        data class Downloading(val downloaded: Long, val total: Long) : ApkDownloadState
        data object Verifying : ApkDownloadState
        data class Ready(val file: File, val versionName: String) : ApkDownloadState
        data class Failed(val message: String) : ApkDownloadState
    }

    private sealed interface UpdatesState {
        data object NotChecked : UpdatesState
        data object Checking : UpdatesState
        data class Ready(
            val manifest: Manifest,
            val status: UpdateStatus,
            val installedVersionCode: Long,
            val installedVersionName: String,
            val checkedAt: Long,
        ) : UpdatesState
        data class Failed(val message: String) : UpdatesState
    }

    private companion object {
        val secondaryText = 0xFF9CA3AF.toInt()
        val errorText = 0xFFF87171.toInt()
        val dividerColor = 0xFF23262B.toInt()
        val runningText = 0xFF7CE38B.toInt()
        val pendingText = 0xFFFBBF24.toInt()
        val fieldColor = 0xFF111318.toInt()
    }
}
