package com.box.android.feature.faq

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.box.android.R

@Immutable
data class FaqItem(
    val id: String,
    val category: FaqCategory,
    @StringRes val questionRes: Int,
    @StringRes val answerRes: Int
)

val DEFAULT_FAQ_ITEMS: List<FaqItem> = listOf(
    FaqItem(
        id = "faq_1",
        category = FaqCategory.GENERAL,
        questionRes = R.string.faq_q1,
        answerRes = R.string.faq_a1
    ),
    FaqItem(
        id = "faq_2",
        category = FaqCategory.GENERAL,
        questionRes = R.string.faq_q2,
        answerRes = R.string.faq_a2
    ),
    FaqItem(
        id = "faq_3",
        category = FaqCategory.PACKAGES,
        questionRes = R.string.faq_q3,
        answerRes = R.string.faq_a3
    ),
    FaqItem(
        id = "faq_4",
        category = FaqCategory.PACKAGES,
        questionRes = R.string.faq_q4,
        answerRes = R.string.faq_a4
    ),
    FaqItem(
        id = "faq_5",
        category = FaqCategory.STORAGE,
        questionRes = R.string.faq_q5,
        answerRes = R.string.faq_a5
    ),
    FaqItem(
        id = "faq_6",
        category = FaqCategory.STORAGE,
        questionRes = R.string.faq_q6,
        answerRes = R.string.faq_a6
    ),
    FaqItem(
        id = "faq_7",
        category = FaqCategory.RUNTIME,
        questionRes = R.string.faq_q7,
        answerRes = R.string.faq_a7
    ),
    FaqItem(
        id = "faq_8",
        category = FaqCategory.RUNTIME,
        questionRes = R.string.faq_q8,
        answerRes = R.string.faq_a8
    ),
    FaqItem(
        id = "faq_9",
        category = FaqCategory.NETWORK,
        questionRes = R.string.faq_q9,
        answerRes = R.string.faq_a9
    )
)
