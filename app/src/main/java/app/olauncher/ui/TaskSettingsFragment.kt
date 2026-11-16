package app.olauncher.ui

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.data.TokenStore
import app.olauncher.databinding.FragmentTaskSettingsBinding
import app.olauncher.helper.TaskSync
import app.olauncher.helper.showToast
import java.util.Date

/** Task settings: where tasks.json lives on GitHub, the token, and the sync status. */
class TaskSettingsFragment : BaseFragment() {

    private lateinit var prefs: Prefs

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
