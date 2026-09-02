package com.example.poseexercise.views.activity

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.poseexercise.R
import com.example.poseexercise.databinding.ActivityMainBinding
import np.com.susanthapa.curved_bottom_navigation.CbnMenuItem

/**
 * Main Activity and entry point for the app.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var prefManager: PrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        prefManager = PrefManager(this)
        if (prefManager.isFirstTimeLaunch()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        increaseNotificationVolume()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val menuItems = arrayOf(
            CbnMenuItem(
                R.drawable.home,
                R.drawable.avd_home,
                R.id.homeFragment
            ),
            CbnMenuItem(
                R.drawable.workout,
                R.drawable.avd_workout,
                R.id.workoutFragment
            ),
            CbnMenuItem(
                R.drawable.plan,
                R.drawable.avd_plan,
                R.id.planStepOneFragment
            ),
            CbnMenuItem(
                R.drawable.profile,
                R.drawable.avd_profile,
                R.id.profileFragment
            )
        )
        binding.navView.setMenuItems(menuItems, 0)
        binding.navView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.workoutFragment) {
                binding.navView.visibility = android.view.View.GONE
            } else {
                binding.navView.visibility = android.view.View.VISIBLE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    companion object {
        var workoutResultData: String? = null
        var workoutTimer: String? = null
    }

    private fun increaseNotificationVolume() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_NOTIFICATION,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION),
            0
        )
    }
}