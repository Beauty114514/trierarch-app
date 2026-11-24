package app.trierarch.core

import android.content.ContextWrapper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProotAudioProfileTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun pipewireIsAcceptedAndReadForProotProfiles() {
        val rootfs = rootfs()
        val profile = profile(rootfs, "[audio]\nbackend = \"pipewire\"")
        val store = profileStore()

        assertEquals("proot-audio", store.validate(profile))
        assertEquals(ProfileService.AUDIO_PIPEWIRE, store.prootProfile(profile).audioBackend)
    }

    @Test
    fun audioRemainsDisabledByDefaultForProotProfiles() {
        val profile = profile(rootfs())

        assertEquals(ProfileService.AUDIO_OFF, profileStore().prootProfile(profile).audioBackend)
    }

    private fun rootfs(): File = temporaryFolder.newFolder("rootfs").apply {
        File(this, "bin/bash").apply {
            parentFile?.mkdirs()
            writeText("")
        }
    }

    private fun profile(rootfs: File, audio: String = ""): String = """
        id = "proot-audio"
        runtime = "proot"
        rootfs = "${rootfs.absolutePath}"
        shell = "/bin/bash"
        $audio
    """.trimIndent()

    private fun profileStore(): ProfileService {
        val files = temporaryFolder.newFolder("files")
        val context = object : ContextWrapper(null) {
            override fun getFilesDir(): File = files
        }
        return ProfileService(context)
    }
}
