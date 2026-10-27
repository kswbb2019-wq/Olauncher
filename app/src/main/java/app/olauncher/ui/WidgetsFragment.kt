package app.olauncher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentWidgetsBinding
import app.olauncher.helper.dpToPx
import app.olauncher.helper.widgetHost
import java.util.Collections

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

        binding.widgetsRoot.onSwipeLeft = { findNavController().popBackStack() }
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
        val maxWidth = resources.configuration.screenWidthDp - 48
        // Ids whose app is gone or unavailable are skipped but kept
        val widgets = prefs.widgetIds.mapNotNull { id -> widgetManager.getAppWidgetInfo(id)?.let { id to it } }
        val widgetContext = widgetContext()

        widgets.forEachIndexed { index, (widgetId, info) ->
            val (width, height) = widgetSize(widgetId, info, maxWidth)
            val hostView = widgetHost.createView(widgetContext, widgetId, info)
            @Suppress("DEPRECATION")
            hostView.updateAppWidgetSize(Bundle(), width, height, width, height)

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            if (editing) row.addView(
                editControls(
                    widgetId, info, width, height, maxWidth,
                    previousId = widgets.getOrNull(index - 1)?.first,
                    nextId = widgets.getOrNull(index + 1)?.first,
                )
            )
            row.addView(hostView, LinearLayout.LayoutParams(width.dpToPx(), height.dpToPx()))
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

    // The activity's inflater swaps in AppCompat views, whose overridden setters RemoteViews
    // refuses ("Couldn't add widget"), so widgets inflate from the app context with the system theme
    private fun widgetContext() = ContextThemeWrapper(
        requireContext().applicationContext,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) android.R.style.Theme_DeviceDefault_DayNight
        else android.R.style.Theme_DeviceDefault_Light
    )

    /** Width and height in dp: the saved size, or what the widget asks for, kept on screen. */
    private fun widgetSize(widgetId: Int, info: AppWidgetProviderInfo, maxWidth: Int): Pair<Int, Int> {
        val saved = prefs.getWidgetSize(widgetId)
        val width = saved?.first ?: DEFAULT_WIDTH
        val height = saved?.second ?: defaultHeight(info)
        return clampSize(width, height, maxWidth)
    }

    private fun clampSize(width: Int, height: Int, maxWidth: Int) =
        width.coerceIn(MIN_SIZE, maxOf(MIN_SIZE, maxWidth)) to height.coerceIn(MIN_SIZE, MAX_HEIGHT)

    // Newer widgets give a preferred size in home screen cells, older ones only a minimum in px
    private fun defaultHeight(info: AppWidgetProviderInfo): Int {
        val cells = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.targetCellHeight else 0
        return maxOf((info.minHeight / resources.displayMetrics.density).toInt(), cells * CELL_HEIGHT)
    }

    private fun editControls(
        widgetId: Int,
        info: AppWidgetProviderInfo,
        width: Int,
        height: Int,
        maxWidth: Int,
        previousId: Int?,
        nextId: Int?,
    ): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, 0, 0, 8.dpToPx())

        addView(TextView(requireContext(), null, 0, R.style.TextSmall).apply {
            text = "${info.loadLabel(requireContext().packageManager)} · $width × $height"
        })
        addView(buttonRow(
            controlButton("↑", previousId != null) { previousId?.let { swapWidgets(widgetId, it) } },
            controlButton("↓", nextId != null) { nextId?.let { swapWidgets(widgetId, it) } },
            controlButton(getString(R.string.widgets_remove)) { removeWidget(widgetId) },
        ))
        val widthLabel = getString(R.string.widgets_width)
        val heightLabel = getString(R.string.widgets_height)
        addView(buttonRow(
            controlButton("$widthLabel −", width > MIN_SIZE) { resizeWidget(widgetId, width - SIZE_STEP, height, maxWidth) },
            controlButton("$widthLabel +", width < maxWidth) { resizeWidget(widgetId, width + SIZE_STEP, height, maxWidth) },
            controlButton("$heightLabel −", height > MIN_SIZE) { resizeWidget(widgetId, width, height - SIZE_STEP, maxWidth) },
            controlButton("$heightLabel +", height < MAX_HEIGHT) { resizeWidget(widgetId, width, height + SIZE_STEP, maxWidth) },
        ))
    }

    private fun buttonRow(vararg buttons: View) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEach { addView(it) }
    }

    private fun controlButton(label: String, enabled: Boolean = true, onClick: () -> Unit) =
        TextView(requireContext(), null, 0, R.style.TextSmallBold).apply {
            text = label
            setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 8.dpToPx())
            alpha = if (enabled) 1f else 0.3f
            if (enabled) setOnClickListener { onClick() }
        }

    private fun swapWidgets(first: Int, second: Int) {
        val widgetIds = prefs.widgetIds.toMutableList()
        Collections.swap(widgetIds, widgetIds.indexOf(first), widgetIds.indexOf(second))
        prefs.widgetIds = widgetIds
        populateWidgets()
    }

    private fun resizeWidget(widgetId: Int, width: Int, height: Int, maxWidth: Int) {
        prefs.setWidgetSize(widgetId, clampSize(width, height, maxWidth))
        populateWidgets()
    }

    private fun removeWidget(widgetId: Int) {
        widgetHost.deleteAppWidgetId(widgetId)
        prefs.widgetIds = prefs.widgetIds - widgetId
        prefs.setWidgetSize(widgetId, null)
        populateWidgets()
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

    companion object {
        // Sizes in dp
        private const val DEFAULT_WIDTH = 640
        private const val CELL_HEIGHT = 96
        private const val MIN_SIZE = 48
        private const val MAX_HEIGHT = 1600
        private const val SIZE_STEP = 40
    }
}
