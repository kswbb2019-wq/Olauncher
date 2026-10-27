package app.olauncher.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Process
import android.provider.CalendarContract
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import app.olauncher.R
import app.olauncher.helper.getColorFromAttr
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

/**
 * Month grid for the widget screen with the events of the device calendar,
 * which is where Samsung Calendar keeps them. The arrows change the month,
 * a day or an event opens Samsung Calendar there, a long press adds an event.
 */
@SuppressLint("ViewConstructor")
class MonthCalendarView(context: Context) : View(context) {

    /** Events need READ_CALENDAR, which only the fragment can request. */
    var onPermissionNeeded: () -> Unit = {}

    private class Event(
        val id: Long,
        val title: String,
        val color: Int,
        val startDay: Int,
        val endDay: Int,
        val allDay: Boolean,
        val begin: Long,
        val end: Long,
    )

    private val month: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    private var eventsByDay: Map<Int, List<Event>> = emptyMap()
    private var loadGeneration = 0

    // Where things were drawn, for taps
    private val prevRect = RectF()
    private val nextRect = RectF()
    private val headerRect = RectF()
    private val dayRects = mutableListOf<Pair<RectF, Calendar>>()
    private val eventRects = mutableListOf<Pair<RectF, Event>>()

    private val textColor = context.getColorFromAttr(R.attr.primaryColor)
    private val inverseColor = context.getColorFromAttr(R.attr.primaryInverseColor)

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(inverseColor, 0xE0) }
    private val titlePaint = textPaint(18f, textColor).apply { isFakeBoldText = true }
    private val hintPaint = textPaint(12f, withAlpha(textColor, 0x99))
    private val arrowPaint = textPaint(24f, textColor).apply { textAlign = Paint.Align.CENTER }
    private val weekdayPaint = textPaint(12f, withAlpha(textColor, 0x99)).apply { textAlign = Paint.Align.CENTER }
    private val dayPaint = textPaint(14f, textColor).apply { textAlign = Paint.Align.CENTER }
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor }
    private val eventPaint = textPaint(11f, textColor)
    private val eventBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onTap(e.x, e.y)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            dayAt(e.x, e.y)?.let { addEvent(it) }
        }
    })

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadEvents()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

    override fun onDraw(canvas: Canvas) {
        val pad = dp(16f)
        val corner = dp(24f)
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), corner, corner, cardPaint)

        // Header: month name, permission hint, arrows
        val headerHeight = dp(44f)
        val headerCenter = pad + headerHeight / 2
        val arrowWidth = dp(44f)
        nextRect.set(width - pad - arrowWidth, pad, width - pad, pad + headerHeight)
        prevRect.set(nextRect.left - arrowWidth, pad, nextRect.left, pad + headerHeight)
        headerRect.set(pad, pad, prevRect.left, pad + headerHeight)
        val title = SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(month.time)
        canvas.drawText(title, pad, headerCenter + textCenterOffset(titlePaint), titlePaint)
        if (!hasPermission()) {
            val hintLeft = pad + titlePaint.measureText(title) + dp(12f)
            val hint = TextUtils.ellipsize(
                context.getString(R.string.calendar_show_events), hintPaint,
                prevRect.left - hintLeft, TextUtils.TruncateAt.END
            )
            canvas.drawText(hint, 0, hint.length, hintLeft, headerCenter + textCenterOffset(hintPaint), hintPaint)
        }
        canvas.drawText("‹", prevRect.centerX(), headerCenter + textCenterOffset(arrowPaint), arrowPaint)
        canvas.drawText("›", nextRect.centerX(), headerCenter + textCenterOffset(arrowPaint), arrowPaint)

        // Weekday names, starting on the locale's first day of the week
        val cellWidth = (width - 2 * pad) / 7
        val weekdayTop = pad + headerHeight
        val weekdayHeight = dp(24f)
        val weekdays = DateFormatSymbols.getInstance().shortWeekdays
        for (column in 0 until 7) {
            val dayOfWeek = (month.firstDayOfWeek - 1 + column) % 7 + 1
            canvas.drawText(
                weekdays[dayOfWeek].trimEnd('.'),
                pad + cellWidth * (column + 0.5f),
                weekdayTop + weekdayHeight / 2 + textCenterOffset(weekdayPaint),
                weekdayPaint
            )
        }

        // Day cells
        val rows = rowCount()
        val gridTop = weekdayTop + weekdayHeight
        val cellHeight = (height - pad - gridTop) / rows
        val today = julianDay(Calendar.getInstance())
        val currentMonth = month.get(Calendar.MONTH)
        dayRects.clear()
        eventRects.clear()
        val day = gridStart()
        for (index in 0 until rows * 7) {
            val left = pad + cellWidth * (index % 7)
            val top = gridTop + cellHeight * (index / 7)
            val cell = RectF(left, top, left + cellWidth, top + cellHeight)
            dayRects += cell to (day.clone() as Calendar)
            drawDay(canvas, cell, day, today, day.get(Calendar.MONTH) == currentMonth)
            day.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun drawDay(canvas: Canvas, cell: RectF, day: Calendar, today: Int, inMonth: Boolean) {
        val julian = julianDay(day)
        val alpha = if (inMonth) 0xFF else 0x55
        val numberHeight = dp(26f)
        val numberY = cell.top + numberHeight / 2
        if (julian == today) {
            canvas.drawCircle(cell.centerX(), numberY, dp(12f), todayPaint)
            dayPaint.color = inverseColor
        } else {
            dayPaint.color = withAlpha(textColor, alpha)
        }
        canvas.drawText(day.get(Calendar.DAY_OF_MONTH).toString(), cell.centerX(), numberY + textCenterOffset(dayPaint), dayPaint)

        val events = eventsByDay[julian] ?: return
        val lineHeight = dp(15f)
        val lines = ((cell.height() - numberHeight) / lineHeight).toInt()
        if (lines < 1) {
            // No room for titles, show up to three dots
            val count = min(events.size, 3)
            val spacing = dp(6f)
            val startX = cell.centerX() - spacing * (count - 1) / 2
            for (i in 0 until count) {
                eventBarPaint.color = withAlpha(events[i].color, alpha)
                canvas.drawCircle(startX + spacing * i, cell.bottom - dp(4f), dp(2f), eventBarPaint)
            }
            return
        }

        val shown = if (events.size > lines) lines - 1 else events.size
        var top = cell.top + numberHeight
        eventPaint.color = withAlpha(textColor, alpha)
        for (event in events.take(shown)) {
            val line = RectF(cell.left + dp(2f), top, cell.right - dp(2f), top + lineHeight)
            eventBarPaint.color = withAlpha(event.color, alpha)
            canvas.drawRect(line.left, line.top + dp(2f), line.left + dp(3f), line.bottom - dp(2f), eventBarPaint)
            val text = TextUtils.ellipsize(event.title, eventPaint, line.width() - dp(6f), TextUtils.TruncateAt.END)
            canvas.drawText(text, 0, text.length, line.left + dp(6f), line.centerY() + textCenterOffset(eventPaint), eventPaint)
            eventRects += line to event
            top += lineHeight
        }
        if (events.size > shown)
            canvas.drawText("+${events.size - shown}", cell.left + dp(8f), top + lineHeight / 2 + textCenterOffset(eventPaint), eventPaint)
    }

    private fun onTap(x: Float, y: Float) {
        when {
            prevRect.contains(x, y) -> changeMonth(-1)
            nextRect.contains(x, y) -> changeMonth(1)
            headerRect.contains(x, y) -> if (hasPermission()) showCurrentMonth() else onPermissionNeeded()
            else -> {
                val event = eventRects.firstOrNull { it.first.contains(x, y) }?.second
                if (event != null) openEvent(event)
                else dayAt(x, y)?.let { openDay(it) }
            }
        }
    }

    private fun dayAt(x: Float, y: Float): Calendar? = dayRects.firstOrNull { it.first.contains(x, y) }?.second

    private fun changeMonth(offset: Int) {
        month.add(Calendar.MONTH, offset)
        loadEvents()
    }

    private fun showCurrentMonth() {
        val now = Calendar.getInstance()
        month.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), 1)
        loadEvents()
    }

    private fun leadingDays() = (month.get(Calendar.DAY_OF_WEEK) - month.firstDayOfWeek + 7) % 7

    private fun rowCount() = ceil((leadingDays() + month.getActualMaximum(Calendar.DAY_OF_MONTH)) / 7f).toInt()

    private fun gridStart(): Calendar = (month.clone() as Calendar).apply {
        add(Calendar.DAY_OF_MONTH, -leadingDays())
    }

    // Same day numbering as CalendarContract.Instances.START_DAY and END_DAY
    private fun julianDay(day: Calendar): Int {
        val localMillis = day.timeInMillis + day.get(Calendar.ZONE_OFFSET) + day.get(Calendar.DST_OFFSET)
        return Math.floorDiv(localMillis, DAY_MS).toInt() + EPOCH_JULIAN_DAY
    }

    private fun hasPermission() = context.checkPermission(
        Manifest.permission.READ_CALENDAR, Process.myPid(), Process.myUid()
    ) == PackageManager.PERMISSION_GRANTED

    private fun loadEvents() {
        val generation = ++loadGeneration
        invalidate()
        if (!hasPermission()) {
            eventsByDay = emptyMap()
            return
        }
        val start = gridStart()
        val days = rowCount() * 7
        val firstDay = julianDay(start)
        val lastDay = firstDay + days - 1
        // A day of margin on each side so all-day events, stored in UTC, are not missed
        val begin = start.timeInMillis - DAY_MS
        val end = start.timeInMillis + (days + 1) * DAY_MS
        val resolver = context.contentResolver
        Thread {
            val byDay = HashMap<Int, MutableList<Event>>()
            for (event in queryEvents(resolver, begin, end))
                for (julian in maxOf(event.startDay, firstDay)..minOf(event.endDay, lastDay))
                    byDay.getOrPut(julian) { mutableListOf() } += event
            byDay.values.forEach { events -> events.sortWith(compareBy({ !it.allDay }, { it.begin })) }
            post {
                if (generation != loadGeneration) return@post
                eventsByDay = byDay
                invalidate()
            }
        }.start()
    }

    private fun queryEvents(resolver: ContentResolver, begin: Long, end: Long): List<Event> {
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.START_DAY,
            CalendarContract.Instances.END_DAY,
            CalendarContract.Instances.END_MINUTE,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
        )
        val events = mutableListOf<Event>()
        try {
            CalendarContract.Instances.query(resolver, projection, begin, end)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val startDay = cursor.getInt(3)
                    val allDay = cursor.getInt(6) == 1
                    val instanceBegin = cursor.getLong(7)
                    val instanceEnd = cursor.getLong(8)
                    // Treat the end as exclusive, so an event ending at midnight stays on its own day
                    val endDay = when {
                        allDay -> startDay + maxOf(1, ((instanceEnd - instanceBegin) / DAY_MS).toInt()) - 1
                        cursor.getInt(5) == 0 && cursor.getInt(4) > startDay -> cursor.getInt(4) - 1
                        else -> cursor.getInt(4)
                    }
                    events += Event(
                        id = cursor.getLong(0),
                        title = cursor.getString(1).orEmpty(),
                        color = cursor.getInt(2) or OPAQUE,
                        startDay = startDay,
                        endDay = endDay,
                        allDay = allDay,
                        begin = instanceBegin,
                        end = instanceEnd,
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return events
    }

    private fun openDay(day: Calendar) {
        val uri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(day.timeInMillis.toString())
            .build()
        startCalendar(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun openEvent(event: Event) = startCalendar(
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
    )

    // A new event at 9:00 on that day, finished in the calendar app
    private fun addEvent(day: Calendar) {
        val begin = (day.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 9) }.timeInMillis
        startCalendar(
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + HOUR_MS)
        )
    }

    // Samsung Calendar first, then any calendar app that handles the intent
    private fun startCalendar(intent: Intent) {
        try {
            context.startActivity(Intent(intent).setPackage(SAMSUNG_CALENDAR))
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    private fun textPaint(sizeSp: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics)
        this.color = color
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun textCenterOffset(paint: Paint) = -(paint.descent() + paint.ascent()) / 2

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    companion object {
        /** Kept among the widget ids; real app widget ids are positive. */
        const val WIDGET_ID = -1

        private const val SAMSUNG_CALENDAR = "com.samsung.android.calendar"
        private const val DAY_MS = 86_400_000L
        private const val HOUR_MS = 3_600_000L
        private const val EPOCH_JULIAN_DAY = 2_440_588
        private const val OPAQUE = 0xFF000000.toInt()
    }
}
