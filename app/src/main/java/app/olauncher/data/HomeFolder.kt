package app.olauncher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A folder on a home slot. Its apps are kept the way home apps are:
 * label, package, activity and user, so work profile apps launch too.
 */
data class HomeFolder(val name: String, val apps: List<App>) {

    data class App(
        val name: String,
        val packageName: String,
        val activityClassName: String?,
        val user: String,
    ) {
        /** Same app in the same profile, whatever its label. */
        val key: String get() = "$packageName/$activityClassName|$user"
    }

    fun toJson(): JSONObject = JSONObject()
        .put(NAME, name)
        .put(APPS, JSONArray(apps.map { app ->
            JSONObject()
                .put(NAME, app.name)
                .put(PACKAGE, app.packageName)
                .put(ACTIVITY, app.activityClassName.orEmpty())
                .put(USER, app.user)
        }))

    companion object {
        private const val NAME = "name"
        private const val APPS = "apps"
        private const val PACKAGE = "package"
        private const val ACTIVITY = "activity"
        private const val USER = "user"

        fun fromJson(json: JSONObject): HomeFolder {
            val apps = json.optJSONArray(APPS)
            return HomeFolder(
                name = json.optString(NAME),
                apps = (0 until (apps?.length() ?: 0))
                    .mapNotNull { apps?.optJSONObject(it) }
                    .map {
                        App(
                            name = it.optString(NAME),
                            packageName = it.optString(PACKAGE),
                            activityClassName = it.optString(ACTIVITY).ifEmpty { null },
                            user = it.optString(USER),
                        )
                    }
                    .filter { it.packageName.isNotEmpty() },
            )
        }
    }
}
