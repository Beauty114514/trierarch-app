package app.trierarch.core

/** State changes a frontend should react to; commands still return their own outcome. */
sealed interface CoreEvent {
    data object DocumentsChanged : CoreEvent
}
