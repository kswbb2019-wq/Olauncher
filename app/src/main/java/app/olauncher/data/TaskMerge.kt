package app.olauncher.data

/**
 * Merges the launcher's tasks with the ones in the repository file.
 * Kept free of Android classes so it runs in plain unit tests.
 */
object TaskMerge {

    const val TOMBSTONE_DAYS = 30
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * Per id, keeps the copy with the later updatedAt; an id on one side only is kept as is.
     * Deletions are tombstones and merge the same way. On a tie the remote copy wins, so an
     * edit to the file that forgot to touch updatedAt still shows. Tombstones whose updatedAt
     * is more than [TOMBSTONE_DAYS] old are dropped. The result is sorted by date and time,
     * keeping the file's order within those and adding the launcher's new tasks after.
     */
    fun merge(local: List<Task>, remote: List<Task>, nowMillis: Long): List<Task> {
        val merged = LinkedHashMap<String, Task>()
        remote.forEach { merged[it.id] = it }
        local.forEach { mine ->
            val theirs = merged[mine.id]
            if (theirs == null || isNewer(mine, theirs)) merged[mine.id] = mine
        }
        val cutoff = nowMillis - TOMBSTONE_DAYS * DAY_MS
        return merged.values
            .filterNot { it.deleted && (IsoTime.parse(it.updatedAt) ?: nowMillis) < cutoff }
            .sortedWith(ORDER)
    }

    // An unreadable updatedAt counts as the oldest possible
    private fun isNewer(a: Task, b: Task) =
        (IsoTime.parse(a.updatedAt) ?: Long.MIN_VALUE) > (IsoTime.parse(b.updatedAt) ?: Long.MIN_VALUE)

    /** Date, then time with untimed tasks last; the sort is stable, so ties keep their order. */
    val ORDER: Comparator<Task> = compareBy({ it.date }, { it.time ?: "~" })
}
