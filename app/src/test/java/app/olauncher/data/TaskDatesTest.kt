package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class TaskDatesTest {

    private fun millis(date: String, hour: Int, minute: Int) = Calendar.getInstance().apply {
        time = TaskDates.parse(date)!!
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }.timeInMillis

    private fun task(id: String, date: String, time: String? = null, done: Boolean = false, deleted: Boolean = false) =
        Task(id, date, time, id, null, null, done, deleted, "2026-11-16T00:00:00Z")

    @Test
    fun untimedTasksUseTheDefaultTime() {
        val tasks = listOf(task("a", "2026-11-17"))
        assertEquals(millis("2026-11-17", 8, 0), TaskDates.nextReminder(tasks, "08:00", millis("2026-11-16", 20, 0)))
    }

    @Test
    fun timedTaskBeforeTheDefaultComesFirst() {
        val tasks = listOf(task("a", "2026-11-17"), task("b", "2026-11-17", "07:30"))
        assertEquals(millis("2026-11-17", 7, 30), TaskDates.nextReminder(tasks, "08:00", millis("2026-11-16", 20, 0)))
    }

    @Test
    fun skipsPastTimesAndCheckedOrDeletedTasks() {
        val tasks = listOf(
            task("past", "2026-11-16"),
            task("done", "2026-11-17", done = true),
            task("deleted", "2026-11-17", deleted = true),
            task("later", "2026-11-18", "21:00"),
        )
        assertEquals(millis("2026-11-18", 21, 0), TaskDates.nextReminder(tasks, "08:00", millis("2026-11-16", 9, 0)))
    }

    @Test
    fun badTimesFallBackAndNothingLeftIsNull() {
        val tasks = listOf(task("a", "2026-11-17", "9am"))
        assertEquals(millis("2026-11-17", 8, 0), TaskDates.nextReminder(tasks, "08:00", millis("2026-11-16", 9, 0)))
        assertNull(TaskDates.nextReminder(tasks, "08:00", millis("2026-11-18", 0, 0)))
        assertNull(TaskDates.parseTime("24:00"))
    }
}
