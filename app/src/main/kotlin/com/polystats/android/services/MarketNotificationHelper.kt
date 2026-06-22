package com.polystats.android.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.polystats.android.MainActivity
import com.polystats.android.R
import com.polystats.android.domain.RankedMarket
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class MarketNotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        notificationManager.createNotificationChannelGroup(
            NotificationChannelGroup(CHANNEL_ID, context.getString(R.string.notification_channel_name))
        )
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_content_text)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            setShowBadge(false)
            setSound(null, null)
            group = CHANNEL_ID
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun buildNowBarNotification(topMarket: RankedMarket?, paused: Boolean = false): Notification {
        val market = topMarket?.market
        val title = market?.title ?: context.getString(R.string.notification_content_title)
        val compact = market?.let { "YES ${(it.yesProbability * 100).roundToInt()}%  NO ${(it.noProbability * 100).roundToInt()}%" }
            ?: context.getString(R.string.notification_content_text)
        val openIntent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).apply {
                market?.id?.let { putExtra(MainActivity.EXTRA_MARKET_ID, it) }
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            context,
            11,
            Intent(context, MarketMonitorService::class.java).setAction(MarketMonitorService.ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            context,
            12,
            Intent(context, MarketMonitorService::class.java).setAction(MarketMonitorService.ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(if (paused) "$compact | rotation paused" else compact)
            .setSubText(market?.category?.label ?: "Real-Time Polymarket Intelligence")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent)
            .setShortCriticalText("PolyStats")
            .setRequestPromotedOngoing(true)
            .addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_speed, "Next", nextIntent).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_notifications, if (paused) "Resume" else "Pause", pauseIntent).build()
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    market?.let {
                        "YES ${(it.yesProbability * 100).roundToInt()}% | NO ${(it.noProbability * 100).roundToInt()}% | 24H ${signedPercent(it.dailyChange)} | Volume ${formatMoney(it.volume)} | Liquidity ${formatMoney(it.liquidity)} | ${it.category.label}"
                    } ?: context.getString(R.string.notification_content_text)
                )
            )
            .build()
    }

    private fun signedPercent(value: Double): String {
        val pct = value * 100.0
        return "${if (pct >= 0) "+" else ""}${"%.1f".format(pct)}%"
    }

    private fun formatMoney(value: Double): String = when {
        value >= 1_000_000 -> "$${"%.1f".format(value / 1_000_000)}M"
        value >= 1_000 -> "$${"%.0f".format(value / 1_000)}K"
        else -> "$${value.roundToInt()}"
    }

    companion object {
        const val CHANNEL_ID = "polystats_live_markets"
        const val NOTIFICATION_ID = 7001
    }
}
