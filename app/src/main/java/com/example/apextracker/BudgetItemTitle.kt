package com.example.apextracker

/** The category id every subscription-derived budget item carries — see `subscriptionsCategory()`. */
const val SUBSCRIPTION_CATEGORY_ID = -1L

/**
 * The English prefix `checkAndAddSubscriptions()` used to bake into auto-created budget-item
 * titles before Issue #119. Rows written by older builds (and their Firestore copies) still carry
 * it, so it has to be stripped at render time — the label is now composed from strings.xml
 * instead.
 */
const val LEGACY_SUBSCRIPTION_TITLE_PREFIX = "[Subscription] "

/**
 * The stored title with any legacy prefix removed. Pure so both the prefix-stripping and the
 * "is this an auto item" test have one home; the localized prefix itself is applied by the UI.
 */
fun budgetItemBaseTitle(title: String): String = title.removePrefix(LEGACY_SUBSCRIPTION_TITLE_PREFIX)

/** True when [item] was auto-created from a subscription rather than entered by the user. */
fun isSubscriptionItem(item: BudgetItem): Boolean = item.categoryId == SUBSCRIPTION_CATEGORY_ID
