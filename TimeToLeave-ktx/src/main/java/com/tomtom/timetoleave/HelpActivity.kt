package com.tomtom.timetoleave

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        initToolbarSettings()

        findViewById<ImageView>(R.id.imageview_help_mainimage).apply {
            this.setOnClickListener { finish() }
        }
    }

    private fun initToolbarSettings() {
        val toolbar = findViewById<Toolbar>(R.id.custom_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            this.setHomeAsUpIndicator(R.drawable.ic_close)
            this.setDisplayHomeAsUpEnabled(true)
            this.setDisplayShowHomeEnabled(true)
            this.setDisplayShowTitleEnabled(false)
        }
    }
}
