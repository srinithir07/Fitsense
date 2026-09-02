package com.example.poseexercise.views.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.poseexercise.R
import com.example.poseexercise.adapters.PlanAdapter
import com.example.poseexercise.adapters.RecentActivityAdapter
import com.example.poseexercise.data.database.AppRepository
import com.example.poseexercise.data.plan.Plan
import com.example.poseexercise.data.results.RecentActivityItem
import com.example.poseexercise.data.results.WorkoutResult
import com.example.poseexercise.util.MemoryManagement
import com.example.poseexercise.util.MyApplication
import com.example.poseexercise.util.MyUtils
import com.example.poseexercise.viewmodels.AddPlanViewModel
import com.example.poseexercise.viewmodels.HomeViewModel
import com.example.poseexercise.viewmodels.ResultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlin.math.min

class HomeFragment : Fragment(), PlanAdapter.ItemListener, MemoryManagement {
    @Suppress("PropertyName")
    val TAG = "FitSense Home Fragment"
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var recentActivityRecyclerView: RecyclerView
    private lateinit var recentActivityAdapter: RecentActivityAdapter
    private var planList: List<Plan>? = emptyList()
    private var notCompletePlanList: MutableList<Plan>? = mutableListOf()
    private var today: String = DateFormat.format("EEEE", Date()).toString()
    private lateinit var progressText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var noPlanTV: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressPercentage: TextView
    private var workoutResults: List<WorkoutResult>? = null
    private lateinit var appRepository: AppRepository
    private lateinit var addPlanViewModel: AddPlanViewModel
    private lateinit var adapter: PlanAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressText = view.findViewById(R.id.exercise_left)
        recyclerView = view.findViewById(R.id.today_plans)
        recentActivityRecyclerView = view.findViewById(R.id.recentActivityRecyclerView)
        noPlanTV = view.findViewById(R.id.no_plan)
        progressBar = view.findViewById(R.id.progress_bar)
        progressPercentage = view.findViewById(R.id.progress_text)

        appRepository = AppRepository(requireActivity().application)
        resultViewModel = ResultViewModel(MyApplication.getInstance())
        addPlanViewModel = AddPlanViewModel(MyApplication.getInstance())
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        recentActivityAdapter = RecentActivityAdapter(emptyList())
        recentActivityRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        recentActivityRecyclerView.adapter = recentActivityAdapter

        adapter = PlanAdapter(requireContext())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.setListener(this)

        // Load recent activity history
        lifecycleScope.launch {
            val workoutResults = resultViewModel.getRecentWorkout()
            val imageResources = arrayOf(R.drawable.blue, R.drawable.green, R.drawable.orange)
            val recentActivityItems = workoutResults?.mapIndexed { index, it ->
                RecentActivityItem(
                    imageResId = imageResources[index % imageResources.size],
                    exerciseType = MyUtils.exerciseNameToDisplay(it.exerciseName),
                    reps = "${it.repeatedCount} reps"
                )
            }
            recentActivityAdapter.updateData(recentActivityItems ?: emptyList())
            if (recentActivityItems.isNullOrEmpty()) {
                recentActivityRecyclerView.isVisible = false
                val noActivityMessage = view.findViewById<TextView>(R.id.no_activity_message)
                noActivityMessage.text = getString(R.string.no_activities_yet)
                noActivityMessage.isVisible = true
            } else {
                recentActivityRecyclerView.isVisible = true
            }
        }

        // Live observation of plans & workout progress
        setupLivePlansObserver()
    }

    private fun setupLivePlansObserver() {
        appRepository.allPlans.observe(viewLifecycleOwner) { exercisePlans ->
            if (exercisePlans == null) return@observe

            lifecycleScope.launch(Dispatchers.IO) {
                val currentDay = DateFormat.format("EEEE", Date()).toString()

                // Reset completion flag for plans completed on previous days
                exercisePlans.forEach { plan ->
                    if (plan.completed && plan.timeCompleted != null) {
                        val completedDay = getDayFromTimestamp(plan.timeCompleted)
                        if (completedDay != currentDay) {
                            addPlanViewModel.updateComplete(false, null, plan.id)
                        }
                    }
                }

                // Filter plans for today
                val todayExercisePlans = exercisePlans.filter { it.selectedDays.contains(currentDay) }
                val notCompletedToday = todayExercisePlans.filter { !it.completed }.toMutableList()

                // Fetch today's workout results
                val allResults = resultViewModel.getAllResult()
                val todayResults = allResults?.filter { isToday(it.timestamp) } ?: emptyList()

                withContext(Dispatchers.Main) {
                    planList = todayExercisePlans
                    notCompletePlanList = notCompletedToday

                    // Update Today's Plans UI
                    if (notCompletedToday.isNotEmpty()) {
                        noPlanTV.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        adapter.setPlans(notCompletedToday)
                    } else {
                        noPlanTV.text = getString(R.string.there_is_no_plan_set_at_the_moment)
                        noPlanTV.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                        adapter.setPlans(mutableListOf())
                    }

                    val exerciseLeftString = resources.getString(R.string.exercise_left, notCompletedToday.size)
                    progressText.text = exerciseLeftString

                    // Calculate progress based on individual exercise completion targets
                    var totalPlannedRepetitions = 0
                    var effectiveCompletedRepetitions = 0

                    todayExercisePlans.forEach { plan ->
                        totalPlannedRepetitions += plan.repeatCount
                        val completedForThisExercise = todayResults
                            .filter { it.exerciseName.equalsIgnoreCaseOrContains(plan.exercise) }
                            .sumOf { it.repeatedCount }
                        effectiveCompletedRepetitions += minOf(completedForThisExercise, plan.repeatCount)
                    }

                    var progressPercent = if (totalPlannedRepetitions > 0) {
                        ((effectiveCompletedRepetitions.toDouble() / totalPlannedRepetitions) * 100).toInt()
                    } else {
                        0
                    }

                    // If incomplete plans remain today, cap progress at 99%
                    if (notCompletedToday.isNotEmpty() && progressPercent >= 100) {
                        progressPercent = 99
                    }

                    updateProgressViews(progressPercent, todayExercisePlans.isNotEmpty())
                }
            }
        }
    }

    private fun String.equalsIgnoreCaseOrContains(other: String): Boolean {
        return this.equalsIgnoreCase(other) || this.lowercase().contains(other.lowercase()) || other.lowercase().contains(this.lowercase())
    }

    private fun String.equalsIgnoreCase(other: String): Boolean {
        return this.equals(other, ignoreCase = true)
    }

    private fun updateProgressViews(progress: Int, hasPlans: Boolean) {
        if (hasPlans && progress > 0) {
            progressBar.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE
            progressPercentage.visibility = View.VISIBLE
            val cappedProgress = min(progress, 100)
            progressBar.progress = cappedProgress
            progressPercentage.text = String.format("%d%%", cappedProgress)
        } else if (hasPlans) {
            progressBar.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE
            progressPercentage.visibility = View.VISIBLE
            progressBar.progress = 0
            progressPercentage.text = "0%"
        } else {
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
            progressPercentage.visibility = View.GONE
        }
    }

    private fun isToday(s: Long, locale: Locale = Locale.getDefault()): Boolean {
        return try {
            val sdf = SimpleDateFormat("MM/dd/yyyy", locale)
            val netDate = Date(s)
            val currentDate = sdf.format(Date())
            sdf.format(netDate) == currentDate
        } catch (e: Exception) {
            false
        }
    }

    private fun getDayFromTimestamp(time: Long, locale: Locale = Locale.getDefault()): String? {
        return try {
            val sdf = SimpleDateFormat("EEEE", locale)
            val netDate = Date(time)
            sdf.format(netDate)
        } catch (e: Exception) {
            null
        }
    }

    override fun onItemClicked(planId: Int, position: Int) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder
            .setMessage("Are you sure you want to delete the plan?")
            .setTitle("Delete plan")
            .setPositiveButton("Delete") { dialog, _ ->
                lifecycleScope.launch {
                    addPlanViewModel.deletePlan(planId)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    override fun clearMemory() {
        planList = null
        notCompletePlanList = null
        workoutResults = null
    }

    override fun onDestroy() {
        clearMemory()
        super.onDestroy()
    }
}
