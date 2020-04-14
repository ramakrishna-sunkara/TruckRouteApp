package com.tomtom.timetoleave

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class SafeTravelsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_safe_travels)

        initToolbarSettings()

        val btnNextTrip = findViewById<Button>(R.id.button_safe_travels_next_trip)
        btnNextTrip.setOnClickListener {
            val intent = Intent(this@SafeTravelsActivity, MainActivity::class.java)
            startActivity(intent)
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
