package app.olauncher.ui

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import app.olauncher.MainActivity
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentWidgetsBinding
import app.olauncher.helper.dpToPx
import app.olauncher.helper.widgetHost
import java.util.Collections
import kotlin.math.ceil
import kotlin.math.floor

class WidgetsFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private val viewModel: MainViewModel by activityViewModels()
    private var editing = false

    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.isWidgetSetupOpen = false
        if (_binding != null) populateWidgets()
    }

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
        val screenWidth = resources.configuration.screenWidthDp - 48
        // The month calendar has no provider info; ids whose app is gone or unavailable are skipped but kept
        val widgets = prefs.widgetIds.mapNotNull { id ->
            if (id == MonthCalendarView.WIDGET_ID) id to null
            else widgetManager.getAppWidgetInfo(id)?.let { id to it }
        }
        val widgetContext = widgetContext()

        widgets.forEachIndexed { index, (widgetId, info) ->
            val limits = sizeLimits(info, screenWidth)
            val (width, height) = widgetSize(widgetId, info, limits)
            val widgetView = if (info == null) monthCalendar() else widgetHost.createView(widgetContext, widgetId, info).apply {
                @Suppress("DEPRECATION")
                updateAppWidgetSize(Bundle(), width, height, width, height)
            }
            val label = info?.loadLabel(requireContext().packageManager) ?: getString(R.string.month_calendar)

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            if (editing) row.addView(
                editControls(
                    widgetId, label, width, height, limits,
                    previousId = widgets.getOrNull(index - 1)?.first,
                    nextId = widgets.getOrNull(index + 1)?.first,
                )
            )
            row.addView(widgetView, LinearLayout.LayoutParams(width.dpToPx(), height.dpToPx()))
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

    /** Allowed width and height in dp. */
    private class SizeLimits(val width: IntRange, val height: IntRange)

    // The widget's own resize range, which One UI Home enforces. Samsung widgets given a size
    // outside it show "Inhalt kann nicht angezeigt werden" instead of their content
    private fun sizeLimits(info: AppWidgetProviderInfo?, screenWidth: Int): SizeLimits {
        val maxWidth = maxOf(MIN_SIZE, screenWidth)
        if (info == null) return SizeLimits(MIN_SIZE..maxWidth, MIN_SIZE..MAX_HEIGHT)
        val (preferredWidth, preferredHeight) = preferredSize(info)
        val newer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val width = if (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL == 0)
            preferredWidth.coerceIn(MIN_SIZE, maxWidth).let { it..it }
        else limit(info.minResizeWidth, if (newer) info.maxResizeWidth else 0, maxWidth)
        val height = if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL == 0)
            preferredHeight.coerceIn(MIN_SIZE, MAX_HEIGHT).let { it..it }
        else limit(info.minResizeHeight, if (newer) info.maxResizeHeight else 0, MAX_HEIGHT)
        return SizeLimits(width, height)
    }

    // minPx and maxPx come from the widget, a max of 0 meaning no limit
    private fun limit(minPx: Int, maxPx: Int, cap: Int): IntRange {
        val density = resources.displayMetrics.density
        val max = if (maxPx > 0) minOf(floor(maxPx / density).toInt(), cap) else cap
        return maxOf(MIN_SIZE, ceil(minPx / density).toInt()).coerceAtMost(max)..max
    }

    // Newer widgets give a preferred size in home screen cells, older ones only a minimum in px
    private fun preferredSize(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val newer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val density = resources.displayMetrics.density
        val cellsWide = if (newer) info.targetCellWidth else 0
        val cellsHigh = if (newer) info.targetCellHeight else 0
        return maxOf(ceil(info.minWidth / density).toInt(), cellsWide * CELL_SIZE) to
                maxOf(ceil(info.minHeight / density).toInt(), cellsHigh * CELL_SIZE)
    }

    /** Width and height in dp: the saved size, or the widget's preferred one, within its limits. */
    private fun widgetSize(widgetId: Int, info: AppWidgetProviderInfo?, limits: SizeLimits): Pair<Int, Int> {
        val saved = prefs.getWidgetSize(widgetId)
        val preferred = info?.let { preferredSize(it) } ?: (CALENDAR_WIDTH to CALENDAR_HEIGHT)
        val width = saved?.first ?: preferred.first
        val height = saved?.second ?: preferred.second
        return width.coerceIn(limits.width) to height.coerceIn(limits.height)
    }

    private fun monthCalendar() = MonthCalendarView(requireContext()).apply {
        onPermissionNeeded = { requestCalendarPermission() }
    }

    private fun requestCalendarPermission() {
        viewModel.isWidgetSetupOpen = true
        calendarPermission.launch(Manifest.permission.READ_CALENDAR)
    }

    private fun editControls(
        widgetId: Int,
        label: String,
        width: Int,
        height: Int,
        limits: SizeLimits,
        previousId: Int?,
        nextId: Int?,
    ): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, 0, 0, 8.dpToPx())

        addView(TextView(requireContext(), null, 0, R.style.TextSmall).apply {
            text = "$label · $width × $height"
        })
        addView(buttonRow(
            controlButton("↑", previousId != null) { previousId?.let { swapWidgets(widgetId, it) } },
            controlButton("↓", nextId != null) { nextId?.let { swapWidgets(widgetId, it) } },
            controlButton(getString(R.string.widgets_remove)) { removeWidget(widgetId) },
        ))
        val widthLabel = getString(R.string.widgets_width)
        val heightLabel = getString(R.string.widgets_height)
        addView(buttonRow(
            controlButton("$widthLabel −", width > limits.width.first) { resizeWidget(widgetId, width - SIZE_STEP, height, limits) },
            controlButton("$widthLabel +", width < limits.width.last) { resizeWidget(widgetId, width + SIZE_STEP, height, limits) },
            controlButton("$heightLabel −", height > limits.height.first) { resizeWidget(widgetId, width, height - SIZE_STEP, limits) },
            controlButton("$heightLabel +", height < limits.height.last) { resizeWidget(widgetId, width, height + SIZE_STEP, limits) },
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

    private fun resizeWidget(widgetId: Int, width: Int, height: Int, limits: SizeLimits) {
        prefs.setWidgetSize(widgetId, width.coerceIn(limits.width) to height.coerceIn(limits.height))
        populateWidgets()
    }

    private fun removeWidget(widgetId: Int) {
        if (widgetId != MonthCalendarView.WIDGET_ID) widgetHost.deleteAppWidgetId(widgetId)
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
        // The launcher's own month calendar comes first, once
        val calendar: List<Pair<String, AppWidgetProviderInfo?>> =
            if (MonthCalendarView.WIDGET_ID in prefs.widgetIds) emptyList()
            else listOf("${getString(R.string.app_name)}: ${getString(R.string.month_calendar)}" to null)
        val entries = calendar + providers
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.widgets_add)
            .setItems(entries.map { it.first }.toTypedArray()) { _, which ->
                if (!isAdded) return@setItems
                val provider = entries[which].second
                if (provider == null) addMonthCalendar()
                else (requireActivity() as MainActivity).addWidget(provider)
                if (_binding != null) populateWidgets()
            }
            .show()
    }

    private fun addMonthCalendar() {
        prefs.widgetIds = prefs.widgetIds + MonthCalendarView.WIDGET_ID
        if (requireContext().checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            requestCalendarPermission()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // Sizes in dp
        private const val CELL_SIZE = 96
        private const val CALENDAR_WIDTH = 640
        private const val CALENDAR_HEIGHT = 400
        private const val MIN_SIZE = 48
        private const val MAX_HEIGHT = 1600
        private const val SIZE_STEP = 40
    }
}
