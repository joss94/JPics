package fr.curlyspiker.jpics

import android.content.Context
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


object LogManager {

    class LogItem(val message: String)

    val logMessages = mutableListOf<LogItem>()
    var logsChangedCallback: () -> Unit = {}

    fun addLog(message: String) {
        synchronized(this) {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            val current = LocalDateTime.now().format(formatter)
            logMessages.add(LogItem("[$current] $message"))
        }
    }

    fun flushToFile(ctx: Context) {
        var logsChanged = false
        val sb = StringBuilder()
        synchronized(this) {
            while (logMessages.isNotEmpty()) {
                logMessages.removeFirstOrNull()?.let {
                    sb.append(it.message).append("\n")
                    logsChanged = true
                }
            }
        }

        if (logsChanged) {
            Utils.writeLog(sb.toString(), ctx)
            logsChangedCallback()
        }
    }
}