package app.trierarch.ui.gui.pages

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.trierarch.feature.updates.UpdatesState
import app.trierarch.ui.gui.GuiDialogs
import app.trierarch.ui.gui.GuiHost
import app.trierarch.ui.gui.componentStateText
import app.trierarch.update.AppState
import app.trierarch.update.ComponentState
import app.trierarch.update.ComponentStatus

internal object UpdatesPage {
    fun render(host: GuiHost) {
        val style = host.style
        val state = host.updatesState
        host.header.addView(
            style.title("Updates"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        host.header.addView(
            style.headerButton(
                if (state.phase == UpdatesState.Phase.CHECKING) "Checking…" else "Check",
            ) { host.updates.check() },
        )
        host.header.addView(style.headerButton("Back") { host.closeUpdates() })

        host.content.addView(style.sectionLabel("APP"))
        host.content.addView(
            style.info(
                "current",
                "${state.installedVersionName} (${state.installedVersionCode})",
            ),
        )
        when (state.phase) {
            UpdatesState.Phase.NOT_CHECKED ->
                host.content.addView(style.info("latest", "not checked"))
            UpdatesState.Phase.CHECKING ->
                host.content.addView(style.info("latest", "checking…"))
            UpdatesState.Phase.FAILED -> host.content.addView(TextView(host.context).apply {
                setText(state.error ?: "update check failed")
                setTextColor(style.errorText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, style.dp(10), 0, 0)
            })
            UpdatesState.Phase.READY -> {
                val manifest = state.manifest ?: return
                val status = state.status ?: return
                host.content.addView(
                    style.info(
                        "latest",
                        "${manifest.app.versionName} (${manifest.app.versionCode})",
                    ),
                )
                val appState = status.app.state
                host.content.addView(
                    style.statusText(
                        if (appState == AppState.UPDATE_AVAILABLE) "Update available" else "Up to date",
                        if (appState == AppState.UPDATE_AVAILABLE) {
                            style.pendingText
                        } else {
                            style.runningText
                        },
                    ),
                )
                if (appState == AppState.UPDATE_AVAILABLE) {
                    host.content.addView(apkControls(host, state))
                }
                host.content.addView(style.sectionLabel("COMPONENTS"))
                status.components.forEach { componentStatus ->
                    host.content.addView(componentRow(host, componentStatus))
                }
                if (state.checkedAt > 0) {
                    host.content.addView(TextView(host.context).apply {
                        setText("Last checked: ${style.timeText(state.checkedAt)}")
                        setTextColor(style.secondaryText)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setPadding(0, style.dp(18), 0, 0)
                    })
                }
            }
        }
    }

    private fun apkControls(host: GuiHost, state: UpdatesState): View {
        val style = host.style
        val app = state.manifest?.app ?: return View(host.context)
        val box = LinearLayout(host.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, style.dp(10), 0, 0)
        }
        when (val apk = state.apk) {
            UpdatesState.ApkState.Idle -> {
                box.addView(style.headerButton("Download") { host.updates.downloadApk() })
                box.addView(TextView(host.context).apply {
                    setText("${style.megabytes(app.apkSize)} · ${app.versionName}")
                    setTextColor(style.secondaryText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, style.dp(4), 0, 0)
                })
            }
            is UpdatesState.ApkState.Downloading -> box.addView(
                style.statusText(style.downloadText(apk.downloaded, apk.total)),
            )
            UpdatesState.ApkState.Verifying -> box.addView(style.statusText("Verifying…"))
            is UpdatesState.ApkState.Ready -> {
                box.addView(style.headerButton("Install") { host.updates.installApk() })
                box.addView(style.statusText("Downloaded ${apk.versionName}", style.runningText))
            }
            is UpdatesState.ApkState.Failed -> {
                box.addView(style.statusText(apk.message, style.errorText))
                box.addView(style.headerButton("Retry") { host.updates.downloadApk() })
            }
        }
        return box
    }

    private fun componentRow(host: GuiHost, status: ComponentStatus): View {
        val style = host.style
        val component = status.release
        val box = LinearLayout(host.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, style.dp(12), 0, 0)
        }
        box.addView(TextView(host.context).apply {
            setText(component.name + (component.version?.let { " · $it" } ?: ""))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        when (val download = host.updatesState.components[component.id]) {
            is UpdatesState.ComponentDownload.Downloading -> box.addView(
                style.statusText(style.downloadText(download.downloaded, download.total)),
            )
            UpdatesState.ComponentDownload.Verifying -> box.addView(style.statusText("Verifying…"))
            is UpdatesState.ComponentDownload.Failed -> {
                box.addView(style.statusText(download.message, style.errorText))
                box.addView(style.headerButton("Retry") {
                    host.updates.downloadComponent(component)
                })
            }
            null -> when (status.state) {
                ComponentState.NOT_DOWNLOADED, ComponentState.UPDATE_AVAILABLE -> {
                    box.addView(
                        style.statusText(
                            componentStateText(status.state),
                            if (status.state == ComponentState.UPDATE_AVAILABLE) {
                                style.pendingText
                            } else {
                                style.secondaryText
                            },
                        ),
                    )
                    box.addView(style.headerButton("Download") {
                        host.updates.downloadComponent(component)
                    })
                }
                ComponentState.UP_TO_DATE -> {
                    box.addView(style.statusText("Downloaded", style.runningText))
                    box.addView(TextView(host.context).apply {
                        setText("files/components/${component.file}")
                        setTextColor(style.secondaryText)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setPadding(0, style.dp(2), 0, 0)
                    })
                    box.addView(style.textAction("Delete", style.errorText) {
                        GuiDialogs.confirmRemoveComponent(host, component)
                    })
                }
            }
        }
        return box
    }
}
