package com.box.android.feature.faq

data class FaqUiState(
    val searchQuery: String = "",
    val selectedCategory: FaqCategory = FaqCategory.ALL,
    val expandedItemIds: Set<String> = emptySet(),
    val allItems: List<FaqItem> = DEFAULT_FAQ_ITEMS
)
