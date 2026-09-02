package com.example.poseexercise.views.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.poseexercise.R

/**
 * SplashActivity: A simple splash screen that appears when the app is launched.
 *
 * This activity displays a splash screen for a specified duration and then navigates
 * to the MainActivity.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Use a Handler to delay the intent navigation
        Handler(Looper.getMainLooper()).postDelayed({
            val prefManager = PrefManager(this)
            val intent = if (prefManager.isFirstTimeLaunch()) {
                Intent(this, OnboardingActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java)
            }

            // Start activity and finish SplashActivity
            startActivity(intent)
            finish()
        }, 1000)
    }
}
