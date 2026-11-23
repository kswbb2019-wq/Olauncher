package app.olauncher.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Paint
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.olauncher.R
import app.olauncher.data.IsoTime
import app.olauncher.data.LauncherTheme
import app.olauncher.data.Prefs
import app.olauncher.data.Task
import app.olauncher.data.TaskDates
import app.olauncher.data.TaskMerge
import app.olauncher.data.TaskRepository
import app.olauncher.databinding.DialogTaskEditBinding
import app.olauncher.databinding.FragmentTasksBinding
import app.olauncher.helper.TaskSync
import app.olauncher.helper.createDialog
import app.olauncher.helper.dpToPx
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.showToast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Every task by date. A tap checks a task off, a long press edits or deletes it,
 * and + adds one. Changes save locally and upload through TaskSync.
 */
class TasksFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private var theme = LauncherTheme.DEFAULT
    private var scrolledToToday = false
    private var calendar: MonthCalendarView? = null
    private val dateHeaders = sortedMapOf<String, View>()

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        theme = prefs.launcherTheme
        binding.tasksAdd.setOnClickListener { showTaskDialog(null) }

        // The same tasks as a month calendar: a day opens it in the list, a long press adds a task there
        calendar = MonthCalendarView(requireContext()).apply {
            onTaskDayClick = { date -> showList(date) }
            onTaskDayLongClick = { date -> showTaskDialog(null, date) }
        }
        binding.tasksCalendar.addView(calendar, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        binding.tasksRoot.onSwipeLeft = { showCalendar() }
        binding.tasksRoot.onSwipeRight = { showList(null) }
        binding.tasksListTab.setOnClickListener { showList(null) }
        binding.tasksCalendarTab.setOnClickListener { showCalendar() }
        TaskRepository.changes.observe(viewLifecycleOwner) { populateTasks() }
    }

    private fun populateTasks() {
        val content = binding.tasksContent
        content.removeAllViews()
        content.addView(TextView(requireContext(), null, 0, R.style.TextLarge).apply {
            text = getString(R.string.tasks)
            theme.styleText(this)
        })

        val today = TaskDates.today()
        var todayHeader: View? = null
        // Launcher edits stay where they were added until the next sync sorts the file
        val tasks = TaskRepository.visible(requireContext()).sortedWith(TaskMerge.ORDER)
        calendar?.tasks = tasks
        dateHeaders.clear()
        if (tasks.isEmpty()) content.addView(smallText(getString(R.string.tasks_empty)))
        tasks.groupBy { it.date }.forEach { (date, dayTasks) ->
            val header = smallText(dateHeading(date, today)).apply {
                alpha = 0.6f
                setPadding(0, 24.dpToPx(), 0, 4.dpToPx())
            }
            content.addView(header)
            dateHeaders[date] = header
            // Jump to today, or else to the first day after it
            if (todayHeader == null && date >= today) todayHeader = header
            dayTasks.forEach { content.addView(taskRow(it)) }
        }

        if (!scrolledToToday) {
            scrolledToToday = true
            todayHeader?.let { header -> binding.tasksScroll.post { binding.tasksScroll.scrollTo(0, header.top) } }
        }
    }

    private fun showCalendar() {
        binding.tasksScroll.visibility = View.GONE
        binding.tasksCalendar.visibility = View.VISIBLE
        binding.tasksListTab.alpha = 0.4f
        binding.tasksCalendarTab.alpha = 1f
    }

    /** Back to the list, scrolled to [date] or the first later day with tasks when given. */
    private fun showList(date: String?) {
        binding.tasksCalendar.visibility = View.GONE
        binding.tasksScroll.visibility = View.VISIBLE
        binding.tasksListTab.alpha = 1f
        binding.tasksCalendarTab.alpha = 0.4f
        val header = date?.let { dateHeaders.tailMap(it).values.firstOrNull() } ?: return
        binding.tasksScroll.post { binding.tasksScroll.scrollTo(0, header.top) }
    }

    private fun dateHeading(date: String, today: String): String {
        val day = TaskDates.parse(date) ?: return date
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        val heading = SimpleDateFormat(pattern, Locale.getDefault()).format(day)
        return if (date == today) heading + " · " + getString(R.string.tasks_today_label) else heading
    }

    private fun smallText(text: String) = TextView(requireContext(), null, 0, R.style.TextSmall).apply {
        this.text = text
        theme.styleText(this)
    }

    private fun taskRow(task: Task) = TextView(requireContext(), null, 0, R.style.TextMedium).apply {
        val details = listOfNotNull(task.time, task.note?.takeIf { it.isNotBlank() }, task.tag?.takeIf { it.isNotBlank() })
        text = SpannableStringBuilder((if (task.done) "✓  " else "○  ") + task.title).apply {
            if (details.isNotEmpty())
                append("\n" + details.joinToString(" · "), RelativeSizeSpan(0.7f), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
        theme.styleText(this)
        if (task.done) {
            alpha = 0.5f
            paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        setOnClickListener { saveTask(task.copy(done = !task.done)) }
        setOnLongClickListener { row ->
            row.showPopupMenu(configure = { menu ->
                menu.add(0, EDIT, 0, R.string.tasks_edit_short)
                menu.add(0, DELETE, 1, R.string.tasks_delete)
            }) { item ->
                // Deleting keeps a tombstone so the deletion syncs too
                if (item.itemId == EDIT) showTaskDialog(task) else saveTask(task.copy(deleted = true))
            }
            true
        }
    }

    /** Saves [task] in place of the one with its id, or adds it, with a fresh updatedAt. */
    private fun saveTask(task: Task) {
        val changed = task.copy(updatedAt = IsoTime.now())
        TaskSync.edit(requireContext()) { tasks ->
            if (tasks.any { it.id == changed.id }) tasks.map { if (it.id == changed.id) changed else it }
            else tasks + changed
        }
    }

    /** Adds a task (on [newDate], or today) when [task] is null, otherwise edits its title, date, time and note. */
    private fun showTaskDialog(task: Task?, newDate: String? = null) {
        var date = task?.date ?: newDate ?: TaskDates.today()
        var time = task?.time
        var fields: DialogTaskEditBinding? = null

        fun populateDateTime() {
            val binding = fields ?: return
            binding.taskDate.text = TaskDates.parse(date)
                ?.let { DateFormat.getMediumDateFormat(requireContext()).format(it) } ?: date
            binding.taskTime.text = time ?: getString(R.string.tasks_no_time)
        }

        val dialog = requireContext().createDialog(
            title = if (task == null) R.string.tasks_add else R.string.tasks_edit,
            action = R.string.tasks_save,
            onAction = {
                val title = fields?.taskTitle?.text?.toString()?.trim().orEmpty()
                val note = fields?.taskNote?.text?.toString()?.trim().orEmpty().ifEmpty { null }
                if (title.isEmpty()) {
                    requireContext().showToast(R.string.tasks_title_needed)
                } else if (task == null) {
                    // Ids made in the launcher never clash with the ones written in the file
                    saveTask(Task("app-" + UUID.randomUUID(), date, time, title, note, null, false, false, ""))
                } else {
                    saveTask(task.copy(title = title, date = date, time = time, note = note))
                }
            },
        ) { container ->
            DialogTaskEditBinding.inflate(LayoutInflater.from(container.context), container, false)
                .also { fields = it }.root
        }

        fields?.apply {
            taskTitle.setText(task?.title.orEmpty())
            taskNote.setText(task?.note.orEmpty())
            taskDate.setOnClickListener {
                val calendar = Calendar.getInstance().apply { TaskDates.parse(date)?.let { setTime(it) } }
                DatePickerDialog(requireContext(), { _, year, month, day ->
                    date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                    populateDateTime()
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            }
            taskTime.setOnClickListener {
                val (hour, minute) = time?.split(":")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }
                    ?.let { it[0] to it[1] } ?: (9 to 0)
                TimePickerDialog(requireContext(), { _, h, m ->
                    time = String.format(Locale.US, "%02d:%02d", h, m)
                    populateDateTime()
                }, hour, minute, DateFormat.is24HourFormat(requireContext())).show()
            }
            taskTimeClear.setOnClickListener {
                time = null
                populateDateTime()
            }
        }
        populateDateTime()
        dialog.showRespectingStatusBar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        calendar = null
        _binding = null
    }

    companion object {
        private const val EDIT = 1
        private const val DELETE = 2
    }
}
