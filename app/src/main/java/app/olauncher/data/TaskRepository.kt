package app.olauncher.data

import android.content.Context
import android.util.AtomicFile
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.io.FileNotFoundException

/**
 * The launcher's copy of tasks.json, kept in the app's files folder so it works
 * offline and survives app updates. TaskSync merges it with the repository file.
 */
object TaskRepository {

    private const val FILE = "tasks.json"
    private val lock = Any()

    /** Bumped on every saved change, so screens can redraw. */
    val changes = MutableLiveData(0)

    /** All tasks, deleted ones (tombstones) included. */
    fun all(context: Context): List<Task> = synchronized(lock) { read(context) }

    /** Tasks to show: everything not deleted. */
    fun visible(context: Context): List<Task> = all(context).filterNot { it.deleted }

    /**
     * Saves what [change] returns for the current tasks, safe against other changes and
     * syncs running at the same time, and returns the saved list.
     */
    fun update(context: Context, change: (List<Task>) -> List<Task>): List<Task> = synchronized(lock) {
        val before = read(context)
        val after = change(before)
        if (after != before) write(context, after)
        after
    }

    private fun file(context: Context) = AtomicFile(File(context.applicationContext.filesDir, FILE))

    // An unreadable copy reads as empty; the next sync fills it again from the repository
    private fun read(context: Context): List<Task> = try {
        TaskJson.parse(String(file(context).readFully(), Charsets.UTF_8))
    } catch (e: FileNotFoundException) {
        emptyList()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    private fun write(context: Context, tasks: List<Task>) {
        val file = file(context)
        val out = file.startWrite()
        try {
            out.write(TaskJson.write(tasks, IsoTime.now()).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
        changes.postValue((changes.value ?: 0) + 1)
    }
}
