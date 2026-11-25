package app.trierarch.ui.gui

import android.content.DialogInterface
import android.graphics.Typeface
import android.widget.EditText
import app.trierarch.core.ProfileDocument
import app.trierarch.update.ComponentRelease
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal object GuiDialogs {
    fun promptCreate(host: GuiHost) {
        val input = EditText(host.context).apply {
            hint = "profile id"
            setSingleLine()
            typeface = Typeface.MONOSPACE
        }
        val dialog = MaterialAlertDialogBuilder(host.context)
            .setTitle("New environment")
            .setMessage("Creates the profile template; edit it before running.")
            .setView(input)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val id = input.text.toString().trim()
                if (id.isEmpty()) {
                    host.showError("profile id is empty")
                    return@setOnClickListener
                }
                dialog.dismiss()
                host.profiles.create(id)
            }
        }
        dialog.show()
    }

    fun promptImport(host: GuiHost) {
        val input = EditText(host.context).apply {
            hint = "rootfs name"
            setSingleLine()
            typeface = Typeface.MONOSPACE
        }
        MaterialAlertDialogBuilder(host.context)
            .setTitle("Import rootfs")
            .setMessage("Choose a .tar.xz archive next. The name becomes files/rootfs/<name>.")
            .setView(input)
            .setPositiveButton("Choose archive") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    host.showError("rootfs name is empty")
                } else {
                    host.pickArchive(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun confirmDelete(host: GuiHost, document: ProfileDocument) {
        MaterialAlertDialogBuilder(host.context)
            .setTitle("Delete ${document.id}?")
            .setMessage("The profile file is removed. A running session is not stopped.")
            .setPositiveButton("Delete") { _, _ -> host.profiles.delete(document.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun confirmRemoveComponent(host: GuiHost, component: ComponentRelease) {
        MaterialAlertDialogBuilder(host.context)
            .setTitle("Delete ${component.name}?")
            .setMessage(
                "Removes files/components/${component.file}. " +
                    "A copy already placed in a rootfs is not touched.",
            )
            .setPositiveButton("Delete") { _, _ -> host.updates.deleteComponent(component) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
