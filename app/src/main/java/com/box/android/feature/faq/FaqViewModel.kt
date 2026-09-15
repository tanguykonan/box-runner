package com.box.android.feature.faq

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface FaqEvent {
    data class SearchQueryChanged(val query: String) : FaqEvent
    data class CategorySelected(val category: FaqCategory) : FaqEvent
    data class ToggleItemExpanded(val itemId: String) : FaqEvent
    data object ClearSearch : FaqEvent
}

class FaqViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FaqUiState())
    val uiState: StateFlow<FaqUiState> = _uiState.asStateFlow()

    fun onEvent(event: FaqEvent) {
        when (event) {
            is FaqEvent.SearchQueryChanged -> {
                _uiState.update { it.copy(searchQuery = event.query) }
            }
            is FaqEvent.CategorySelected -> {
                _uiState.update { it.copy(selectedCategory = event.category) }
            }
            is FaqEvent.ToggleItemExpanded -> {
                _uiState.update { current ->
                    val nextExpanded = if (current.expandedItemIds.contains(event.itemId)) {
                        current.expandedItemIds - event.itemId
                    } else {
                        current.expandedItemIds + event.itemId
                    }
                    current.copy(expandedItemIds = nextExpanded)
                }
            }
            is FaqEvent.ClearSearch -> {
                _uiState.update { it.copy(searchQuery = "") }
            }
        }
    }
}
