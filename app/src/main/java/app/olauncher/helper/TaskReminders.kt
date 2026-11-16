package app.olauncher.helper

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.TaskDates
import app.olauncher.data.TaskMerge
import app.olauncher.data.TaskRepository

/**
 * One notification per reminder time listing the day's unchecked tasks. Only the next
 * reminder is ever scheduled; every task, setting, boot or clock change schedules it again.
 * The alarm is allowed in Doze but not exact, so Android may deliver it a little late.
 */
object TaskReminders {

    private const val CHANNEL = "tasks"
    private const val NOTIFICATION_ID = 7001
    private const val REQUEST_CODE = 7002
    private const val MAX_LINES = 7

    fun reschedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val alarm = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TaskReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(alarm)
        val prefs = Prefs(context)
        if (!prefs.taskReminders) return
        val next = TaskDates.nextReminder(
            TaskRepository.visible(context),
            prefs.taskReminderTime,
            System.currentTimeMillis()
        ) ?: return
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm)
    }

    /** Posts today's unchecked tasks, if there are any and notifications are allowed. */
    fun notifyToday(context: Context) {
        if (!Prefs(context).taskReminders) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val today = TaskDates.today()
        val open = TaskRepository.visible(context)
            .filter { it.date == today && !it.done }
            .sortedWith(TaskMerge.ORDER)
        if (open.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.tasks_reminder_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val openLauncher = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val lines = NotificationCompat.InboxStyle()
        open.take(MAX_LINES).forEach { task -> lines.addLine(listOfNotNull(task.time, task.title).joinToString("  ")) }
        if (open.size > MAX_LINES) lines.setSummaryText("+${open.size - MAX_LINES}")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_task_notification)
            .setContentTitle(context.getString(R.string.tasks_reminder_title, open.size))
            .setContentText(open.joinToString(", ") { it.title })
            .setStyle(lines)
            .setContentIntent(openLauncher)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}

/** The reminder alarm: show today's tasks, then schedule the next reminder. */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskReminders.notifyToday(context)
        TaskReminders.reschedule(context)
    }
}

/** Alarms do not survive a reboot, and a clock or time zone change moves them: schedule again. */
class TaskRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in ACTIONS) TaskReminders.reschedule(context)
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
