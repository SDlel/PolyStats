package com.polystats.android.services

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.polystats.android.data.repository.MarketRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class MarketMonitorService : Service() {
    @Inject lateinit var repository: MarketRepository
    @Inject lateinit var notificationHelper: MarketNotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var rotationIndex = 0
    private var pausedUntil = 0L
    private var loopsStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_NEXT -> {
                rotationIndex++
                pausedUntil = System.currentTimeMillis() + PAUSE_MS
                publishCurrent()
                return START_STICKY
            }
            ACTION_PAUSE -> {
                pausedUntil = if (System.currentTimeMillis() < pausedUntil) 0L else System.currentTimeMillis() + PAUSE_MS
                publishCurrent()
                return START_STICKY
            }
        }

        val initial = notificationHelper.buildNowBarNotification(currentMarket())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(MarketNotificationHelper.NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(MarketNotificationHelper.NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }
        } else {
            startForeground(MarketNotificationHelper.NOTIFICATION_ID, initial)
        }

        if (!loopsStarted) {
            loopsStarted = true
            repository.startAdaptiveRefresh()
            scope.launch {
                repository.uiState.collectLatest { state ->
                    rotationIndex %= max(1, state.nowBarMarkets.size)
                    publishCurrent()
                }
            }
            scope.launch {
                while (true) {
                    val state = repository.uiState.value
                    val interval = state.userState.rotationMinutes.coerceIn(1, 60) * 60_000L
                    delay(interval)
                    if (state.userState.rotationEnabled && state.userState.lockedNowBarMarketId == null && System.currentTimeMillis() >= pausedUntil) {
                        rotationIndex++
                        publishCurrent()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun currentMarket() = repository.uiState.value.nowBarMarkets.let { markets ->
        if (markets.isEmpty()) null else markets[rotationIndex.floorMod(markets.size)]
    }

    private fun publishCurrent() {
        val paused = System.currentTimeMillis() < pausedUntil
        getSystemService(NotificationManager::class.java)
            .notify(MarketNotificationHelper.NOTIFICATION_ID, notificationHelper.buildNowBarNotification(currentMarket(), paused))
    }

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

    companion object {
        const val ACTION_NEXT = "com.polystats.android.action.NEXT_MARKET"
        const val ACTION_PAUSE = "com.polystats.android.action.PAUSE_ROTATION"
        private const val PAUSE_MS = 2 * 60_000L
    }
}
