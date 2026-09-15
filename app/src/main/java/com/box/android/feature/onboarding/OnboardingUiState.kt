package com.box.android.feature.onboarding

import com.box.android.feature.onboarding.model.OnboardingPage
import com.box.android.feature.onboarding.model.OnboardingPages

data class OnboardingUiState(
    val pages: List<OnboardingPage> = OnboardingPages.list,
    val currentPageIndex: Int = 0
) {
    val totalPages: Int get() = pages.size
    val isLastPage: Boolean get() = currentPageIndex == totalPages - 1
    val isFirstPage: Boolean get() = currentPageIndex == 0
}
