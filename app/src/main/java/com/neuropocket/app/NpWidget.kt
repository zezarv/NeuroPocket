package com.neuropocket.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Виджет 2x1: быстрый чат и голосовой чат. */
class NpWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(ctx.packageName, R.layout.np_widget)
            views.setOnClickPendingIntent(R.id.w_chat, actionIntent(ctx, "np.action.CHAT", id))
            views.setOnClickPendingIntent(R.id.w_voice, actionIntent(ctx, "np.action.VOICE", id))
            mgr.updateAppWidget(id, views)
        }
    }

    private fun actionIntent(ctx: Context, action: String, widgetId: Int): PendingIntent {
        val it = Intent(ctx, MainActivity::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getActivity(
            ctx, action.hashCode() + widgetId, it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
