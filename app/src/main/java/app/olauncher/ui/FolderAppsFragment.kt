package app.olauncher.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.HomeFolder
import app.olauncher.data.LauncherTheme
import app.olauncher.data.Prefs
import app.olauncher.databinding.AdapterFolderAppBinding
import app.olauncher.databinding.FragmentFolderAppsBinding
import app.olauncher.helper.showToast
import java.text.Normalizer

/**
 * Checks apps for a home folder. With a [NAME] it creates that folder on the
 * slot and returns home; without one it edits the slot's folder and goes back.
 */
class FolderAppsFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private var theme = LauncherTheme.DEFAULT
    private var location = 0
    private var folderName = ""
    private var creating = false

    // Checked apps by key, in the order they were picked
    private val selected = LinkedHashMap<String, HomeFolder.App>()
    private var allApps: List<AppModel.App> = emptyList()
    private val adapter = PickAdapter()
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentFolderAppsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFolderAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        theme = prefs.launcherTheme
        val arguments = requireArguments()
        location = arguments.getInt(LOCATION)
        val existing = prefs.getFolder(location)
        creating = arguments.containsKey(NAME)
        folderName = arguments.getString(NAME) ?: existing?.name.orEmpty()
        if (!creating && selected.isEmpty()) existing?.apps?.forEach { selected[it.key] = it }

        binding.folderSearch.gravity = theme.drawerAlign ?: prefs.appLabelAlignment
        theme.styleText(binding.folderSearch, theme.drawerTextSize, theme.drawerFont)
        binding.folderSearch.doAfterTextChanged { filter() }
        binding.folderAppsList.layoutManager = LinearLayoutManager(requireContext())
        binding.folderAppsList.adapter = adapter
        binding.folderAppsDone.setOnClickListener { save() }

        viewModel.appList.observe(viewLifecycleOwner) { apps ->
            allApps = apps.orEmpty().filterIsInstance<AppModel.App>()
            filter()
        }
        viewModel.getAppList()
    }

    private fun filter() {
        val query = normalize(binding.folderSearch.text.toString())
        adapter.submit(if (query.isEmpty()) allApps else allApps.filter { normalize(it.appLabel).contains(query) })
    }

    private fun normalize(text: String) =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(diacritics, "").lowercase().trim()

    private fun save() {
        if (selected.isEmpty()) {
            requireContext().showToast(R.string.folder_pick_needed)
            return
        }
        prefs.setFolder(location, HomeFolder(folderName, selected.values.toList()))
        if (creating) findNavController().popBackStack(R.id.mainFragment, false)
        else findNavController().popBackStack()
    }

    private fun AppModel.App.toFolderApp() =
        HomeFolder.App(appLabel, appPackage, activityClassName, user.toString())

    private inner class PickAdapter : RecyclerView.Adapter<PickAdapter.Holder>() {

        private var apps: List<AppModel.App> = emptyList()

        inner class Holder(val binding: AdapterFolderAppBinding) : RecyclerView.ViewHolder(binding.root)

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<AppModel.App>) {
            apps = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = apps.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(AdapterFolderAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)).apply {
                binding.root.gravity = theme.drawerAlign ?: prefs.appLabelAlignment
                theme.styleText(binding.root, theme.drawerTextSize, theme.drawerFont)
            }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = apps[position]
            val key = app.toFolderApp().key
            holder.binding.root.text = if (key in selected) "${app.appLabel}  ✓" else app.appLabel
            holder.binding.root.setOnClickListener {
                if (selected.remove(key) == null) selected[key] = app.toFolderApp()
                notifyItemChanged(holder.bindingAdapterPosition)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val LOCATION = "location"
        const val NAME = "name"
    }
}
