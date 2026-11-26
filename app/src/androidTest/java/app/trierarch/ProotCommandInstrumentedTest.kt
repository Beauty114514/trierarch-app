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
class ProotCommandInstrumentedTest {
    private fun context(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun config(arguments: List<String>): Array<String> = buildList {
        val context = context()
        add("rootfs=${File(context.filesDir, "rootfs/archlinux-test").absolutePath}")
        add("shell=/bin/sh")
        add("native_library_directory=${context.applicationInfo.nativeLibraryDir}")
        add("cache_directory=${context.cacheDir.absolutePath}")
        add("launch_argv.count=${arguments.size}")
        arguments.forEachIndexed { index, value -> add("launch_argv.$index=$value") }
    }.toTypedArray()

    @Test
    fun runsACommandInsideProotAndCapturesOutput() {
        assumeTrue(File(context().filesDir, "rootfs/archlinux-test").isDirectory)
        val result = NativePtyBridge.runProotCommand(
            config(listOf("/bin/echo", "trierarch-proot-probe")),
        )
        val newline = result.indexOf('\n')
        assertTrue("output: $result", newline > 0)
        assertTrue("output: $result", result.substring(newline + 1).contains("trierarch-proot-probe"))
    }

    @Test
    fun runsPacmanQueryInsideProot() {
        assumeTrue(File(context().filesDir, "rootfs/archlinux-test").isDirectory)
        val result = NativePtyBridge.runProotCommand(
            config(listOf("/usr/bin/pacman", "-Q", "bash")),
        )
        val newline = result.indexOf('\n')
        assertTrue("output: $result", newline > 0)
        assertTrue("output: $result", result.substring(newline + 1).contains("bash"))
    }
}

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
