package app.trierarch.core


/**
 * X11 and Wayland are host-side singletons: one X server and one Wayland host
 * serve every session in the app. Starting a second session on the same host
 * would leave the first one on screen, because `open` can only re-show the
 * shared host, not switch the guest compositor inside it.
 *
 * Returns the running profile that already owns [display], or null.
 */
internal fun findDisplayConflict(
    running: Map<String, String>,
    id: String,
    display: String,
): String? {
    if (display == ProfileService.DISPLAY_NONE) return null
    return running.entries.firstOrNull { (otherId, otherDisplay) ->
        otherId != id && otherDisplay == display
    }?.key
}
