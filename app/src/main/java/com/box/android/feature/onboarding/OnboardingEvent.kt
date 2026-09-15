package com.box.android.feature.onboarding

sealed interface OnboardingEvent {
    data class PageChanged(val pageIndex: Int) : OnboardingEvent
    data object NextClicked : OnboardingEvent
    data object SkipClicked : OnboardingEvent
    data object GetStartedClicked : OnboardingEvent
}
