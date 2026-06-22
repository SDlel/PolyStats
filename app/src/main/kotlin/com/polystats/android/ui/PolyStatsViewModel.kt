package com.polystats.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polystats.android.data.repository.MarketRepository
import com.polystats.android.domain.MarketCategory
import com.polystats.android.domain.ScoringWeights
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PolyStatsViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {
    val uiState = repository.uiState

    fun refresh() = repository.refresh()
    fun updateQuery(value: String) = repository.updateQuery(value)
    fun setProfile(id: String) = viewModelScope.launch { repository.setProfile(id) }
    fun toggleFavorite(id: String) = viewModelScope.launch { repository.toggleFavorite(id) }
    fun togglePinned(id: String) = viewModelScope.launch { repository.togglePinned(id) }
    fun toggleMuted(id: String) = viewModelScope.launch { repository.toggleMuted(id) }
    fun hide(id: String) = viewModelScope.launch { repository.hide(id) }
    fun lockNowBar(id: String?) = viewModelScope.launch { repository.lockNowBar(id) }
    fun setRotationEnabled(enabled: Boolean) = viewModelScope.launch { repository.setRotationEnabled(enabled) }
    fun setRotationMinutes(minutes: Int) = viewModelScope.launch { repository.setRotationMinutes(minutes) }
    fun toggleCategory(category: MarketCategory) = viewModelScope.launch { repository.toggleCategory(category) }
    fun setScoringWeights(weights: ScoringWeights) = viewModelScope.launch { repository.setScoringWeights(weights) }
}
