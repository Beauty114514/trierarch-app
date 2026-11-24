package app.trierarch.core

/** One running session as far as conflict checks are concerned. */
internal data class SessionSlot(
    val profileId: String,
    val display: String,
    val sourceKey: String,
)

/** Returns the other session already using [sourceKey], or null. */
internal fun findSourceConflict(
    sessions: Collection<SessionSlot>,
    id: String,
    sourceKey: String,
): SessionSlot? = sessions.firstOrNull { it.profileId != id && it.sourceKey == sourceKey }

/**
 * X11 and Wayland are host-side singletons: one X server and one Wayland host
 * serve every session. A second session on the same host would leave the first
 * one on screen, because `open` can only re-show the shared host, not switch
 * the guest compositor inside it.
 *
 * Returns the running session that already owns [display], or null.
 */
internal fun findDisplayConflict(
    sessions: Collection<SessionSlot>,
    id: String,
    display: String,
): SessionSlot? {
    if (display == ProfileService.DISPLAY_NONE) return null
    return sessions.firstOrNull { it.profileId != id && it.display == display }
}
