package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMergeTest {

    private val now = IsoTime.parse("2026-11-16T12:00:00Z")!!

    private fun task(
        id: String,
        updatedAt: String,
        title: String = id,
        done: Boolean = false,
        deleted: Boolean = false,
        date: String = "2026-11-16",
        time: String? = null,
    ) = Task(id, date, time, title, null, null, done, deleted, updatedAt)

    @Test
    fun newerLocalWins() {
        val local = task("a", "2026-11-16T10:00:00Z", done = true)
        val remote = task("a", "2026-11-16T09:00:00Z")
        assertEquals(listOf(local), TaskMerge.merge(listOf(local), listOf(remote), now))
    }

    @Test
    fun newerRemoteWins() {
        val local = task("a", "2026-11-16T09:00:00Z", done = true)
        val remote = task("a", "2026-11-16T10:00:00Z", title = "renamed")
        assertEquals(listOf(remote), TaskMerge.merge(listOf(local), listOf(remote), now))
    }

    @Test
    fun tieKeepsRemote() {
        val local = task("a", "2026-11-16T09:00:00Z", title = "local")
        val remote = task("a", "2026-11-16T09:00:00Z", title = "remote")
        assertEquals("remote", TaskMerge.merge(listOf(local), listOf(remote), now).single().title)
    }

    @Test
    fun comparesInstantsNotText() {
        // 10:00+09:00 is 01:00 UTC, so it is older than 09:00Z
        val local = task("a", "2026-11-16T10:00:00+09:00", title = "local")
        val remote = task("a", "2026-11-16T09:00:00.5Z", title = "remote")
        assertEquals("remote", TaskMerge.merge(listOf(local), listOf(remote), now).single().title)
    }

    @Test
    fun idsOnOneSideAreKept() {
        val localOnly = task("app-1", "2026-11-16T08:00:00Z")
        val remoteOnly = task("file-1", "2026-11-16T08:00:00Z")
        val merged = TaskMerge.merge(listOf(localOnly), listOf(remoteOnly), now)
        assertEquals(setOf("app-1", "file-1"), merged.map { it.id }.toSet())
    }

    @Test
    fun newerTombstoneSpreadsBothWays() {
        val alive = task("a", "2026-11-16T08:00:00Z")
        val deleted = task("a", "2026-11-16T09:00:00Z", deleted = true)
        assertTrue(TaskMerge.merge(listOf(deleted), listOf(alive), now).single().deleted)
        assertTrue(TaskMerge.merge(listOf(alive), listOf(deleted), now).single().deleted)
    }

    @Test
    fun newerEditRevivesOlderTombstone() {
        val deleted = task("a", "2026-11-16T08:00:00Z", deleted = true)
        val edited = task("a", "2026-11-16T09:00:00Z", title = "back")
        assertEquals(edited, TaskMerge.merge(listOf(deleted), listOf(edited), now).single())
    }

    @Test
    fun oldTombstonesArePurged() {
        val old = task("old", "2026-10-01T00:00:00Z", deleted = true)
        val recent = task("recent", "2026-11-01T00:00:00Z", deleted = true)
        val oldButAlive = task("alive", "2026-01-01T00:00:00Z")
        val merged = TaskMerge.merge(listOf(old), listOf(recent, oldButAlive), now)
        assertEquals(setOf("recent", "alive"), merged.map { it.id }.toSet())
    }

    @Test
    fun unreadableTimeLosesAndIsNotPurged() {
        val broken = task("a", "yesterday", title = "broken")
        val good = task("a", "2026-11-16T08:00:00Z", title = "good")
        assertEquals("good", TaskMerge.merge(listOf(good), listOf(broken), now).single().title)
        val brokenTombstone = task("b", "", deleted = true)
        assertEquals(1, TaskMerge.merge(emptyList(), listOf(brokenTombstone), now).size)
    }

    @Test
    fun sortedByDateAndTimeKeepingFileOrder() {
        val remote = listOf(
            task("next-day", "2026-11-16T08:00:00Z", date = "2026-11-17"),
            task("z-first-in-file", "2026-11-16T08:00:00Z"),
            task("a-second-in-file", "2026-11-16T08:00:00Z"),
            task("timed", "2026-11-16T08:00:00Z", time = "09:30"),
        )
        val local = listOf(task("app-new", "2026-11-16T08:00:00Z"))
        assertEquals(
            listOf("timed", "z-first-in-file", "a-second-in-file", "app-new", "next-day"),
            TaskMerge.merge(local, remote, now).map { it.id }
        )
    }

    @Test
    fun isoTimeReadsCommonForms() {
        val utc = IsoTime.parse("2026-11-16T09:00:00Z")
        assertNotNull(utc)
        assertEquals(utc, IsoTime.parse("2026-11-16T18:00:00+09:00"))
        assertEquals(utc!! + 123, IsoTime.parse("2026-11-16T09:00:00.123456Z"))
        assertEquals(utc, IsoTime.parse(IsoTime.format(utc)))
        assertNull(IsoTime.parse("not a time"))
    }
}
