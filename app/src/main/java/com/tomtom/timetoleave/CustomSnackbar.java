package com.tomtom.timetoleave;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.google.android.material.snackbar.BaseTransientBottomBar;

public class CustomSnackbar extends BaseTransientBottomBar<CustomSnackbar> {
    private CustomSnackbar(ViewGroup parent, View content,
                           ContentViewCallback contentViewCallback) {
        super(parent, content, contentViewCallback);
    }

    private static class ContentViewCallback implements
            BaseTransientBottomBar.ContentViewCallback {

        private final View content;

        private ContentViewCallback(View content) {
            this.content = content;
        }

        @Override
        public void animateContentIn(int delay, int duration) {
            content.setAlpha(0f);
            ViewCompat.animate(content)
                    .alpha(1f).setDuration(duration)
                    .setStartDelay(delay);
        }

        @Override
        public void animateContentOut(int delay, int duration) {
            content.setAlpha(1f);
            ViewCompat.animate(content)
                    .alpha(0f)
                    .setDuration(duration)
                    .setStartDelay(delay);
        }
    }

    public static CustomSnackbar make(ViewGroup parent, int duration, int layout) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View content = inflater.inflate(layout, parent, false);

        ContentViewCallback callback= new ContentViewCallback(content);
        CustomSnackbar customSnackbar = new CustomSnackbar(parent, content, callback);

        customSnackbar.setDuration(duration);
        return customSnackbar;
    }

    public void setText(CharSequence text) {
        TextView textView = getView().findViewById(R.id.text_view_dialog_recalculation_info);
        textView.setText(text);
    }

    public void setAction(CharSequence text, final View.OnClickListener listener) {
        Button actionView = getView().findViewById(R.id.button_dialog_recalculation_ok);
        actionView.setText(text);
        actionView.setVisibility(View.VISIBLE);
        actionView.setOnClickListener(listener);
    }
}
