package com.tomtom.timetoleave;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.TimePicker;
import androidx.fragment.app.DialogFragment;

import org.jetbrains.annotations.NotNull;

import java.util.Calendar;


public class TimePickerFragment extends DialogFragment implements TimePickerDialog.OnTimeSetListener{

    private MainActivity mainActivity;

    @NotNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){
        mainActivity = (MainActivity)getActivity();
        int currentHour = mainActivity.getCalArriveAt().get(Calendar.HOUR_OF_DAY);
        int currentMinute = mainActivity.getCalArriveAt().get(Calendar.MINUTE);

        return new TimePickerDialog(getActivity(),this, currentHour, currentMinute,
                DateFormat.is24HourFormat(getActivity()));
    }

    public void onTimeSet(TimePicker view, int hourOfDay, int minute){
        mainActivity.getCalArriveAt().set(Calendar.HOUR_OF_DAY, hourOfDay);
        mainActivity.getCalArriveAt().set(Calendar.MINUTE, minute);
        mainActivity.setTimerDisplay();
    }
}