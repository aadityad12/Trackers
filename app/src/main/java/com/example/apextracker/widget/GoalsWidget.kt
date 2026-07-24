package com.example.apextracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.apextracker.AppDatabase
import com.example.apextracker.GoalStatus
import com.example.apextracker.MainActivity
import com.example.apextracker.R
import com.example.apextracker.loadDashboardSnapshot
import java.time.LocalDate

/**
 * Home-screen widget listing today's goals with their met/unmet state (Issue #131). Read-only —
 * checking off happens in the app (a widget can't host the app's ViewModel toggle path safely), so
 * tapping opens the app. Shares [loadDashboardSnapshot] with the streak widget and the Dashboard.
 */
class GoalsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getDatabase(context)
        val snapshot = loadDashboardSnapshot(db, LocalDate.now())
        provideContent {
            GoalsWidgetContent(snapshot.today, context)
        }
    }
}

private val accent = ColorProvider(Color(0xFF34C77B))
private val onBg = ColorProvider(Color(0xFFE6E6E6))
private val muted = ColorProvider(Color(0xFF8A9299))
private val bg = ColorProvider(Color(0xFF14181B))

@Composable
private fun GoalsWidgetContent(goals: List<GoalStatus>, context: Context) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Text(
            text = context.getString(R.string.widget_goals_title),
            style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )
        if (goals.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_goals_empty),
                style = TextStyle(color = muted, fontSize = 13.sp),
                modifier = GlanceModifier.padding(top = 8.dp)
            )
        } else {
            LazyColumn(modifier = GlanceModifier.padding(top = 6.dp)) {
                items(goals) { status ->
                    GoalRow(status)
                }
            }
        }
    }
}

@Composable
private fun GoalRow(status: GoalStatus) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = if (status.satisfied) "✓" else "○",
            style = TextStyle(color = if (status.satisfied) accent else muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        )
        Text(
            text = "  " + status.goal.name,
            style = TextStyle(
                color = if (status.satisfied) muted else onBg,
                fontSize = 14.sp
            )
        )
    }
}

class GoalsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalsWidget()
}
