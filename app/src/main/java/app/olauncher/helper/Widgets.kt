package app.olauncher.helper

import android.appwidget.AppWidgetHost
import android.content.Context
import app.olauncher.data.Constants

/** Every instance shares the same host id, so ids allocated by one are visible to all. */
fun Context.widgetHost() = AppWidgetHost(applicationContext, Constants.WIDGET_HOST_ID)
