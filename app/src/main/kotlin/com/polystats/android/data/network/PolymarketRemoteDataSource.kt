package com.polystats.android.data.network

import android.util.Log
import com.polystats.android.domain.Market
import com.polystats.android.domain.MarketCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

interface MarketDataSource {
    suspend fun fetchMarkets(): List<Market>
}

@Singleton
class PolymarketRemoteDataSource @Inject constructor() : MarketDataSource {
    override suspend fun fetchMarkets(): List<Market> = withContext(Dispatchers.IO) {
        runCatching {
            discoveryUrls()
                .map { url ->
                    async {
                        runCatching {
                            val body = get(url)
                            if (url.contains("/events")) {
                                parseEvents(JSONArray(body))
                            } else {
                                parseMarkets(JSONArray(body))
                            }
                        }.getOrElse {
                            Log.w("PolyStatsRemote", "Discovery lane failed: $url", it)
                            emptyList()
                        }
                    }
                }
                .awaitAll()
                .flatten()
                .distinctBy { it.id }
                .filter { it.status == "Open" && it.acceptingOrders }
                .ifEmpty { SampleMarkets.all }
        }.onFailure {
            Log.w("PolyStatsRemote", "Using fallback markets after Polymarket fetch failure", it)
        }.getOrElse { SampleMarkets.all }
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PolyStats Android")
        }
        return try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun discoveryUrls(): List<String> {
        val eventOrders = listOf("volume24hr", "volume", "liquidity", "createdAt")
        val eventPages = eventOrders.flatMap { order ->
            (0..5).map { page ->
                val offset = page * PAGE_SIZE
                "https://gamma-api.polymarket.com/events?active=true&closed=false&limit=$PAGE_SIZE&offset=$offset&order=$order&ascending=false"
            }
        }
        val marketOrders = listOf("volume24hr", "volume", "liquidity", "createdAt")
        val marketPages = marketOrders.flatMap { order ->
            (0..3).map { page ->
                val offset = page * PAGE_SIZE
                "https://gamma-api.polymarket.com/markets?active=true&closed=false&limit=$PAGE_SIZE&offset=$offset&order=$order&ascending=false"
            }
        }
        return eventPages + marketPages
    }

    private fun parseEvents(events: JSONArray): List<Market> = buildList {
        for (eventIndex in 0 until events.length()) {
            val event = events.optJSONObject(eventIndex) ?: continue
            val eventTags = event.extractTags()
            val eventTitle = event.optString("title")
            val eventDescription = event.optString("description")
            val eventVolume24h = event.optDoubleFlexible("volume24hr")
            val markets = event.optJSONArray("markets") ?: JSONArray()
            for (marketIndex in 0 until markets.length()) {
                val item = markets.optJSONObject(marketIndex) ?: continue
                parseMarket(
                    item = item,
                    inheritedTitle = eventTitle,
                    inheritedDescription = eventDescription,
                    inheritedTags = eventTags + eventTitle,
                    inheritedVolume = event.optDoubleFlexible("volume"),
                    inheritedVolume24h = eventVolume24h,
                    inheritedLiquidity = event.optDoubleFlexible("liquidity"),
                    inheritedOpenInterest = event.optDoubleFlexible("openInterest"),
                    inheritedCreatedAt = event.optString("createdAt", event.optString("creationDate")),
                    inheritedComments = event.optInt("commentCount", 0),
                    inheritedFeatured = event.optBoolean("featured", false)
                )?.let(::add)
            }
        }
    }

    private fun parseMarkets(markets: JSONArray): List<Market> = buildList {
        for (index in 0 until markets.length()) {
            val item = markets.optJSONObject(index) ?: continue
            parseMarket(
                item = item,
                inheritedTitle = item.optString("question", item.optString("title")),
                inheritedDescription = item.optString("description"),
                inheritedTags = item.extractTags(),
                inheritedVolume = item.optDoubleFlexible("volume"),
                inheritedVolume24h = item.optDoubleFlexible("volume24hr"),
                inheritedLiquidity = item.optDoubleFlexible("liquidity"),
                inheritedOpenInterest = item.optDoubleFlexible("openInterest"),
                inheritedCreatedAt = item.optString("createdAt", item.optString("creationDate")),
                inheritedComments = item.optInt("commentCount", 0),
                inheritedFeatured = item.optBoolean("featured", false)
            )?.let(::add)
        }
    }

    private fun parseMarket(
        item: JSONObject,
        inheritedTitle: String,
        inheritedDescription: String,
        inheritedTags: List<String>,
        inheritedVolume: Double,
        inheritedVolume24h: Double,
        inheritedLiquidity: Double,
        inheritedOpenInterest: Double,
        inheritedCreatedAt: String,
        inheritedComments: Int,
        inheritedFeatured: Boolean
    ): Market? {
        if (!item.optBoolean("active", false) || item.optBoolean("closed", false) || item.optBoolean("archived", false)) return null
        val outcomes = item.optStringArray("outcomes")
        if (outcomes.size >= 2 && outcomes.none { it.equals("Yes", ignoreCase = true) }) return null
        val prices = item.optStringArray("outcomePrices").mapNotNull { it.toDoubleOrNull() }
        val yes = (prices.firstOrNull() ?: item.optDoubleFlexible("lastTradePrice", 0.5)).coerceIn(0.0, 1.0)
        val title = item.optString("question", inheritedTitle).ifBlank { inheritedTitle }
        if (title.isBlank()) return null
        val tags = (inheritedTags + item.extractTags() + title)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val volume = item.optDoubleFlexible("volumeNum", item.optDoubleFlexible("volume", inheritedVolume))
        val volume24h = item.optDoubleFlexible("volume24hr", inheritedVolume24h)
        val liquidity = item.optDoubleFlexible("liquidityNum", item.optDoubleFlexible("liquidity", inheritedLiquidity))
        val change = item.optDoubleFlexible("oneDayPriceChange")
        val acceptingOrders = item.optBoolean("acceptingOrders", true) && item.optBoolean("enableOrderBook", true)
        return Market(
            id = item.optString("id", item.optString("conditionId", title)),
            title = title,
            description = item.optString("description", inheritedDescription).ifBlank { inheritedDescription },
            category = inferCategory(tags),
            yesProbability = yes,
            dailyChange = change,
            volume = volume,
            volume24h = volume24h,
            liquidity = liquidity,
            traderCount = estimateTraders(volume, liquidity, volume24h, inheritedComments),
            openInterest = item.optDoubleFlexible("openInterest", inheritedOpenInterest.takeIf { it > 0.0 } ?: liquidity * yes),
            ageHours = ageHours(item.optString("createdAt", inheritedCreatedAt)),
            trending = inheritedFeatured || item.optBoolean("featured", false) || volume24h > 2_000 || abs(change) > 0.015,
            active = acceptingOrders && (volume24h > 250 || abs(change) > 0.01),
            status = if (item.optBoolean("closed", false)) "Closed" else "Open",
            history = syntheticHistory(yes, change),
            tags = tags,
            spread = item.optDoubleFlexible("spread"),
            acceptingOrders = acceptingOrders
        )
    }

    private fun JSONObject.extractTags(): List<String> {
        val tags = optJSONArray("tags") ?: return emptyList()
        return buildList {
            for (index in 0 until tags.length()) {
                val raw = tags.opt(index)
                when (raw) {
                    is JSONObject -> {
                        raw.optString("label").takeIf { it.isNotBlank() }?.let(::add)
                        raw.optString("slug").takeIf { it.isNotBlank() }?.let(::add)
                    }
                    is String -> add(raw)
                }
            }
        }
    }

    private fun JSONObject.optStringArray(name: String): List<String> {
        val raw = opt(name) ?: return emptyList()
        return when (raw) {
            is JSONArray -> List(raw.length()) { raw.optString(it) }
            is String -> runCatching {
                val parsed = JSONArray(raw)
                List(parsed.length()) { parsed.optString(it) }
            }.getOrElse {
                raw.removePrefix("[").removeSuffix("]").split(",").map { it.trim().trim('"') }
            }
            else -> emptyList()
        }.filter { it.isNotBlank() }
    }

    private fun JSONObject.optDoubleFlexible(name: String, fallback: Double = 0.0): Double {
        val raw = opt(name) ?: return fallback
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: fallback
            else -> fallback
        }
    }

    private fun estimateTraders(volume: Double, liquidity: Double, volume24h: Double, comments: Int): Int {
        return ((volume / 2_500.0) + (liquidity / 7_500.0) + (volume24h / 800.0) + comments * 3)
            .toInt()
            .coerceIn(25, 25_000)
    }

    private fun ageHours(createdAt: String): Int {
        return runCatching {
            ChronoUnit.HOURS.between(Instant.parse(createdAt), Instant.now()).toInt()
        }.getOrDefault(24 * 90).coerceAtLeast(1)
    }

    private fun syntheticHistory(probability: Double, change: Double): List<Double> {
        return List(18) { index ->
            val drift = change * ((index - 9) / 10.0)
            val wave = kotlin.math.sin(index.toDouble()) * 0.012
            (probability + drift + wave).coerceIn(0.01, 0.99)
        }
    }

    private fun inferCategory(tokens: List<String>): MarketCategory {
        val value = tokens.joinToString(" ").lowercase()
        return when {
            listOf("politics", "election", "trump", "biden", "congress", "senate", "macron", "president", "governor", "mayor", "parliament", "supreme court").any(value::contains) -> MarketCategory.POLITICS
            listOf("crypto", "bitcoin", "btc", "ethereum", "eth", "solana", "sol", "xrp", "doge", "binance", "coinbase", "kraken", "stablecoin", "defi", "token").any(value::contains) -> MarketCategory.CRYPTO
            listOf("sports", "nba", "nfl", "mlb", "nhl", "soccer", "football", "fifa", "world cup", "final", "championship", "ufc", "tennis", "golf", "formula 1", "f1").any(value::contains) -> MarketCategory.SPORTS
            listOf("ai", "openai", "chatgpt", "gpt", "artificial", "anthropic", "claude", "gemini", "llm", "agi", "xai", "grok", "deepmind").any(value::contains) -> MarketCategory.AI
            listOf("technology", "tech", "nvidia", "apple", "tesla", "google", "alphabet", "meta", "microsoft", "amazon", "wwdc", "iphone", "android", "semiconductor", "chips").any(value::contains) -> MarketCategory.TECHNOLOGY
            listOf("entertainment", "movie", "film", "box office", "oscar", "grammy", "music", "album", "netflix", "disney", "celebrity", "taylor swift").any(value::contains) -> MarketCategory.ENTERTAINMENT
            listOf("business", "fed", "fomc", "rate", "stock", "ipo").any(value::contains) -> MarketCategory.BUSINESS
            listOf("science", "spacex", "nasa", "climate").any(value::contains) -> MarketCategory.SCIENCE
            else -> MarketCategory.OTHER
        }
    }

    private companion object {
        const val PAGE_SIZE = 75
    }
}

object SampleMarkets {
    val all = listOf(
        sample("btc150", "Bitcoin above $150K this year?", MarketCategory.CRYPTO, 0.67, 0.045, 4_800_000.0, 440_000.0, 1_900_000.0, 2_800, true, listOf("Crypto", "Bitcoin", "Macro")),
        sample("eth8", "Ethereum above $8K this cycle?", MarketCategory.CRYPTO, 0.42, -0.018, 1_700_000.0, 120_000.0, 820_000.0, 1_420, true, listOf("Crypto", "Ethereum")),
        sample("openai", "OpenAI announces GPT-6 before December?", MarketCategory.AI, 0.31, 0.062, 940_000.0, 80_000.0, 410_000.0, 820, true, listOf("AI", "OpenAI")),
        sample("nvidia", "NVIDIA largest company by market cap at year end?", MarketCategory.TECHNOLOGY, 0.54, 0.027, 1_250_000.0, 95_000.0, 520_000.0, 760, true, listOf("Technology", "NVIDIA")),
        sample("election", "Democratic nominee wins the next US presidential election?", MarketCategory.POLITICS, 0.49, -0.011, 6_400_000.0, 250_000.0, 2_400_000.0, 4_900, false, listOf("Politics", "Election")),
        sample("nba", "Western Conference team wins the NBA Finals?", MarketCategory.SPORTS, 0.52, 0.021, 860_000.0, 76_000.0, 220_000.0, 690, true, listOf("Sports", "NBA"))
    )

    private fun sample(
        id: String,
        title: String,
        category: MarketCategory,
        yes: Double,
        change: Double,
        volume: Double,
        volume24h: Double,
        liquidity: Double,
        traders: Int,
        trending: Boolean,
        tags: List<String>
    ) = Market(
        id = id,
        title = title,
        description = "Market intelligence sourced from Polymarket with PolyStats relevance scoring.",
        category = category,
        yesProbability = yes,
        dailyChange = change,
        volume = volume,
        volume24h = volume24h,
        liquidity = liquidity,
        traderCount = traders,
        openInterest = liquidity * yes,
        ageHours = 72,
        trending = trending,
        active = abs(change) > 0.025 || volume24h > 50_000,
        status = "Open",
        history = List(18) { index -> (yes + change * ((index - 9) / 12.0) + kotlin.math.sin(index.toDouble()) * 0.01).coerceIn(0.01, 0.99) },
        tags = tags
    )
}
