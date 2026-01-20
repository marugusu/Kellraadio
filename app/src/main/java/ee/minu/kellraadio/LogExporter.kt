package ee.minu.kellraadio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExporter {

    fun exportAndShareLog(context: Context) {
        try {
            val logDir = File(context.cacheDir, "logs")
            if (!logDir.exists()) logDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "kellraadio_log_$timestamp.txt"
            val logFile = File(logDir, fileName)

            val process = Runtime.getRuntime().exec("logcat -b all -d -v threadtime")

            val bufferedReader = process.inputStream.bufferedReader()
            val outputStream = logFile.outputStream().bufferedWriter()

            bufferedReader.use { reader ->
                outputStream.use { writer ->
                    reader.forEachLine { line ->
                        writer.write(line)
                        writer.newLine()
                    }
                }
            }

            shareFile(context, logFile)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shareFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // MUUDATUS: Kasutame tõlgitud pealkirja "Saada logi"
        val chooserTitle = context.getString(R.string.settings_send_log)
        val chooser = Intent.createChooser(intent, chooserTitle)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}