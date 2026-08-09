package com.polystats.android

import android.app.Application
import com.polystats.android.services.MarketNotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PolyStatsApplication : Application() {
    @Inject lateinit var notificationHelper: MarketNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
    }
}
