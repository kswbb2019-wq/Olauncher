package app.olauncher.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.HomeFolder
import app.olauncher.data.LauncherTheme
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentFolderBinding
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.showTextInputDialog
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.ViewSwipeTouchListener

/**
 * A home folder: its name, its apps styled like the home screen, and small
 * actions to add apps, rename or delete it. Back or a swipe down returns home.
 */
class FolderFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private var theme = LauncherTheme.DEFAULT
    private var location = 0

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentFolderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFolderBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        theme = prefs.launcherTheme
        location = requireArguments().getInt(LOCATION)

        // Swipes on the empty area; app rows report their own
        binding.folderContent.setOnTouchListener(object : OnSwipeTouchListener(requireContext()) {
            override fun onSwipeDown() {
                super.onSwipeDown()
                close()
            }
        })
        binding.folderAddApps.setOnClickListener {
            findNavController().navigate(
                R.id.action_folderFragment_to_folderAppsFragment,
                bundleOf(FolderAppsFragment.LOCATION to location)
            )
        }
        binding.folderRename.setOnClickListener { renameFolder() }
        binding.folderDelete.setOnClickListener { deleteFolder() }
    }

    override fun onResume() {
        super.onResume()
        populateFolder()
    }

    private fun populateFolder() {
        val folder = prefs.getFolder(location) ?: return close()

        // Same alignment, position and side margin as the home apps
        val horizontal = theme.homeAlign ?: prefs.homeAlignment
        val vertical = theme.homePosition
            ?: if (prefs.homeBottomAlignment) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val side = theme.homeMarginSide?.toInt() ?: if (landscape) 98 else 24
        binding.folderContent.gravity = horizontal or vertical
        binding.folderContent.setPadding(side.dpToPx(), 0, side.dpToPx(), 0)
        (binding.folderActions.layoutParams as LinearLayout.LayoutParams).gravity = horizontal

        binding.folderName.text = folder.name
        binding.folderName.gravity = horizontal
        theme.styleText(binding.folderName, theme.homeTextSize, theme.homeFont)

        binding.folderApps.removeAllViews()
        // Uninstalled apps are skipped quietly and stay saved
        folder.apps
            .filter { isPackageInstalled(requireContext(), it.packageName, it.user) }
            .forEach { binding.folderApps.addView(appView(it, horizontal)) }
        binding.folderEmpty.isVisible = binding.folderApps.childCount == 0
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun appView(app: HomeFolder.App, gravity: Int) =
        TextView(requireContext(), null, 0, R.style.TextLarge).apply {
            text = app.name
            this.gravity = gravity
            val spacing = theme.homeAppSpacing?.toInt()?.dpToPx()
                ?: resources.getDimensionPixelSize(R.dimen.home_app_padding_vertical)
            setPadding(0, spacing, 0, spacing)
            theme.styleText(this, theme.homeTextSize, theme.homeFont)
            setOnTouchListener(object : ViewSwipeTouchListener(requireContext(), this) {
                override fun onClick(view: View) {
                    super.onClick(view)
                    launchApp(app)
                }

                override fun onLongClick(view: View) {
                    super.onLongClick(view)
                    view.showPopupMenu(configure = { it.add(R.string.folder_remove_app) }) { removeApp(app) }
                }

                override fun onSwipeDown() {
                    super.onSwipeDown()
                    close()
                }
            })
        }

    private fun launchApp(app: HomeFolder.App) {
        viewModel.selectedApp(
            AppModel.App(
                appLabel = app.name,
                key = null,
                appPackage = app.packageName,
                activityClassName = app.activityClassName,
                isNew = false,
                user = getUserHandleFromString(requireContext(), app.user)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun removeApp(app: HomeFolder.App) {
        val folder = prefs.getFolder(location) ?: return
        prefs.setFolder(location, folder.copy(apps = folder.apps.filterNot { it.key == app.key }))
        if (_binding != null) populateFolder()
    }

    private fun renameFolder() {
        val folder = prefs.getFolder(location) ?: return
        requireContext().showTextInputDialog(
            title = R.string.rename,
            action = R.string.okay,
            initial = folder.name,
            blankMessage = R.string.folder_name_needed,
        ) { name ->
            prefs.getFolder(location)?.let { prefs.setFolder(location, it.copy(name = name)) }
            if (_binding != null) populateFolder()
        }
    }

    private fun deleteFolder() {
        requireContext().showMessageDialog(
            R.string.folder_delete,
            R.string.folder_delete_message,
            R.string.folder_delete_confirm
        ) {
            prefs.setFolder(location, null)
            if (isAdded) close()
        }
    }

    private fun close() {
        findNavController().popBackStack(R.id.mainFragment, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val LOCATION = "location"
    }
}
