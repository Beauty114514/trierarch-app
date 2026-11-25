package app.trierarch.ui.gui

import android.content.Context
import android.widget.LinearLayout
import app.trierarch.feature.profiles.ProfilesController
import app.trierarch.feature.profiles.ProfilesState
import app.trierarch.feature.updates.UpdatesState
import app.trierarch.feature.updates.UpdatesViewModel

/** What a GUI page may use; pages never talk to Core or the platform directly. */
internal interface GuiHost {
    val context: Context
    val style: GuiStyle
    val header: LinearLayout
    val content: LinearLayout
    val profiles: ProfilesController
    val updates: UpdatesViewModel
    val profilesState: ProfilesState
    val updatesState: UpdatesState

    fun showError(message: String)
    fun hideError()
    fun openUpdates()
    fun closeUpdates()
    fun pickArchive(name: String)
    fun closeGui()
}
