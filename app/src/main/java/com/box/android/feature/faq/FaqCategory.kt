package com.box.android.feature.faq

import androidx.annotation.StringRes
import com.box.android.R

enum class FaqCategory(@StringRes val labelRes: Int) {
    ALL(R.string.faq_cat_all),
    GENERAL(R.string.faq_cat_general),
    PACKAGES(R.string.faq_cat_packages),
    STORAGE(R.string.faq_cat_storage),
    RUNTIME(R.string.faq_cat_runtime),
    NETWORK(R.string.faq_cat_network)
}
