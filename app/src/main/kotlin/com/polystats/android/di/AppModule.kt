package com.polystats.android.di

import com.polystats.android.data.network.MarketDataSource
import com.polystats.android.data.network.PolymarketRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindMarketDataSource(source: PolymarketRemoteDataSource): MarketDataSource
}
