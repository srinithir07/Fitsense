package com.example.poseexercise.util

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class MyApplication : Application() {
    companion object {
        private lateinit var instance: MyApplication

        fun getInstance(): MyApplication {
            return instance
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize FitSense theme mode (default: Light)
        val prefs = getSharedPreferences("FitSenseThemePref", MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", "light")
        when (mode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}