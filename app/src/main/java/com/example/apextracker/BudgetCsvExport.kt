package com.example.apextracker

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.format.DateTimeFormatter

/** RFC 4180 quoting: wrap in quotes and double any embedded quote if the field contains a comma, quote, or newline. */
internal fun csvEscape(field: String): String {
    val needsQuoting = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    return if (needsQuoting) "\"" + field.replace("\"", "\"\"") + "\"" else field
}

/**
 * Same category-name resolution the Budget UI uses: -1L is the synthetic "Subscriptions" bucket,
 * else a lookup, else blank.
 *
 * The -1L label stays an untranslated literal here rather than moving to
 * `R.string.budget_category_subscriptions` with the UI call sites (Issue #65), for two reasons:
 * CSV is machine-readable output where a stable column value beats a localized one, and taking a
 * Context would cost this function its purity — it's unit-tested in `BudgetCsvExportTest` with no
 * framework. If the export ever should follow the UI's locale, pass the resolved name in as a
 * parameter rather than reaching for a Context in here.
 */
internal fun resolveCategoryName(categoryId: Long?, categories: List<Category>): String = when (categoryId) {
    -1L -> "Subscriptions"
    null -> ""
    else -> categories.find { it.id == categoryId }?.name ?: ""
}

/** Pure CSV builder: `date,title,amount,category,description`, one row per item, RFC 4180 quoted. */
fun buildBudgetCsv(items: List<BudgetItem>, categories: List<Category>): String {
    val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE
    val header = "date,title,amount,category,description"
    val rows = items.map { item ->
        listOf(
            item.date.format(dateFormat),
            csvEscape(item.title),
            item.amount.toString(),
            csvEscape(resolveCategoryName(item.categoryId, categories)),
            csvEscape(item.description ?: "")
        ).joinToString(",")
    }
    return (listOf(header) + rows).joinToString("\n")
}

/** Writes the CSV to the app cache dir and launches a share sheet via FileProvider — no storage permissions needed. */
fun shareBudgetCsv(context: Context, csv: String, fileName: String) {
    val exportDir = File(context.cacheDir, "csv_exports").apply { mkdirs() }
    val file = File(exportDir, fileName)
    file.writeText(csv)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, fileName))
}
