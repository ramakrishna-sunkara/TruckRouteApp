package com.tomtom.timetoleave

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.app.Dialog
import java.util.Calendar
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity

/**
 * A simple [FragmentActivity] subclass.
 */
class TimePickerFragment : DialogFragment(), TimePickerDialog.OnTimeSetListener {

    private lateinit var mainActivity: MainActivity

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mainActivity = activity as MainActivity
        val currentHour = mainActivity.calArriveAt.get(Calendar.HOUR_OF_DAY)
        val currentMinute = mainActivity.calArriveAt.get(Calendar.MINUTE)

        return TimePickerDialog(mainActivity, this, currentHour, currentMinute,
                DateFormat.is24HourFormat(mainActivity))
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        mainActivity.calArriveAt.set(Calendar.HOUR_OF_DAY, hourOfDay)
        mainActivity.calArriveAt.set(Calendar.MINUTE, minute)
        mainActivity.setTimerDisplay()
    }
}