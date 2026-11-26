package app.trierarch

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.trierarch.nativebridge.NativePtyBridge
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChrootCommandInstrumentedTest {
    private fun rootfs(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.filesDir, "rootfs/archlinux-test")
    }

    private fun config(arguments: List<String>): Array<String> = buildList {
        add("rootfs=${rootfs().absolutePath}")
        add("launch_argv.count=${arguments.size}")
        arguments.forEachIndexed { index, value -> add("launch_argv.$index=$value") }
    }.toTypedArray()

    @Test
    fun runsACommandInsideChrootAndCapturesOutput() {
        assumeTrue(rootfs().isDirectory)
        val result = NativePtyBridge.runChrootCommand(
            config(listOf("/bin/echo", "trierarch-chroot-probe")),
        )
        val newline = result.indexOf('\n')
        assertTrue("output: $result", newline > 0)
        assertTrue("output: $result", result.substring(newline + 1).contains("trierarch-chroot-probe"))
    }

    @Test
    fun runsPacmanQueryInsideChroot() {
        assumeTrue(rootfs().isDirectory)
        val result = NativePtyBridge.runChrootCommand(
            config(listOf("/usr/bin/pacman", "-Q", "bash")),
        )
        val newline = result.indexOf('\n')
        assertTrue("output: $result", newline > 0)
        assertTrue("output: $result", result.substring(newline + 1).contains("bash"))
    }
}

@RunWith(AndroidJUnit4::class)
class DroidSpacesCommandInstrumentedTest {
    private fun config(arguments: List<String>): Array<String> = buildList {
        add("container=Arch")
        add("launch_argv.count=${arguments.size}")
        arguments.forEachIndexed { index, value -> add("launch_argv.$index=$value") }
        add("stop_after=true")
    }.toTypedArray()

    @Test
    fun runsPacmanQueryInsideDroidSpaces() {
        assumeTrue(
            runCatching { NativePtyBridge.listDroidspacesContainers() }
                .getOrDefault("")
                .lineSequence()
                .any { it == "Arch" },
        )
        val result = NativePtyBridge.runDroidspacesCommand(
            config(listOf("/usr/bin/pacman", "-Q", "bash")),
        )
        val newline = result.indexOf('\n')
        assertTrue("output: $result", newline > 0)
        assertTrue("output: $result", result.substring(newline + 1).contains("bash"))
    }
}
