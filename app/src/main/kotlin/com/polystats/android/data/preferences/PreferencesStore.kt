package com.polystats.android.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.polystats.android.domain.MarketCategory
import com.polystats.android.domain.PreferenceProfile
import com.polystats.android.domain.ScoringWeights
import com.polystats.android.domain.UserMarketState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.polyStatsDataStore by preferencesDataStore(name = "polystats_preferences")

@Singleton
class PreferencesStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.polyStatsDataStore

    val activeProfileId: Flow<String> = dataStore.data.map { it[ACTIVE_PROFILE] ?: "balanced" }
    val userMarketState: Flow<UserMarketState> = dataStore.data.map {
        UserMarketState(
            favoriteIds = it[FAVORITES].toSet(),
            pinnedIds = it[PINS].toSet(),
            mutedIds = it[MUTES].toSet(),
            hiddenIds = it[HIDDEN].toSet(),
            enabledCategories = it[ENABLED_CATEGORIES].toCategorySet(),
            lockedNowBarMarketId = it[LOCKED_NOW_BAR],
            rotationEnabled = it[ROTATION_ENABLED] != "false",
            rotationMinutes = it[ROTATION_MINUTES]?.toIntOrNull()?.coerceIn(1, 60) ?: 1,
            customScoringWeights = ScoringWeights(
                volume = it[WEIGHT_VOLUME] ?: ScoringWeights().volume,
                liquidity = it[WEIGHT_LIQUIDITY] ?: ScoringWeights().liquidity,
                trend = it[WEIGHT_TREND] ?: ScoringWeights().trend,
                activity = it[WEIGHT_ACTIVITY] ?: ScoringWeights().activity,
                userInterest = it[WEIGHT_USER_INTEREST] ?: ScoringWeights().userInterest
            )
        )
    }

    suspend fun setActiveProfile(id: String) = dataStore.edit { it[ACTIVE_PROFILE] = id }
    suspend fun setRotationEnabled(enabled: Boolean) = dataStore.edit { it[ROTATION_ENABLED] = enabled.toString() }
    suspend fun setRotationMinutes(minutes: Int) = dataStore.edit { it[ROTATION_MINUTES] = minutes.coerceIn(1, 60).toString() }
    suspend fun setLockedNowBarMarket(id: String?) = dataStore.edit {
        if (id == null) it.remove(LOCKED_NOW_BAR) else it[LOCKED_NOW_BAR] = id
    }
    suspend fun toggleCategory(category: MarketCategory) = dataStore.edit { prefs ->
        val next = prefs[ENABLED_CATEGORIES].toCategorySet().toMutableSet()
        if (category in next && next.size > 1) next.remove(category) else next.add(category)
        prefs[ENABLED_CATEGORIES] = next.joinToString("|") { it.name }
    }
    suspend fun setScoringWeights(weights: ScoringWeights) = dataStore.edit {
        it[WEIGHT_VOLUME] = weights.volume.coerceIn(0.0, 1.0)
        it[WEIGHT_LIQUIDITY] = weights.liquidity.coerceIn(0.0, 1.0)
        it[WEIGHT_TREND] = weights.trend.coerceIn(0.0, 1.0)
        it[WEIGHT_ACTIVITY] = weights.activity.coerceIn(0.0, 1.0)
        it[WEIGHT_USER_INTEREST] = weights.userInterest.coerceIn(0.0, 1.0)
    }

    suspend fun toggleFavorite(id: String) = toggle(FAVORITES, id)
    suspend fun togglePinned(id: String) = toggle(PINS, id)
    suspend fun toggleMuted(id: String) = toggle(MUTES, id)
    suspend fun hide(id: String) = toggle(HIDDEN, id, forceAdd = true)

    fun profileFor(id: String): PreferenceProfile = PreferenceProfile.presets().firstOrNull { it.id == id } ?: PreferenceProfile.presets().first()

    private suspend fun toggle(key: androidx.datastore.preferences.core.Preferences.Key<String>, id: String, forceAdd: Boolean = false) {
        dataStore.edit { prefs ->
            val next = prefs[key].toSet().toMutableSet()
            if (id in next && !forceAdd) next.remove(id) else next.add(id)
            prefs[key] = next.sorted().joinToString("|")
        }
    }

    private fun String?.toSet(): Set<String> = this?.split("|")?.filter { it.isNotBlank() }?.toSet().orEmpty()
    private fun String?.toCategorySet(): Set<MarketCategory> {
        val parsed = toSet().mapNotNull { raw -> MarketCategory.entries.firstOrNull { it.name == raw } }.toSet()
        return parsed.ifEmpty { MarketCategory.entries.toSet() }
    }

    private companion object {
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val FAVORITES = stringPreferencesKey("favorite_ids")
        val PINS = stringPreferencesKey("pinned_ids")
        val MUTES = stringPreferencesKey("muted_ids")
        val HIDDEN = stringPreferencesKey("hidden_ids")
        val ENABLED_CATEGORIES = stringPreferencesKey("enabled_categories")
        val LOCKED_NOW_BAR = stringPreferencesKey("locked_now_bar_id")
        val ROTATION_ENABLED = stringPreferencesKey("rotation_enabled")
        val ROTATION_MINUTES = stringPreferencesKey("rotation_minutes")
        val WEIGHT_VOLUME = doublePreferencesKey("weight_volume")
        val WEIGHT_LIQUIDITY = doublePreferencesKey("weight_liquidity")
        val WEIGHT_TREND = doublePreferencesKey("weight_trend")
        val WEIGHT_ACTIVITY = doublePreferencesKey("weight_activity")
        val WEIGHT_USER_INTEREST = doublePreferencesKey("weight_user_interest")
    }
}
