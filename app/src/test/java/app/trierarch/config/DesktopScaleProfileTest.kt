package app.trierarch.config

import android.content.ContextWrapper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopScaleProfileTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun waylandScaleIsAcceptedAndRead() {
        val store = profileStore()
        val text = profile(scale = 125)

        assertEquals("scale-profile", store.validate(text))
        assertEquals(125, store.droidspacesProfile(text).desktopScale)
    }

    @Test
    fun largerScaleStepsAreAccepted() {
        val store = profileStore()
        val text = profile(scale = 300)

        assertEquals("scale-profile", store.validate(text))
        assertEquals(300, store.droidspacesProfile(text).desktopScale)
    }

    @Test
    fun scaleDefaultsTo100() {
        assertEquals(100, profileStore().droidspacesProfile(profile()).desktopScale)
    }

    @Test(expected = IllegalArgumentException::class)
    fun scaleRequiresWaylandDisplay() {
        profileStore().validate(profile(scale = 125, display = "x11"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedScaleIsRejected() {
        profileStore().validate(profile(scale = 110))
    }

    private fun profile(scale: Int? = null, display: String = "wayland"): String = buildString {
        appendLine("id = \"scale-profile\"")
        appendLine("runtime = \"droidspaces\"")
        appendLine("container = \"Arch\"")
        appendLine("[display]")
        appendLine("type = \"$display\"")
        scale?.let { appendLine("desktop_scale = $it") }
        appendLine("[graphics]")
        appendLine("renderer = \"adreno\"")
    }

    private fun profileStore(): ProfileStore {
        val files = temporaryFolder.newFolder("files")
        val context = object : ContextWrapper(null) {
            override fun getFilesDir(): File = files
        }
        return ProfileStore(context)
    }
}
