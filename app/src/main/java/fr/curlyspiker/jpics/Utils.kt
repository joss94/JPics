package fr.curlyspiker.jpics

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import java.io.ByteArrayOutputStream
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
}