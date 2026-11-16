package app.olauncher.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.data.TaskDates
import app.olauncher.data.TokenStore
import app.olauncher.databinding.FragmentTaskSettingsBinding
import app.olauncher.helper.TaskReminders
import app.olauncher.helper.TaskSync
import app.olauncher.helper.showToast
import java.util.Date
import java.util.Locale

/** Task settings: home display, reminders, where tasks.json lives on GitHub, the token and sync status. */
class TaskSettingsFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private val viewModel: MainViewModel by activityViewModels()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.isWidgetSetupOpen = false
        if (!isAdded) return@registerForActivityResult
        if (granted) setReminders(true)
        else requireContext().showToast(R.string.tasks_notifications_blocked)
    }

    private var _binding: FragmentTaskSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTaskSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        populateTasksOnHome()
        binding.tasksOnHome.setOnClickListener {
            prefs.showTasksOnHome = !prefs.showTasksOnHome
            populateTasksOnHome()
        }
        binding.tasksOpenAll.setOnClickListener {
            findNavController().navigate(R.id.action_taskSettingsFragment_to_tasksFragment)
        }
        populateReminders()
        binding.taskReminders.setOnClickListener { toggleReminders() }
        binding.taskReminderTime.setOnClickListener { pickReminderTime() }

        binding.taskOwner.setText(prefs.taskRepoOwner)
        binding.taskRepo.setText(prefs.taskRepoName)
        binding.taskBranch.setText(prefs.taskRepoBranch)
        binding.taskPath.setText(prefs.taskRepoPath)
        populateToken()

        binding.taskSave.setOnClickListener { saveAndSync() }
        binding.taskSyncNow.setOnClickListener { syncNow() }
        binding.taskTokenRemove.setOnClickListener {
            TokenStore.set(requireContext(), "")
            populateToken()
        }
        TaskSync.statusChanges.observe(viewLifecycleOwner) { populateStatus() }
    }

    private fun populateTasksOnHome() {
        binding.tasksOnHome.text = getString(if (prefs.showTasksOnHome) R.string.on else R.string.off)
    }

    private fun populateReminders() {
        binding.taskReminders.text = getString(if (prefs.taskReminders) R.string.on else R.string.off)
        binding.taskReminderTime.text = prefs.taskReminderTime
    }

    // Android 13+ asks for permission to post notifications first
    private fun toggleReminders() {
        if (prefs.taskReminders) return setReminders(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Keeps the launcher from returning home while the permission dialog shows
            viewModel.isWidgetSetupOpen = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        setReminders(true)
    }

    private fun setReminders(enabled: Boolean) {
        prefs.taskReminders = enabled
        TaskReminders.reschedule(requireContext())
        if (_binding != null) populateReminders()
    }

    private fun pickReminderTime() {
        val (hour, minute) = TaskDates.parseTime(prefs.taskReminderTime) ?: (8 to 0)
        TimePickerDialog(requireContext(), { _, h, m ->
            prefs.taskReminderTime = String.format(Locale.US, "%02d:%02d", h, m)
            TaskReminders.reschedule(requireContext())
            if (_binding != null) populateReminders()
        }, hour, minute, DateFormat.is24HourFormat(requireContext())).show()
    }

    // An empty field keeps the saved token; its value is never put back on screen
    private fun populateToken() {
        binding.taskToken.setText("")
        binding.taskToken.hint = getString(
            if (TokenStore.has(requireContext())) R.string.tasks_token_saved_hint else R.string.tasks_token_hint
        )
    }

    private fun saveAndSync() {
        prefs.taskRepoOwner = binding.taskOwner.text.toString().trim().ifEmpty { Constants.Tasks.REPO_OWNER }
        prefs.taskRepoName = binding.taskRepo.text.toString().trim().ifEmpty { Constants.Tasks.REPO_NAME }
        prefs.taskRepoBranch = binding.taskBranch.text.toString().trim().ifEmpty { Constants.Tasks.REPO_BRANCH }
        prefs.taskRepoPath = binding.taskPath.text.toString().trim().ifEmpty { Constants.Tasks.REPO_PATH }
        val token = binding.taskToken.text.toString().trim()
        if (token.isNotEmpty()) TokenStore.set(requireContext(), token)
        populateToken()
        requireContext().showToast(R.string.tasks_saved)
        syncNow()
    }

    private fun syncNow() {
        binding.taskSyncStatus.text = getString(R.string.tasks_syncing)
        TaskSync.syncInBackground(requireContext())
    }

    private fun populateStatus() {
        val binding = _binding ?: return
        binding.taskSyncStatus.text = getString(statusText(prefs))
        val lastSync = prefs.taskLastSync
        binding.taskLastSync.text = getString(
            R.string.tasks_last_sync,
            if (lastSync == 0L) getString(R.string.tasks_never)
            else DateFormat.getMediumDateFormat(requireContext()).format(Date(lastSync)) + ", " +
                    DateFormat.getTimeFormat(requireContext()).format(Date(lastSync))
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Short status for the settings row and this screen. */
        fun statusText(prefs: Prefs): Int = when (prefs.taskSyncStatus) {
            Constants.TaskSyncStatus.OK -> R.string.tasks_status_ok
            Constants.TaskSyncStatus.PENDING -> R.string.tasks_status_pending
            Constants.TaskSyncStatus.ERROR -> R.string.tasks_status_error
            Constants.TaskSyncStatus.TOKEN -> R.string.tasks_status_token
            else -> R.string.tasks_status_never
        }
    }
}
