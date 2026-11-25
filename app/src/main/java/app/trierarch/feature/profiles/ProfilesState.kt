package app.trierarch.feature.profiles

import app.trierarch.core.ProfileDocument
import app.trierarch.core.RuntimeSnapshot

/** Everything the profile side of the GUI needs to draw itself. */
data class ProfilesState(
    val documents: List<ProfileDocument> = emptyList(),
    val sessions: RuntimeSnapshot = RuntimeSnapshot(emptyMap(), null),
    val screen: ProfileScreen = ProfileScreen.List,
    val editorText: String = "",
    val editorRevision: String = "",
    val logText: String? = null,
    val error: String? = null,
)

sealed interface ProfileScreen {
    data object List : ProfileScreen
    data class Details(val id: String) : ProfileScreen
    data class Editor(val id: String) : ProfileScreen
    data class Logs(val id: String) : ProfileScreen
}

sealed interface ProfilesEvent {
    /** A Run/Open command succeeded; the GUI should get out of the way. */
    data object FollowSession : ProfilesEvent

    data class Message(val text: String) : ProfilesEvent
}
