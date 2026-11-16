package app.olauncher.helper

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.MutableLiveData
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.olauncher.data.Constants
import app.olauncher.data.IsoTime
import app.olauncher.data.Prefs
import app.olauncher.data.Task
import app.olauncher.data.TaskJson
import app.olauncher.data.TaskMerge
import app.olauncher.data.TaskRepository
import app.olauncher.data.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Two-way sync of the launcher's tasks with tasks.json in a GitHub repository, through
 * the Contents API: read the file, merge it with the local copy task by task
 * (TaskMerge), save that locally, and write it back when it differs from the file.
 * Failures keep the local data as it is and never crash the launcher.
 */
object TaskSync {

    private const val API = "https://api.github.com"
    private const val COMMIT_MESSAGE = "tasks: update from launcher"
    private const val MAX_TRIES = 3
    private const val UPLOAD_DELAY_SECONDS = 30L
    private const val PERIODIC_HOURS = 6L
    private const val HOME_INTERVAL_MS = 15 * 60 * 1000L

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Bumped when a sync finishes, so settings can show the new status. */
    val statusChanges = MutableLiveData(0)

    private class Remote(val tasks: List<Task>, val sha: String?)
    private class HttpError(val code: Int) : IOException("HTTP $code")

    /** At app start: sync now and keep the six-hourly sync scheduled. */
    fun start(context: Context) {
        val request = PeriodicWorkRequestBuilder<TaskSyncWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
            .setConstraints(onlineOnly())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            Constants.Tasks.SYNC_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        syncInBackground(context)
    }

    /** Back on the home screen: sync when the last try is at least 15 minutes old. */
    fun syncIfStale(context: Context) {
        if (System.currentTimeMillis() - Prefs(context).taskLastAttempt >= HOME_INTERVAL_MS)
            syncInBackground(context)
    }

    /** Syncs now and passes the result to [onDone] on the main thread. */
    fun syncInBackground(context: Context, onDone: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        scope.launch {
            val synced = sync(appContext)
            onDone?.let { withContext(Dispatchers.Main) { it(synced) } }
        }
    }

    /**
     * Saves a change made in the launcher and uploads it 30 seconds after the last
     * change, or as soon as the device is back online.
     */
    fun edit(context: Context, change: (List<Task>) -> List<Task>) {
        TaskRepository.update(context, change)
        val prefs = Prefs(context)
        prefs.taskLocalRevision = prefs.taskLocalRevision + 1
        prefs.taskSyncStatus = Constants.TaskSyncStatus.PENDING
        val request = OneTimeWorkRequestBuilder<TaskSyncWorker>()
            .setInitialDelay(UPLOAD_DELAY_SECONDS, TimeUnit.SECONDS)
            .setConstraints(onlineOnly())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            Constants.Tasks.SYNC_UPLOAD_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
        statusChanges.postValue((statusChanges.value ?: 0) + 1)
    }

    /** Whether launcher changes are still waiting for upload. */
    fun isPending(prefs: Prefs) = prefs.taskLocalRevision != prefs.taskSyncedRevision

    /** One sync; returns whether it reached GitHub and left both sides equal. Never throws. */
    suspend fun sync(context: Context): Boolean = withContext(Dispatchers.IO) {
        val synced = mutex.withLock { syncLocked(context.applicationContext) }
        statusChanges.postValue((statusChanges.value ?: 0) + 1)
        synced
    }

    private fun syncLocked(context: Context): Boolean {
        val prefs = Prefs(context)
        prefs.taskLastAttempt = System.currentTimeMillis()
        val token = TokenStore.get(context)
        return try {
            for (attempt in 1..MAX_TRIES) {
                val remote = fetch(prefs, token)
                var revision = 0L
                val merged = TaskRepository.update(context) { local ->
                    revision = prefs.taskLocalRevision
                    TaskMerge.merge(local, remote.tasks, System.currentTimeMillis())
                }
                if (merged.toSet() == remote.tasks.toSet()) {
                    succeeded(prefs, remote.sha, revision)
                    return true
                }
                // Reading works without a token on a public repository, writing does not
                if (token.isEmpty()) {
                    prefs.taskSyncStatus = Constants.TaskSyncStatus.TOKEN
                    return false
                }
                try {
                    succeeded(prefs, put(prefs, token, merged, remote.sha), revision)
                    return true
                } catch (e: HttpError) {
                    // The file changed after it was read: read, merge and try again
                    if (e.code != 409 && e.code != 422) throw e
                }
            }
            prefs.taskSyncStatus = Constants.TaskSyncStatus.ERROR
            false
        } catch (e: HttpError) {
            prefs.taskSyncStatus =
                if (e.code == 401 || e.code == 403) Constants.TaskSyncStatus.TOKEN
                else Constants.TaskSyncStatus.ERROR
            false
        } catch (e: Exception) {
            // Offline, a broken response or an unreadable file: keep local data, retry later
            prefs.taskSyncStatus = Constants.TaskSyncStatus.ERROR
            false
        }
    }

    private fun succeeded(prefs: Prefs, sha: String?, revision: Long) {
        prefs.taskRemoteSha = sha.orEmpty()
        prefs.taskLastSync = System.currentTimeMillis()
        prefs.taskSyncedRevision = revision
        prefs.taskSyncStatus =
            if (isPending(prefs)) Constants.TaskSyncStatus.PENDING else Constants.TaskSyncStatus.OK
    }

    private fun fetch(prefs: Prefs, token: String): Remote {
        val url = contentsUrl(prefs) + "?ref=" + Uri.encode(prefs.taskRepoBranch.trim())
        val connection = open(url, "GET", token)
        try {
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val content = json.optString("content").replace(Regex("\\s"), "")
                    val text = String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
                    Remote(TaskJson.parse(text), json.optString("sha").ifEmpty { null })
                }
                // No file yet: the first upload creates it
                HttpURLConnection.HTTP_NOT_FOUND -> Remote(emptyList(), null)
                else -> throw HttpError(code)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Writes [tasks] as the file's new content and returns its new sha. */
    private fun put(prefs: Prefs, token: String, tasks: List<Task>, sha: String?): String? {
        val content = TaskJson.write(tasks, IsoTime.now()).toByteArray(Charsets.UTF_8)
        val body = JSONObject()
            .put("message", COMMIT_MESSAGE)
            .put("content", Base64.encodeToString(content, Base64.NO_WRAP))
            .put("branch", prefs.taskRepoBranch.trim())
        if (sha != null) body.put("sha", sha)
        val connection = open(contentsUrl(prefs), "PUT", token)
        try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_CREATED) throw HttpError(code)
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            return response.optJSONObject("content")?.optString("sha")
        } finally {
            connection.disconnect()
        }
    }

    private fun contentsUrl(prefs: Prefs): String {
        val owner = Uri.encode(prefs.taskRepoOwner.trim())
        val repo = Uri.encode(prefs.taskRepoName.trim())
        val path = Uri.encode(prefs.taskRepoPath.trim().trim('/'), "/")
        return "$API/repos/$owner/$repo/contents/$path"
    }

    // The token only ever goes into this header
    private fun open(url: String, method: String, token: String) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Olauncher-tasks")
            if (token.isNotEmpty()) setRequestProperty("Authorization", "Bearer $token")
        }

    private fun onlineOnly() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
