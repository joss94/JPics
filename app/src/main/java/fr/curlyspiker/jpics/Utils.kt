package fr.curlyspiker.jpics

import android.R.attr.data
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import java.io.*
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*


object Utils {
    fun getDatetime(supportFragmentManager: FragmentManager, originalDate: Date, callback: (timeInMillis: Long) -> Unit) {
        val c = Calendar.getInstance()
        c.time = originalDate

        var hour = c.get(Calendar.HOUR_OF_DAY)
        var minute = c.get(Calendar.MINUTE)
        var year = c.get(Calendar.YEAR)
        var month = c.get(Calendar.MONTH)
        var day = c.get(Calendar.DAY_OF_MONTH)

        class TimePickerFragment : DialogFragment(), TimePickerDialog.OnTimeSetListener {

            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                return TimePickerDialog(requireContext(), R.style.DatetimePickerStyle, this, hour, minute, DateFormat.is24HourFormat(activity))
            }

            override fun onTimeSet(view: TimePicker, h: Int, m: Int) {
                hour = h
                minute = m

                c.set(year, month, day, hour, minute)
                callback(c.timeInMillis)
            }
        }

        class DatePickerFragment : DialogFragment(), DatePickerDialog.OnDateSetListener {

            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                return DatePickerDialog(requireContext(), R.style.DatetimePickerStyle, this, year, month, day)
            }

            override fun onDateSet(view: DatePicker, y: Int, m: Int, d: Int) {
                year = y
                month = m
                day = d

                TimePickerFragment().show(supportFragmentManager, "timePicker")
            }
        }

        DatePickerFragment().show(supportFragmentManager, "datePicker")
    }

    fun imgToByteArray(img: Bitmap): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        return byteArrayOutputStream.toByteArray()
    }

    fun md5(input:ByteArray): String {
        return BigInteger(1, MessageDigest.getInstance("MD5").digest(input)).toString(16).padStart(32, '0')
    }

    fun writeLog(input: String, ctx: Context) {
        Log.d("LOG", input)
        try {
            val outputStream: OutputStream = ctx.openFileOutput("log.txt", Context.MODE_PRIVATE or Context.MODE_APPEND)
            val outputStreamWriter = OutputStreamWriter(outputStream)
            outputStreamWriter.append(input)
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
        }
    }

    fun readLog(ctx: Context): String {
        var ret = ""

        try {
            val inputStream: InputStream = ctx.openFileInput("log.txt")
            val inputStreamReader = InputStreamReader(inputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            var receiveString: String? = ""
            val stringBuilder = StringBuilder()
            while (bufferedReader.readLine().also { receiveString = it } != null) {
                Log.d("LOG", "Read line: $receiveString")
                stringBuilder.append("\n").append(receiveString)
            }
            inputStreamReader.close()
            ret = stringBuilder.toString()
        } catch (e: FileNotFoundException) {
            Log.e("login activity", "File not found: $e")
        } catch (e: IOException) {
            Log.e("login activity", "Can not read file: $e")
        }

        return ret
    }

    fun clearLog(ctx: Context) {
        try {
            val outputStream: OutputStream = ctx.openFileOutput("log.txt", Context.MODE_PRIVATE)
            val outputStreamWriter = OutputStreamWriter(outputStream)
            outputStreamWriter.write("")
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
        }
    }
}