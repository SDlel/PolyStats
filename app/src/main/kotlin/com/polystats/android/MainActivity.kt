package com.polystats.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.polystats.android.data.repository.MarketUiState
import com.polystats.android.domain.Market
import com.polystats.android.domain.MarketCategory
import com.polystats.android.domain.RankedMarket
import com.polystats.android.domain.ScoringWeights
import com.polystats.android.services.MarketMonitorService
import com.polystats.android.ui.PolyStatsViewModel
import com.polystats.android.ui.theme.PolyStatsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.text.DateFormat
import java.util.Date

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<PolyStatsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PolyStatsTheme {
                val state by viewModel.uiState.collectAsState()
                PolyStatsApp(state, viewModel)
            }
        }
    }

    companion object {
        const val EXTRA_MARKET_ID = "market_id"
        const val EXTRA_OPEN_PREFERENCES = "open_preferences"
    }
}

@Composable
private fun PolyStatsApp(state: MarketUiState, viewModel: PolyStatsViewModel) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ContextCompat.startForegroundService(context, Intent(context, MarketMonitorService::class.java))
    }
    LaunchedEffect(Unit) {
        if (MarketMonitorService.isRunning(context)) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ContextCompat.startForegroundService(context, Intent(context, MarketMonitorService::class.java))
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PolyStats", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (state.lastUpdatedAt > 0L) "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.lastUpdatedAt))}" else "Real-Time Polymarket Intelligence",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { HeroNowBar(state, viewModel) }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = Color(0xFF1A1A2E)) }
            if (state.liveEventMode) item { LiveEventCard(state.nowBarMarkets.firstOrNull()) }
            item { SearchAndProfiles(state, viewModel) }
            item { PreferenceConsole(state, viewModel) }
            items(state.homeMarkets, key = { it.market.id }) { ranked ->
                MarketCard(ranked, state, viewModel)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HeroNowBar(state: MarketUiState, viewModel: PolyStatsViewModel) {
    val top = state.nowBarMarkets.firstOrNull()
    val market = top?.market
    GlassPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF8B6DFF), Color(0xFF22C55E)))),
                contentAlignment = Alignment.Center
            ) {
                Text("PS", fontWeight = FontWeight.Black, color = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("Samsung Now Bar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(market?.title ?: "Finding your best market", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(market?.let { "YES ${percent(it.yesProbability)}  NO ${percent(it.noProbability)} | ${it.category.label} | Score ${"%.2f".format(top.score)}" } ?: "Personalized from filters and preferences", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = state.userState.rotationEnabled,
                onCheckedChange = viewModel::setRotationEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF22C55E),
                    uncheckedThumbColor = Color(0xFF888888),
                    uncheckedTrackColor = Color(0xFF2A2A2E)
                )
            )
        }
    }
}

@Composable
private fun LiveEventCard(ranked: RankedMarket?) {
    GlassPanel(borderColor = Color(0xFF22C55E).copy(alpha = 0.6f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.AddAlert, contentDescription = null, tint = Color(0xFF22C55E))
            Column {
                Text("Live Event Mode", fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                Text(ranked?.market?.let { "${it.title} | YES ${percent(it.yesProbability)}  NO ${percent(it.noProbability)}" } ?: "High activity market detected", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SearchAndProfiles(state: MarketUiState, viewModel: PolyStatsViewModel) {
    var searchText by rememberSaveable { mutableStateOf(state.query) }

    LaunchedEffect(state.query) {
        if (state.query.isEmpty() && searchText.isNotEmpty()) {
            searchText = ""
        }
    }

    LaunchedEffect(searchText) {
        delay(220)
        if (searchText != state.query) {
            viewModel.updateQuery(searchText)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (searchText.isNotBlank()) IconButton(onClick = {
                    searchText = ""
                    viewModel.updateQuery("")
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            singleLine = true,
            placeholder = { Text("Search markets, tags, categories", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFF2A2A2E),
                focusedContainerColor = Color(0xFF0A0A0A),
                unfocusedContainerColor = Color(0xFF0A0A0A)
            ),
            shape = RoundedCornerShape(12.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MarketCategory.entries.take(6).forEach { category ->
                AssistChip(
                    onClick = { viewModel.toggleCategory(category) },
                    label = { Text(category.label) },
                    leadingIcon = if (category in state.userState.enabledCategories) ({ Icon(Icons.Default.Star, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary) }) else null,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (category in state.userState.enabledCategories) Color(0xFF1A1A2E) else Color(0xFF0E0E12),
                        labelColor = if (category in state.userState.enabledCategories) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                     border = if (category in state.userState.enabledCategories) BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else BorderStroke(0.5.dp, Color(0xFF2A2A2E))
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.profiles.forEach { profile ->
                ElevatedAssistChip(
                    onClick = { viewModel.setProfile(profile.id) },
                    label = { Text(profile.name) },
                    leadingIcon = if (profile.id == state.activeProfile.id) ({ Icon(Icons.Default.Star, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary) }) else null
                )
            }
        }
    }
}

@Composable
private fun PreferenceConsole(state: MarketUiState, viewModel: PolyStatsViewModel) {
    var showWeights by remember { mutableStateOf(false) }
    GlassPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(state.activeProfile.name, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${state.activeProfile.discoveryMode.label} discovery | Now Bar filter: volume and trader thresholds applied", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { showWeights = !showWeights },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Weights") }
        }
        AnimatedContent(showWeights, label = "weights") { expanded ->
            if (expanded) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeightRow("Volume", state.activeProfile.scoringWeights.volume) {
                        viewModel.setScoringWeights(state.activeProfile.scoringWeights.copy(volume = it))
                    }
                    WeightRow("Liquidity", state.activeProfile.scoringWeights.liquidity) {
                        viewModel.setScoringWeights(state.activeProfile.scoringWeights.copy(liquidity = it))
                    }
                    WeightRow("Trending", state.activeProfile.scoringWeights.trend) {
                        viewModel.setScoringWeights(state.activeProfile.scoringWeights.copy(trend = it))
                    }
                    WeightRow("Activity", state.activeProfile.scoringWeights.activity) {
                        viewModel.setScoringWeights(state.activeProfile.scoringWeights.copy(activity = it))
                    }
                    WeightRow("User Interest", state.activeProfile.scoringWeights.userInterest) {
                        viewModel.setScoringWeights(state.activeProfile.scoringWeights.copy(userInterest = it))
                    }
                    RotationRow(state.userState.rotationMinutes, viewModel::setRotationMinutes)
                }
            }
        }
    }
}

@Composable
private fun WeightRow(label: String, value: Double, onChange: (Double) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White)
            Text("${(value * 100).roundToInt()}%", color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color(0xFF2A2A2E)
            )
        )
    }
}

@Composable
private fun RotationRow(minutes: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Now Bar Rotation", color = Color.White)
            Text("$minutes min", color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = minutes.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(1, 15)) },
            valueRange = 1f..15f,
            steps = 13,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color(0xFF2A2A2E)
            )
        )
    }
}

@Composable
private fun MarketCard(ranked: RankedMarket, state: MarketUiState, viewModel: PolyStatsViewModel) {
    val market = ranked.market
    val favorite = market.id in state.userState.favoriteIds
    val pinned = market.id in state.userState.pinnedIds
    GlassPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(market.category.label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        if (market.trending) Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                    }
                    Text(market.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
                ProbabilityPill(market.yesProbability)
            }
            MiniChart(market.history, Modifier.fillMaxWidth().height(54.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Metric("YES", percent(market.yesProbability), Color(0xFF22C55E))
                Metric("NO", percent(market.noProbability), Color(0xFFEF4444))
                Metric("24H", signedPercent(market.dailyChange), if (market.dailyChange >= 0) Color(0xFF22C55E) else Color(0xFFEF4444))
                Metric("Volume", money(market.volume))
                Metric("Liquidity", money(market.liquidity))
                Metric("Traders", market.traderCount.toString())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Favorite", favorite, Icons.Default.Favorite) { viewModel.toggleFavorite(market.id) }
                ActionChip("Pin", pinned, Icons.Default.PushPin) { viewModel.togglePinned(market.id) }
                ActionChip("Now Bar", state.userState.lockedNowBarMarketId == market.id, Icons.Default.AddAlert) {
                    viewModel.lockNowBar(if (state.userState.lockedNowBarMarketId == market.id) null else market.id)
                }
                ActionChip("Mute", false, Icons.Default.Block) { viewModel.toggleMuted(market.id) }
            }
            Text(market.description, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionChip(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(16.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) Color(0xFF1A1A2E) else Color(0xFF0E0E12)
        ),
        border = if (selected) BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else BorderStroke(0.5.dp, Color(0xFF2A2A2E))
    )
}

@Composable
private fun ProbabilityPill(value: Double) {
    val animated by animateFloatAsState(value.toFloat(), label = "probability")
    Surface(color = Color(0xFF0E1210), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("YES", style = MaterialTheme.typography.labelSmall, color = Color(0xFF22C55E))
            Text(percent(animated.toDouble()), fontWeight = FontWeight.Black, color = Color.White)
            Text("NO ${percent(1.0 - animated.toDouble())}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color = Color.White) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniChart(values: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.lastIndex)
            val y = size.height - (value.toFloat().coerceIn(0f, 1f) * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF8B6DFF), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        values.lastOrNull()?.let {
            drawCircle(Color(0xFF22C55E), radius = 4.dp.toPx(), center = Offset(size.width, size.height - it.toFloat() * size.height))
        }
    }
}

@Composable
private fun GlassPanel(
    borderColor: Color = Color.White.copy(alpha = 0.08f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF080808)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

private fun percent(value: Double) = "${(value * 100).roundToInt()}%"
private fun signedPercent(value: Double): String {
    val pct = value * 100
    return "${if (pct >= 0) "+" else ""}${"%.1f".format(pct)}%"
}
private fun money(value: Double): String = when {
    value >= 1_000_000 -> "$${"%.1f".format(value / 1_000_000)}M"
    value >= 1_000 -> "$${"%.0f".format(value / 1_000)}K"
    else -> "$${value.roundToInt()}"
}
