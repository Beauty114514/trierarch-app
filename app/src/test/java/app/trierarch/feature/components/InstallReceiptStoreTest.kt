package app.trierarch.feature.components

import app.trierarch.update.InstallReceipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InstallReceiptStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun recordsAndRemovesAReceipt() {
        val store = InstallReceiptStore(temporaryFolder.newFolder("receipts"))
        val receipt = InstallReceipt(
            target = "droidspaces:Arch",
            componentId = "mesa",
            releaseVersion = "27.0.0-91",
            packages = mapOf("mesa" to "1:27.0.0-91", "vulkan-freedreno" to "1:27.0.0-91"),
        )

        store.record(receipt)

        val loaded = store.receipt("droidspaces:Arch", "mesa", "27.0.0-91")
        assertEquals(receipt, loaded)
        assertEquals(listOf(receipt), store.all())

        store.remove("droidspaces:Arch", "mesa", "27.0.0-91")
        assertNull(store.receipt("droidspaces:Arch", "mesa", "27.0.0-91"))
    }

    @Test
    fun keepsReceiptsForDifferentTargets() {
        val store = InstallReceiptStore(temporaryFolder.newFolder("receipts"))
        val first = InstallReceipt("proot:/a", "mesa", "1", mapOf("mesa" to "1"))
        val second = InstallReceipt("droidspaces:Arch", "mesa", "1", mapOf("mesa" to "1"))

        store.record(first)
        store.record(second)

        assertEquals(first, store.receipt("proot:/a", "mesa", "1"))
        assertEquals(second, store.receipt("droidspaces:Arch", "mesa", "1"))
    }
}
