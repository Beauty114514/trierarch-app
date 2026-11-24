package app.trierarch.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Hands the verified APK to the system installer; the user must confirm. */
internal object ApkInstaller {
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
