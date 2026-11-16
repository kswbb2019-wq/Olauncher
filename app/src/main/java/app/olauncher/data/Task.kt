package app.olauncher.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One to-do item of data/tasks.json, see README "tasks.json 수정 규칙".
 * [id] never changes. [updatedAt] is when the task last changed and decides
 * merges. A deleted task stays as a tombstone with [deleted] so the deletion
 * syncs too.
 */
data class Task(
    val id: String,
    val date: String, // yyyy-MM-dd
    val time: String?, // HH:mm, or null for no time
    val title: String,
    val note: String?,
    val tag: String?,
    val done: Boolean,
    val deleted: Boolean,
    val updatedAt: String, // ISO 8601
)

/** Reads and writes the tasks.json format. */
object TaskJson {

    /** Throws [org.json.JSONException] when the file is not valid JSON. */
    fun parse(json: String): List<Task> {
        val tasks = JSONObject(json).optJSONArray("tasks") ?: JSONArray()
        return (0 until tasks.length())
            .mapNotNull { tasks.optJSONObject(it) }
            .mapNotNull { fromJson(it) }
    }

    private fun fromJson(json: JSONObject): Task? {
        val id = json.optString("id")
        if (id.isEmpty()) return null
        return Task(
            id = id,
            date = json.optString("date"),
            time = json.stringOrNull("time"),
            title = json.optString("title"),
            note = json.stringOrNull("note"),
            tag = json.stringOrNull("tag"),
            done = json.optBoolean("done"),
            deleted = json.optBoolean("deleted"),
            updatedAt = json.optString("updatedAt"),
        )
    }

    // optString turns a JSON null into the text "null"
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

    /** One task per line, fields always in the same order, so the file is easy to edit and diff. */
    fun write(tasks: List<Task>, updatedAt: String): String = buildString {
        append("{\n  \"version\": 1,\n  \"updatedAt\": ").append(quote(updatedAt)).append(",\n  \"tasks\": [")
        tasks.forEachIndexed { index, task ->
            append(if (index == 0) "\n    { " else ",\n    { ")
            append("\"id\": ").append(quote(task.id))
            append(", \"date\": ").append(quote(task.date))
            append(", \"time\": ").append(quote(task.time))
            append(", \"title\": ").append(quote(task.title))
            append(", \"note\": ").append(quote(task.note))
            append(", \"tag\": ").append(quote(task.tag))
            append(", \"done\": ").append(task.done)
            append(", \"deleted\": ").append(task.deleted)
            append(", \"updatedAt\": ").append(quote(task.updatedAt))
            append(" }")
        }
        append(if (tasks.isEmpty()) "]\n}\n" else "\n  ]\n}\n")
    }

    private fun quote(text: String?): String {
        if (text == null) return "null"
        val quoted = StringBuilder("\"")
        for (c in text) {
            when (c) {
                '"' -> quoted.append("\\\"")
                '\\' -> quoted.append("\\\\")
                '\n' -> quoted.append("\\n")
                '\r' -> quoted.append("\\r")
                '\t' -> quoted.append("\\t")
                else -> if (c < ' ') quoted.append(String.format(Locale.US, "\\u%04x", c.code)) else quoted.append(c)
            }
        }
        return quoted.append('"').toString()
    }
}

/** Local calendar dates as tasks use them (yyyy-MM-dd). */
object TaskDates {

    fun today(): String = format(Date())

    fun format(date: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    /** Local midnight of [date], or null if it is not a yyyy-MM-dd date. */
    fun parse(date: String): Date? = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(date)
    } catch (_: ParseException) {
        null
    }
}

/** ISO 8601 times for [Task.updatedAt], written in UTC with milliseconds. */
object IsoTime {

    fun now(): String = format(System.currentTimeMillis())

    fun format(millis: Long): String = utc("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(Date(millis))

    /** Milliseconds for an ISO 8601 time with or without fraction and offset, or null if unreadable. */
    fun parse(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        // SimpleDateFormat reads "SSS" as a count of milliseconds, so the fraction needs exactly 3 digits
        val normalized = text.trim().replace(FRACTION) { "." + it.groupValues[1].padEnd(3, '0').take(3) }
        for (pattern in PATTERNS) {
            try {
                return utc(pattern).apply { isLenient = false }.parse(normalized)?.time
            } catch (_: ParseException) {
            }
        }
        return null
    }

    private val FRACTION = Regex("""\.(\d+)""")

    // Times without an offset are taken as UTC
    private val PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mmXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd",
    )

    private fun utc(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
