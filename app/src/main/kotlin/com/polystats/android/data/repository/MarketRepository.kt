package com.polystats.android.data.repository

import com.polystats.android.data.network.MarketDataSource
import com.polystats.android.data.preferences.PreferencesStore
import com.polystats.android.domain.Market
import com.polystats.android.domain.MarketCategory
import com.polystats.android.domain.MarketRanker
import com.polystats.android.domain.PreferenceProfile
import com.polystats.android.domain.RankedMarket
import com.polystats.android.domain.ScoringWeights
import com.polystats.android.domain.UserMarketState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class MarketUiState(
    val loading: Boolean = true,
    val query: String = "",
    val activeProfile: PreferenceProfile = PreferenceProfile.presets().first(),
    val profiles: List<PreferenceProfile> = PreferenceProfile.presets(),
    val userState: UserMarketState = UserMarketState(),
    val homeMarkets: List<RankedMarket> = emptyList(),
    val nowBarMarkets: List<RankedMarket> = emptyList(),
    val selectedMarket: Market? = null,
    val liveEventMode: Boolean = false,
    val lastUpdatedAt: Long = 0L,
    val error: String? = null
)

@Singleton
class MarketRepository @Inject constructor(
    private val dataSource: MarketDataSource,
    private val preferencesStore: PreferencesStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ranker = MarketRanker()
    private val markets = MutableStateFlow<List<Market>>(emptyList())
    private val query = MutableStateFlow("")
    private val loading = MutableStateFlow(true)
    private val lastUpdatedAt = MutableStateFlow(0L)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MarketUiState>
    private val mutableUiState = MutableStateFlow(MarketUiState())

    init {
        uiState = mutableUiState.asStateFlow()
        scope.launch {
            combine(
                markets,
                query,
                preferencesStore.activeProfileId,
                preferencesStore.userMarketState,
                loading,
                lastUpdatedAt,
                error
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val allMarkets = values[0] as List<Market>
                val search = values[1] as String
                val profile = preferencesStore.profileFor(values[2] as String)
                val userState = values[3] as UserMarketState
                val activeProfile = profile.copy(scoringWeights = userState.customScoringWeights ?: profile.scoringWeights)
                val isLoading = values[4] as Boolean
                val updatedAt = values[5] as Long
                val issue = values[6] as String?
                val searched = allMarkets.filter {
                    it.id in userState.pinnedIds ||
                        search.isBlank() ||
                        it.title.contains(search, ignoreCase = true) ||
                        it.tags.any { tag -> tag.contains(search, ignoreCase = true) }
                }
                val home = ranker.rank(searched, activeProfile, userState, nowBar = false)
                val nowBar = ranker.rankForNowBarRotation(allMarkets, activeProfile, userState)
                MarketUiState(
                    loading = isLoading,
                    query = search,
                    activeProfile = activeProfile,
                    userState = userState,
                    homeMarkets = home,
                    nowBarMarkets = if (userState.lockedNowBarMarketId != null) {
                        nowBar.sortedByDescending { it.market.id == userState.lockedNowBarMarketId }
                    } else nowBar,
                    selectedMarket = home.firstOrNull()?.market,
                    liveEventMode = allMarkets.any { it.active && (it.trending || abs(it.dailyChange) > 0.05) },
                    lastUpdatedAt = updatedAt,
                    error = issue
                )
            }.collect { mutableUiState.value = it }
        }
        refresh()
    }

    fun refresh() {
        scope.launch {
            loading.value = true
            runCatching { dataSource.fetchMarkets() }
                .onSuccess {
                    markets.value = it
                    lastUpdatedAt.value = System.currentTimeMillis()
                    error.value = null
                }
                .onFailure { error.value = it.message ?: "Unable to refresh markets" }
            loading.value = false
        }
    }

    fun startAdaptiveRefresh() {
        scope.launch {
            while (true) {
                val state = mutableUiState.value
                val active = state.liveEventMode || state.nowBarMarkets.firstOrNull()?.market?.active == true
                delay(if (active) 60_000L else 6 * 60_000L)
                refresh()
            }
        }
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    suspend fun setProfile(id: String) = preferencesStore.setActiveProfile(id)
    suspend fun toggleFavorite(id: String) = preferencesStore.toggleFavorite(id)
    suspend fun togglePinned(id: String) = preferencesStore.togglePinned(id)
    suspend fun toggleMuted(id: String) = preferencesStore.toggleMuted(id)
    suspend fun hide(id: String) = preferencesStore.hide(id)
    suspend fun lockNowBar(id: String?) = preferencesStore.setLockedNowBarMarket(id)
    suspend fun setRotationEnabled(enabled: Boolean) = preferencesStore.setRotationEnabled(enabled)
    suspend fun setRotationMinutes(minutes: Int) = preferencesStore.setRotationMinutes(minutes)
    suspend fun toggleCategory(category: MarketCategory) = preferencesStore.toggleCategory(category)
    suspend fun setScoringWeights(weights: ScoringWeights) = preferencesStore.setScoringWeights(weights)
}
