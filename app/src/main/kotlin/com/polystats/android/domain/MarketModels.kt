package com.polystats.android.domain

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

enum class MarketCategory(val label: String) {
    POLITICS("Politics"),
    CRYPTO("Crypto"),
    SPORTS("Sports"),
    AI("AI"),
    TECHNOLOGY("Technology"),
    ENTERTAINMENT("Entertainment"),
    BUSINESS("Business"),
    SCIENCE("Science"),
    OTHER("Other")
}

enum class InterestPriority(val label: String, val weight: Double) {
    HIGH("High Interest", 1.0),
    MEDIUM("Medium Interest", 0.62),
    LOW("Low Interest", 0.28),
    DISABLED("Disabled", 0.0)
}

enum class DiscoveryMode(val label: String, val noveltyWeight: Double) {
    CONSERVATIVE("Conservative", 0.04),
    BALANCED("Balanced", 0.12),
    AGGRESSIVE("Aggressive", 0.25)
}

data class Market(
    val id: String,
    val title: String,
    val description: String,
    val category: MarketCategory,
    val yesProbability: Double,
    val noProbability: Double = 1.0 - yesProbability,
    val dailyChange: Double,
    val volume: Double,
    val volume24h: Double = 0.0,
    val liquidity: Double,
    val traderCount: Int,
    val openInterest: Double,
    val ageHours: Int,
    val trending: Boolean,
    val active: Boolean,
    val status: String,
    val history: List<Double>,
    val tags: List<String> = emptyList(),
    val spread: Double = 0.0,
    val acceptingOrders: Boolean = true
)

data class MetricRange(
    val minVolume: Double? = null,
    val maxVolume: Double? = null,
    val minLiquidity: Double? = null,
    val maxLiquidity: Double? = null,
    val minTraders: Int? = null,
    val maxTraders: Int? = null,
    val minAgeHours: Int? = null,
    val maxAgeHours: Int? = null,
    val minProbability: Double? = null,
    val maxProbability: Double? = null,
    val minDailyChange: Double? = null,
    val maxDailyChange: Double? = null,
    val minOpenInterest: Double? = null,
    val maxOpenInterest: Double? = null,
    val onlyTrending: Boolean = false,
    val onlyFavorites: Boolean = false
) {
    fun matches(market: Market, favoriteIds: Set<String>): Boolean {
        if (onlyTrending && !market.trending) return false
        if (onlyFavorites && market.id !in favoriteIds) return false
        if (minVolume != null && market.volume < minVolume) return false
        if (maxVolume != null && market.volume > maxVolume) return false
        if (minLiquidity != null && market.liquidity < minLiquidity) return false
        if (maxLiquidity != null && market.liquidity > maxLiquidity) return false
        if (minTraders != null && market.traderCount < minTraders) return false
        if (maxTraders != null && market.traderCount > maxTraders) return false
        if (minAgeHours != null && market.ageHours < minAgeHours) return false
        if (maxAgeHours != null && market.ageHours > maxAgeHours) return false
        val probabilityPercent = market.yesProbability * 100.0
        if (minProbability != null && probabilityPercent < minProbability) return false
        if (maxProbability != null && probabilityPercent > maxProbability) return false
        val changePercent = market.dailyChange * 100.0
        if (minDailyChange != null && changePercent < minDailyChange) return false
        if (maxDailyChange != null && changePercent > maxDailyChange) return false
        if (minOpenInterest != null && market.openInterest < minOpenInterest) return false
        if (maxOpenInterest != null && market.openInterest > maxOpenInterest) return false
        return true
    }
}

data class ScoringWeights(
    val volume: Double = 0.34,
    val liquidity: Double = 0.18,
    val trend: Double = 0.18,
    val activity: Double = 0.12,
    val userInterest: Double = 0.18
)

data class PreferenceProfile(
    val id: String,
    val name: String,
    val categoryPriorities: Map<MarketCategory, InterestPriority>,
    val homeFilter: MetricRange,
    val nowBarFilter: MetricRange,
    val scoringWeights: ScoringWeights,
    val discoveryMode: DiscoveryMode
) {
    companion object {
        fun presets(): List<PreferenceProfile> {
            fun priorities(vararg high: MarketCategory) = MarketCategory.entries.associateWith {
                when (it) {
                    in high -> InterestPriority.HIGH
                    MarketCategory.OTHER -> InterestPriority.LOW
                    else -> InterestPriority.MEDIUM
                }
            }
            return listOf(
                PreferenceProfile(
                    id = "balanced",
                    name = "Balanced Intelligence",
                    categoryPriorities = MarketCategory.entries.associateWith { InterestPriority.MEDIUM },
                    homeFilter = MetricRange(minProbability = 15.0, maxProbability = 85.0),
                    nowBarFilter = MetricRange(minVolume = 100_000.0, minTraders = 120, minProbability = 20.0, maxProbability = 80.0),
                    scoringWeights = ScoringWeights(),
                    discoveryMode = DiscoveryMode.BALANCED
                ),
                PreferenceProfile(
                    id = "crypto_trader",
                    name = "Crypto Trader",
                    categoryPriorities = priorities(MarketCategory.CRYPTO),
                    homeFilter = MetricRange(minVolume = 250_000.0, minProbability = 15.0, maxProbability = 85.0),
                    nowBarFilter = MetricRange(minVolume = 500_000.0, minTraders = 300, minProbability = 20.0, maxProbability = 80.0),
                    scoringWeights = ScoringWeights(volume = 0.40, liquidity = 0.20, trend = 0.20, activity = 0.10, userInterest = 0.10),
                    discoveryMode = DiscoveryMode.CONSERVATIVE
                ),
                PreferenceProfile(
                    id = "politics",
                    name = "Politics Enthusiast",
                    categoryPriorities = priorities(MarketCategory.POLITICS),
                    homeFilter = MetricRange(minProbability = 10.0, maxProbability = 90.0),
                    nowBarFilter = MetricRange(minVolume = 250_000.0, minProbability = 20.0, maxProbability = 80.0),
                    scoringWeights = ScoringWeights(volume = 0.24, liquidity = 0.14, trend = 0.22, activity = 0.16, userInterest = 0.24),
                    discoveryMode = DiscoveryMode.BALANCED
                ),
                PreferenceProfile(
                    id = "sports_fan",
                    name = "Sports Fan",
                    categoryPriorities = priorities(MarketCategory.SPORTS),
                    homeFilter = MetricRange(minProbability = 10.0, maxProbability = 90.0),
                    nowBarFilter = MetricRange(minProbability = 20.0, maxProbability = 80.0),
                    scoringWeights = ScoringWeights(volume = 0.22, liquidity = 0.12, trend = 0.26, activity = 0.22, userInterest = 0.18),
                    discoveryMode = DiscoveryMode.AGGRESSIVE
                ),
                PreferenceProfile(
                    id = "ai_watcher",
                    name = "AI Watcher",
                    categoryPriorities = priorities(MarketCategory.AI, MarketCategory.TECHNOLOGY),
                    homeFilter = MetricRange(minTraders = 80, minProbability = 15.0, maxProbability = 85.0),
                    nowBarFilter = MetricRange(minVolume = 150_000.0, minProbability = 20.0, maxProbability = 80.0),
                    scoringWeights = ScoringWeights(volume = 0.24, liquidity = 0.14, trend = 0.30, activity = 0.12, userInterest = 0.20),
                    discoveryMode = DiscoveryMode.BALANCED
                )
            )
        }
    }
}

data class UserMarketState(
    val favoriteIds: Set<String> = emptySet(),
    val pinnedIds: Set<String> = emptySet(),
    val mutedIds: Set<String> = emptySet(),
    val hiddenIds: Set<String> = emptySet(),
    val enabledCategories: Set<MarketCategory> = MarketCategory.entries.toSet(),
    val lockedNowBarMarketId: String? = null,
    val rotationEnabled: Boolean = true,
    val rotationMinutes: Int = 1,
    val customScoringWeights: ScoringWeights? = null
)

data class RankedMarket(
    val market: Market,
    val score: Double,
    val reasons: List<String>
)

class MarketRanker {
    fun rank(
        markets: List<Market>,
        profile: PreferenceProfile,
        userState: UserMarketState,
        nowBar: Boolean
    ): List<RankedMarket> {
        val filter = if (nowBar) profile.nowBarFilter else profile.homeFilter
        val ranked = markets
            .asSequence()
            .filter { it.id !in userState.mutedIds && it.id !in userState.hiddenIds }
            .filter {
                val pinned = it.id in userState.pinnedIds
                pinned || (it.category in userState.enabledCategories &&
                    profile.categoryPriorities[it.category] != InterestPriority.DISABLED &&
                    filter.matches(it, userState.favoriteIds))
            }
            .map { market -> scoreMarket(market, profile, userState) }
            .toList()
        return categoryBalanced(ranked, userState)
    }

    fun rankForNowBarRotation(
        markets: List<Market>,
        profile: PreferenceProfile,
        userState: UserMarketState
    ): List<RankedMarket> {
        val eligible = markets
            .asSequence()
            .filter { it.id !in userState.mutedIds && it.id !in userState.hiddenIds }
            .filter { it.status == "Open" && it.acceptingOrders }
            .filter { it.category in userState.enabledCategories || it.id in userState.pinnedIds }
            .filter { profile.categoryPriorities[it.category] != InterestPriority.DISABLED || it.id in userState.pinnedIds }
            .map { scoreMarket(it, profile, userState) }
            .toList()

        val pinned = eligible
            .filter { it.market.id in userState.pinnedIds }
            .sortedByDescending { it.score }

        val pinnedIds = pinned.map { it.market.id }.toSet()
        val buckets = eligible
            .filterNot { it.market.id in pinnedIds }
            .groupBy { it.market.category }

        val categoryCandidates = userState.enabledCategories
            .sortedBy { it.ordinal }
            .associateWith { category ->
                val ranked = buckets[category].orEmpty().sortedByDescending { it.score }
                val strict = ranked.filter { profile.nowBarFilter.matches(it.market, userState.favoriteIds) }
                val uncertain = ranked.filter { it.market.yesProbability in 0.10..0.90 }
                val broad = ranked.filter { it.market.yesProbability in 0.02..0.98 }
                when {
                    strict.isNotEmpty() -> strict
                    uncertain.isNotEmpty() -> uncertain
                    broad.isNotEmpty() -> broad
                    else -> ranked
                }
            }
            .filterValues { it.isNotEmpty() }

        return roundRobinByCategory(pinned, userState.enabledCategories) +
            roundRobinBuckets(categoryCandidates)
    }

    private fun categoryBalanced(markets: List<RankedMarket>, state: UserMarketState): List<RankedMarket> {
        val pinned = markets
            .filter { it.market.id in state.pinnedIds }
            .sortedByDescending { it.score }
        val normal = markets
            .filterNot { it.market.id in state.pinnedIds }
            .sortedByDescending { it.score }
        return roundRobinByCategory(pinned, state.enabledCategories) + roundRobinByCategory(normal, state.enabledCategories)
    }

    private fun roundRobinByCategory(markets: List<RankedMarket>, enabledCategories: Set<MarketCategory>): List<RankedMarket> {
        if (markets.isEmpty()) return emptyList()
        val categoryOrder = MarketCategory.entries.filter { it in enabledCategories || markets.any { ranked -> ranked.market.category == it } }
        val buckets = markets.groupBy { it.market.category }.mapValues { (_, value) -> value.sortedByDescending { it.score } }
        val cursors = mutableMapOf<MarketCategory, Int>()
        val output = mutableListOf<RankedMarket>()
        while (output.size < markets.size) {
            var addedThisPass = false
            for (category in categoryOrder) {
                val bucket = buckets[category].orEmpty()
                val index = cursors[category] ?: 0
                if (index < bucket.size) {
                    output += bucket[index]
                    cursors[category] = index + 1
                    addedThisPass = true
                }
            }
            if (!addedThisPass) break
        }
        return output
    }

    private fun roundRobinBuckets(buckets: Map<MarketCategory, List<RankedMarket>>): List<RankedMarket> {
        val categoryOrder = MarketCategory.entries.filter { buckets[it].orEmpty().isNotEmpty() }
        val cursors = mutableMapOf<MarketCategory, Int>()
        val output = mutableListOf<RankedMarket>()
        val total = buckets.values.sumOf { it.size }
        while (output.size < total) {
            var addedThisPass = false
            for (category in categoryOrder) {
                val bucket = buckets[category].orEmpty()
                val index = cursors[category] ?: 0
                if (index < bucket.size) {
                    output += bucket[index]
                    cursors[category] = index + 1
                    addedThisPass = true
                }
            }
            if (!addedThisPass) break
        }
        return output.distinctBy { it.market.id }
    }

    private fun scoreMarket(market: Market, profile: PreferenceProfile, state: UserMarketState): RankedMarket {
        val weights = state.customScoringWeights ?: profile.scoringWeights
        val volumeScore = normalizedMoney(market.volume)
        val liquidityScore = normalizedMoney(market.liquidity)
        val uncertaintyScore = uncertaintyScore(market.yesProbability)
        val trendScore = (if (market.trending) 0.55 else 0.0) +
            min(0.45, abs(market.dailyChange) * 7.0)
        val activityScore = (
            (market.traderCount / 1_500.0).coerceAtMost(0.45) +
                normalizedMoney(market.volume24h).coerceAtMost(0.35) +
                if (market.active) 0.20 else 0.0
            ).coerceAtMost(1.0)
        val interestScore = profile.categoryPriorities[market.category]?.weight ?: 0.0
        val novelty = profile.discoveryMode.noveltyWeight * (1.0 / max(1.0, market.ageHours / 24.0))
        val favoriteBoost = if (market.id in state.favoriteIds) 0.28 else 0.0
        val pinBoost = if (market.id in state.pinnedIds) 0.42 else 0.0
        val decidedPenalty = if (market.yesProbability !in 0.05..0.95 || market.status != "Open" || !market.acceptingOrders) 0.55 else 0.0
        val score = weights.volume * volumeScore +
            weights.liquidity * liquidityScore +
            weights.trend * trendScore +
            weights.activity * activityScore +
            weights.userInterest * interestScore +
            0.30 * uncertaintyScore +
            novelty + favoriteBoost + pinBoost -
            decidedPenalty
        val reasons = buildList {
            add(market.category.label)
            if (market.trending) add("Trending")
            if (market.active) add("Live")
            if (market.yesProbability in 0.20..0.80) add("Uncertain")
            if (market.id in state.pinnedIds) add("Pinned")
            if (market.id in state.favoriteIds) add("Favorite")
        }
        return RankedMarket(market, score, reasons)
    }

    private fun normalizedMoney(value: Double): Double = (ln(value.coerceAtLeast(1.0)) / ln(10_000_000.0)).coerceIn(0.0, 1.0)
    private fun uncertaintyScore(probability: Double): Double {
        val distanceFromCenter = abs(probability - 0.5) / 0.5
        return (1.0 - distanceFromCenter).coerceIn(0.0, 1.0)
    }
}
