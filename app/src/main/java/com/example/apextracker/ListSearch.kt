package com.example.apextracker

/**
 * Case-insensitive substring search shared by the list screens (Issue #123), following the
 * pattern Notes established in Issue #40: filter in the ViewModel over the already-loaded list,
 * not in SQL. A blank query matches everything, so the caller can bind the field directly.
 */
fun matchesQuery(query: String, vararg fields: String?): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return fields.any { it?.contains(trimmed, ignoreCase = true) == true }
}

/** Reminders matching [query] on name or description. */
fun filterReminders(reminders: List<Reminder>, query: String): List<Reminder> =
    if (query.isBlank()) reminders
    else reminders.filter { matchesQuery(query, it.name, it.description) }

/**
 * Budget items matching [query] on title, description, or the name of their category — searching
 * "groceries" should find everything in that category, not just items that say it in the title.
 * Titles go through [budgetItemBaseTitle] so a legacy "[Subscription] " prefix doesn't affect
 * matching either way.
 */
fun filterBudgetItems(items: List<BudgetItem>, categoryNames: Map<Long, String>, query: String): List<BudgetItem> =
    if (query.isBlank()) items
    else items.filter { item ->
        matchesQuery(
            query,
            budgetItemBaseTitle(item.title),
            item.description,
            item.categoryId?.let { categoryNames[it] }
        )
    }
