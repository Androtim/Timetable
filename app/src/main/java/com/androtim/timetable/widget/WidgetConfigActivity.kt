package com.androtim.timetable.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.androtim.timetable.R
import com.androtim.timetable.ui.theme.TimetableTheme

/**
 * Per-widget settings sheet, reachable on One UI via long-press → Settings
 * (and shown when a new widget is placed). Options are stored per widget id.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Cancelled result until explicitly saved — required by the widget host.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val prefs = getSharedPreferences(TimetableWidgetProvider.STATE_PREFS, Context.MODE_PRIVATE)

        setContent {
            TimetableTheme {
                var bgIndex by rememberSaveable {
                    mutableIntStateOf(
                        prefs.getInt(
                            TimetableWidgetProvider.bgKey(widgetId),
                            TimetableWidgetProvider.DEFAULT_BG_INDEX
                        )
                    )
                }
                var scaleIndex by rememberSaveable {
                    mutableIntStateOf(
                        prefs.getInt(
                            TimetableWidgetProvider.scaleKey(widgetId),
                            TimetableWidgetProvider.DEFAULT_SCALE_INDEX
                        )
                    )
                }
                var cardIndex by rememberSaveable {
                    mutableIntStateOf(
                        prefs.getInt(
                            TimetableWidgetProvider.cardKey(widgetId),
                            TimetableWidgetProvider.DEFAULT_CARD_INDEX
                        )
                    )
                }

                Scaffold { padding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        Text(stringResource(R.string.widget_settings), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))

                        Text(stringResource(R.string.bg_opacity), style = MaterialTheme.typography.titleMedium)
                        RadioGroup(
                            options = listOf(
                                stringResource(R.string.opacity_opaque),
                                stringResource(R.string.opacity_standard),
                                stringResource(R.string.opacity_semi),
                                stringResource(R.string.opacity_very),
                                stringResource(R.string.opacity_full),
                            ),
                            selected = bgIndex,
                            onSelect = { bgIndex = it },
                        )

                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.text_size), style = MaterialTheme.typography.titleMedium)
                        RadioGroup(
                            options = listOf(
                                stringResource(R.string.size_small),
                                stringResource(R.string.size_medium),
                                stringResource(R.string.size_large),
                            ),
                            selected = scaleIndex,
                            onSelect = { scaleIndex = it },
                        )

                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.card_color), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.card_color_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        RadioGroup(
                            options = listOf(
                                stringResource(R.string.color_slate),
                                stringResource(R.string.color_charcoal),
                                stringResource(R.string.color_indigo),
                                stringResource(R.string.color_teal),
                                stringResource(R.string.color_purple),
                            ),
                            selected = cardIndex,
                            onSelect = { cardIndex = it },
                            swatches = listOf(
                                Color(0xFF1F2430),
                                Color(0xFF2A2A2A),
                                Color(0xFF303F9F),
                                Color(0xFF00695C),
                                Color(0xFF4A148C),
                            ),
                        )

                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                prefs.edit()
                                    .putInt(TimetableWidgetProvider.bgKey(widgetId), bgIndex)
                                    .putInt(TimetableWidgetProvider.scaleKey(widgetId), scaleIndex)
                                    .putInt(TimetableWidgetProvider.cardKey(widgetId), cardIndex)
                                    .apply()
                                TimetableWidgetProvider.requestUpdate(this@WidgetConfigActivity, widgetId)
                                setResult(
                                    RESULT_OK,
                                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                )
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.done))
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RadioGroup(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    swatches: List<Color>? = null,
) {
    options.forEachIndexed { index, label ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onSelect(index) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = index == selected, onClick = { onSelect(index) })
            swatches?.getOrNull(index)?.let { color ->
                Box(
                    Modifier
                        .size(20.dp)
                        .background(color, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(label)
        }
    }
}
