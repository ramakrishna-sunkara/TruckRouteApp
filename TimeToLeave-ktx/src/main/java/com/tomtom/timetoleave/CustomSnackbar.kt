package com.tomtom.timetoleave

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

import androidx.core.view.ViewCompat

import com.google.android.material.snackbar.BaseTransientBottomBar

class CustomSnackbar constructor(parent: ViewGroup, content: View, contentViewCallback: ContentViewCallback) :
        BaseTransientBottomBar<CustomSnackbar>(parent, content, contentViewCallback) {

    class ContentViewCallback constructor(private val content: View) : com.google.android.material.snackbar.ContentViewCallback {

        override fun animateContentIn(delay: Int, duration: Int) {
            content.alpha = 0f
            ViewCompat.animate(content)
                    .alpha(1f).setDuration(duration.toLong()).startDelay = delay.toLong()
        }

        override fun animateContentOut(delay: Int, duration: Int) {
            content.alpha = 1f
            ViewCompat.animate(content)
                    .alpha(0f)
                    .setDuration(duration.toLong()).startDelay = delay.toLong()
        }
    }

    fun setText(text: CharSequence) {
        val textView = getView().findViewById<TextView>(R.id.text_view_dialog_recalculation_info)
        textView.text = text
    }

    fun setAction(text: CharSequence, listener: (Any) -> Unit) {
        getView().findViewById<Button>(R.id.button_dialog_recalculation_ok).apply {
            this.text = text
            this.visibility = View.VISIBLE
            this.setOnClickListener(listener)
        }
    }

    companion object {
        fun make(parent: ViewGroup, duration: Int, layout: Int): CustomSnackbar {
            val inflater = LayoutInflater.from(parent.context)
            val content = inflater.inflate(layout, parent, false)

            val callback = ContentViewCallback(content)
            return CustomSnackbar(parent, content, callback).apply {
                this.duration = duration
            }
        }
    }
}
