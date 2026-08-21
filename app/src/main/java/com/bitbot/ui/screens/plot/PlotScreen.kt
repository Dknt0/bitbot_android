package com.bitbot.ui.screens.plot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import com.bitbot.data.model.ConnectionState
import com.bitbot.data.plot.PlotChannel
import com.bitbot.ui.screens.plot.components.LegendEntry
import com.bitbot.ui.screens.plot.components.PlotCanvas
import com.bitbot.ui.screens.plot.components.PlotFrame
import com.bitbot.ui.screens.plot.components.PlotRenderer
import com.bitbot.ui.screens.plot.components.PlotSeries
import com.bitbot.ui.screens.plot.components.PlotViewState
import com.bitbot.ui.screens.plot.components.autoFitY
import com.bitbot.util.Constants

@Composable
fun PlotScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlotViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    var userDisconnecting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val viewState = remember { PlotViewState() }
    val hiddenKeys = remember { mutableStateListOf<String>() }
    var plotWidthPx by remember { mutableIntStateOf(1) }
    var plotHeightPx by remember { mutableIntStateOf(1) }
    val densityScale = LocalDensity.current.density

    var showChannelPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.dismissSaveMessage()
        }
    }

    if (connectionState !is ConnectionState.Connected && !userDisconnecting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Connection Lost", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.disconnect(); onNavigateBack() }) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    fun seriesProvider(): List<PlotSeries> = uiState.selected
        .filter { it.key !in hiddenKeys }
        .mapIndexed { i, ch ->
            val color = uiState.selected.indexOf(ch).let { Constants.Plot.colorFor(it) }
            PlotSeries(Color(color), viewModel.seriesFor(ch))
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // --- Top bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 2.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { userDisconnecting = true; viewModel.disconnect(); onNavigateBack() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
            Spacer(Modifier.width(4.dp))
            Text("Connected", fontSize = 10.sp, color = Color(0xFF4CAF50))

            Spacer(Modifier.weight(1f))

            // Status
            if (uiState.isRecording) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF44336)))
                Spacer(Modifier.width(4.dp))
                Text(
                    "REC ${uiState.rateHz}Hz  idx ${uiState.sampleIdx}",
                    fontSize = 11.sp,
                    color = Color(0xFFF44336),
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    "Paused  idx ${uiState.sampleIdx}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.width(8.dp))

            // Save actions — visible only when stopped and data exists
            if (!uiState.isRecording && uiState.hasData) {
                IconButton(onClick = viewModel::saveCsv, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Save, "Save CSV", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        val frame = buildFrame(viewState, viewModel.xSeries(), ::seriesProvider)
                        val legend = uiState.selected
                            .filter { it.key !in hiddenKeys }
                            .map { ch ->
                                val colorIdx = uiState.selected.indexOf(ch)
                                LegendEntry(
                                    Constants.Plot.colorFor(colorIdx).toInt(),
                                    "${ch.group}.${ch.name}"
                                )
                            }
                        val bmp = PlotRenderer.renderToBitmap(
                            frame, plotWidthPx, plotHeightPx, densityScale, legend
                        )
                        viewModel.savePng(bmp)
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Image, "Save PNG", modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = viewModel::clearData, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Delete, "Clear", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { showChannelPicker = true },
                modifier = Modifier.size(34.dp),
                enabled = uiState.registry.isNotEmpty()
            ) {
                Icon(Icons.Default.Checklist, "Select channels", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showSettings = true }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Tune, "Plot settings", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = viewModel::toggleRecording,
                modifier = Modifier.size(38.dp),
                enabled = uiState.selected.isNotEmpty()
            ) {
                Icon(
                    if (uiState.isRecording) Icons.Default.FiberManualRecord else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isRecording) "Stop recording" else "Record",
                    modifier = Modifier.size(26.dp),
                    tint = if (uiState.isRecording) Color(0xFFF44336)
                    else MaterialTheme.colorScheme.primary
                )
            }
        }

        // --- Save hint row when stopped with data ---
        if (!uiState.isRecording && uiState.hasData) {
            Text(
                "Recording stopped — save (CSV / PNG) available, drag to inspect.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp, bottom = 2.dp)
            )
        }

        // --- Plot area ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            if (uiState.selected.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (uiState.registry.isEmpty()) "Waiting for headers…"
                        else "No channels selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.registry.isNotEmpty()) {
                        Text(
                            "Tap the checklist icon to choose channels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                PlotCanvas(
                    state = viewState,
                    versionFlow = viewModel.version,
                    horizonSpanX = uiState.horizonSpanX,
                    xsProvider = viewModel::xSeries,
                    seriesProvider = ::seriesProvider,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged {
                            plotWidthPx = it.width
                            plotHeightPx = it.height
                        }
                )
            }

            // View-mode overlay chips
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ViewModeChip("FOLLOW", viewState.followX) { viewState.followX = true }
                ViewModeChip("AUTO Y", viewState.autoY) { viewState.autoY = true }
                ViewModeChip("RESET", false) { viewState.reset() }
            }
        }

        // --- Legend ---
        if (uiState.selected.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uiState.selected.forEachIndexed { i, ch ->
                    val hidden = ch.key in hiddenKeys
                    LegendChip(
                        label = "${ch.group}.${ch.name}",
                        color = Color(Constants.Plot.colorFor(i)),
                        hidden = hidden,
                        modifier = Modifier.alpha(if (hidden) 0.35f else 1f)
                    ) {
                        if (hidden) hiddenKeys.remove(ch.key) else hiddenKeys.add(ch.key)
                    }
                }
            }
        }
    }

    if (showChannelPicker) {
        ChannelPickerDialog(
            registry = uiState.registry,
            initialSelection = uiState.selected.map { it.key },
            onConfirm = { keys ->
                viewModel.setSelectedKeys(keys)
                showChannelPicker = false
            },
            onDismiss = { showChannelPicker = false }
        )
    }

    if (showSettings) {
        PlotSettingsDialog(
            rateHz = uiState.rateHz,
            horizonSeconds = uiState.horizonSeconds,
            onRateChange = viewModel::setRate,
            onHorizonChange = viewModel::setHorizon,
            onDismiss = { showSettings = false }
        )
    }
}

/** Freeze the current view (or auto-fit) into a frame for PNG export. */
private fun buildFrame(
    state: PlotViewState,
    xs: List<Double>,
    seriesProvider: () -> List<PlotSeries>
): PlotFrame {
    val series = seriesProvider()
    var yMin = state.yMin
    var yMax = state.yMax
    if (state.autoY) {
        autoFitY(series, xs, state.xEnd - state.xSpan, state.xEnd)?.let { (lo, hi) ->
            yMin = lo; yMax = hi
        }
    }
    return PlotFrame(state.xEnd, state.xSpan, yMin, yMax, xs, series)
}

@Composable
private fun ViewModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun LegendChip(
    label: String,
    color: Color,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 10.sp, maxLines = 1)
        }
    }
}

/**
 * Full-screen channel picker. Channels are grouped (kernel / each device /
 * extra) into collapsible tree nodes — tap a group to reveal its channels.
 * Group rows offer a tri-state select-all checkbox; the search box switches
 * to a flat filtered list across all groups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelPickerDialog(
    registry: List<PlotChannel>,
    initialSelection: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // Local ordered selection: existing order preserved, new picks appended
    val selectedKeys = remember { mutableStateListOf<String>().also { it.addAll(initialSelection) } }
    var search by remember { mutableStateOf("") }
    val expandedGroups = remember { mutableStateListOf<String>() }

    val groups: Map<String, List<PlotChannel>> = remember(registry) {
        registry.groupBy { it.group } // LinkedHashMap preserves registry order
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Select channels (${selectedKeys.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onConfirm(selectedKeys.toList()) }) { Text("OK") }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search (flat list)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))

                if (search.isNotBlank()) {
                    // Flat search results across all groups
                    val matches = remember(search, registry) {
                        registry.filter {
                            it.name.contains(search, true) || it.group.contains(search, true)
                        }
                    }
                    Text(
                        "${matches.size} matches",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(matches.size) { i ->
                            val ch = matches[i]
                            CheckRow(
                                title = ch.name,
                                subtitle = ch.group,
                                checked = ch.key in selectedKeys
                            ) {
                                if (ch.key in selectedKeys) selectedKeys.remove(ch.key)
                                else selectedKeys.add(ch.key)
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        groups.forEach { (group, channels) ->
                            val selectedCount = channels.count { it.key in selectedKeys }
                            item(key = "group_$group") {
                                val expanded = group in expandedGroups
                                val toggleState = when {
                                    selectedCount == channels.size -> ToggleableState.On
                                    selectedCount == 0 -> ToggleableState.Off
                                    else -> ToggleableState.Indeterminate
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 4.dp)
                                ) {
                                    TriStateCheckbox(
                                        state = toggleState,
                                        onClick = {
                                            val selectAll = toggleState != ToggleableState.On
                                            channels.forEach { ch ->
                                                selectedKeys.remove(ch.key)
                                                if (selectAll) selectedKeys.add(ch.key)
                                            }
                                        }
                                    )
                                    Text(
                                        "$group  ($selectedCount/${channels.size})",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                if (expanded) expandedGroups.remove(group)
                                                else expandedGroups.add(group)
                                            }
                                    )
                                    IconButton(onClick = {
                                        if (expanded) expandedGroups.remove(group)
                                        else expandedGroups.add(group)
                                    }) {
                                        Icon(
                                            if (expanded) Icons.Default.KeyboardArrowDown
                                            else Icons.Default.KeyboardArrowRight,
                                            contentDescription = if (expanded) "Collapse" else "Expand",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            if (group in expandedGroups) {
                                items(channels.size, key = { "ch_${group}_$it" }) { i ->
                                    val ch = channels[i]
                                    CheckRow(
                                        title = ch.name,
                                        subtitle = null,
                                        checked = ch.key in selectedKeys,
                                        indent = true
                                    ) {
                                        if (ch.key in selectedKeys) selectedKeys.remove(ch.key)
                                        else selectedKeys.add(ch.key)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    indent: Boolean = false,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = if (indent) 28.dp else 4.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(4.dp))
        if (subtitle != null) {
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
        }
        Text(title, fontSize = 13.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlotSettingsDialog(
    rateHz: Int,
    horizonSeconds: Int,
    onRateChange: (Int) -> Unit,
    onHorizonChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plot settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sample rate", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Constants.Plot.RATE_CHOICES_HZ.forEach { rate ->
                        FilterChip(
                            selected = rate == rateHz,
                            onClick = { onRateChange(rate) },
                            label = { Text("$rate Hz") }
                        )
                    }
                }
                Text(
                    "Applies immediately; also drives the shared data-request rate " +
                        "(the plot never conflicts with the Data panel — one loop serves both).",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Time horizon: ${horizonSeconds}s", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = horizonSeconds.toFloat(),
                    onValueChange = { onHorizonChange(it.toInt()) },
                    valueRange = Constants.Plot.MIN_HORIZON_SECONDS.toFloat()..
                        Constants.Plot.MAX_HORIZON_SECONDS.toFloat()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
