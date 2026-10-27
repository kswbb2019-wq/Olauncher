package app.olauncher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.findNavController
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentWidgetsBinding
import app.olauncher.helper.dpToPx
import app.olauncher.helper.widgetHost
import kotlin.math.min

class WidgetsFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private var editing = false

    private var _binding: FragmentWidgetsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWidgetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        widgetHost = requireContext().widgetHost()
        widgetManager = AppWidgetManager.getInstance(requireContext())

        binding.widgetsRoot.onSwipeRight = { findNavController().popBackStack() }
        binding.addWidget.setOnClickListener { showWidgetPicker() }
        binding.editWidgets.setOnClickListener {
            editing = !editing
            populateWidgets()
        }
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    // Also picks up widgets that MainActivity finished adding in onActivityResult
    override fun onResume() {
        super.onResume()
        populateWidgets()
    }

    override fun onStop() {
        widgetHost.stopListening()
        super.onStop()
    }

    private fun populateWidgets() {
        val container = binding.widgetsContainer
        container.removeAllViews()
        val columnWidth = min(resources.displayMetrics.widthPixels - 48.dpToPx(), 640.dpToPx())
        container.updateLayoutParams { width = columnWidth }

        for (widgetId in prefs.widgetIds) {
            val info = widgetManager.getAppWidgetInfo(widgetId) ?: continue // app uninstalled
            val height = widgetHeight(info)
            val hostView = widgetHost.createView(requireContext(), widgetId, info)
            val density = resources.displayMetrics.density
            val widthDp = (columnWidth / density).toInt()
            val heightDp = (height / density).toInt()
            @Suppress("DEPRECATION")
            hostView.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)

            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            if (editing) row.addView(removeButton(widgetId, info))
            row.addView(hostView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height))
            container.addView(
                row,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = 16.dpToPx() }
            )
        }

        val hasWidgets = container.childCount > 0
        if (!hasWidgets) editing = false
        binding.widgetsEmpty.isVisible = !hasWidgets
        binding.editWidgets.isVisible = hasWidgets
        binding.editWidgets.setText(if (editing) R.string.widgets_done else R.string.widgets_edit)
    }

    // Newer widgets give a preferred size in home screen cells, older ones only a minimum
    private fun widgetHeight(info: AppWidgetProviderInfo): Int {
        val cells = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.targetCellHeight else 0
        return maxOf(info.minHeight, cells * 96.dpToPx())
    }

    private fun removeButton(widgetId: Int, info: AppWidgetProviderInfo): TextView =
        TextView(requireContext(), null, 0, R.style.TextSmallBold).apply {
            text = getString(R.string.widgets_remove, info.loadLabel(requireContext().packageManager))
            setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
            setOnClickListener {
                widgetHost.deleteAppWidgetId(widgetId)
                prefs.widgetIds = prefs.widgetIds - widgetId
                populateWidgets()
            }
        }

    private fun showWidgetPicker() {
        val packageManager = requireContext().packageManager
        val providers = widgetManager.installedProviders
            .map { info ->
                val appLabel = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(info.provider.packageName, 0)
                    ).toString()
                } catch (e: Exception) {
                    info.provider.packageName
                }
                "$appLabel: ${info.loadLabel(packageManager)}" to info
            }
            .sortedBy { it.first.lowercase() }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.widgets_add)
            .setItems(providers.map { it.first }.toTypedArray()) { _, which ->
                (requireActivity() as MainActivity).addWidget(providers[which].second)
                if (_binding != null) populateWidgets()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
